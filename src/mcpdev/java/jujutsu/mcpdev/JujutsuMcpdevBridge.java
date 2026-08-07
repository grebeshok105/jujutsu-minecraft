package jujutsu.mcpdev;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.character.CharacterSelectionManager;

/**
 * Dev-only lifecycle bridge (issue #43 spike) for the mcpdev companion mod.
 *
 * <p>The upstream MCP adapter exposes no public MinecraftServer accessor, so this
 * initializer keeps the current server reference for {@link JujutsuModStatusTool}.
 * Reads happen on the server main thread via the tool's normal upstream dispatch;
 * this class never touches MCP protocol types.
 */
public final class JujutsuMcpdevBridge implements ModInitializer {

	private static volatile MinecraftServer server;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/** Friendly version of the jujutsumod mod container, or "unknown" when absent. */
	static String modVersion() {
		return FabricLoader.getInstance()
				.getModContainer("jujutsumod")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	/**
	 * Vessel id of the first online player, or {@code null} when nobody is online.
	 * Call on the server main thread only.
	 */
	static String selectedVesselId() {
		MinecraftServer current = server;
		if (current == null) {
			return null;
		}
		for (ServerPlayer player : current.getPlayerList().getPlayers()) {
			return CharacterSelectionManager.selected(player).id();
		}
		return null;
	}
}
