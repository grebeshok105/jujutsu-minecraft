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

import jujutsu.mod.cursedincident.IncidentControl;

/** Replaces the deterministic seed of one incident through IncidentControl. */
@McpTool(
		name = "jujutsu_incident_seed",
		description = "Sets an incident seed for deterministic replay and returns its snapshot.",
		readOnly = false)
public final class JujutsuIncidentSeedTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("value", Schemas.integer("New deterministic seed"))
			.build();

	public JujutsuIncidentSeedTool() {
		super("jujutsu_incident_seed");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		long seed = r.requireLong("value");
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			long result = IncidentControl.reseed(id, seed);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("seed_result", result);
			return ToolResult.ofToon(node);
		});
	}
}
