package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Max Elephant's sic behaviour: a short windup, then a trunk water jet that hoses every hostile
 * in the corridor — damage, a shove along the jet, douse, and the {@code MEGUMI_SOAKED} combo
 * marker Nue's shock consumes. The corridor is 12 blocks of water from a trunk 1.2 ahead of the
 * body (about 13.2 from the body itself), and the trigger is gated on exactly that reach: a sic
 * past it starts no jet and burns no jet cooldown instead of firing a blank volley. The aim
 * re-resolves every tick from the body's facing, so the jet tracks the sic target while it stays
 * valid.
 */
final class MegumiElephantBrain {
	private MegumiElephantBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiElephantEntity elephant, long gameTime) {
		// The presence is constant: it runs whether or not the body is jetting, because it is what
		// the body *is*, not an action it takes.
		tickPresence(level, owner, elephant, gameTime);
		tickFootprint(level, elephant, gameTime);
		if (elephant.jetActive()) {
			tickJet(level, owner, elephant, gameTime);
			return;
		}
		LivingEntity target = resolve(level, elephant.sicTargetUuid());
		if (target == null) {
			return;
		}
		// Soft coordination (issue #107 §12): a jet would shove and soak a victim an ally is already
		// committed to — a toad's windup or hold, a dive in flight. The jet is not forbidden, only
		// de-preferred: while the shared context names the target as an ally's work, the elephant
		// keeps melee-approaching instead of firing through the setup.
		if (owner != null && MegumiPackCoordinator.contextFor(owner, level)
				.intentTargets().contains(target.getUUID())) {
			return;
		}
		if (!canStartJet(elephant, target, gameTime)) {
			return;
		}
		elephant.beginJet(MegumiShikigamiProfile.ELEPHANT_JET_WINDUP_TICKS
				+ MegumiShikigamiProfile.ELEPHANT_JET_DURATION_TICKS);
		// A firing elephant plants its feet: melee approach would drag the trunk off aim, so the
		// vanilla target drops for the jet's duration and is restored when the jet ends. Clearing
		// the target alone does not hold the body — MeleeAttackGoal/FollowOwnerGoal keep driving
		// on the pending path — so navigation stops and the goals suspend exactly like Nue's dive.
		elephant.setTarget(null);
		elephant.getNavigation().stop();
		elephant.setNoAi(true);
		// The windup tell: the plan's sound table gives the jet an audible start, mirroring the
		// toad's FROG_TONGUE at tongue commit.
		level.playSound(null, elephant.getX(), elephant.getY(), elephant.getZ(), SoundEvents.RAVAGER_ATTACK,
				SoundSource.NEUTRAL, 0.7f, 1.0f);
	}

	private static void tickJet(ServerLevel level, ServerPlayer owner,
			MegumiElephantEntity elephant, long gameTime) {
		if (elephant.actionTicks() <= 0) {
			elephant.endJet();
			// Goals resume exactly as Nue's dive ends: NoAI clears on an ACTIVE body, so the
			// follow behaviour picks up where the jet interrupted it. This covers the natural end
			// and the early-exit path alike — both funnel through here.
			elephant.setNoAi(!elephant.combatEnabled());
			elephant.markAttackUsed(gameTime, MegumiShikigamiProfile.ELEPHANT_JET_COOLDOWN_TICKS);
			// The plant dropped the vanilla target for the jet's duration; hand it back, or the body
			// stands there with its command spent while the sic mark walks away.
			LivingEntity standing = resolve(level, elephant.sicTargetUuid());
			if (standing != null) {
				elephant.setTarget(standing);
			}
			return;
		}
		LivingEntity target = resolve(level, elephant.sicTargetUuid());
		if (target != null) {
			faceTarget(elephant, target);
		}
		if (elephant.actionTicks() > MegumiShikigamiProfile.ELEPHANT_JET_DURATION_TICKS) {
			return;
		}
		int fired = MegumiShikigamiProfile.ELEPHANT_JET_DURATION_TICKS - elephant.actionTicks();
		if (fired % MegumiShikigamiProfile.ELEPHANT_JET_PULSE_TICKS != 0) {
			return;
		}
		firePulse(level, owner, elephant);
	}

	private static void firePulse(ServerLevel level, ServerPlayer owner, MegumiElephantEntity elephant) {
		Vec3 direction = elephant.getLookAngle();
		if (direction.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 trunk = elephant.getEyePosition()
				.add(direction.scale(MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD));
		Vec3 unit = direction.normalize();
		double length = MegumiShikigamiProfile.ELEPHANT_JET_LENGTH;
		double halfWidth = MegumiShikigamiProfile.ELEPHANT_JET_HALF_WIDTH;
		AABB sweep = new AABB(trunk, trunk.add(unit.scale(length))).inflate(halfWidth + 1.0);
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep,
				entity -> entity.isAlive() && !entity.isRemoved())) {
			if (candidate == elephant
					|| candidate.isSpectator()
					|| candidate.isInvulnerable()
					|| (owner != null && MegumiShikigamiFriendlyFire.isProtected(owner, candidate))) {
				continue;
			}
			Vec3 center = candidate.position().add(0.0, candidate.getBbHeight() * 0.5, 0.0);
			if (!MegumiElephantPolicy.inJetCorridor(center, trunk, unit, length, halfWidth)) {
				continue;
			}
			soak(level, owner, elephant, candidate, unit);
		}
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.ELEPHANT_JET,
				trunk, elephant.getId(), Vec3.ZERO);
		level.playSound(null, trunk.x, trunk.y, trunk.z, SoundEvents.GENERIC_SPLASH,
				SoundSource.NEUTRAL, 0.7f, 1.1f);
	}

	private static void soak(ServerLevel level, ServerPlayer owner, MegumiElephantEntity elephant,
			LivingEntity target, Vec3 unit) {
		DamageSource source = owner != null
				? level.damageSources().playerAttack(owner)
				: level.damageSources().magic();
		target.hurtServer(level, source, (float) MegumiShikigamiProfile.ELEPHANT_JET_DAMAGE);
		Vec3 shove = MegumiElephantPolicy.knockbackVector(unit);
		target.knockback(MegumiShikigamiProfile.ELEPHANT_JET_KNOCKBACK, -shove.x, -shove.z);
		target.clearFire();
		target.setRemainingFireTicks(0);
		target.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SOAKED,
				MegumiShikigamiProfile.ELEPHANT_JET_SOAK_TICKS, 0, true, false, true), owner);
	}

	private static boolean canStartJet(MegumiElephantEntity elephant, LivingEntity target, long gameTime) {
		return elephant.attackReady(gameTime)
				&& jetTriggerInReach(elephant.distanceTo(target))
				&& elephant.hasLineOfSight(target);
	}

	/**
	 * Whether a feet-to-feet sic distance can actually be hosed: the corridor runs
	 * {@code ELEPHANT_JET_LENGTH} forward from a trunk {@code ELEPHANT_TRUNK_FORWARD} ahead of the
	 * body, so anything past their sum eats a windup, twenty VFX'd pulses, and a 220-tick lockout
	 * for zero effect. Refusing here is silent and cheap — no windup, no sound, no jet cooldown —
	 * and the body keeps melee-approaching until the target is genuinely reachable.
	 * Package-visible for the reach pin test.
	 */
	static boolean jetTriggerInReach(double distanceFeet) {
		return distanceFeet <= MegumiShikigamiProfile.ELEPHANT_JET_LENGTH
				+ MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD;
	}

	private static void faceTarget(MegumiElephantEntity elephant, LivingEntity target) {
		Vec3 delta = target.getEyePosition().subtract(elephant.getEyePosition());
		if (delta.horizontalDistanceSqr() < 1.0E-8) {
			return;
		}
		float yaw = (float) (Math.atan2(-delta.x, delta.z) * (180.0 / Math.PI));
		elephant.setYRot(yaw);
		elephant.yBodyRot = yaw;
		elephant.yHeadRot = yaw;
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}

	/**
	 * The presence (issue #79): Max Elephant is an area of pressure. Everything not on the owner's
	 * own side is shoved away on the presence period, and anything the hostility policy calls
	 * hostile takes damage on top. A velocity impulse, not {@code knockback()} — knockback
	 * resistance (iron golems, ravagers) would eat the shove, and the shove is the whole point.
	 */
	private static void tickPresence(ServerLevel level, ServerPlayer owner,
			MegumiElephantEntity elephant, long gameTime) {
		if (!MegumiElephantPresencePolicy.presenceDue(gameTime)) {
			return;
		}
		AABB sweep = elephant.getBoundingBox().inflate(MegumiShikigamiProfile.ELEPHANT_PRESENCE_RADIUS);
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep,
				entity -> entity.isAlive() && !entity.isRemoved() && !entity.isSpectator())) {
			if (candidate == elephant
					|| MegumiShikigamiFriendlyFire.isOwnSideOnly(owner, candidate)
					|| !MegumiElephantPresencePolicy.inside(elephant.distanceTo(candidate))) {
				continue;
			}
			candidate.setDeltaMovement(MegumiElephantPresencePolicy.pushVelocity(
					elephant.position(), candidate.position(),
					MegumiShikigamiProfile.ELEPHANT_PRESENCE_PUSH,
					MegumiShikigamiProfile.ELEPHANT_PRESENCE_KNOCKBACK));
			candidate.hurtMarked = true;
			if (MegumiHostilityPolicy.isHostile(owner, candidate)) {
				DamageSource source = owner != null
						? level.damageSources().playerAttack(owner)
						: level.damageSources().magic();
				// A zone, not a hit: the pulse is due every ELEPHANT_PRESENCE_PERIOD_TICKS, and the
				// vanilla 20-tick hurt cooldown would silently swallow every second one (the plan's
				// balance is one pulse per period). Vanilla zones that ignore the cooldown —
				// lava, cactus, the void — express the same intent.
				candidate.invulnerableTime = 0;
				candidate.hurtServer(level, source,
						(float) MegumiShikigamiProfile.ELEPHANT_PRESENCE_DAMAGE);
			}
		}
	}

	/**
	 * The footprint (issue #79): while walking, the body crushes what its feet pass over. The
	 * trigger is movement, never a collision — natural ground has to break even when the body
	 * walks through open space. The allowlist is natural terrain only (dirt, sand, leaves and
	 * the like), so anything a player builds a base out of — planks, glass, torches, crops —
	 * is never touched, and the broken ground drops nothing.
	 */
	private static void tickFootprint(ServerLevel level, MegumiElephantEntity elephant, long gameTime) {
		if (!MegumiElephantPresencePolicy.footprintDue(gameTime)) {
			return;
		}
		// Sampled movement, not the velocity field: see MegumiElephantEntity#sampleFootprintStep.
		Vec3 motion = elephant.sampleFootprintStep(gameTime);
		if (!MegumiElephantPresencePolicy.footprintMoves(motion)) {
			return;
		}
		AABB box = elephant.getBoundingBox();
		Vec3 heading = MegumiElephantPresencePolicy.footprintHeading(motion);
		double reach = Math.max(box.getXsize(), box.getZsize()) * 0.5;
		BlockPos min = BlockPos.containing(
				box.minX - Math.abs(heading.x) * reach, box.minY - 1.0, box.minZ - Math.abs(heading.z) * reach);
		BlockPos max = BlockPos.containing(
				box.maxX + Math.abs(heading.x) * reach, box.minY + 1.0, box.maxZ + Math.abs(heading.z) * reach);
		int budget = MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_BUDGET;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (budget <= 0) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()
					|| !MegumiShikigamiTags.breaksAllowed(state)) {
				continue;
			}
			if (level.destroyBlock(pos.immutable(), false, elephant, 512)) {
				budget--;
			}
		}
	}
}
