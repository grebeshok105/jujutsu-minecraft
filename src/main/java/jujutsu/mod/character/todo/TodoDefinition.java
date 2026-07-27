package jujutsu.mod.character.todo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacter;

/** Todo on the server: a heavier melee that shrugs off stagger, and three casts on the shared slots. */
public final class TodoDefinition implements CharacterDefinition {
	private static final ResourceLocation DAMAGE_ID = JujutsuMod.id("todo/melee_damage");
	private static final ResourceLocation ATTACK_SPEED_ID = JujutsuMod.id("todo/attack_speed");

	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.TODO;
	}

	/**
	 * Shift+B is B for him. The pair swap takes two presses on one key and cares about neither stance nor
	 * hands, so crouching to line up the second participant used to lose the press — and silently, since
	 * the mark then ticked on toward an expiry the player never saw explained.
	 */
	@Override
	public CharacterAbility canonicalSlot(CharacterAbility slot) {
		return slot == CharacterAbility.SECONDARY_SNEAK ? CharacterAbility.SECONDARY : slot;
	}

	@Override
	public boolean tryCast(ServerPlayer player, CharacterAbility slot, boolean notify) {
		return TodoAbilityRouter.tryCast(player, slot, notify);
	}

	@Override
	public void applyAttributes(ServerPlayer player) {
		addMultiplier(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, TodoProfile.MELEE_DAMAGE_MULTIPLIER - 1.0);
		addMultiplier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID, TodoProfile.ATTACK_SPEED_MULTIPLIER - 1.0);
	}

	@Override
	public void removeAttributes(ServerPlayer player) {
		remove(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID);
		remove(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID);
	}

	@Override
	public void registerServerHooks() {
		TodoBlackFlashRuntime.register();
		// After the Black Flash, so its re-entrant bonus hit is already inside APPLYING_BONUS by the time
		// the momentum listener sees the nested pass and declines to spend on it.
		TodoSwapMomentumRuntime.register();
		TodoBoogieWoogieRuntime.register();
		TodoPairSwapRuntime.register();
		TodoSwapMarks.register();
		TodoStateLifecycle.register();
	}

	/** Halves it, but never rounds a real stagger away, and never invents one from nothing. */
	@Override
	public int adjustIncomingStaggerTicks(int requestedTicks) {
		if (requestedTicks <= 0) {
			return requestedTicks;
		}
		return Math.max(1, (int) Math.ceil(requestedTicks * TodoProfile.STAGGER_DURATION_MULTIPLIER));
	}

	/**
	 * Leaving mid-setup must not leave a mark a later cast could consume, nor a glowing body or a resting
	 * marker in the world with no owner who can use it.
	 *
	 * <p>This used to run on every selection change rather than only on leaving him, which also destroyed
	 * a marker thrown by someone who was never Todo — see E12. Running it only for the vessel being left
	 * is the honest rule.
	 *
	 * <p>The teardown itself lives in {@link TodoStateLifecycle}, because death needs exactly the same one
	 * and two copies of a five-line cleanup are two chances to forget the fifth line. That includes the
	 * momentum effect: the spend path checks the attacker is still Todo, so leaving mid-window would
	 * otherwise strand a live {@code +25%} attack modifier on another vessel with nothing left that could
	 * take it off — the attribute sweeps do not reach it, since it belongs to the effect rather than here.
	 */
	@Override
	public void onDeselected(ServerPlayer player) {
		TodoStateLifecycle.dropEverything(player);
	}

	private static void addMultiplier(AttributeInstance attribute, ResourceLocation id, double amount) {
		if (attribute == null) {
			return;
		}
		attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void remove(AttributeInstance attribute, ResourceLocation id) {
		if (attribute != null) {
			attribute.removeModifier(id);
		}
	}
}
