package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Ox behaviour: FOLLOW → ACQUIRE → ALIGN → WINDUP → CHARGE → IMPACT/PASS_THROUGH → RECOVERY.
 * The committed line is frozen at windup — the charge never homes.
 */
final class MegumiOxBrain {
	private MegumiOxBrain() {}

	private static final String CHARGE_FAILURE_KEY = "ox_charge";
	/** Per-hit target stagger; the profile only pins the ox's own wall stagger. */
	private static final int IMPACT_STAGGER_TICKS = 12;
	/** Re-aim can stall on a fast target — the windup locks the position line anyway. */
	private static final int ALIGN_MAX_TICKS = 20;
	/** Ground probe depth for the corridor trace: deeper than a slab, shallower than a ledge. */
	private static final double SUPPORT_PROBE_DEPTH = 0.3;
	/** Charge-step hoofbeat cadence. */
	private static final int CHARGE_STEP_PERIOD = 4;
	/**
	 * Small downward pull inside every charge move — the forced step must still meet the floor so
	 * {@code onGround}/{@code verticalCollision} stay honest after each {@code move()} call, and a
	 * ledge reads as a drop the very tick the floor runs out.
	 */
	private static final double GRAVITY_STEP = -0.08;

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiOxEntity ox, long gameTime) {
		ox.advanceStateTick();
		switch (ox.oxState()) {
			case FOLLOW -> tickFollow(level, owner, ox, gameTime);
			case ACQUIRE -> tickAcquire(level, owner, ox, gameTime);
			case ALIGN -> tickAlign(level, owner, ox, gameTime);
			case WINDUP -> tickWindup(level, owner, ox, gameTime);
			case CHARGE -> tickCharge(level, owner, ox, gameTime);
			case RECOVERY -> tickRecovery(ox, gameTime);
		}
	}

	// --- FOLLOW / ACQUIRE ---------------------------------------------------

	private static void tickFollow(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		if (owner == null || owner.level() != level || !ox.isAlive()) {
			return;
		}
		LivingEntity target = resolveMark(level, ox);
		if (target == null) {
			return; // No mark — the ox never self-starts a charge.
		}
		double distanceToTarget = horizontalDistance(ox.position(), target.position());
		Vec3 direction = MegumiOxPolicy.lockDirection(ox.position(), target.position());
		if (direction.lengthSqr() < 1.0E-6) {
			return; // Sharing a column with the mark — nothing to aim along.
		}
		double corridorReach = corridorReach(level, ox, direction);
		Vec3 projectedStop = MegumiOxPolicy.projectedStop(ox.position(), direction, corridorReach);
		double stopToOwner = horizontalDistance(projectedStop, owner.position());

		MegumiOxPolicy.CommitFacts facts = new MegumiOxPolicy.CommitFacts(
				true,
				MegumiSummonRuntime.isEligibleTarget(owner, target),
				ox.hasLineOfSight(target),
				distanceToTarget,
				gameTime >= ox.nextChargeGameTime(),
				corridorReach,
				stopToOwner);
		if (MegumiOxPolicy.canCommit(facts)) {
			ox.setNoAi(true);
			ox.getNavigation().stop();
			ox.setDeltaMovement(Vec3.ZERO);
			ox.armChargeTarget(target.getUUID());
			ox.transitionTo(MegumiOxEntity.OxState.ACQUIRE);
		}
	}

	/** One settling tick: re-validate the order, then turn to face the mark. */
	private static void tickAcquire(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		LivingEntity target = resolveMark(level, ox);
		if (target == null) {
			abortQuietly(ox, gameTime);
			return;
		}
		if (owner == null || !MegumiSummonRuntime.isEligibleTarget(owner, target)
				|| gameTime < ox.nextChargeGameTime()) {
			abortQuietly(ox, gameTime);
			return;
		}
		// Planted stance: a committed ox holds its position until the line locks.
		ox.setDeltaMovement(Vec3.ZERO);
		ox.armChargeTarget(target.getUUID());
		ox.transitionTo(MegumiOxEntity.OxState.ALIGN);
	}

	// --- ALIGN / WINDUP ------------------------------------------------------

	private static void tickAlign(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		LivingEntity target = resolveMark(level, ox);
		if (target == null) {
			abortQuietly(ox, gameTime);
			return;
		}
		// Planted stance: pushing or leftover momentum must not shift the lock point.
		ox.setDeltaMovement(Vec3.ZERO);
		faceTarget(ox, target);
		double yawError = yawErrorDegrees(ox, target);
		if (MegumiOxPolicy.alignedEnough(yawError) || ox.stateTicks() >= ALIGN_MAX_TICKS) {
			Vec3 frozen = MegumiOxPolicy.lockDirection(ox.position(), target.position());
			if (frozen.lengthSqr() < 1.0E-6) {
				abortQuietly(ox, gameTime);
				return;
			}
			ox.beginWindup(frozen, target.getUUID());
			ox.setPresentationAction(MegumiOxEntity.ACTION_WINDUP);
			ox.setYRot((float) (Mth.atan2(-frozen.x, frozen.z) * Mth.RAD_TO_DEG));
			ox.setYHeadRot(ox.getYRot());
			// Hoof-scrape telegraph: horn snap plus the first heavy step.
			playSound(level, ox, SoundEvents.GOAT_HORN_BREAK, 0.7f, 1.1f);
			playSound(level, ox, SoundEvents.RAVAGER_STEP, 0.8f, 0.8f);
			MegumiShikigamiRuntime.broadcastCue(level, owner,
					windupCue(ox.position(), ox.getId(), gameTime));
		}
	}

	private static void tickWindup(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		if (ox.markKind() == null) {
			// The order vanished (leash/coordinator drop) mid-windup — the committed line never runs.
			abortQuietly(ox, gameTime);
			return;
		}
		ox.setDeltaMovement(Vec3.ZERO);
		if (ox.stateTicks() >= MegumiShikigamiProfile.OX_WINDUP_TICKS) {
			ox.beginCharge(gameTime);
			ox.setPresentationAction(MegumiOxEntity.ACTION_CHARGE);
			MegumiShikigamiRuntime.broadcastCue(level, owner,
					chargeCue(ox.position(), ox.getId(), ox.accumulatedDistance(), gameTime,
							ox.chargeDirection()));
		}
	}

	// --- CHARGE ----------------------------------------------------------------

	private static void tickCharge(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		if (ox.tickImpactFlash()) {
			ox.setPresentationAction(MegumiOxEntity.ACTION_CHARGE);
		}
		if (ox.markKind() == null || ox.chargeDirection() == null) {
			// Leash or order recall mid-flight — stop committed, no wall theatrics.
			ox.setDeltaMovement(Vec3.ZERO);
			enterRecovery(ox, gameTime);
			return;
		}

		Vec3 direction = ox.chargeDirection();
		Vec3 beforeMove = ox.position();
		ox.setDeltaMovement(Vec3.ZERO);
		ox.move(MoverType.SELF, direction.scale(MegumiShikigamiProfile.OX_CHARGE_SPEED)
				.add(0.0, GRAVITY_STEP, 0.0));
		Vec3 afterMove = ox.position();
		ox.addAccumulatedDistance(MegumiOxPolicy.accumulatedDelta(beforeMove, afterMove));
		ox.setYRot((float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG));
		ox.setYHeadRot(ox.getYRot());
		ox.setDeltaMovement(Vec3.ZERO); // No residual drift into next tick's travel.

		sweepHits(level, owner, ox, beforeMove, afterMove, gameTime);

		if (ox.stateTicks() % CHARGE_STEP_PERIOD == 0) {
			playSound(level, ox, SoundEvents.RAVAGER_STEP, 0.7f, 0.7f);
		}
		// The trail's intensity is the real distance already covered — it grows as the ox runs.
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				chargeCue(ox.position(), ox.getId(), ox.accumulatedDistance(), gameTime,
						ox.chargeDirection()));

		MegumiOxPolicy.ChargeAction action = MegumiOxPolicy.chargeAction(new MegumiOxPolicy.ChargeFacts(
				ox.horizontalCollision, ox.verticalCollision, ox.onGround(),
				ox.stateTicks(), ox.accumulatedDistance()));
		if (action == MegumiOxPolicy.ChargeAction.WALL_ABORT) {
			wallAbort(level, owner, ox, gameTime);
		} else if (action == MegumiOxPolicy.ChargeAction.PASS_THROUGH) {
			enterRecovery(ox, gameTime);
		}
	}

	/** Every eligible entity the swept body path crossed this tick is hit once. */
	private static void sweepHits(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			Vec3 beforeMove, Vec3 afterMove, long gameTime) {
		AABB oxBox = ox.getBoundingBox();
		AABB scanBox = oxBox.inflate(1.0);
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, scanBox,
				candidate -> candidate != ox && candidate.isAlive())) {
			// Friendlies and the owner's own bodies are filtered BEFORE damage.
			if (MegumiShikigamiFriendlyFire.isProtected(owner, candidate)
					|| !MegumiSummonRuntime.isEligibleTarget(owner, candidate)) {
				continue;
			}
			if (MegumiOxPolicy.alreadyHit(ox.hitUuids(), candidate.getUUID())
					|| !MegumiOxPolicy.sweptHit(oxBox, candidate.getBoundingBox(), beforeMove, afterMove)) {
				continue;
			}
			ox.hitUuids().add(candidate.getUUID());
			resolveHit(level, owner, ox, candidate, gameTime);
		}
	}

	private static void resolveHit(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			LivingEntity target, long gameTime) {
		double power = MegumiOxPolicy.impactPower(ox.accumulatedDistance());
		double damage = MegumiOxPolicy.damageFor(ox.accumulatedDistance());
		target.hurtServer(level, owner != null
				? level.damageSources().playerAttack(owner)
				: level.damageSources().mobAttack(ox), (float) damage);
		Vec3 direction = ox.chargeDirection();
		target.knockback(MegumiOxPolicy.knockbackFor(power), -direction.x, -direction.z);
		CombatStagger.GLOBAL.apply(target, gameTime, IMPACT_STAGGER_TICKS);
		ox.beginImpactFlash();
		ox.setPresentationAction(MegumiOxEntity.ACTION_IMPACT);
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				impactCue(target.position(), target.getId(), ox.accumulatedDistance(), gameTime,
						ox.chargeDirection()));
		playSound(level, ox, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.2f);
		playSound(level, ox, SoundEvents.POLAR_BEAR_WARNING, 0.7f, 0.9f);
	}

	/** The charge met the world: impact presentation, self-stagger, failure memory, then rest. */
	private static void wallAbort(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			long gameTime) {
		CombatStagger.GLOBAL.apply(ox, gameTime, MegumiShikigamiProfile.OX_WALL_STAGGER_TICKS);
		MegumiFailureMemory.recordFailure(ox.getUUID(), CHARGE_FAILURE_KEY, gameTime);
		ox.beginImpactFlash();
		ox.setPresentationAction(MegumiOxEntity.ACTION_IMPACT);
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				wallCue(ox.position(), ox.getId(), ox.accumulatedDistance(), gameTime,
						ox.chargeDirection()));
		playSound(level, ox, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.0f);
		playSound(level, ox, SoundEvents.POLAR_BEAR_WARNING, 0.7f, 0.8f);
		enterRecovery(ox, gameTime);
	}

	// --- RECOVERY ---------------------------------------------------------------

	private static void tickRecovery(MegumiOxEntity ox, long gameTime) {
		ox.setDeltaMovement(Vec3.ZERO);
		if (ox.tickImpactFlash() && ox.presentationAction() == MegumiOxEntity.ACTION_IMPACT) {
			ox.setPresentationAction(MegumiOxEntity.ACTION_RECOVER);
		}
		if (ox.stateTicks() >= MegumiShikigamiProfile.OX_RECOVERY_TICKS) {
			releaseBody(ox);
		}
	}

	/** Shared "stop committed, stand still a moment" path for all charge outcomes. */
	private static void enterRecovery(MegumiOxEntity ox, long gameTime) {
		ox.setDeltaMovement(Vec3.ZERO);
		ox.enterRecovery(gameTime);
		if (ox.presentationAction() != MegumiOxEntity.ACTION_IMPACT) {
			ox.setPresentationAction(MegumiOxEntity.ACTION_RECOVER);
		}
	}

	/** Mark/order gone before the line was ever bought — no impact, no cooldown spent. */
	private static void abortQuietly(MegumiOxEntity ox, long gameTime) {
		ox.setDeltaMovement(Vec3.ZERO);
		releaseBody(ox);
	}

	private static void releaseBody(MegumiOxEntity ox) {
		ox.backToFollow();
		ox.setNoAi(!ox.combatEnabled());
		ox.setPresentationAction(MegumiOxEntity.ACTION_NONE);
	}

	// --- geometry + marks ------------------------------------------------------

	/**
	 * The committed mark, resolved live. A dead/left-the-world mark also drops the sic command so
	 * a stale order never rubber-bands the body.
	 */
	private static LivingEntity resolveMark(ServerLevel level, MegumiOxEntity ox) {
		UUID markUuid = ox.sicTargetUuid();
		if (markUuid == null) {
			return null;
		}
		if (!(level.getEntity(markUuid) instanceof LivingEntity target)
				|| !target.isAlive() || target.isRemoved()) {
			ox.clearSicCommand();
			return null;
		}
		return target;
	}

	/**
	 * Samples the charge line at {@code OX_CORRIDOR_SAMPLE_STEP} until the body box collides or
	 * loses floor support — the wall-clamped reach the commit gate reasons over.
	 */
	static double corridorReach(ServerLevel level, MegumiOxEntity ox, Vec3 direction) {
		AABB box = ox.getDimensions(ox.getPose()).makeBoundingBox(ox.position());
		double travelled = 0.0;
		while (travelled < MegumiShikigamiProfile.OX_CHARGE_MAX_DISTANCE) {
			Vec3 probe = ox.position().add(direction.scale(travelled + MegumiShikigamiProfile.OX_CORRIDOR_SAMPLE_STEP));
			AABB probeBox = box.move(probe.subtract(ox.position()));
			if (!level.noBlockCollision(ox, probeBox)
					|| level.noBlockCollision(ox, probeBox.move(0.0, -SUPPORT_PROBE_DEPTH, 0.0))) {
				break;
			}
			travelled += MegumiShikigamiProfile.OX_CORRIDOR_SAMPLE_STEP;
		}
		return travelled;
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static void faceTarget(MegumiOxEntity ox, LivingEntity target) {
		Vec3 delta = target.position().subtract(ox.position());
		float yaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
		ox.setYRot(yaw);
		ox.setYHeadRot(yaw);
	}

	private static double yawErrorDegrees(MegumiOxEntity ox, LivingEntity target) {
		Vec3 delta = target.position().subtract(ox.position());
		float want = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
		return Mth.wrapDegrees(want - ox.getYRot());
	}

	// --- cues + sounds ---------------------------------------------------------

	/** Telegraph at windup lock, anchored on the ox itself. */
	static VfxCue windupCue(Vec3 origin, int anchorEntityId, long gameTime) {
		return VfxCues.anchoredWithOffset(MegumiVfxIds.OX_WINDUP, origin, anchorEntityId,
				Vec3.ZERO, 1, gameTime, seedFor(anchorEntityId, gameTime));
	}

	/** Per-tick trail cue; intensity is the real distance already travelled. */
	static VfxCue chargeCue(Vec3 origin, int anchorEntityId, double accumulatedDistance,
			long gameTime, Vec3 direction) {
		int intensity = Math.max(1, (int) Math.round(accumulatedDistance));
		return MegumiShikigamiRuntime.directedCue(MegumiVfxIds.OX_CHARGE, origin,
				anchorEntityId, origin, intensity, gameTime, seedFor(anchorEntityId, gameTime),
				direction != null ? direction : Vec3.ZERO);
	}

	/** Per-hit burst, anchored on the victim, intensity = the impact power. */
	static VfxCue impactCue(Vec3 origin, int anchorEntityId, double accumulatedDistance,
			long gameTime, Vec3 direction) {
		int intensity = Math.max(1,
				(int) Math.round(MegumiOxPolicy.impactPower(accumulatedDistance)));
		return MegumiShikigamiRuntime.directedCue(MegumiVfxIds.OX_IMPACT, origin,
				anchorEntityId, origin, intensity, gameTime, seedFor(anchorEntityId, gameTime),
				direction != null ? direction : Vec3.ZERO);
	}

	/** Wall slam at the abort point, aimed along the frozen line. */
	static VfxCue wallCue(Vec3 origin, int anchorEntityId, double accumulatedDistance,
			long gameTime, Vec3 direction) {
		int intensity = Math.max(1,
				(int) Math.round(MegumiOxPolicy.impactPower(accumulatedDistance)));
		return MegumiShikigamiRuntime.directedCue(MegumiVfxIds.OX_WALL_HIT, origin,
				anchorEntityId, origin, intensity, gameTime, seedFor(anchorEntityId, gameTime),
				direction != null ? direction : Vec3.ZERO);
	}

	private static long seedFor(int entityId, long gameTime) {
		return gameTime * 31L + entityId;
	}

	private static void playSound(ServerLevel level, MegumiOxEntity ox,
			net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, ox.getX(), ox.getY(), ox.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
	}

}
