package jujutsu.mcpdev;

import java.util.List;
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

/**
 * Drains {@link JujutsuCombatLog} — the ring buffer of damage/death events
 * recorded by Fabric hooks. Filters: {@code since_tick} (entries strictly newer
 * than the given server tick — pass the tick a previous call ended at for
 * drain semantics), {@code target_uuid}, {@code attacker_uuid}, and
 * {@code limit} (most recent N after filtering, default all). The buffer holds
 * at most {@value JujutsuCombatLog#CAPACITY} entries and clears on server stop.
 */
@McpTool(
		name = "jujutsu_combat_log",
		description = "Reads the combat event ring buffer (damage/death with real attacker attribution)")
public final class JujutsuCombatLogTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.optional("since_tick", Schemas.integer("Only events with tick > since_tick"))
			.optional("limit", Schemas.integerBetween("Max events returned (most recent first after filter)", 1, 512))
			.optional("target_uuid", Schemas.string("Only events with this target entity uuid"))
			.optional("attacker_uuid", Schemas.string("Only events credited to this attacker uuid"))
			.build();

	public JujutsuCombatLogTool() {
		super("jujutsu_combat_log");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		JujutsuMcpdevPlayers.requireServer();
		long sinceTick = r.has("since_tick") ? r.requireInt("since_tick") : Long.MIN_VALUE;
		int limit = r.optInt("limit", JujutsuCombatLog.CAPACITY);
		String rawTarget = r.optString("target_uuid", null);
		String rawAttacker = r.optString("attacker_uuid", null);
		UUID targetUuid = rawTarget == null ? null : JujutsuMcpdevPlayers.parseUuid(rawTarget);
		UUID attackerUuid = rawAttacker == null ? null : JujutsuMcpdevPlayers.parseUuid(rawAttacker);

		List<JujutsuCombatLog.Entry> all = JujutsuCombatLog.snapshot();
		// Chronological order; when more than `limit` match, keep the most recent tail.
		List<JujutsuCombatLog.Entry> matched = new java.util.ArrayList<>();
		long lastTick = -1L;
		for (JujutsuCombatLog.Entry e : all) {
			if (e.tick() <= sinceTick) {
				continue;
			}
			if (targetUuid != null && !targetUuid.equals(e.targetUuid())) {
				continue;
			}
			if (attackerUuid != null && !attackerUuid.equals(e.attackerUuid())) {
				continue;
			}
			matched.add(e);
			lastTick = Math.max(lastTick, e.tick());
		}
		ArrayNode events = context.mapper().createArrayNode();
		for (int i = Math.max(0, matched.size() - limit); i < matched.size(); i++) {
			JujutsuCombatLog.Entry e = matched.get(i);
			ObjectNode node = context.mapper().createObjectNode();
			node.put("tick", e.tick());
			node.put("kind", e.kind());
			node.put("target_type", e.targetType());
			node.put("target_uuid", e.targetUuid() == null ? null : e.targetUuid().toString());
			if (e.attackerType() == null) {
				node.putNull("attacker_type");
				node.putNull("attacker_uuid");
			} else {
				node.put("attacker_type", e.attackerType());
				node.put("attacker_uuid", e.attackerUuid().toString());
			}
			node.put("amount", e.amount());
			if (e.blocked()) {
				node.put("blocked", true);
			}
			events.add(node);
		}
		for (JujutsuCombatLog.Entry e : all) {
			lastTick = Math.max(lastTick, e.tick());
		}
		ObjectNode result = context.mapper().createObjectNode();
		result.put("count", events.size());
		result.put("last_tick", lastTick);
		result.set("events", events);
		return ToolResult.ofToon(result);
	}
}
