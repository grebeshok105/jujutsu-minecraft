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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;

/**
 * Switches the target player's selected vessel (issue #43 slice 2).
 *
 * <p>The vessel id must equal a canonical {@link JujutsuCharacter#id()} (case-insensitive):
 * {@link JujutsuCharacter#byId}'s unknown-to-NONE fallback must not swallow typos, so the
 * input is checked against the canonical ids before resolution and anything else is
 * {@code TOOL_INPUT_INVALID}. The mutation runs on the server main thread.
 */
@McpTool(
		name = "jujutsu_vessel_select",
		description = "Selects a vessel (character) for the target player.")
public final class JujutsuVesselSelectTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("vessel_id", Schemas.string("Canonical vessel id, e.g. megumi (case-insensitive)"))
			.build();

	public JujutsuVesselSelectTool() {
		super("jujutsu_vessel_select");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		String vesselId = r.requireString("vessel_id");
		JujutsuCharacter target = resolveVessel(vesselId);
		return onMainThread(
				context,
				ignored -> {
					MinecraftServer server = JujutsuMcpdevBridge.server();
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					JujutsuCharacter previous = CharacterSelectionManager.selected(player);
					CharacterSelectionManager.select(player, target);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("player", player.getUUID().toString());
					node.put("previous", previous.id());
					node.put("selected", CharacterSelectionManager.selected(player).id());
					return ToolResult.ofToon(node);
				});
	}

	/** Canonical-id check that refuses typos instead of falling back to NONE. */
	private static JujutsuCharacter resolveVessel(String vesselId) {
		for (JujutsuCharacter character : JujutsuCharacter.values()) {
			if (character.id().equalsIgnoreCase(vesselId)) {
				return character;
			}
		}
		throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Unknown vessel id: " + vesselId);
	}
}
