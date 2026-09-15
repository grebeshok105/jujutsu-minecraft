package jujutsu.mcpdev;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;

/**
 * Generalized async wait (extends {@link JujutsuTicksWaitTool}'s TickTask
 * pattern): polls a list of conditions every server tick until they hold or
 * {@code cap_ticks} elapses. The HTTP thread never blocks the server — it parks
 * on a {@link CompletableFuture} completed by the job.
 *
 * <p>{@code mode} is {@code "any"} (default — first condition that holds ends
 * the wait) or {@code "all"}. Supported conditions:
 * <ul>
 *   <li>{@code entity_dead {uuid}} — entity removed or not alive;</li>
 *   <li>{@code entity_gone {uuid}} — entity not found in any dimension;</li>
 *   <li>{@code entity_present {type, dimension?, near_uuid?, radius?}} — an
 *       entity of the type loaded, optionally within radius of another entity;</li>
 *   <li>{@code effect_on / effect_off {uuid, effect}} — living entity has/lacks
 *       the status effect;</li>
 *   <li>{@code health_below / health_above {uuid, value}};</li>
 *   <li>{@code cooldown_clear {player_uuid, slot}} — ability cooldown at 0.</li>
 * </ul>
 *
 * <p>Server stop mid-wait fails fast, as does a malformed condition. The result
 * reports {@code waited_ticks}, whether the wait was satisfied or capped, and a
 * per-condition matched flag for post-mortem debugging.
 */
@McpTool(
		name = "jujutsu_wait_until",
		description = "Waits asynchronously until listed conditions hold (mode any/all), capped at cap_ticks")
public final class JujutsuWaitUntilTool extends BaseTool {

	private static final int MAX_TICKS = 1200;
	private static final long TICK_MS = 50L;
	private static final long TIMEOUT_MARGIN_MS = 10_000L;

	private static final JsonNode SCHEMA = Schemas.object()
			.required("cap_ticks", Schemas.integerBetween(
					"Max server ticks to wait before giving up (1-1200)", 1, MAX_TICKS))
			.required("conditions", Schemas.arrayOf(
					"Conditions to poll; each is an object with a 'type' field",
					Schemas.object().build()))
			.optional("mode", Schemas.enumOf(
					"any (default) ends the wait on the first matching condition; all requires every condition",
					"any", "all"))
			.build();

	public JujutsuWaitUntilTool() {
		super("jujutsu_wait_until");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		int capTicks = r.requireInt("cap_ticks");
		JsonNode conditionsNode = r.optArray("conditions");
		if (conditionsNode == null || conditionsNode.isEmpty()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Tool 'jujutsu_wait_until': 'conditions' must be a non-empty array");
		}
		String mode = r.optString("mode", "any").toLowerCase(Locale.ROOT);
		if (!mode.equals("any") && !mode.equals("all")) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Invalid mode: " + mode + " (expected any|all)");
		}
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();

		CompletableFuture<Void> done = new CompletableFuture<>();
		WaitJob job = onMainThread(
				context,
				ignored -> {
					WaitJob created = new WaitJob(server, conditionsNode, mode.equals("all"), capTicks, done);
					created.scheduleNext();
					return created;
				});

