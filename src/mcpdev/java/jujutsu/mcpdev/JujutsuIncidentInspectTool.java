package jujutsu.mcpdev;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.KnowledgeLevel;
import jujutsu.mod.cursedincident.SourceKind;

/**
 * Read-only machine snapshot for one incident, or for every known incident when no id is given.
 * Every field is rendered explicitly so agent scenarios do not depend on Java record names or
 * implementation details.
 */
@McpTool(
		name = "jujutsu_incident_inspect",
		description = "Inspects one cursed incident, or lists full machine-readable snapshots when incident_id is omitted.",
		readOnly = true)
public final class JujutsuIncidentInspectTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.optional("incident_id", Schemas.string("Incident UUID; omit to inspect all incidents"))
			.build();

	public JujutsuIncidentInspectTool() {
		super("jujutsu_incident_inspect");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID incidentId = r.has("incident_id") ? requireIncidentId(r.requireString("incident_id")) : null;
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					if (incidentId != null) {
						return ToolResult.ofToon(viewNode(context, IncidentControl.inspect(incidentId)));
					}
					ObjectNode node = context.mapper().createObjectNode();
					ArrayNode incidents = node.putArray("incidents");
					for (var view : IncidentControl.list()) {
						incidents.add(viewNode(context, view));
					}
					node.put("count", incidents.size());
					return ToolResult.ofToon(node);
				});
	}

	static UUID requireIncidentId(String raw) {
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException e) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid incident UUID: " + raw);
		}
	}

	static BlockPos requirePosition(ArgumentReader reader, String name) {
		return position(reader.requireObject(name), name);
	}

	static BlockPos position(JsonNode node, String name) {
		if (node == null || !node.isObject()
				|| !node.has("x") || !node.has("y") || !node.has("z")
				|| !node.path("x").isNumber() || !node.path("y").isNumber() || !node.path("z").isNumber()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Invalid " + name + ": expected an object with numeric x, y and z");
		}
		double x = node.path("x").asDouble();
		double y = node.path("y").asDouble();
		double z = node.path("z").asDouble();
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid " + name + ": coordinates must be finite");
		}
		return BlockPos.containing(x, y, z);
	}

	static IncidentStage requireStage(String raw) {
		IncidentStage stage = IncidentStage.byName(raw);
		if (stage == null) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid stage: " + raw);
		}
		return stage;
	}

	static KnowledgeLevel requireKnowledge(String raw) {
		KnowledgeLevel level = KnowledgeLevel.byName(raw);
		if (level == null) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid knowledge level: " + raw);
		}
		return level;
	}

	static SourceKind optionalSourceKind(String raw) {
		if (raw == null) {
			return null;
		}
		SourceKind kind = SourceKind.byName(raw);
		if (kind == null) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid source kind: " + raw);
		}
		return kind;
	}

	/** Renders the pinned §17 inspect shape; this is shared by all mutating tools. */
	static ObjectNode viewNode(ToolContext context, IncidentControl.InspectView view) {
		ObjectNode node = context.mapper().createObjectNode();
		node.put("id", view.id().toString());
		node.put("seed", view.seed());
		node.put("template", view.templateId());
		node.put("stage", view.stage().wireName());
		node.put("scarred", view.scarred());
		node.put("age_ticks", view.ageTicks());
		node.put("created_at", view.createdGameTime());
		node.put("last_update", view.lastUpdateGameTime());
		putPosition(node, "center", view.center());
		node.put("radius", view.radius());
		node.put("shape", view.zoneShape());

		ObjectNode curseSet = node.putObject("curse_set");
		if (view.curseSet() != null) {
			for (var entry : view.curseSet().entrySet()) {
				curseSet.put(entry.getKey(), entry.getValue());
			}
		}
		node.put("atmosphere", view.atmosphereId());
		ArrayNode goals = node.putArray("local_goals");
		if (view.localGoals() != null) {
			for (String goal : view.localGoals()) {
				goals.add(goal);
			}
		}
		node.put("ignore_shelter", view.ignoreShelter());

		ObjectNode source = node.putObject("source");
		if (view.objectInstanceId() == null) source.putNull("object_uuid");
		else source.put("object_uuid", view.objectInstanceId().toString());
		if (view.objectTypeId() == null) source.putNull("type");
		else source.put("type", view.objectTypeId());
		if (view.objectGrade() == null) source.putNull("grade");
		else source.put("grade", view.objectGrade());
		putPosition(source, "pos", view.sourcePos());
		putPosition(source, "container", view.sourceContainer());

		ObjectNode seal = node.putObject("seal");
		seal.put("state", view.sealed() ? "sealed" : "unsealed");
		seal.put("integrity", view.sealIntegrity());
		seal.put("tier", view.sealTier());
		seal.put("failures", view.sealFailures());
		node.put("knowledge", view.knowledge());

		ArrayNode secondaries = node.putArray("secondaries");
		if (view.secondaries() != null) {
			for (var secondary : view.secondaries()) {
				ObjectNode entry = secondaries.addObject();
				entry.put("id", secondary.id().toString());
				putPosition(entry, "center", secondary.center());
				entry.put("radius", secondary.radius());
				entry.put("created_at", secondary.createdGameTime());
				entry.put("self_sustaining", secondary.selfSustaining());
			}
		}
		node.put("dependent_centers", view.dependentCenters());

		ArrayNode scars = node.putArray("scars");
		if (view.scars() != null) {
			for (BlockPos scar : view.scars()) {
				ObjectNode entry = scars.addObject();
				putPosition(entry, "pos", scar);
			}
		}

		ObjectNode counters = node.putObject("work_counters");
		putCounter(counters, view.workCounters(), "blocks_changed");
		putCounter(counters, view.workCounters(), "curses_spawned");
		putCounter(counters, view.workCounters(), "animals_culled");
		putCounter(counters, view.workCounters(), "chunk_edits_deferred");

		ArrayNode transitions = node.putArray("transition_log");
		if (view.transitionLog() != null) {
			for (String transition : view.transitionLog()) {
				transitions.add(transition);
			}
		}
		return node;
	}

	private static void putCounter(ObjectNode node, java.util.Map<String, Long> counters, String name) {
		node.put(name, counters == null ? 0L : counters.getOrDefault(name, 0L));
	}

	static void putPosition(ObjectNode node, String name, BlockPos pos) {
		if (pos == null) {
			node.putNull(name);
			return;
		}
		ObjectNode coordinates = node.putObject(name);
		coordinates.put("x", pos.getX());
		coordinates.put("y", pos.getY());
		coordinates.put("z", pos.getZ());
	}
}
