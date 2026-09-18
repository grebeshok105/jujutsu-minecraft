package jujutsu.mod.registry;

import jujutsu.mod.JujutsuMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.character.megumi.MegumiProfile;
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

	/**
	 * The hold of Megumi's shadow trap. Pure vanilla attribute mathematics, mirrored to clients by the
	 * effect system itself: speed collapses to a quarter and jumping is suppressed outright, so a gripped
	 * body still crawls out of the pool but never hops over it. The trap runtime re-applies the effect
	 * every tick a body stays inside; leaving the zone lets the short duration expire on its own, which
	 * is the whole cleanup story — nothing ever needs manual effect scrubbing.
	 */
	public static final Holder<MobEffect> MEGUMI_SHADOW_GRIP = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("megumi_shadow_grip"),
			new MegumiShadowGripEffect()
					.addAttributeModifier(
							Attributes.MOVEMENT_SPEED,
							JujutsuMod.id("megumi/shadow_grip_speed"),
							MegumiProfile.SHADOW_GRIP_SPEED_MULTIPLIER,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(
							Attributes.JUMP_STRENGTH,
							JujutsuMod.id("megumi/shadow_grip_jump"),
							MegumiProfile.SHADOW_GRIP_JUMP_MULTIPLIER,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	/**
	 * The drench left by Max Elephant's trunk. Pure combo marker with no attribute work of its own:
	 * Nue's shock reads it for the canon soak-and-zap escalation, and it expires by itself.
	 */
	public static final Holder<MobEffect> MEGUMI_SOAKED = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("megumi_soaked"),
			new MegumiSoakedEffect());

	/**
	 * The hold marker (issue #79): whoever carries it is being held in place by something else — the
	 * Toad's grab or a cursed spirit's runner — so the client must not fight the server's pin with
	 * its own movement input. A pure marker: no attribute work and no icon, position is enforced by
	 * {@code HoldSupport}. One id for both systems on purpose: the mechanic is one, only the VFX
	 * differ.
	 */
	public static final Holder<MobEffect> GRIPPED = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("gripped"),
			new GrippedEffect());

	/**
	 * The fear marker (Block 3, #86): whoever carries it is feared by a cursed spirit — the
	 * client inverts that player's input while it lasts. A pure marker like GRIPPED: the
	 * readable debuffs (DARKNESS, NAUSEA) ride alongside it as separate vanilla effects, and
	 * position/behaviour stay server-side. Suppressed from HUD (applied invisible).
	 */
	public static final Holder<MobEffect> CURSED_FEAR = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("cursed_fear"),
			new CursedFearEffect());

	/**
	 * Nue's partial manifestation (issue #108): while the owner carries it, the wings are out —
	 * {@code EntityElytraEvents} grant fall-flying and fall damage is cancelled. Pure marker, no
	 * attribute work.
	 */
	public static final Holder<MobEffect> MEGUMI_NUE_WINGS = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("megumi_nue_wings"),
			new MegumiNueWingsEffect());

	/**
	 * Toad's partial manifestation (issue #108): marks that the tongue grapple is anchored. The
	 * anchor itself travels in {@code MegumiTongueStatePayload}; this flag is the both-sides gate.
	 */
	public static final Holder<MobEffect> MEGUMI_TOAD_TONGUE = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			JujutsuMod.id("megumi_toad_tongue"),
			new MegumiToadTongueEffect());

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

	private static final class MegumiShadowGripEffect extends MobEffect {
		private MegumiShadowGripEffect() {
			super(MobEffectCategory.HARMFUL, 0x102E2B);
		}
	}

	private static final class MegumiSoakedEffect extends MobEffect {
		private MegumiSoakedEffect() {
			super(MobEffectCategory.HARMFUL, 0x2E6FA8);
		}
	}
	private static final class GrippedEffect extends MobEffect {
		private GrippedEffect() {
			super(MobEffectCategory.HARMFUL, 0x4A5D23);
		}
	}

	private static final class CursedFearEffect extends MobEffect {
		private CursedFearEffect() {
			super(MobEffectCategory.HARMFUL, 0x1A0B2E);
		}
	}

	private static final class MegumiNueWingsEffect extends MobEffect {
		private MegumiNueWingsEffect() {
			super(MobEffectCategory.BENEFICIAL, 0x3B2E5A);
		}
	}

	private static final class MegumiToadTongueEffect extends MobEffect {
		private MegumiToadTongueEffect() {
			super(MobEffectCategory.BENEFICIAL, 0x2E5A3B);
		}
	}
}
