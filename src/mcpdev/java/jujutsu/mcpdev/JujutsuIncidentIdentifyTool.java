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
import jujutsu.mod.cursedincident.KnowledgeLevel;

/** Sets the data-only identification level through the shared incident API. */
@McpTool(
		name = "jujutsu_incident_identify",
		description = "Sets an incident's knowledge level and returns its complete inspect snapshot.",
		readOnly = false)
public final class JujutsuIncidentIdentifyTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("level", Schemas.enumOf("Knowledge level", "unknown", "rough_danger", "effect_type", "properties", "origin", "name", "sealing_methods", "restrictions"))
			.build();

	public JujutsuIncidentIdentifyTool() {
		super("jujutsu_incident_identify");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		KnowledgeLevel level = JujutsuIncidentInspectTool.requireKnowledge(r.requireString("level"));
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.identify(id, level);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("result", "identified");
			return ToolResult.ofToon(node);
		});
	}
}
