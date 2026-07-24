package jujutsu.mod.character;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.todo.TodoProfile;

/** Applies the small set of character-owned vanilla combat attribute modifiers. */
public final class CharacterCombatModifiers {
	private static final ResourceLocation TODO_DAMAGE_ID = JujutsuMod.id("todo/melee_damage");
	private static final ResourceLocation TODO_ATTACK_SPEED_ID = JujutsuMod.id("todo/attack_speed");

	private CharacterCombatModifiers() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reapply(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reapply(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> reapply(player));
	}

	public static void applyForSelection(ServerPlayer player, JujutsuCharacter selected) {
		clearTodoModifiers(player);
		if (selected != JujutsuCharacter.TODO) {
			return;
		}
		addMultiplier(player.getAttribute(Attributes.ATTACK_DAMAGE), TODO_DAMAGE_ID, TodoProfile.MELEE_DAMAGE_MULTIPLIER - 1.0);
		addMultiplier(player.getAttribute(Attributes.ATTACK_SPEED), TODO_ATTACK_SPEED_ID, TodoProfile.ATTACK_SPEED_MULTIPLIER - 1.0);
	}

	public static void reapply(ServerPlayer player) {
		applyForSelection(player, CharacterSelectionManager.selected(player));
	}

	public static int adjustedStaggerTicks(LivingEntity entity, int requestedTicks) {
		if (requestedTicks <= 0 || !(entity instanceof ServerPlayer player)
				|| CharacterSelectionManager.selected(player) != JujutsuCharacter.TODO) {
			return requestedTicks;
		}
		return Math.max(1, (int) Math.ceil(requestedTicks * TodoProfile.STAGGER_DURATION_MULTIPLIER));
	}

	private static void clearTodoModifiers(ServerPlayer player) {
		remove(player.getAttribute(Attributes.ATTACK_DAMAGE), TODO_DAMAGE_ID);
		remove(player.getAttribute(Attributes.ATTACK_SPEED), TODO_ATTACK_SPEED_ID);
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
