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

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;

/**
 * Spawns one incident through the production control facade. The anchor can be an online player
 * or an explicit overworld position, but never both; this keeps scenario setup deterministic.
 */
@McpTool(
		name = "jujutsu_incident_spawn",
		description = "Spawns a cursed incident at an online player or explicit position with optional template, grade, seed, stage, and object source.",
		readOnly = false)
public final class JujutsuIncidentSpawnTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.optional("player_uuid", Schemas.string("Online player UUID used as the spawn anchor"))
			.optional("pos", Schemas.position3d("Explicit overworld block position; mutually exclusive with player_uuid"))
			.optional("template", Schemas.string("Template id; omit to roll"))
			.optional("grade", Schemas.integerBetween("Object grade 5 (weak) .. 1 (strong)", 1, 5))
			.optional("seed", Schemas.number("Deterministic seed; omit to roll"))
			.optional("stage", Schemas.enumOf("Start stage", "initial", "growing", "infested", "critical", "catastrophic"))
			.optional("object_type", Schemas.string("Cursed object type; omit for a rolled source"))
			.optional("source_kind", Schemas.enumOf("Source kind", "object", "free"))
			.optional("radius", Schemas.number("Optional deterministic zone radius override"))
			.build();

	public JujutsuIncidentSpawnTool() {
		super("jujutsu_incident_spawn");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		String rawPlayer = r.optString("player_uuid", null);
		JsonNode positionNode = r.optObject("pos");
		if ((rawPlayer == null) == (positionNode == null)) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Exactly one of player_uuid or pos is required");
		}
		UUID playerUuid = rawPlayer == null ? null : JujutsuMcpdevPlayers.parseUuid(rawPlayer);
		String template = r.optString("template", null);
		Integer grade = r.has("grade") ? r.requireInt("grade") : null;
		Long seed = r.has("seed") ? r.requireLong("seed") : null;
		String rawStage = r.optString("stage", null);
		IncidentStage stage = rawStage == null ? null : JujutsuIncidentInspectTool.requireStage(rawStage);
		String objectType = r.optString("object_type", null);
		SourceKind sourceKind = JujutsuIncidentInspectTool.optionalSourceKind(r.optString("source_kind", null));
		if (sourceKind == SourceKind.FREE && objectType != null && !objectType.isBlank()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"source_kind=free cannot be combined with object_type");
		}
		if (sourceKind == null && objectType != null && !objectType.isBlank()) {
			sourceKind = SourceKind.OBJECT;
		}
		Double radius = r.has("radius") ? r.requireDouble("radius") : null;
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		SourceKind finalSourceKind = sourceKind;
		return onMainThread(
				context,
				ignored -> {
					ServerLevel level;
					BlockPos center;
					if (playerUuid != null) {
						ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
						level = (ServerLevel) player.level();
						center = BlockPos.containing(player.position());
					} else {
						level = server.overworld();
						center = JujutsuIncidentInspectTool.position(positionNode, "pos");
					}
					var record = IncidentControl.spawn(new IncidentControl.SpawnRequest(
							center, level.dimension(), template, grade, seed, stage, objectType, finalSourceKind, radius));
					ObjectNode node = context.mapper().createObjectNode();
					node.put("spawned", true);
					node.set("incident", JujutsuIncidentInspectTool.viewNode(
							context, IncidentControl.inspect(record.id)));
					return ToolResult.ofToon(node);
				});
	}
}
