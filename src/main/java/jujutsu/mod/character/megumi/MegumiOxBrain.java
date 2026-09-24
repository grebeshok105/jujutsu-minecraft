package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;

/** Server-authoritative Piercing Ox state machine and collision-resolved charge. */
final class MegumiOxBrain {
	private MegumiOxBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiOxEntity ox, long gameTime) {
		if (!ox.combatEnabled()) {
			return;
		}
		switch (ox.chargeState()) {
			case FOLLOW -> tickFollow(level, owner, ox, gameTime);
			case ACQUIRE -> tickAcquire(level, owner, ox, gameTime);
			case ALIGN -> tickAlign(level, owner, ox, gameTime);
			case WINDUP -> tickWindup(level, owner, ox, gameTime);
			case CHARGE, PASS_THROUGH -> tickCharge(level, owner, ox, gameTime);
			case IMPACT -> {
				transition(ox, MegumiOxPolicy.Event.PASS_THROUGH, gameTime);
				tickCharge(level, owner, ox, gameTime);
			}
			case RECOVERY -> tickRecovery(ox, gameTime);
		}
	}

	private static void tickFollow(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox, long gameTime) {
		if (owner == null || !ox.attackReady(gameTime)) {
			return;
		}
		LivingEntity target = resolve(level, ox.sicTargetUuid());
		if (target == null) {
			return;
		}
		transition(ox, MegumiOxPolicy.Event.TARGET_AVAILABLE, gameTime);
	}

	private static void tickAcquire(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox, long gameTime) {
		LivingEntity target = resolve(level, ox.sicTargetUuid());
		if (!validMarkedTarget(level, owner, ox, target)
				|| !MegumiOxPolicy.canAcquire(new MegumiOxPolicy.AcquireFacts(
						target != null,
						target != null && ox.hasLineOfSight(target),
						target == null ? Double.POSITIVE_INFINITY : ox.distanceTo(target)))) {
			transition(ox, MegumiOxPolicy.Event.ABANDON, gameTime);
			ox.beginRecovery(gameTime);
			return;
		}
		transition(ox, MegumiOxPolicy.Event.ACQUIRED, gameTime);
		ox.beginAlign(target.getUUID(), gameTime);
	}

	private static void tickAlign(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox, long gameTime) {
		LivingEntity target = lockedTarget(level, owner, ox);
		if (!validPreCommitTarget(level, owner, ox, target)) {
			transition(ox, MegumiOxPolicy.Event.ABANDON, gameTime);
			ox.beginRecovery(gameTime);
			return;
		}
		Vec3 targetDirection = target.position().subtract(ox.position()).multiply(1.0, 0.0, 1.0);
		if (targetDirection.lengthSqr() < 1.0E-8) {
			transition(ox, MegumiOxPolicy.Event.ABANDON, gameTime);
			ox.beginRecovery(gameTime);
			return;
		}
		faceTarget(ox, targetDirection);
		Vec3 facing = ox.getLookAngle();
		if (MegumiOxPolicy.aligned(facing, targetDirection, MegumiShikigamiProfile.OX_ALIGN_MAX_ANGLE)) {
			transition(ox, MegumiOxPolicy.Event.ALIGNED, gameTime);
			ox.beginWindup(gameTime);
			playCue(level, owner, ox, MegumiVfxIds.OX_WINDUP, ox.position(), 1, ox.chargeDirection(), gameTime);
			level.playSound(null, ox.getX(), ox.getY(), ox.getZ(), SoundEvents.RAVAGER_ATTACK,
					SoundSource.NEUTRAL, 0.85f, 0.75f);
		}
	}

	private static void tickWindup(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox, long gameTime) {
		LivingEntity target = lockedTarget(level, owner, ox);
		if (!validPreCommitTarget(level, owner, ox, target)) {
			transition(ox, MegumiOxPolicy.Event.ABANDON, gameTime);
			finishCharge(ox, gameTime, false, level);
			return;
		}
		if (gameTime - ox.stateStartedGameTime() >= MegumiShikigamiProfile.OX_WINDUP_TICKS) {
			transition(ox, MegumiOxPolicy.Event.WINDUP_COMPLETE, gameTime);
			if (ox.chargeDirection().lengthSqr() < 1.0E-8) {
				finishCharge(ox, gameTime, false, level);
				return;
			}
			ox.beginCharge(gameTime);
			playCue(level, owner, ox, MegumiVfxIds.OX_CHARGE, ox.position(), 1, ox.chargeDirection(), gameTime);
		}
	}

	private static void tickCharge(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox, long gameTime) {
		if (ox.chargeState() == MegumiOxPolicy.State.IMPACT) {
			transition(ox, MegumiOxPolicy.Event.PASS_THROUGH, gameTime);
		}
		Vec3 before = ox.position();
		AABB previousBox = ox.getBoundingBox();
		Vec3 requestedStep = ox.chargeDirection().scale(MegumiShikigamiProfile.OX_CHARGE_SPEED);
		ox.move(MoverType.SELF, requestedStep);
		Vec3 after = ox.position();
		Vec3 resolvedDelta = after.subtract(before);
		ox.recordChargeStep(MegumiOxPolicy.resolvedTravel(before, after));

		AABB broadPhase = previousBox.expandTowards(resolvedDelta)
				.inflate(MegumiShikigamiProfile.OX_SWEEP_MARGIN);
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, broadPhase,
				entity -> entity != ox && entity.isAlive() && !entity.isRemoved()
						&& !entity.isSpectator() && !entity.isInvulnerable())) {
			UUID candidateUuid = candidate.getUUID();
			if (owner == null || !MegumiSummonRuntime.isEligibleTarget(owner, candidate)
					|| !MegumiOxPolicy.canRegisterHit(candidateUuid, ox.hitTargetUuids())
					|| !MegumiOxPolicy.sweptHit(previousBox, ox.getBoundingBox(), candidate.getBoundingBox(),
							before, after)) {
				continue;
			}
			ox.hitTargetUuids().add(candidateUuid);
			applyImpact(level, owner, ox, candidate, gameTime);
			transition(ox, MegumiOxPolicy.Event.ENTITY_HIT, gameTime);
			ox.recordImpact(gameTime);
		}

		int intensity = chargeIntensity(ox.accumulatedChargeDistance());
		playCue(level, owner, ox, MegumiVfxIds.OX_CHARGE, after, intensity, ox.chargeDirection(), gameTime);
		boolean abort = MegumiOxPolicy.shouldAbort(ox.horizontalCollision, ox.verticalCollision,
				ox.onGround(), ox.chargeElapsedTicks());
		boolean distanceComplete = ox.accumulatedChargeDistance() >= MegumiShikigamiProfile.OX_CHARGE_MAX_DISTANCE;
		if (abort) {
			transition(ox, MegumiOxPolicy.Event.WALL_ABORT, gameTime);
			playCue(level, owner, ox, MegumiVfxIds.OX_ABORT, after, intensity, ox.chargeDirection(), gameTime);
			finishCharge(ox, gameTime, true, level);
			return;
		}
		if (distanceComplete) {
			transition(ox, MegumiOxPolicy.Event.CHARGE_FINISHED, gameTime);
			finishCharge(ox, gameTime, false, level);
			return;
		}
		if (!ox.impactClipActive(gameTime)) {
			ox.beginAction(MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS
					+ MegumiShikigamiProfile.OX_RECOVERY_TICKS);
		}
	}

	private static void applyImpact(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			LivingEntity target, long gameTime) {
		double power = MegumiOxPolicy.impactPower(ox.accumulatedChargeDistance());
		double normalized = power / MegumiShikigamiProfile.OX_IMPACT_MAX;
		float damage = (float) power;
		DamageSource source = owner != null
				? level.damageSources().playerAttack(owner)
				: level.damageSources().magic();
		boolean accepted = target.hurtServer(level, source, damage);
		if (accepted) {
			int staggerTicks = Math.max(1, (int) Math.round(
					MegumiShikigamiProfile.OX_HIT_STAGGER_TICKS * normalized));
			CombatStagger.GLOBAL.apply(target, gameTime, staggerTicks);
			double knockback = MegumiShikigamiProfile.OX_HIT_KNOCKBACK * normalized;
			Vec3 direction = ox.chargeDirection();
			target.knockback(knockback, -direction.x, -direction.z);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.RAVAGER_ATTACK,
					SoundSource.NEUTRAL, 0.9f, 0.85f + (float) normalized * 0.25f);
		}
		playCue(level, owner, ox, MegumiVfxIds.OX_IMPACT, target.position(),
				chargeIntensity(ox.accumulatedChargeDistance()), ox.chargeDirection(), gameTime);
	}

	private static void finishCharge(MegumiOxEntity ox, long gameTime, boolean aborted,
			ServerLevel level) {
		if (ox.chargeElapsedTicks() > 0) {
			ox.markAttackUsed(gameTime, MegumiShikigamiProfile.OX_CHARGE_COOLDOWN_TICKS);
		}
		if (aborted) {
			level.playSound(null, ox.getX(), ox.getY(), ox.getZ(), SoundEvents.RAVAGER_ROAR,
					SoundSource.NEUTRAL, 0.6f, 0.8f);
		} else if (!ox.hitTargetUuids().isEmpty()) {
			level.playSound(null, ox.getX(), ox.getY(), ox.getZ(), SoundEvents.RAVAGER_ROAR,
					SoundSource.NEUTRAL, 0.45f, 1.15f);
		}
		if (ox.chargeState() != MegumiOxPolicy.State.RECOVERY) {
			transition(ox, aborted ? MegumiOxPolicy.Event.WALL_ABORT : MegumiOxPolicy.Event.CHARGE_FINISHED,
					gameTime);
		}
		if (ox.chargeState() == MegumiOxPolicy.State.RECOVERY) {
			ox.beginRecovery(gameTime);
		}
	}

	private static void tickRecovery(MegumiOxEntity ox, long gameTime) {
		if (gameTime - ox.stateStartedGameTime() >= MegumiShikigamiProfile.OX_RECOVERY_TICKS) {
			transition(ox, MegumiOxPolicy.Event.RECOVERY_COMPLETE, gameTime);
			ox.finishRecovery(gameTime);
		}
	}

	private static boolean validMarkedTarget(ServerLevel level, ServerPlayer owner,
			MegumiOxEntity ox, LivingEntity target) {
		return owner != null && target != null && target.isAlive() && !target.isRemoved()
				&& !target.isSpectator() && !target.isInvulnerable() && target.level() == level && target != ox
				&& target.getUUID().equals(ox.sicTargetUuid())
				&& MegumiSummonRuntime.isEligibleTarget(owner, target);
	}

	private static boolean validPreCommitTarget(ServerLevel level, ServerPlayer owner,
			MegumiOxEntity ox, LivingEntity target) {
		return validMarkedTarget(level, owner, ox, target)
				&& ox.chargeTargetUuid() != null
				&& ox.chargeTargetUuid().equals(target.getUUID())
				// A sic overwrite after ALIGN locked the mark must abort the stale line, the same
				// gate the serpent's ambushTargetStillValid applies to its locked target.
				&& ox.chargeTargetUuid().equals(ox.sicTargetUuid())
				&& ox.hasLineOfSight(target)
				&& ox.distanceTo(target) <= MegumiShikigamiProfile.OX_ACQUIRE_RANGE;
	}

	private static LivingEntity lockedTarget(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox) {
		UUID targetUuid = ox.chargeTargetUuid();
		return owner == null || targetUuid == null ? null : resolve(level, targetUuid);
	}


	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}

	private static void faceTarget(MegumiOxEntity ox, Vec3 horizontalDirection) {
		float yaw = (float) (Math.atan2(-horizontalDirection.x, horizontalDirection.z) * (180.0 / Math.PI));
		ox.setYRot(yaw);
		ox.yBodyRot = yaw;
		ox.yHeadRot = yaw;
		ox.setXRot(0.0f);
	}

	private static int chargeIntensity(double distance) {
		double normalized = Math.max(0.0, Math.min(1.0,
			distance / MegumiShikigamiProfile.OX_CHARGE_MAX_DISTANCE));
		return 1 + (int) Math.round(normalized * 3.0);
	}

	private static void playCue(ServerLevel level, ServerPlayer owner, MegumiOxEntity ox,
			ResourceLocation effectId, Vec3 origin, int intensity,
			Vec3 direction, long gameTime) {
		if (owner == null) {
			return;
		}
		VfxCue cue = MegumiShikigamiRuntime.directedCue(effectId, origin, ox.getId(), ox.position(),
				intensity, gameTime, owner.getRandom().nextLong(), direction);
		MegumiShikigamiRuntime.broadcastCue(level, owner, cue);
	}

	private static void transition(MegumiOxEntity ox, MegumiOxPolicy.Event event, long gameTime) {
		MegumiOxPolicy.State next = MegumiOxPolicy.nextState(ox.chargeState(), event);
		if (next != ox.chargeState()) {
			ox.setChargeState(next, gameTime);
		}
	}
}
