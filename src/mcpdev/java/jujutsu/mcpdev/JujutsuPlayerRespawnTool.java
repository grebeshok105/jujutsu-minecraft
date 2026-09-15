package jujutsu.mcpdev;

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
import net.minecraft.world.entity.Entity;

/**
 * Respawns a dead player through the vanilla {@code PlayerList.respawn} path —
 * the scriptable equivalent of the client-side "Respawn" button, so combat
 * scripts can re-enter the fight without a human clicking.
 *
 * <p>Fails fast when the player is alive (respawning a living player would
 * duplicate them). {@code keep_inventory} defaults to false, matching a normal
 * death; pass true to keep the inventory across the respawn.
 */
@McpTool(
		name = "jujutsu_player_respawn",
		description = "Respawns a dead player via the vanilla respawn path; fails if the player is alive")
public final class JujutsuPlayerRespawnTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.optional("keep_inventory", Schemas.bool("Keep the inventory across respawn; default false"))
			.build();

	public JujutsuPlayerRespawnTool() {
		super("jujutsu_player_respawn");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		boolean keepInventory = r.optBoolean("keep_inventory", false);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					if (player.isAlive()) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Player is alive, refusing respawn: " + playerUuid);
					}
					ServerPlayer respawned = server.getPlayerList().respawn(
							player, keepInventory, Entity.RemovalReason.KILLED);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("player_uuid", respawned.getUUID().toString());
					node.put("alive", respawned.isAlive());
					node.put("health", respawned.getHealth());
					node.put("dimension", respawned.level().dimension().location().toString());
					node.put("x", respawned.getX());
					node.put("y", respawned.getY());
					node.put("z", respawned.getZ());
					return ToolResult.ofToon(node);
				});
	}
}
