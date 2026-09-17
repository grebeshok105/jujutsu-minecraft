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

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import jujutsu.mod.cursedincident.IncidentControl;

/** Mints one physical cursed object through the object-spawner seam owned by the core facade. */
@McpTool(
		name = "jujutsu_incident_object_spawn",
		description = "Spawns one physical cursed object at an overworld position.",
		readOnly = false)
public final class JujutsuIncidentObjectSpawnTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("pos", Schemas.position3d("Overworld block position"))
			.optional("type", Schemas.string("Object type id; omit to roll a natural type"))
			.optional("grade", Schemas.integerBetween("Object grade 5 (weak) .. 1 (strong)", 1, 5))
			.optional("seed", Schemas.number("Deterministic seed; omit for the current game time"))
			.build();

	public JujutsuIncidentObjectSpawnTool() {
		super("jujutsu_incident_object_spawn");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		BlockPos pos = JujutsuIncidentInspectTool.requirePosition(r, "pos");
		String type = r.optString("type", "");
		int grade = r.optInt("grade", 3);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerLevel level = server.overworld();
					long seed = r.has("seed") ? r.requireLong("seed") : level.getGameTime();
					UUID objectUuid = IncidentControl.spawnObject(level, pos, type, grade, seed);
					ObjectNode node = context.mapper().createObjectNode();
					if (objectUuid == null) node.putNull("object_uuid");
					else node.put("object_uuid", objectUuid.toString());
					node.put("type", type);
					node.put("grade", grade);
					node.put("seed", seed);
					JujutsuIncidentInspectTool.putPosition(node, "pos", pos);
					return ToolResult.ofToon(node);
				});
	}
}
