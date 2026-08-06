package jujutsu.mcpdev;

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

import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiShadowDropRuntime;
import jujutsu.mod.character.megumi.MegumiShadowMoveRuntime;
import jujutsu.mod.character.megumi.MegumiShadowTrapRuntime;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.character.nobara.projectjjk.EmbeddedNailRegistry;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;
import jujutsu.mod.character.todo.TodoStateLifecycle;
import jujutsu.mod.combat.BlackFlashFocus;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Dev fixture reset tool (issue #43 slice 2).
 *
 * <p>Clears the RUNTIME combat state of one online player in the frozen C3 order: cooldowns,
 * stagger, Todo transient state, Megumi summons/traps/moves/drops, Nobara nails/traps/marks/
 * resonance, and the shared black-flash tags and effects. Deliberately does NOT touch vessel
 * selection, {@code claimedStarterCharacters}, or inventory, and applies no cooldowns.
 * Each step is best-effort (gametest {@code safe(Runnable)} precedent): one failing step is
 * recorded in the result and the rest still run.
 */
@McpTool(
		name = "jujutsu_fixture_reset",
		description = "Clears runtime combat state for one online player: cooldowns, stagger, Todo transient state, Megumi summons/traps/moves/drops, Nobara nails/traps/marks/resonance, and shared black-flash tags and effects. Does not touch vessel selection, starter claims, or inventory.",
		readOnly = false)
public final class JujutsuFixtureResetTool extends BaseTool {

	private static final JsonNode SCHEMA =
			Schemas.object().required("player_uuid", Schemas.string("Player UUID")).build();

	public JujutsuFixtureResetTool() {
		super("jujutsu_fixture_reset");
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
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					// Fail-closed: TOOL_HANDLER_ERROR when the player is offline.
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerId);

					ObjectNode node = context.mapper().createObjectNode();
					ArrayNode steps = node.putArray("steps");
					runStep(steps, "cooldowns_clear", () -> CharacterAbilityCooldowns.clearAllForPlayer(playerId));
					runStep(steps, "stagger_clear", () -> CombatStagger.GLOBAL.clear(playerId));
					runStep(steps, "todo_drop_everything", () -> TodoStateLifecycle.dropEverything(player));
					runStep(steps, "megumi_summon_teardown",
							() -> MegumiSummonRuntime.teardown(server, playerId, MegumiSummonRuntime.TeardownReason.FIXTURE_RESET));
					runStep(steps, "megumi_shadow_trap_clear", () -> MegumiShadowTrapRuntime.clearOwned(server, playerId));
					runStep(steps, "megumi_shadow_drop_clear", () -> MegumiShadowDropRuntime.clearOwned(server, playerId));
					runStep(steps, "megumi_shadow_move_teardown", () -> MegumiShadowMoveRuntime.teardownOwned(server, playerId));
					runStep(steps, "megumi_shadow_grip_effect", () -> player.removeEffect(JujutsuEffects.MEGUMI_SHADOW_GRIP));
					runStep(steps, "nobara_embedded_nails_discard", () -> EmbeddedNailRegistry.discardOwned(server, playerId));
					runStep(steps, "nobara_nail_traps_clear", () -> NailTrapRuntime.clearOwned(server, playerId));
					runStep(steps, "nobara_nail_marks_clear", () -> ProjectJjkNailMarks.clearAll());
					runStep(steps, "nobara_hammer_combat_clear", () -> NobaraHammerCombatRuntime.clearPlayer(playerId));
					runStep(steps, "nobara_runtime_clear", () -> ProjectJjkNobaraRuntime.clearPlayer(playerId));
					runStep(steps, "nobara_self_resonance_clear", () -> SelfResonanceRuntime.clearCaster(playerId));
					runStep(steps, "nobara_straw_doll_reset", () -> ProjectJjkStrawDollRuntime.resetCaster(server, playerId));
					runStep(steps, "nobara_ritual_glow_restore", () -> ProjectJjkRitualRuntime.restoreAllGlow(server));
					runStep(steps, "black_flash_focus_clear", () -> BlackFlashFocus.clear(player));
					runStep(steps, "forced_black_flash_clear", () -> ForcedBlackFlash.set(player, false));
					runStep(steps, "todo_swap_momentum_effect", () -> player.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM));
					runStep(steps, "resonant_momentum_effect", () -> player.removeEffect(JujutsuEffects.RESONANT_MOMENTUM));
					return ToolResult.ofToon(node);
				});
	}

	/**
	 * Runs one reset step and records its outcome; any failure (including Error-class
	 * ones — this is a dev-only fixture tool, so a stale-jar linkage error must not
	 * silently skip the remaining steps) is captured into the step's {@code detail}
	 * instead of aborting the rest.
	 */
	private static void runStep(ArrayNode steps, String name, Runnable step) {
		ObjectNode entry = steps.addObject();
		entry.put("name", name);
		try {
			step.run();
			entry.put("detail", "ok");
		} catch (Throwable e) {
			String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			entry.put("detail", "error: " + message);
		}
	}
}
