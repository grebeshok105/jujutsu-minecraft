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
	public int adjustStaggerTicks(int requestedTicks) {
		return Math.max(1, (int) Math.ceil(requestedTicks * TodoProfile.STAGGER_DURATION_MULTIPLIER));
	}

	/**
	 * Leaving mid-setup must not leave a mark a later cast could consume, nor a glowing body or a resting
	 * marker in the world with no owner who can use it.
	 *
	 * <p>This used to run on every selection change rather than only on leaving him, which also destroyed
	 * a marker thrown by someone who was never Todo — see E12. Running it only for the vessel being left
	 * is the honest rule; the stray marker it no longer destroys expires on its own.
	 */
	@Override
	public void onDeselected(ServerPlayer player) {
		TodoPairSwapRuntime.forget(player.getUUID());
		TodoSwapMarks.clear(player.getServer(), player.getUUID());
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
