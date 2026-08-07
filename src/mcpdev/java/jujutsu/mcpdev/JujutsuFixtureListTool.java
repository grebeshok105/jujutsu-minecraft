package jujutsu.mcpdev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Static enumeration of the dev fixture scenarios (issue #43 L3, contract C3): the
 * reproduction scenes an agent can stage with the dev-control tools, each with the
 * cleanup guarantee it relies on. Purely static — no world state is touched, so the
 * tool is read-only and needs no server access.
 */
@McpTool(
		name = "jujutsu_fixture_list",
		description = "Lists available fixture scenarios and their cleanup guarantees",
		readOnly = true)
public final class JujutsuFixtureListTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object().build();

	public JujutsuFixtureListTool() {
		super("jujutsu_fixture_list");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ObjectNode node = context.mapper().createObjectNode();
		ArrayNode fixtures = node.putArray("fixtures");

		fixtures.add(fixture(context,
				"aimed_swap",
				"Todo Boogie Woogie clap-swap aimed at a target entity — the canonical L3 reproduction "
						+ "(vessel_select, entity_summon, player_set_rotation, ability_invoke, state_get, entity_get).",
				"jujutsu_fixture_reset: cooldowns, Todo transient swap state and swap momentum."));
		fixtures.add(fixture(context,
				"stone_lifecycle",
				"Todo stone throw: flight at constant speed, block collision, vanish and terminal-state cleanup.",
				"jujutsu_fixture_reset: cooldowns and Todo transient state."));
		fixtures.add(fixture(context,
				"todo_pair_swap",
				"Todo pair-mark commit and triple cycle, including the fake-clap feint refusal path.",
				"jujutsu_fixture_reset: cooldowns and Todo transient state."));
		fixtures.add(fixture(context,
				"megumi_pack",
				"Megumi Divine Dogs pack: summon, sic-chase, and the shadow trap/move/drop maneuvers.",
				"jujutsu_fixture_reset: summons, traps, shadow moves and drops."));
		fixtures.add(fixture(context,
				"nobara_nails",
				"Nobara embedded nails: nail traps, marks, hairpin resonance and the straw-doll ritual.",
				"jujutsu_fixture_reset: nails, traps, marks and resonance."));

		return ToolResult.ofToon(node);
	}

	private static ObjectNode fixture(ToolContext context, String name, String description, String cleanup) {
		ObjectNode entry = context.mapper().createObjectNode();
		entry.put("name", name);
		entry.put("description", description);
		entry.put("cleanup", cleanup);
		return entry;
	}
}
