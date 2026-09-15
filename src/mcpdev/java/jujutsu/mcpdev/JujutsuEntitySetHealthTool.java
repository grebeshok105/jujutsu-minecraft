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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sets a living entity's health directly (combat-test hygiene).
 *
 * <p>{@code health} is clamped to {@code 0..maxHealth}. {@code health=0} is a kill
 * request: it goes through {@link LivingEntity#kill(ServerLevel)} rather than
 * {@code setHealth(0)} so death events fire and {@link JujutsuCombatLog} records
 * a death entry (a raw setHealth(0) leaves a corpse that never dies).
 */
@McpTool(
		name = "jujutsu_entity_set_health",
		description = "Sets a living entity's health; health=0 kills it through the normal death path")
public final class JujutsuEntitySetHealthTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("uuid", Schemas.string("Entity UUID"))
			.required("health", Schemas.number("Health to set; 0 kills the entity, values above maxHealth clamp"))
			.build();

	public JujutsuEntitySetHealthTool() {
		super("jujutsu_entity_set_health");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID uuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("uuid"));
		float health = (float) r.requireDouble("health");
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					Entity entity = JujutsuMcpdevEntities.require(server, uuid);
					if (!(entity instanceof LivingEntity living)) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Entity is not living: " + uuid);
					}
					ServerLevel level = (ServerLevel) living.level();
					ObjectNode node = context.mapper().createObjectNode();
					node.put("uuid", uuid.toString());
					if (health <= 0f) {
						living.kill(level);
						node.put("killed", true);
						node.put("health", 0f);
					} else {
						float clamped = Math.min(health, living.getMaxHealth());
						living.setHealth(clamped);
						node.put("killed", false);
						node.put("health", living.getHealth());
						node.put("max_health", living.getMaxHealth());
					}
					return ToolResult.ofToon(node);
				});
	}
}
