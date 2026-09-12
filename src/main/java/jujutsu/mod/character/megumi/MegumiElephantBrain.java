package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
		if (elephant.jetActive()) {
			tickJet(level, owner, elephant, gameTime);
			return;
		}
		LivingEntity target = resolve(level, elephant.sicTargetUuid());
		if (target == null) {
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
}
