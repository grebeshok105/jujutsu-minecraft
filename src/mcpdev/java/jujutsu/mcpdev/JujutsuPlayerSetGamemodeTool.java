package jujutsu.mcpdev;

import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Switches a player's game mode server-side — the typed replacement for
 * {@code /gamemode} via command_execute (whose text output is lost upstream).
 */
@McpTool(
		name = "jujutsu_player_set_gamemode",
		description = "Sets a player's game mode (survival/creative/adventure/spectator)")
public final class JujutsuPlayerSetGamemodeTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("gamemode", Schemas.enumOf("Game mode", "survival", "creative", "adventure", "spectator"))
			.build();

	public JujutsuPlayerSetGamemodeTool() {
		super("jujutsu_player_set_gamemode");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		GameType gameType = parseGameType(r.requireString("gamemode"));
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					player.setGameMode(gameType);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("player_uuid", playerUuid.toString());
					node.put("gamemode", player.gameMode.getGameModeForPlayer().getName());
					return ToolResult.ofToon(node);
				});
	}

	private static GameType parseGameType(String raw) {
		GameType type = GameType.byName(raw.toLowerCase(Locale.ROOT), null);
		if (type == null) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Invalid gamemode: " + raw + " (expected survival/creative/adventure/spectator)");
		}
		return type;
	}
}
