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

import jujutsu.mod.cursedincident.IncidentControl;

/** Multiplies an incident's escalation speed once through the shared control facade. */
@McpTool(
		name = "jujutsu_incident_escalate",
		description = "Multiplies an incident's escalation speed and returns its complete inspect snapshot.",
		readOnly = false)
public final class JujutsuIncidentEscalateTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.optional("multiplier", Schemas.number("Positive escalation-speed multiplier; default 2.0"))
			.build();

	public JujutsuIncidentEscalateTool() {
		super("jujutsu_incident_escalate");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		double multiplier = r.optDouble("multiplier", 2.0D);
		if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "multiplier must be finite and positive");
		}
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.escalate(id, multiplier);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("multiplier", multiplier);
			return ToolResult.ofToon(node);
		});
	}
}
