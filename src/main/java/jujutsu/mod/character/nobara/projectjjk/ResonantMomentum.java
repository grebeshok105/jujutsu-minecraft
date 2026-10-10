package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.registry.JujutsuEffects;

/** Native-effect-backed Momentum helpers for explicit Nobara gameplay scaling. */
public final class ResonantMomentum {
	private ResonantMomentum() {}

	/**
	 * Grants the short execution window used by a successful resonance, chain, or mega impact.
	 * Re-granting refreshes the same bounded amplifier rather than stacking damage multipliers.
	 */
	public static void grantExecutionMoment(ServerPlayer player, int ticks) {
		if (player == null || ticks <= 0) {
			return;
		}
		player.addEffect(new MobEffectInstance(
				JujutsuEffects.RESONANT_MOMENTUM,
				ticks,
				0,
				false,
				false,
				true));
	}

	public static boolean isActive(LivingEntity entity) {
		return entity != null && entity.hasEffect(JujutsuEffects.RESONANT_MOMENTUM);
	}

	public static float damageMultiplier(ServerPlayer player) {
		return isActive(player) ? ProjectJjkNobaraProfile.MOMENTUM_DAMAGE_MULT : 1.0f;
	}

	public static int scaleTicks(int baseTicks, float speedMultiplier) {
		if (baseTicks <= 0) return 0;
		return Math.max(1, Math.round(baseTicks / Math.max(1.0f, speedMultiplier)));
	}

	public static int scaleTicks(ServerPlayer player, int baseTicks) {
		return scaleTicks(baseTicks, damageMultiplier(player));
	}

	public static int accelerateElapsedTicks(ServerPlayer player, int elapsedTicks) {
		return Math.max(0, (int) Math.floor(elapsedTicks * damageMultiplier(player)));
	}
}
