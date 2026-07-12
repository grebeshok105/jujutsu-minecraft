package jujutsu.mod.registry;

import jujutsu.mod.JujutsuMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class JujutsuEffects {
	public static final Holder<MobEffect> RESONANT_MOMENTUM = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("resonant_momentum"),
			new ResonantMomentumEffect());

	private JujutsuEffects() {}

	public static void register() {
		// Class loading performs the registrations above.
	}

	private static final class ResonantMomentumEffect extends MobEffect {
		private ResonantMomentumEffect() {
			super(MobEffectCategory.BENEFICIAL, 0x55D6DC);
		}
	}
}
