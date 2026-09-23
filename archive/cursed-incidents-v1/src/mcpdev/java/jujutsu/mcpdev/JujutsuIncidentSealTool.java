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

/** Attempts to seal an incident and preserves the machine-readable refusal details. */
@McpTool(
		name = "jujutsu_incident_seal",
		description = "Attempts to seal an incident with a talisman tier and returns required tier and refusal reason when rejected.",
		readOnly = false)
public final class JujutsuIncidentSealTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.required("tier", Schemas.integerBetween("Seal/talisman tier", 1, 3))
			.build();

	public JujutsuIncidentSealTool() {
		super("jujutsu_incident_seal");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		int tier = r.requireInt("tier");
		JujutsuMcpdevPlayers.requireServer();
		return onMainThread(context, ignored -> {
			IncidentControl.SealAttempt attempt = IncidentControl.seal(id, tier);
			ObjectNode node = context.mapper().createObjectNode();
			node.put("ok", attempt.ok());
			node.put("required_tier", attempt.requiredTier());
			if (attempt.reason() == null) node.putNull("reason");
			else node.put("reason", attempt.reason());
			node.set("incident", JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id)));
			return ToolResult.ofToon(node);
		});
	}
}
