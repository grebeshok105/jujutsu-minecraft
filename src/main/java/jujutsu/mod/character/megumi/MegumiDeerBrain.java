package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.character.megumi.MegumiDeerPolicy.WoundedFacts;
import jujutsu.mod.character.megumi.MegumiDeerPolicy.WoundedKind;

/**
 * The Round Deer's support loop (spec §7). Four independent sub-ticks:
 *
 * <ul>
 *   <li><b>ANTLER_SHOVE</b> — a cornered defensive reflex: only the deer's own fresh aggressor,
 *       and only when it is hostile to the owner too, gets a weak push. Never a chase, never
 *       damage — the deer is not a damage dealer.</li>
 *   <li><b>HEAL</b> — a scan every {@code DEER_HEAL_SCAN_TICKS} arms a pulse channel
 *       ({@code beginAction(DEER_HEAL_ACTION_TICKS)}); when the window closes the pulse lands,
 *       emitting {@code DEER_PULSE} at the recipient. The scan clock advances whether or not
 *       anyone qualified — scanning is the cadence, not the heal.</li>
 *   <li><b>CLEANSE</b> — a slower scan strips only {@link MegumiDeerPolicy#cleansable} effects
 *       from the highest-priority carrier; internal hold/marker effects are never touched.</li>
 *   <li><b>INTERPOSE</b> — positioning between the owner and whatever the pack reads as the
 *       threat (the deer's own mark or the coordinator's ally-threat map). Awareness only:
 *       the deer never attacks the marked target.</li>
 * </ul>
 */
final class MegumiDeerBrain {

	/** Re-issue the interpose path only when the anchor drifts this far (squared). */
	private static final double INTERPOSE_REISSUE_SQR = 0.25;
	/** The deer is "standing" on the interpose point within this distance (squared). */
	private static final double INTERPOSE_ARRIVED_SQR = 1.0;

