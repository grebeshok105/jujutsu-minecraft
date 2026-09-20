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

/** Advances logical incident age through the same facade used by offline catch-up. */
@McpTool(
		name = "jujutsu_incident_advance",
		description = "Advances one incident by logical ticks or whole in-game days and returns its snapshot.",
		readOnly = false)
public final class JujutsuIncidentAdvanceTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("incident_id", Schemas.string("Incident UUID"))
			.optional("ticks", Schemas.integer("Logical age to add in ticks; mutually exclusive with days"))
			.optional("days", Schemas.integer("Logical age to add in in-game days (24000 ticks); mutually exclusive with ticks"))
			.build();

	public JujutsuIncidentAdvanceTool() {
		super("jujutsu_incident_advance");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID id = JujutsuIncidentInspectTool.requireIncidentId(r.requireString("incident_id"));
		if (r.has("ticks") == r.has("days")) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Exactly one of ticks or days is required");
		}
		long ticks;
		if (r.has("ticks")) {
			ticks = r.requireLong("ticks");
		} else {
			long days = r.requireLong("days");
			try {
				ticks = Math.multiplyExact(days, 24_000L);
			} catch (ArithmeticException e) {
				throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "days is outside the supported range");
			}
		}
		if (ticks < 0L) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "advance amount must not be negative");
		}
		JujutsuMcpdevPlayers.requireServer();
		long advancedTicks = ticks;
		return onMainThread(context, ignored -> {
			long age = IncidentControl.advance(id, advancedTicks);
			ObjectNode node = JujutsuIncidentInspectTool.viewNode(context, IncidentControl.inspect(id));
			node.put("advanced_ticks", advancedTicks);
			node.put("age_result", age);
			return ToolResult.ofToon(node);
		});
	}
}
