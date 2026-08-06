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
 * so {@link #parseUuid(String)} must run in {@code execute()} before
 * {@code onMainThread}, while {@link #requireOnline(MinecraftServer, UUID)} runs inside
 * it. {@link #requireOnline(String)} composes the two for callers that accept the
 * flattened code.
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
	 * Resolves a parsed UUID against the live server on the server main thread,
	 * fail-closed: a missing server is {@code SERVER_NOT_RUNNING}, an offline or
	 * unknown player is {@code TOOL_HANDLER_ERROR} (upstream "Player not online"
	 * idiom).
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

	/**
	 * Convenience composition of {@link #parseUuid(String)} + {@link #requireOnline(MinecraftServer, UUID)}
	 * for use inside main-thread work (the parse error surfaces as
	 * {@code TOOL_HANDLER_ERROR} there — prefer the two-step form when the code matters).
	 */
	static ServerPlayer requireOnline(String rawUuid) {
		UUID uuid = parseUuid(rawUuid);
		return requireOnline(JujutsuMcpdevBridge.server(), uuid);
	}
}
