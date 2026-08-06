package jujutsu.mcpdev;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

/**
 * Shared player resolution for the dev-control tools (issue #43 slice 2). Dumb by
 * design: parse, look up, throw.
 *
 * <p>Error codes survive only on the HTTP thread — {@link com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool#onMainThread} wraps
 * every exception thrown inside its main-thread work into {@code TOOL_HANDLER_ERROR} —
 * so {@link #parseUuid(String)} and {@link #requireServer()} must run in {@code execute()}
 * before {@code onMainThread}, while {@link #requireOnline(MinecraftServer, UUID)} runs
 * inside it (its null-server branch is a last-resort guard there).
 */
final class JujutsuMcpdevPlayers {

	private JujutsuMcpdevPlayers() {}

	/**
	 * Parses a {@code player_uuid} argument on the HTTP thread (upstream UUID idiom):
	 * unparseable input is {@code TOOL_INPUT_INVALID}.
	 */
	static UUID parseUuid(String rawUuid) {
		try {
			return UUID.fromString(rawUuid);
		} catch (IllegalArgumentException e) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid UUID: " + rawUuid);
		}
	}

	/**
	 * Fail-closed server check on the HTTP thread (the bridge field is volatile), so
	 * {@code SERVER_NOT_RUNNING} actually reaches clients instead of being flattened
	 * inside the main-thread hop. Call in {@code execute()} before {@code onMainThread}.
	 */
	static MinecraftServer requireServer() {
		MinecraftServer server = JujutsuMcpdevBridge.server();
		if (server == null) {
			throw new McpException(ErrorCodes.SERVER_NOT_RUNNING, "Minecraft server is not running");
		}
		return server;
	}

	/**
	 * Resolves a parsed UUID against the live server on the server main thread:
	 * an offline or unknown player is {@code TOOL_HANDLER_ERROR} (upstream
	 * "Player not online" idiom); a null server is guarded with the same code.
	 */
	static ServerPlayer requireOnline(MinecraftServer server, UUID uuid) {
		if (server == null) {
			throw new McpException(ErrorCodes.SERVER_NOT_RUNNING, "Minecraft server is not running");
		}
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player == null) {
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR, "Player not online: " + uuid);
		}
		return player;
	}

}
