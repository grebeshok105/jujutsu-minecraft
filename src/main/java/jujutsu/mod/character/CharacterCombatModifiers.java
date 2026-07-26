package jujutsu.mod.character;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Re-applies vanilla combat attribute modifiers on the events that can drop them.
 *
 * <p>Which modifiers exist is not this file's business: it asks the definitions. The ids and numbers
 * live with the vessel that owns them.
 */
public final class CharacterCombatModifiers {
	private CharacterCombatModifiers() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reapply(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reapply(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> reapply(player));
	}

	/**
	 * Every vessel drops its own modifiers, then the selected one adds its own. Sweeping all of them
	 * rather than clearing a known set is what lets this file stop naming vessels: a definition that adds
	 * nothing also removes nothing, and the sweep costs one map lookup per vessel.
	 */
	public static void applyForSelection(ServerPlayer player, JujutsuCharacter selected) {
		for (CharacterDefinition definition : JujutsuCharacters.all()) {
			definition.removeAttributes(player);
		}
		JujutsuCharacters.definition(selected).applyAttributes(player);
	}

	public static void reapply(ServerPlayer player) {
		applyForSelection(player, CharacterSelectionManager.selected(player));
	}

	public static int adjustedStaggerTicks(LivingEntity entity, int requestedTicks) {
		if (requestedTicks <= 0 || !(entity instanceof ServerPlayer player)) {
			return requestedTicks;
		}
		return JujutsuCharacters.of(player).adjustIncomingStaggerTicks(requestedTicks);
	}
}