	private MegumiDeerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiDeerEntity deer, long gameTime) {
		tickAntlerShove(level, owner, deer, gameTime);
		tickHeal(level, owner, deer, gameTime);
		tickCleanse(level, owner, deer, gameTime);
		tickInterpose(level, owner, deer);
	}

	// — Antler shove ———————————————————————————————————————————————————————

	/**
	 * The cornered reflex: the deer's most recent attacker, if it is still hostile to the owner
	 * and standing inside {@code DEER_ANTLER_RANGE}, is shoved away weakly. An attacker that is
	 * not hostile (a teamed body, an own-side body gone wrong) is never answered.
	 */
	private static void tickAntlerShove(ServerLevel level, ServerPlayer owner,
			MegumiDeerEntity deer, long gameTime) {
		if (!MegumiDeerPolicy.shoveReady(gameTime, deer.antlerCooldownUntil())) {
			return;
		}
		LivingEntity aggressor = deer.getLastHurtByMob();
		if (aggressor == null || aggressor == deer || !aggressor.isAlive() || aggressor.isRemoved()
				|| !MegumiHostilityPolicy.aggressorFresh(
						deer.tickCount, deer.getLastHurtByMobTimestamp())) {
			return;
		}
		LivingEntity anchor = owner != null ? owner : deer;
		if (!MegumiHostilityPolicy.isHostile(anchor, aggressor)
				|| deer.distanceTo(aggressor) > MegumiShikigamiProfile.DEER_ANTLER_RANGE) {
			return;
		}
		Vec3 shove = MegumiDeerPolicy.antlerKnockback(deer.position(), aggressor.position());
		aggressor.knockback(MegumiShikigamiProfile.DEER_ANTLER_KNOCKBACK, shove.x, shove.z);
		deer.swing(InteractionHand.MAIN_HAND);
		deer.markAntlerShove(gameTime + MegumiShikigamiProfile.DEER_ANTLER_COOLDOWN_TICKS);
	}

	// — Heal pulse ————————————————————————————————————————————————————————

	private static void tickHeal(ServerLevel level, ServerPlayer owner,
			MegumiDeerEntity deer, long gameTime) {
		if (deer.healTargetUuid() != null) {
			finishHealPulse(level, owner, deer);
			return;
		}
		if (!MegumiDeerPolicy.healDue(gameTime, deer.nextHealScanGameTime())) {
			return;
		}
		// The window always advances, target or not: the cadence is the scan, and a missed beat
		// waits a full period instead of re-scanning every tick (the rabbit bump convention).
		deer.markHealScan(gameTime + MegumiShikigamiProfile.DEER_HEAL_SCAN_TICKS);
		WoundedFacts pick = MegumiDeerPolicy.healPriority(woundedFacts(level, owner, deer));
		if (pick == null) {
			return;
		}
		deer.setHealTarget(pick.uuid());
		deer.beginAction(MegumiShikigamiProfile.DEER_HEAL_ACTION_TICKS);
	}

	/**
	 * The channel just closed: the heal lands now, on whatever the target became. Re-verify the
	 * family at the commit tick — ten ticks are enough for a body to die or a mark to re-point.
	 */
	private static void finishHealPulse(ServerLevel level, ServerPlayer owner, MegumiDeerEntity deer) {
		if (deer.actionTicks() > 0) {
			return;
		}
		UUID targetId = deer.healTargetUuid();
		deer.setHealTarget(null);
		LivingEntity target = resolve(level, targetId);
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}
		WoundedKind kind = kindOf(level, owner, deer, target);
		if (kind == null) {
			return;
		}
		// Ten ticks of channel are enough for either side to drift out of the ring — re-validate
		// the radius at the commit, not just at the pick, or the pulse lands on stale geometry.
		if (deer.distanceToSqr(target)
				> MegumiShikigamiProfile.DEER_HEAL_RANGE * MegumiShikigamiProfile.DEER_HEAL_RANGE) {
			return;
		}
		double missing = missingFraction(target);
		if (missing <= 0.0) {
			return;
		}
		target.heal(MegumiDeerPolicy.healPulse(missing, kind));
		Vec3 anchor = new Vec3(0.0, target.getBbHeight() * 0.6, 0.0);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.DEER_PULSE,
				target.position(), target.getId(), anchor);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9f, 1.2f);
	}

	// — Cleanse ——————————————————————————————————————————————————————————

	/**
	 * The positive-energy cleanse: strip every cleansable effect off the best carrier. The
	 * allowlist lives in {@link MegumiDeerPolicy#cleansable} — hold channels, trap grips and
	 * beneficial internals all survive.
	 */
	private static void tickCleanse(ServerLevel level, ServerPlayer owner,
			MegumiDeerEntity deer, long gameTime) {
		if (!MegumiDeerPolicy.cleanseDue(gameTime, deer.nextCleanseScanGameTime())) {
			return;
		}
		deer.markCleanseScan(gameTime + MegumiShikigamiProfile.DEER_CLEANSE_SCAN_TICKS);
		WoundedFacts pick = MegumiDeerPolicy.cleansePriority(woundedFacts(level, owner, deer));
		if (pick == null) {
			return;
		}
		LivingEntity target = resolve(level, pick.uuid());
		if (target == null || !target.isAlive() || target.isRemoved()) {
			return;
		}
		List<Holder<MobEffect>> strip = new ArrayList<>();
		for (MobEffectInstance instance : target.getActiveEffects()) {
			if (MegumiDeerPolicy.cleansable(instance.getEffect().value())) {
				strip.add(instance.getEffect());
			}
		}
		if (strip.isEmpty()) {
			return;
		}
		for (Holder<MobEffect> effect : strip) {
			target.removeEffect(effect);
		}
		Vec3 anchor = new Vec3(0.0, target.getBbHeight() * 0.6, 0.0);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.DEER_CLEANSE,
				target.position(), target.getId(), anchor);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.CONDUIT_ACTIVATE, SoundSource.NEUTRAL, 0.8f, 1.3f);
	}

	// — Interpose —————————————————————————————————————————————————————————

	/**
	 * Threat-aware positioning: drift to the point on the owner→threat line
	 * {@code DEER_INTERPOSE_RADIUS} out from the owner. The threat read is awareness — the own
	 * mark (sic/retaliation) names the fight, else the coordinator's ally-threat map does. With
	 * no threat the vanilla FollowOwnerGoal keeps the deer at heel.
	 */
	private static void tickInterpose(ServerLevel level, ServerPlayer owner, MegumiDeerEntity deer) {
		if (owner == null || !owner.isAlive() || owner.level() != level) {
			deer.steerInterpose(null);
			return;
		}
		LivingEntity threat = threatFor(level, owner, deer);
		if (threat == null) {
			deer.steerInterpose(null);
			return;
		}
		Vec3 point = MegumiDeerPolicy.interposePoint(owner.position(), threat.position(),
				MegumiShikigamiProfile.DEER_INTERPOSE_RADIUS);
		Vec3 anchor = deer.interposeAnchor();
		boolean drifted = anchor == null || anchor.distanceToSqr(point) > INTERPOSE_REISSUE_SQR;
		if (drifted || (deer.getNavigation().isDone()
				&& deer.distanceToSqr(point) > INTERPOSE_ARRIVED_SQR)) {
			deer.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
			deer.steerInterpose(point);
		}
	}

	/**
	 * What the deer shields against: its own mark first (the pack's committed answer), else the
	 * coordinator's nearest ally-threat to the owner. Awareness only — never an attack order.
	 */
	private static LivingEntity threatFor(ServerLevel level, ServerPlayer owner, MegumiDeerEntity deer) {
		LivingEntity marked = resolve(level, deer.sicTargetUuid());
		if (marked != null && marked != deer && marked.isAlive() && !marked.isRemoved()) {
			return marked;
		}
		MegumiCombatContext context = MegumiPackCoordinator.contextFor(owner, level);
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (UUID threatId : context.allyThreats().values()) {
			LivingEntity threat = resolve(level, threatId);
			if (threat == null || threat == deer || !threat.isAlive() || threat.isRemoved()) {
				continue;
			}
			double distance = owner.distanceToSqr(threat);
			if (distance < best) {
				best = distance;
				nearest = threat;
			}
		}
		return nearest;
	}

	// — Candidate collection ——————————————————————————————————————————————

	/**
	 * The friendly field around the deer, as facts: the owner, every own shikigami body and own
	 * dog in range, allied bodies, and the deer itself. Enemies, foreign shikigami and neutrals
	 * never enter the list — they are ineligible, not merely deprioritized.
	 */
	private static List<WoundedFacts> woundedFacts(ServerLevel level, ServerPlayer owner,
			MegumiDeerEntity deer) {
		List<WoundedFacts> facts = new ArrayList<>();
		double rangeSqr = MegumiShikigamiProfile.DEER_HEAL_RANGE * MegumiShikigamiProfile.DEER_HEAL_RANGE;
		UUID ownerId = deer.ownerUuid();
		Set<UUID> ownBodyIds = new HashSet<>();
		if (owner != null && owner.isAlive() && owner.level() == level
				&& deer.distanceToSqr(owner) <= rangeSqr) {
			facts.add(factsFor(deer, owner, WoundedKind.OWNER));
		}
		for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
				level.getServer(), ownerId)) {
			ownBodyIds.add(body.getUUID());
			if (body != deer && body.isAlive() && body.level() == level
					&& deer.distanceToSqr(body) <= rangeSqr) {
				facts.add(factsFor(deer, body, WoundedKind.OWN_SHIKIGAMI));
			}
		}
		for (MegumiDivineDogEntity dog : MegumiSummonRuntime.livingDogs(level.getServer(), ownerId)) {
			ownBodyIds.add(dog.getUUID());
			if (dog.isAlive() && dog.level() == level && deer.distanceToSqr(dog) <= rangeSqr) {
				facts.add(factsFor(deer, dog, WoundedKind.OWN_SHIKIGAMI));
			}
		}
		if (owner != null) {
			for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
					deer.getBoundingBox().inflate(MegumiShikigamiProfile.DEER_HEAL_RANGE),
					entity -> entity != deer && entity != owner && entity.isAlive()
							&& !entity.isSpectator() && !entity.isRemoved()
							&& !ownBodyIds.contains(entity.getUUID()))) {
				if (owner.isAlliedTo(candidate)) {
					facts.add(factsFor(deer, candidate, WoundedKind.ALLY));
				}
			}
		}
		facts.add(factsFor(deer, deer, WoundedKind.SELF));
		return facts;
	}

	private static WoundedFacts factsFor(MegumiDeerEntity deer, LivingEntity candidate,
			WoundedKind kind) {
		boolean carries = candidate.getActiveEffects().stream()
				.anyMatch(instance -> MegumiDeerPolicy.cleansable(instance.getEffect().value()));
		return new WoundedFacts(candidate.getUUID(), kind, missingFraction(candidate),
				candidate == deer ? 0.0 : deer.distanceToSqr(candidate), carries);
	}

	/** The candidate's lane at commit time; null = no longer eligible (not ours, not allied). */
	private static WoundedKind kindOf(ServerLevel level, ServerPlayer owner,
			MegumiDeerEntity deer, LivingEntity target) {
		if (target == deer) {
			return WoundedKind.SELF;
		}
		if (owner != null && target == owner) {
			return WoundedKind.OWNER;
		}
		UUID ownerId = deer.ownerUuid();
		if (target instanceof MegumiShikigamiEntity body && ownerId.equals(body.ownerUuid())) {
			return WoundedKind.OWN_SHIKIGAMI;
		}
		if (target instanceof MegumiDivineDogEntity dog && ownerId.equals(dog.ownerUuid())) {
			return WoundedKind.OWN_SHIKIGAMI;
		}
		if (owner != null && owner.isAlliedTo(target)) {
			return WoundedKind.ALLY;
		}
		return null;
	}

	private static double missingFraction(LivingEntity entity) {
		return entity.getMaxHealth() > 0.0f
				? (entity.getMaxHealth() - entity.getHealth()) / entity.getMaxHealth()
				: 0.0;
	}

	private static LivingEntity resolve(ServerLevel level, UUID uuid) {
		if (uuid == null) {
			return null;
		}
		Entity entity = level.getEntity(uuid);
		return entity instanceof LivingEntity living ? living : null;
	}
}
