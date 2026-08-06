package jujutsu.mcpdev;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;

/**
 * Read-only cooldown inspection tool (issue #43 slice 2): remaining ticks for every
 * ability slot of the player's selected vessel, via
 * {@link CharacterAbilityCooldowns#remainingTicks}. Never mutates gameplay state; game
 * access goes exclusively through the upstream main-thread dispatch.
 */
@McpTool(
		name = "jujutsu_cooldowns_get",
		description = "Returns remaining cooldown ticks for every ability slot of the target player's vessel.",
		readOnly = true)
public final class JujutsuCooldownsGetTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.build();

	public JujutsuCooldownsGetTool() {
		super("jujutsu_cooldowns_get");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("vessel", CharacterSelectionManager.selected(player).id());
					ArrayNode slots = node.putArray("slots");
					for (CharacterAbility ability : CharacterAbility.values()) {
						ObjectNode slot = slots.addObject();
						slot.put("slot", ability.name());
						slot.put("remaining_ticks", CharacterAbilityCooldowns.remainingTicks(player, ability));
					}
					return ToolResult.ofToon(node);
				});
	}
}
