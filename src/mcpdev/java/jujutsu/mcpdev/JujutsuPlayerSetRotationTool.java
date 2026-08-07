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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sets the target player's server-side look direction (issue #43 L3, contract C2) —
 * the typed replacement for aiming via a {@code /tp} command_execute workaround.
 *
 * <p>Writes {@code yaw} into both the body y-rotation and the head y-rotation so the
 * aimed cast path ({@code Entity#getLookAngle}) sees exactly the requested heading,
 * and {@code pitch} into the x-rotation (clamped to [-90, 90] by the entity itself).
 * The mutation runs on the server main thread, after the standard fail-closed player
 * resolution.
 */
@McpTool(
		name = "jujutsu_player_set_rotation",
		description = "Sets server-side yaw/pitch for aimed targeting")
public final class JujutsuPlayerSetRotationTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("yaw", Schemas.number("Yaw in degrees (clockwise from due south), -180..180"))
			.required("pitch", Schemas.number("Pitch in degrees, -90 (up) .. 90 (down)"))
			.build();

	public JujutsuPlayerSetRotationTool() {
		super("jujutsu_player_set_rotation");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		float yaw = (float) r.requireDouble("yaw");
		float pitch = (float) r.requireDouble("pitch");
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					player.setYRot(yaw);
					player.setXRot(pitch);
					player.setYHeadRot(yaw);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("yaw", yaw);
					node.put("pitch", pitch);
					return ToolResult.ofToon(node);
				});
	}
}
