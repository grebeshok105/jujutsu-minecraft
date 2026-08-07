package jujutsu.mcpdev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import jujutsu.mod.character.JujutsuCharacter;

/**
 * Read-only roster tool (issue #43 slice 2): lists every selectable vessel id straight
 * from {@link JujutsuCharacter#values()}, including {@code none} (the canonical
 * deselect id). Never mutates gameplay state; game access goes exclusively through the
 * upstream main-thread dispatch.
 */
@McpTool(
		name = "jujutsu_vessel_list",
		description = "Lists every selectable vessel (character id), including 'none'.",
		readOnly = true)
public final class JujutsuVesselListTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

	public JujutsuVesselListTool() {
		super("jujutsu_vessel_list");
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
					ArrayNode vessels = context.mapper().createArrayNode();
					for (JujutsuCharacter character : JujutsuCharacter.values()) {
						vessels.addObject().put("id", character.id());
					}
					ObjectNode node = context.mapper().createObjectNode();
					node.set("vessels", vessels);
					return ToolResult.ofToon(node);
				});
	}
}
