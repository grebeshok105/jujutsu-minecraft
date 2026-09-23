package jujutsu.mcpdev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import jujutsu.mod.cursedincident.IncidentControl;

/** Read-only inventory of all persisted incident records, including scarred records. */
@McpTool(
		name = "jujutsu_incident_list",
		description = "Lists all known cursed incidents with the full machine-readable inspect shape.",
		readOnly = true)
public final class JujutsuIncidentListTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object().build();

	public JujutsuIncidentListTool() {
		super("jujutsu_incident_list");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ObjectNode node = context.mapper().createObjectNode();
					ArrayNode incidents = node.putArray("incidents");
					for (var view : IncidentControl.list()) {
						incidents.add(JujutsuIncidentInspectTool.viewNode(context, view));
					}
					node.put("count", incidents.size());
					return ToolResult.ofToon(node);
				});
	}
}
