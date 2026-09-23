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

import jujutsu.mod.cursedincident.IncidentControl;

/** Moves an incident's active centre while retaining the prior centre in its scar list. */
@McpTool(
		name = "jujutsu_incident_relocate",
		description = "Relocates an incident to a block position and returns the updated snapshot.",
		readOnly = false)
public final class JujutsuIncidentRelocateTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("pos", Schemas.position3d("New active centre"))
			.build();

	public JujutsuIncidentRelocateTool() {
		super("jujutsu_incident_relocate");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		BlockPos pos = JujutsuIncidentInspectTool.requirePosition(r, "pos");
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.relocate(id, pos);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("result", "relocated");
			return ToolResult.ofToon(node);
		});
	}
}
