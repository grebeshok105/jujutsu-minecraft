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
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;

/**
 * Invokes one ability slot for the target player (issue #43 slice 2).
 *
 * <p>{@code slot} is a {@link CharacterAbility} enum name, accepted case-insensitively;
 * anything else is {@code TOOL_INPUT_INVALID}. An {@code expect_vessel} that does not
 * match the player's current selection refuses with {@code routed:false,
 * refusal:"vessel_mismatch"} — a result, not an exception, mirroring the receiver's own
 * claim gate. The cast itself runs on the server main thread through
 * {@link CharacterAbilityExecutor#tryCast}.
 */
@McpTool(
		name = "jujutsu_ability_invoke",
		description = "Invokes one ability slot for the target player.")
public final class JujutsuAbilityInvokeTool extends BaseTool {

	private static final String SLOT_NAMES = "PRIMARY, PRIMARY_SNEAK, SECONDARY, SECONDARY_SNEAK, "
			+ "ATTACK_CONTEXT, USE_CONTEXT, SECONDARY_SNEAK_HOLD, SECONDARY_SNEAK_RELEASE, TERTIARY, TERTIARY_SNEAK";

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("slot", Schemas.string("Ability slot name (case-insensitive): " + SLOT_NAMES))
			.optional("expect_vessel", Schemas.string("Required vessel id; a mismatch refuses without invoking"))
			.optional("notify", Schemas.bool("Show the in-game action message; default true"))
			.build();

	public JujutsuAbilityInvokeTool() {
		super("jujutsu_ability_invoke");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		CharacterAbility ability = parseSlot(r.requireString("slot"));
		String expectVessel = r.optString("expect_vessel", null);
		boolean notify = r.optBoolean("notify", true);
		return onMainThread(
				context,
				ignored -> {
					MinecraftServer server = JujutsuMcpdevBridge.server();
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					JujutsuCharacter selected = CharacterSelectionManager.selected(player);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("vessel", selected.id());
					node.put("slot", ability.name());
					if (expectVessel != null && !expectVessel.equalsIgnoreCase(selected.id())) {
						node.put("routed", false);
						node.put("refusal", "vessel_mismatch");
						node.put("cooldown_remaining_ticks",
								CharacterAbilityCooldowns.remainingTicks(player, ability));
						return ToolResult.ofToon(node);
					}
					boolean routed = CharacterAbilityExecutor.tryCast(player, ability, notify);
					node.put("routed", routed);
					node.put("cooldown_remaining_ticks",
							CharacterAbilityCooldowns.remainingTicks(player, ability));
					return ToolResult.ofToon(node);
				});
	}

	/** Parses a slot name case-insensitively; anything else is TOOL_INPUT_INVALID. */
	private static CharacterAbility parseSlot(String slot) {
		try {
			return CharacterAbility.valueOf(slot.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Invalid slot: " + slot + " (expected one of " + SLOT_NAMES + ")");
		}
	}
}
