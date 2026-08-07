package jujutsu.mcpdev;

import java.util.Locale;
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

import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;

/**
 * Clears ability cooldowns for the target player (issue #43 slice 2): one slot of the
 * selected vessel via {@link CharacterAbilityCooldowns#clear}, or every slot of every
 * vessel via {@code clearAllForPlayer} (Block 1 accessor). The mutation runs on the
 * server main thread.
 */
@McpTool(
		name = "jujutsu_cooldowns_clear",
		description = "Clears one ability slot's cooldown, or all cooldowns, for the target player.")
public final class JujutsuCooldownsClearTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.optional("slot", Schemas.string("Ability slot name (case-insensitive); omitted clears all vessels"))
			.build();

	public JujutsuCooldownsClearTool() {
		super("jujutsu_cooldowns_clear");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		String slotName = r.optString("slot", null);
		CharacterAbility ability = slotName == null ? null : parseSlot(slotName);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					ObjectNode node = context.mapper().createObjectNode();
					if (ability == null) {
						CharacterAbilityCooldowns.clearAllForPlayer(player.getUUID());
						node.put("cleared", "all");
					} else {
						CharacterAbilityCooldowns.clear(player, ability);
						node.put("cleared", ability.name());
					}
					return ToolResult.ofToon(node);
				});
	}

	/** Parses a slot name case-insensitively; anything else is TOOL_INPUT_INVALID. */
	private static CharacterAbility parseSlot(String slot) {
		try {
			return CharacterAbility.valueOf(slot.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid slot: " + slot);
		}
	}
}
