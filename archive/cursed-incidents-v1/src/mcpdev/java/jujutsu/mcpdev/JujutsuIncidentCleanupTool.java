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

/** Marks one incident scarred and removes its spawned runtime bodies via the shared facade. */
@McpTool(
		name = "jujutsu_incident_cleanup",
		description = "Cleans up one incident's spawned bodies while retaining its scar record and world damage.",
		readOnly = false)
public final class JujutsuIncidentCleanupTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.build();

	public JujutsuIncidentCleanupTool() {
		super("jujutsu_incident_cleanup");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.cleanup(id);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("result", "cleaned");
			return ToolResult.ofToon(node);
		});
	}
}
