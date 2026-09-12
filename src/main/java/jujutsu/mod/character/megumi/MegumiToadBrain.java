package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Toad's sic behaviour: a short windup, then the tongue strikes on the last windup tick and yanks
 * the target toward the body. The strike is instant (no projectile), so only the target's state is
 * asserted — never a flight path.
 */
final class MegumiToadBrain {
	private MegumiToadBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiToadEntity toad, long gameTime) {
		LivingEntity target = resolve(level, toad.sicTargetUuid());
		if (target == null) {
			return;
		}
		if (MegumiToadPolicy.strikeTickReached(toad.actionTicks())) {
			strike(level, owner, toad, target, gameTime);
			return;
		}
		if (toad.actionTicks() > 0 || owner == null || !toad.attackReady(gameTime)) {
			return;
		}
		if (!MegumiToadPolicy.canTongue(toad.distanceTo(target)) || !owner.hasLineOfSight(target)) {
			return;
		}
		toad.beginAction(MegumiShikigamiProfile.TOAD_TONGUE_WINDUP_TICKS);
		level.playSound(null, toad.getX(), toad.getY(), toad.getZ(), SoundEvents.FROG_TONGUE,
				SoundSource.NEUTRAL, 0.9f, 1.0f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TOAD_TONGUE,
				toad.position(), toad.getId(), Vec3.ZERO);
	}

	private static void strike(ServerLevel level, ServerPlayer owner, MegumiToadEntity toad,
			LivingEntity target, long gameTime) {
		if (MegumiToadPolicy.canTongue(toad.distanceTo(target))
				&& (owner != null ? owner.hasLineOfSight(target) : toad.hasLineOfSight(target))) {
			DamageSource source = owner != null
					? level.damageSources().playerAttack(owner)
					: level.damageSources().magic();
			target.hurtServer(level, source, (float) MegumiShikigamiProfile.TOAD_TONGUE_DAMAGE);
			// Stagger first: its velocity scale would eat the pull below, so the grab is applied
			// last and is exactly what the target carries into the next tick.
			CombatStagger.GLOBAL.apply(target, gameTime, MegumiShikigamiProfile.TOAD_TONGUE_STAGGER_TICKS);
			target.setDeltaMovement(MegumiToadPolicy.pullVelocity(target.position(), toad.position()));
			target.hurtMarked = true;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FROG_EAT,
					SoundSource.NEUTRAL, 0.9f, 1.0f);
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TOAD_TONGUE,
					target.position(), target.getId(), new Vec3(0.0, target.getBbHeight() * 0.5, 0.0));
		}
		toad.markAttackUsed(gameTime, MegumiShikigamiProfile.TOAD_TONGUE_COOLDOWN_TICKS);
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}
}
