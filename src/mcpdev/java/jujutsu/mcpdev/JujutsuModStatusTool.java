package jujutsu.mcpdev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Read-only Jujutsu status tool (issue #43 spike).
 *
 * <p>Reports the jujutsumod mod version (Fabric Loader metadata) and the vessel
 * selected for the first online player, read through
 * {@link jujutsu.mod.character.CharacterSelectionManager} on the server main
 * thread. Never mutates gameplay state; gameplay access goes exclusively through
 * the upstream main-thread dispatch, and no MCP protocol classes leak into
 * production code (the coupling direction is mcpdev -&gt; {main, upstream}).
 */
@McpTool(
		name = "jujutsu_mod_status",
		description = "Returns the Jujutsu mod version and the vessel selected by the first online player.",
		readOnly = true)
public final class JujutsuModStatusTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

	public JujutsuModStatusTool() {
		super("jujutsu_mod_status");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		return onMainThread(
				context,
				ignored -> {
					ObjectNode node = context.mapper().createObjectNode();
					node.put("mod_version", JujutsuMcpdevBridge.modVersion());
					String vessel = JujutsuMcpdevBridge.selectedVesselId();
					if (vessel == null) {
						node.putNull("selected_vessel");
					} else {
						node.put("selected_vessel", vessel);
					}
					node.put("vessel_source", "CharacterSelectionManager");
					return ToolResult.ofToon(node);
				});
	}
}
