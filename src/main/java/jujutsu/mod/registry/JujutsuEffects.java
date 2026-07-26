package jujutsu.mod.registry;

import jujutsu.mod.JujutsuMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.character.todo.TodoProfile;

public final class JujutsuEffects {
	public static final Holder<MobEffect> RESONANT_MOMENTUM = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("resonant_momentum"),
			new ResonantMomentumEffect());

	/**
	 * The window a landed Boogie Woogie opens. The damage rides on the effect's own attribute modifier
	 * rather than on a second hit at damage time, which is what keeps it impossible to double-apply: the
	 * vanilla swing is simply bigger, and the runtime only has to decide when to take the effect away.
	 *
	 * <p>ADD_MULTIPLIED_TOTAL composes with Todo's standing +0.50 as {@code base x 1.50 x 1.25}, which is
	 * "a quarter more than he already hits". ADD_MULTIPLIED_BASE would silently mean x1.75 instead.
	 */
	public static final Holder<MobEffect> TODO_SWAP_MOMENTUM = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("todo_swap_momentum"),
			new TodoSwapMomentumEffect().addAttributeModifier(
					Attributes.ATTACK_DAMAGE,
					JujutsuMod.id("todo/swap_momentum"),
					TodoProfile.SWAP_MOMENTUM_DAMAGE_MULTIPLIER - 1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	private JujutsuEffects() {}

	public static void register() {
		// Class loading performs the registrations above.
	}

	private static final class ResonantMomentumEffect extends MobEffect {
		private ResonantMomentumEffect() {
			super(MobEffectCategory.BENEFICIAL, 0x55D6DC);
		}
	}

	private static final class TodoSwapMomentumEffect extends MobEffect {
		private TodoSwapMomentumEffect() {
			super(MobEffectCategory.BENEFICIAL, 0xB26CFF);
		}
	}
}
