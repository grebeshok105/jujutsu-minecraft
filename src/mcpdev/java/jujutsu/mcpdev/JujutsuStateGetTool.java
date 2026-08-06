package jujutsu.mcpdev;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import jujutsu.mod.character.megumi.MegumiShadowDropRuntime;
import jujutsu.mod.character.megumi.MegumiShadowMoveRuntime;
import jujutsu.mod.character.megumi.MegumiShadowTrapRuntime;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.character.nobara.projectjjk.EmbeddedNailRegistry;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.todo.TodoPendingSelection;
import jujutsu.mod.character.todo.TodoTransientState;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Read-only jujutsu state observation tool (issue #43 slice 2).
 *
 * <p>Returns the C2 row-7 snapshot for one online player: vessel, position, stagger, per-slot
 * cooldowns, effect flags, Todo pair selection and stone, Megumi pack/trap/move/drop presence,
 * and Nobara embedded nails and marks. Reads ONLY public accessors (the C1 statics plus the
 * pre-existing ones) and never mutates gameplay state; all gameplay access goes through the
 * upstream main-thread dispatch.
 */
@McpTool(
		name = "jujutsu_state_get",
		description = "Reads the current combat and transient state of one online player: vessel, position, stagger, cooldowns, effect flags, Todo pair selection and stone, Megumi pack/trap/move/drop, and Nobara embedded nails and marks.",
		readOnly = true)
public final class JujutsuStateGetTool extends BaseTool {

	private static final JsonNode SCHEMA =
			Schemas.object().required("player_uuid", Schemas.string("Player UUID")).build();

	public JujutsuStateGetTool() {
		super("jujutsu_state_get");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		// Parsed on the HTTP thread: onMainThread rewraps thrown exceptions into TOOL_HANDLER_ERROR,
		// which would swallow the TOOL_INPUT_INVALID code for a malformed UUID.
		UUID playerId = JujutsuMcpdevPlayers.parseUuid(reader(arguments).requireString("player_uuid"));
		return onMainThread(
				context,
				ignored -> {
					MinecraftServer server = JujutsuMcpdevBridge.server();
					// Fail-closed: SERVER_NOT_RUNNING when no server, TOOL_HANDLER_ERROR when offline.
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerId);
					long gameTime = player.level().getGameTime();

					ObjectNode node = context.mapper().createObjectNode();
					node.put("vessel", CharacterSelectionManager.selected(player).id());

					ObjectNode position = node.putObject("position");
					position.put("dim", player.level().dimension().location().toString());
					position.put("x", player.getX());
					position.put("y", player.getY());
					position.put("z", player.getZ());

					node.put("stagger", CombatStagger.GLOBAL.isStaggered(playerId, gameTime));

					ArrayNode cooldowns = node.putArray("cooldowns");
					for (CharacterAbility ability : CharacterAbility.values()) {
						ObjectNode entry = cooldowns.addObject();
						entry.put("slot", ability.name());
						entry.put("remaining_ticks", CharacterAbilityCooldowns.remainingTicks(player, ability));
					}

					ObjectNode effects = node.putObject("effects");
					effects.put("todo_swap_momentum", player.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM));
					effects.put("megumi_shadow_grip", player.hasEffect(JujutsuEffects.MEGUMI_SHADOW_GRIP));
					effects.put("resonant_momentum", player.hasEffect(JujutsuEffects.RESONANT_MOMENTUM));

					ObjectNode todo = node.putObject("todo");
					putNullableUuid(todo, "pair_selection",
							TodoTransientState.pairSelection(playerId).map(TodoPendingSelection::targetUuid));
					todo.put("stone", TodoTransientState.stone(playerId).isPresent());

					ObjectNode megumi = node.putObject("megumi");
					MegumiSummonRuntime.packView(server, playerId).ifPresentOrElse(
							pack -> {
								ObjectNode packNode = megumi.putObject("pack");
								packNode.put("dimension", pack.dimension());
								packNode.put("white_alive", pack.whiteAlive());
								packNode.put("black_alive", pack.blackAlive());
								packNode.put("summoned_at_game_time", pack.summonedAtGameTime());
							},
							() -> megumi.putNull("pack"));
					megumi.put("trap", MegumiShadowTrapRuntime.hasOwned(playerId));
					megumi.put("move", MegumiShadowMoveRuntime.hasOwned(playerId));
					megumi.put("drop", MegumiShadowDropRuntime.hasOwned(playerId));

					ObjectNode nobara = node.putObject("nobara");
					nobara.put("embedded_nails_loaded", EmbeddedNailRegistry.loadedOwnedNails(player.level(), playerId).size());
					nobara.put("marks_on_player", ProjectJjkNailMarks.marks(playerId, gameTime) > 0);

					return ToolResult.ofToon(node);
				});
	}

	private static void putNullableUuid(ObjectNode node, String field, Optional<UUID> value) {
		if (value.isPresent()) {
			node.put(field, value.get().toString());
		} else {
			node.putNull(field);
		}
	}
}