		try {
			done.get(capTicks * TICK_MS + TIMEOUT_MARGIN_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			throw new McpException(ErrorCodes.MAIN_THREAD_TIMEOUT,
					"Tool 'jujutsu_wait_until' timed out after " + capTicks + " ticks");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"Tool 'jujutsu_wait_until' interrupted while waiting", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof McpException mcpException) {
				throw mcpException;
			}
			String message = cause == null || cause.getMessage() == null
					? "unknown failure"
					: cause.getMessage();
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"Tool 'jujutsu_wait_until' failed: " + message, cause);
		}

		ObjectNode result = context.mapper().createObjectNode();
		result.put("waited_ticks", job.waitedTicks());
		result.put("satisfied", job.satisfied());
		result.put("mode", mode);
		ArrayNode report = context.mapper().createArrayNode();
		boolean[] matched = job.matched();
		for (int i = 0; i < conditionsNode.size(); i++) {
			ObjectNode item = context.mapper().createObjectNode();
			item.set("condition", conditionsNode.get(i));
			item.put("matched", matched[i]);
			report.add(item);
		}
		result.set("conditions", report);
		return ToolResult.ofToon(result);
	}

	// --- condition evaluation (server main thread only) ---------------------

	private static boolean evaluate(MinecraftServer server, JsonNode cond) {
		String type = cond.path("type").asText("");
		switch (type) {
			case "entity_dead": {
				Entity e = JujutsuMcpdevEntities.find(server, uuidArg(cond, "uuid"));
				return e == null || !e.isAlive();
			}
			case "entity_gone":
				return JujutsuMcpdevEntities.find(server, uuidArg(cond, "uuid")) == null;
			case "entity_present":
				return entityPresent(server, cond);
			case "effect_on":
			case "effect_off": {
				Entity e = JujutsuMcpdevEntities.require(server, uuidArg(cond, "uuid"));
				if (!(e instanceof LivingEntity living)) {
					throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
							"effect_* condition on non-living entity");
				}
				boolean has = living.hasEffect(effectHolder(cond));
				return type.equals("effect_on") ? has : !has;
			}
			case "health_below":
			case "health_above": {
				Entity e = JujutsuMcpdevEntities.require(server, uuidArg(cond, "uuid"));
				if (!(e instanceof LivingEntity living)) {
					throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
							"health_* condition on non-living entity");
				}
				float threshold = (float) cond.path("value").asDouble(Double.NaN);
				if (Double.isNaN(threshold)) {
					throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
							"health_* condition requires 'value'");
				}
				return type.equals("health_below")
						? living.getHealth() < threshold
						: living.getHealth() > threshold;
			}
			case "cooldown_clear": {
				ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(
						server, uuidArg(cond, "player_uuid"));
				CharacterAbility ability = parseAbility(cond.path("slot").asText(""));
				return CharacterAbilityCooldowns.remainingTicks(player, ability) <= 0;
			}
			default:
				throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
						"Unknown condition type: '" + type + "'");
		}
	}

	private static boolean entityPresent(MinecraftServer server, JsonNode cond) {
		String typeId = cond.path("type_id").asText(cond.path("entity_type").asText(""));
		if (typeId.isBlank()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"entity_present condition requires 'type_id'");
		}
		ResourceLocation id = ResourceLocation.parse(typeId);
		String dimension = cond.path("dimension").asText(null);
		Entity near = cond.has("near_uuid")
				? JujutsuMcpdevEntities.require(server, uuidArg(cond, "near_uuid"))
				: null;
		double radius = cond.path("radius").asDouble(8.0);
		for (ServerLevel level : server.getAllLevels()) {
			if (dimension != null && !level.dimension().location().toString().equals(dimension)) {
				continue;
			}
			for (Entity e : level.getAllEntities()) {
				if (!EntityType.getKey(e.getType()).equals(id)) {
					continue;
				}
				if (near == null || e.distanceTo(near) <= radius) {
					return true;
				}
			}
		}
		return false;
	}

	private static Holder<MobEffect> effectHolder(JsonNode cond) {
		String id = cond.path("effect").asText("");
		if (id.isBlank()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"effect_* condition requires 'effect'");
		}
		return BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(id))
				.orElseThrow(() -> new McpException(ErrorCodes.TOOL_INPUT_INVALID,
						"Unknown effect: " + id));
	}

	private static UUID uuidArg(JsonNode cond, String field) {
		String raw = cond.path(field).asText("");
		if (raw.isBlank()) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Condition requires '" + field + "'");
		}
		return JujutsuMcpdevPlayers.parseUuid(raw);
	}

	private static CharacterAbility parseAbility(String raw) {
		try {
			return CharacterAbility.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"Invalid cooldown slot: " + raw);
		}
	}

	/** Self-rescheduling per-tick poll; same idiom as {@link JujutsuTicksWaitTool.WaitJob}. */
	private static final class WaitJob implements Runnable {

		private final MinecraftServer server;
		private final JsonNode conditions;
		private final boolean requireAll;
		private final int capTicks;
		private final int startTick;
		private final CompletableFuture<Void> done;
		private final boolean[] matched;

		private volatile int waitedTicks;
		private volatile boolean satisfied;

		WaitJob(MinecraftServer server, JsonNode conditions, boolean requireAll,
				int capTicks, CompletableFuture<Void> done) {
			this.server = server;
			this.conditions = conditions;
			this.requireAll = requireAll;
			this.capTicks = capTicks;
			this.startTick = server.getTickCount();
			this.done = done;
			this.matched = new boolean[conditions.size()];
		}

		void scheduleNext() {
			server.schedule(new TickTask(server.getTickCount() + 1, this));
		}

		int waitedTicks() {
			return waitedTicks;
		}

		boolean satisfied() {
			return satisfied;
		}

		boolean[] matched() {
			return matched;
		}

		@Override
		public void run() {
			try {
				if (!server.isRunning()) {
					done.completeExceptionally(new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
							"Server stopped while waiting for conditions"));
					return;
				}
				int elapsed = server.getTickCount() - startTick;
				boolean allMatch = true;
				boolean anyMatch = false;
				for (int i = 0; i < conditions.size(); i++) {
					boolean m = evaluate(server, conditions.get(i));
					matched[i] = matched[i] || m;
					allMatch &= m;
					anyMatch |= m;
				}
				boolean hold = requireAll ? allMatch : anyMatch;
				if (hold || elapsed >= capTicks) {
					waitedTicks = elapsed;
					satisfied = hold;
					done.complete(null);
				} else {
					scheduleNext();
				}
			} catch (Throwable t) {
				done.completeExceptionally(t);
			}
		}
	}
}
