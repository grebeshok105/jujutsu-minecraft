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

/** Applies seal integrity damage through IncidentControl, exercising the same break path as gameplay. */
@McpTool(
		name = "jujutsu_incident_damage_seal",
		description = "Damages an incident seal by an amount and returns its new integrity and snapshot.",
		readOnly = false)
public final class JujutsuIncidentDamageSealTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("amount", Schemas.integer("Positive integrity damage"))
			.build();

	public JujutsuIncidentDamageSealTool() {
		super("jujutsu_incident_damage_seal");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		int amount = r.requireInt("amount");
		if (amount <= 0) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "amount must be positive");
		}
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			int integrity = IncidentControl.damageSeal(id, amount);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("integrity_result", integrity);
			return ToolResult.ofToon(node);
		});
	}
}
