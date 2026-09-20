package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Nue's sic behaviour: hover, then a straight electric dive at the assigned target. The flight is
 * server-driven exactly like the dog pounce (velocity + {@code move(SELF)}), because the AI is
 * suspended for the duration.
 */
final class MegumiNueBrain {
	private MegumiNueBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiNueEntity nue, long gameTime) {
		LivingEntity target = resolve(level, nue.sicTargetUuid());
		if (nue.diving()) {
			if (target == null || gameTime >= nue.diveDeadlineGameTime()) {
				nue.endDive();
				return;
			}
			Vec3 velocity = MegumiNuePolicy.diveVelocity(nue.position(), target.getEyePosition());
			if (velocity.lengthSqr() < 1.0E-8) {
				nue.endDive();
				return;
			}
			nue.setDeltaMovement(velocity);
			nue.move(MoverType.SELF, velocity);
			float yaw = yawTo(velocity);
			if (Float.isFinite(yaw)) {
				nue.setYRot(yaw);
				nue.yBodyRot = yaw;
			}
			if (MegumiNuePolicy.impactReachedSq(target.getBoundingBox().distanceToSqr(nue.position()))) {
				impact(level, owner, nue, target, gameTime);
				return;
			}
			if (nue.horizontalCollision || nue.verticalCollision) {
				nue.endDive();
			}
			return;
		}
		if (target == null) {
			return;
		}
		if (!MegumiNuePolicy.canStartDive(nue.distanceTo(target), nue.hasLineOfSight(target),
				nue.attackReady(gameTime))) {
			return;
		}
		nue.beginDive(target, gameTime);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.NUE_DIVE,
				nue.position(), nue.getId(), nue.position(),
				target.getEyePosition().subtract(nue.position()));
	}

	private static void impact(ServerLevel level, ServerPlayer owner, MegumiNueEntity nue,
			LivingEntity target, long gameTime) {
		boolean soaked = target.hasEffect(JujutsuEffects.MEGUMI_SOAKED);
		nue.endDive();
		nue.beginAction(MegumiShikigamiProfile.NUE_ATTACK_ACTION_TICKS);
		DamageSource source = owner != null
				? level.damageSources().playerAttack(owner)
				: level.damageSources().magic();
		target.hurtServer(level, source, (float) MegumiNuePolicy.impactDamage(soaked));
		CombatStagger.GLOBAL.apply(target, gameTime, MegumiNuePolicy.stunTicks(soaked));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
				MegumiNuePolicy.slowTicks(soaked), 0, true, false, true), owner);
		nue.markAttackUsed(gameTime, MegumiShikigamiProfile.NUE_CHARGE_COOLDOWN_TICKS);
		level.playSound(null, nue.getX(), nue.getY(), nue.getZ(), SoundEvents.PHANTOM_BITE,
				SoundSource.NEUTRAL, 0.9f, 1.08f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER.value(),
				SoundSource.NEUTRAL, 0.5f, 1.4f);
		Vec3 targetPos = target.position();
		Vec3 nuePos = nue.position();
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.NUE_SHOCK, targetPos,
				nue.getId(), nuePos, targetPos.subtract(nuePos));
	}

	/**
	 * The production shock payload factory. Keeping this seam pure lets the cue contract be pinned
	 * without a client or a network connection: the target stays immutable in {@code origin}, while
	 * the Nue id is the only live endpoint and the direction is source-to-target.
	 */
	static VfxCue shockCue(Vec3 targetPos, int nueEntityId, Vec3 nuePos, long gameTime, long seed) {
		return MegumiShikigamiRuntime.directedCue(MegumiVfxIds.NUE_SHOCK, targetPos, nueEntityId,
				nuePos, 1, gameTime, seed, targetPos.subtract(nuePos));
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}

	/**
	 * Yaw facing a velocity under the Minecraft convention ({@code x = -sin(yaw)},
	 * {@code z = cos(yaw)}): the x component is negated, exactly like the elephant's aim and the
	 * shared base helper. The unnegated form mirrors the model across Z (eastbound flight faces
	 * west) while steering stays correct. Package-visible for the axis pin test.
	 */
	static float yawTo(Vec3 velocity) {
		if (velocity.horizontalDistanceSqr() < 1.0E-8) {
			return Float.NaN;
		}
		return (float) (Math.atan2(-velocity.x, velocity.z) * (180.0 / Math.PI));
	}
}