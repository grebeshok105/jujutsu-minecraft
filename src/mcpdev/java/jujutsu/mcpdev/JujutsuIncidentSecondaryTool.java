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

/** Forces one self-sustaining secondary node at a requested position or current centre. */
@McpTool(
		name = "jujutsu_incident_secondary",
		description = "Creates a self-sustaining secondary incident node at an optional position.",
		readOnly = false)
public final class JujutsuIncidentSecondaryTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.optional("pos", Schemas.position3d("Secondary-node position; omit to use the active centre"))
			.build();

	public JujutsuIncidentSecondaryTool() {
		super("jujutsu_incident_secondary");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		JsonNode positionNode = r.optObject("pos");
		BlockPos requested = positionNode == null ? null : JujutsuIncidentInspectTool.position(positionNode, "pos");
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			BlockPos pos = requested;
			if (pos == null) {
				pos = IncidentControl.inspect(id).center();
			}
			var secondary = IncidentControl.forceSecondary(id, pos);
			ObjectNode node = context.mapper().createObjectNode();
			if (secondary == null) {
				node.putNull("secondary_id");
			} else {
				node.put("secondary_id", secondary.id().toString());
				JujutsuIncidentInspectTool.putPosition(node, "pos", secondary.center());
				node.put("radius", secondary.radius());
				node.put("self_sustaining", secondary.selfSustaining());
			}
			node.set("incident", JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id)));
			return ToolResult.ofToon(node);
		});
	}
}
