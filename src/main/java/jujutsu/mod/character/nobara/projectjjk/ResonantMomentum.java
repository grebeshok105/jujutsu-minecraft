package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import jujutsu.mod.registry.JujutsuEffects;

/** Native-effect-backed Momentum helpers for explicit Nobara gameplay scaling. */
public final class ResonantMomentum {
	private ResonantMomentum() {}

	public static void grant(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(
				JujutsuEffects.RESONANT_MOMENTUM,
				ProjectJjkNobaraProfile.RESONANT_MOMENTUM_DURATION_TICKS,
				0,
				false,
				false,
				true));
	}

	public static float damageMultiplier(ServerPlayer player) {
		if (player == null || !player.hasEffect(JujutsuEffects.RESONANT_MOMENTUM)) return 1.0f;
		MobEffectInstance effect = player.getEffect(JujutsuEffects.RESONANT_MOMENTUM);
		int level = effect == null ? 1 : effect.getAmplifier() + 1;
		return 1.0f + (ProjectJjkNobaraProfile.RESONANT_MOMENTUM_MULTIPLIER - 1.0f) * level;
	}

	public static int scaleTicks(int baseTicks, float speedMultiplier) {
		if (baseTicks <= 0) return 0;
		return Math.max(1, Math.round(baseTicks / Math.max(1.0f, speedMultiplier)));
	}

	public static int scaleTicks(ServerPlayer player, int baseTicks) {
		return scaleTicks(baseTicks, damageMultiplier(player));
	}

	public static int accelerateElapsedTicks(ServerPlayer player, int elapsedTicks) {
		return Math.max(0, (int)Math.floor(elapsedTicks * damageMultiplier(player)));
	}
}
