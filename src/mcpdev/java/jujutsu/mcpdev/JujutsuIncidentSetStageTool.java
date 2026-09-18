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
import jujutsu.mod.cursedincident.IncidentStage;

/** Forces an incident onto one of the five supported stage values through IncidentControl. */
@McpTool(
		name = "jujutsu_incident_set_stage",
		description = "Sets a cursed incident stage and returns its complete inspect snapshot.",
		readOnly = false)
public final class JujutsuIncidentSetStageTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("stage", Schemas.enumOf("Stage", "initial", "growing", "infested", "critical", "catastrophic"))
			.build();

	public JujutsuIncidentSetStageTool() {
		super("jujutsu_incident_set_stage");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		IncidentStage stage = JujutsuIncidentInspectTool.requireStage(r.requireString("stage"));
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.setStage(id, stage);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("result", "stage_set");
			return ToolResult.ofToon(node);
		});
	}
}
