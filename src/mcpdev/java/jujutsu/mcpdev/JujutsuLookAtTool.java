package jujutsu.mcpdev;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Rotates a player to face a target entity — scriptable aim for aimed casts
 * ({@code look_at} then {@code ability_invoke PRIMARY_SNEAK}). The rotation is
 * computed from the player's eye position to the target's eye position (center
 * mass for entities without a distinct eye height) and applied through
 * {@link Entity#lookAt}, which keeps body and head rotation in sync.
 */
@McpTool(
		name = "jujutsu_look_at",
		description = "Rotates a player to face a target entity (aim helper for targeted casts)")
public final class JujutsuLookAtTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("target_uuid", Schemas.string("Entity UUID to aim at"))
			.build();

	public JujutsuLookAtTool() {
		super("jujutsu_look_at");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		UUID targetUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("target_uuid"));
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					Entity target = JujutsuMcpdevEntities.require(server, targetUuid);
					player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
					ObjectNode node = context.mapper().createObjectNode();
					node.put("yaw", player.getYRot());
					node.put("pitch", player.getXRot());
					node.put("target_uuid", targetUuid.toString());
					return ToolResult.ofToon(node);
				});
	}
}
