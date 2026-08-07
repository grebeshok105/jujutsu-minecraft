package jujutsu.mcpdev;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;

/**
 * Waits out a bounded window of server ticks (issue #43 L3, contract C1).
 *
 * <p>The wait is genuinely asynchronous — the server main thread is never blocked and
 * nothing sleeps. The tool schedules a self-rescheduling {@link TickTask} on the main
 * thread (one per tick, the upstream "delayed task + job handle" idiom) and the HTTP
 * thread parks on a {@link CompletableFuture} until the job completes it; the returned
 * {@code waited_ticks} is the server tick delta actually observed, so early/late tick
 * dispatch jitter never misreports the wait.
 *
 * <p>Termination condition (checked on the main thread every tick):
 * <ul>
 *   <li>without {@code player_uuid} — exactly {@code ticks} server ticks;</li>
 *   <li>with {@code player_uuid} — as soon as the player's PRIMARY cooldown reaches 0
 *       (the readiness gate), but never later than {@code ticks} ticks — {@code ticks}
 *       caps a cooldown that is stuck, restarted, or longer than expected. A
 *       {@code waited_ticks} equal to the cap means the cooldown had not cleared.</li>
 * </ul>
 *
 * <p>If the server stops or the target player goes offline mid-wait, the wait fails
 * fast with {@code TOOL_HANDLER_ERROR} instead of running out the clock.
 */
@McpTool(
		name = "jujutsu_ticks_wait",
		description = "Waits N server ticks asynchronously without blocking the HTTP thread")
public final class JujutsuTicksWaitTool extends BaseTool {

	/** Smallest meaningful wait — one server tick. */
	private static final int MIN_TICKS = 1;

	/** Largest wait the tool accepts — 1200 ticks = 60 s at 20 tps. */
	private static final int MAX_TICKS = 1200;

	/** One server tick at the standard 20 tps, in milliseconds. */
	private static final long TICK_MS = 50L;

	/** Extra headroom on top of the tick budget for scheduling jitter. */
	private static final long TIMEOUT_MARGIN_MS = 10_000L;

	private static final JsonNode SCHEMA = Schemas.object()
			.required("ticks", Schemas.integerBetween(
					"Number of server ticks to wait (1-1200; with player_uuid, the cap for the cooldown wait)",
					MIN_TICKS, MAX_TICKS))
			.optional("player_uuid",
					Schemas.string("Wait until this player's PRIMARY cooldown reaches 0 (capped at ticks)"))
			.build();

	public JujutsuTicksWaitTool() {
		super("jujutsu_ticks_wait");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		int ticks = r.requireInt("ticks");
		if (ticks < MIN_TICKS || ticks > MAX_TICKS) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID,
					"ticks must be between " + MIN_TICKS + " and " + MAX_TICKS + ", got " + ticks);
		}
		String rawUuid = r.optString("player_uuid", null);
		UUID playerUuid = rawUuid == null ? null : JujutsuMcpdevPlayers.parseUuid(rawUuid);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();

		CompletableFuture<Void> done = new CompletableFuture<>();
		WaitJob job = onMainThread(
				context,
				ignored -> {
					ServerPlayer player = playerUuid == null
							? null
							: JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					WaitJob created = new WaitJob(server, player, ticks, done);
					created.scheduleNext();
					return created;
				});

		try {
			done.get(ticks * TICK_MS + TIMEOUT_MARGIN_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			throw new McpException(ErrorCodes.MAIN_THREAD_TIMEOUT,
					"Tool 'jujutsu_ticks_wait' timed out after " + ticks + " ticks");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"Tool 'jujutsu_ticks_wait' interrupted while waiting", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof McpException mcpException) {
				throw mcpException;
			}
			String message = cause == null || cause.getMessage() == null
					? "unknown failure"
					: cause.getMessage();
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
					"Tool 'jujutsu_ticks_wait' failed: " + message, cause);
		}

		ObjectNode node = context.mapper().createObjectNode();
		node.put("waited_ticks", job.waitedTicks());
		node.put("elapsed_ms", (int) job.elapsedMs());
		return ToolResult.ofToon(node);
	}

	/**
	 * One self-rescheduling poll of the wait condition. Every instance runs on the
	 * server main thread (it is scheduled there as a {@link TickTask}); the volatile
	 * results are read back on the HTTP thread after the future completes, so the
	 * future's happens-before edge makes them safely visible.
	 */
	private static final class WaitJob implements Runnable {

		private final MinecraftServer server;
		private final ServerPlayer player;
		private final int ticks;
		private final int startTick;
		private final long startMs;
		private final CompletableFuture<Void> done;

		private volatile int waitedTicks;
		private volatile long elapsedMs;

		WaitJob(MinecraftServer server, ServerPlayer player, int ticks, CompletableFuture<Void> done) {
			this.server = server;
			this.player = player;
			this.ticks = ticks;
			this.startTick = server.getTickCount();
			this.startMs = System.currentTimeMillis();
			this.done = done;
		}

		/** Queues the next poll one tick ahead; called from the main thread (and the very first time). */
		void scheduleNext() {
			server.schedule(new TickTask(server.getTickCount() + 1, this));
		}

		int waitedTicks() {
			return waitedTicks;
		}

		long elapsedMs() {
			return elapsedMs;
		}

		@Override
		public void run() {
			// Main thread. Never let an exception escape a TickTask — route it to the
			// future instead, or it would surface inside the server tick loop.
			try {
				if (!server.isRunning()) {
					done.completeExceptionally(new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
							"Server stopped while waiting for ticks"));
					return;
				}
				int elapsed = server.getTickCount() - startTick;
				boolean ticksElapsed = elapsed >= ticks;
				boolean cooldownClear = player == null
						|| CharacterAbilityCooldowns.remainingTicks(player, CharacterAbility.PRIMARY) <= 0;
				if (ticksElapsed || cooldownClear) {
					waitedTicks = elapsed;
					elapsedMs = System.currentTimeMillis() - startMs;
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
