package jujutsu.mod.client.vfx.domain;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ChatScreen;
import jujutsu.mod.client.character.QuickSelectorScreen;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.character.JujutsuCharacter;

/**
 * Debug/dev toggle for the vessel quick-selector overlay (issue #109 dev lane).
 *
 * <p>The selector normally opens on a ~200 ms hold of the quick-selector key, which the MCP dev lane
 * cannot inject (upstream client tools are read-only). This command opens/closes the same screen the
 * key would, through the same vessel-neutral hook, so a scripted run can screenshot and inspect it.
 * It asks the selected vessel's client definition — it never names one.
 *
 * <p>Trigger: {@code /jujutsu_debug shikigami_selector} — opens when closed, closes when open.
 */
public final class ShikigamiSelectorDebug {
	private ShikigamiSelectorDebug() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("jujutsu_debug")
						.then(ClientCommandManager.literal("shikigami_selector")
								.executes(context -> toggle(context.getSource())))));
	}

	private static int toggle(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.player == null) {
			source.sendError(Component.literal("shikigami_selector needs a loaded player"));
			return 0;
		}
		if (client.screen != null && !(client.screen instanceof ChatScreen)) {
			// Only the selector toggles; any other open screen is left alone. The ChatScreen is the
			// exception: client commands execute before the chat screen closes itself, so the
			// invoking screen is always the chat screen — without the pass-through the open half
			// would be unreachable.
			if (client.screen instanceof QuickSelectorScreen) {
				client.screen.onClose();
				source.sendFeedback(Component.literal("shikigami_selector: closed"));
				return 1;
			}
			source.sendError(Component.literal("another screen is open"));
			return 0;
		}
		JujutsuCharacter selected = ClientCharacterSelectionManager.characterOrNone(client.player.getUUID());
		var definition = JujutsuCharacterClients.definition(selected);
		if (!definition.hasQuickSelector()) {
			source.sendError(Component.literal("selected vessel has no quick selector"));
			return 0;
		}
		// Deferred one tick: the ChatScreen closes itself right after this command returns, and a
		// screen set now would be discarded with it. execute() runs after the chat screen is gone.
		client.execute(() -> {
			if (client.screen == null) {
				definition.openQuickSelector(client);
			}
		});
		source.sendFeedback(Component.literal("shikigami_selector: opened"));
		return 1;
	}
}
