package jujutsu.mod.gametest.client;

import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Minimal deterministic helpers for the Stage B client canaries (issue #42) — the client-side
 * mirror of {@link jujutsu.mod.gametest.GameTestFixtures}.
 *
 * <p>Deliberately tiny: no test driver, no capability framework, no gameplay DSL — just shared
 * identity constants, readable failure messages, and the two entity-visibility waits. Every
 * client-world read happens through the official {@code ClientGameTestContext} handoff primitives
 * ({@code waitFor}/{@code computeOnClient}); {@code Minecraft.getInstance()} is never called from
 * the test thread. Entity handles are UUID-based; network ids are never stored as durable identity.
 */
public final class ClientGameTestFixtures {

	private ClientGameTestFixtures() {}

	/** Production mod id; its presence is what the whole test mod exists to prove. */
	public static final String PRODUCTION_MOD_ID = "jujutsumod";
	/** The isolated test mod that hosts the canaries. */
	public static final String TEST_MOD_ID = "jujutsumod-gametest";
	/**
	 * A real entity id registered by the production mod (see {@code JujutsuEntities.register()}) —
	 * mirrors {@code ServerGameTests.PRODUCTION_ENTITY_ID}.
	 */
	public static final ResourceLocation PRODUCTION_ENTITY_ID =
			ResourceLocation.fromNamespaceAndPath(PRODUCTION_MOD_ID, "todo_stone");
	/** Sentinel tick for asserts made before any world exists. Rendered as "pre-world". */
	public static final long PRE_WORLD_TICK = -1L;

	/**
	 * Builds a failure message naming the fixture, the side, the tick the failure was observed at,
	 * and the expected vs actual state — so a red canary explains itself in the task logs. A
	 * {@link #PRE_WORLD_TICK} tick renders as "pre-world".
	 */
	public static String diagnostic(String fixture, String side, long tick, String what, Object expected, Object actual) {
		String tickText = tick == PRE_WORLD_TICK ? "pre-world" : Long.toString(tick);
		return "[" + fixture + " @" + side + " tick " + tickText + "] " + what + ": expected <" + expected + ">, actual <" + actual + ">";
	}

	/**
	 * Throws {@link AssertionError} with {@link #diagnostic diagnostic(...)} context when
	 * {@code condition} is false. NEVER uses the Java {@code assert} keyword: the client JVM has no
	 * {@code -ea} guarantee, so the check must always be live.
	 */
	public static void assertWithDiagnostic(boolean condition, String fixture, String side, long tick, String what, Object expected, Object actual) {
		if (!condition) {
			throw new AssertionError(diagnostic(fixture, side, tick, what, expected, actual));
		}
	}

	/**
	 * Client game time via {@code computeOnClient(c -> c.level.getGameTime())};
	 * {@link #PRE_WORLD_TICK} when the client has no level yet.
	 */
	public static long clientTick(ClientGameTestContext context) {
		return context.computeOnClient(c -> c.level != null ? c.level.getGameTime() : PRE_WORLD_TICK);
	}

	/**
	 * Waits until the client world observes an entity with this UUID; returns its client-side
	 * {@link Entity}. The wait is bounded by {@code timeoutTicks} (never {@code NO_TIMEOUT}); a
	 * timeout re-throws an {@link AssertionError} carrying fixture diagnostic context, keeping the
	 * original "Timed out waiting for predicate" error as its cause. The post-wait read is
	 * asserted too: if the entity vanishes between the predicate pass and the read (the two are
	 * separate client-thread hops), the failure is a fixture diagnostic, never a silent
	 * {@code null} return.
	 *
	 * <p>Lookup goes through the public {@code Level#getEntity(UUID)} accessor, which delegates to
	 * the official {@code LevelEntityGetter#get(UUID)} entity storage — the same path the plan's
	 * original {@code level.getEntities().get(uuid)} spelling would use (the raw
	 * {@code getEntities()} accessor is protected in {@code ClientLevel}, so it is not reachable
	 * from this package).
	 */
	public static Entity waitForEntityVisible(ClientGameTestContext context, UUID uuid, int timeoutTicks, String fixture) {
		try {
			context.waitFor(c -> c.level != null && c.level.getEntity(uuid) != null, timeoutTicks);
		} catch (AssertionError timeout) {
			throw new AssertionError(diagnostic(fixture, "client", clientTick(context),
					"entity visible", "uuid " + uuid + " present in client world", "uuid " + uuid + " absent"), timeout);
		}
		Entity observed = context.computeOnClient(c -> c.level != null ? c.level.getEntity(uuid) : null);
		assertWithDiagnostic(observed != null, fixture, "client", clientTick(context),
				"entity visible after wait", "uuid " + uuid + " still present at the post-wait read",
				"uuid " + uuid + " absent (removed between wait and read)");
		return observed;
	}

	/**
	 * Waits until the client world no longer observes this UUID (bounded by {@code timeoutTicks};
	 * same diagnostic wrapping as {@link #waitForEntityVisible}, expected {@code <absent>}).
	 * A null client world counts as "not observed" (vacuously gone): with no world there is
	 * nothing the client could still be observing, and failing instead would mislabel a
	 * world-unload as "entity still present".
	 */
	public static void waitForEntityGone(ClientGameTestContext context, UUID uuid, int timeoutTicks, String fixture) {
		try {
			context.waitFor(c -> c.level == null || c.level.getEntity(uuid) == null, timeoutTicks);
		} catch (AssertionError timeout) {
			throw new AssertionError(diagnostic(fixture, "client", clientTick(context),
					"entity gone", "uuid " + uuid + " absent from client world", "uuid " + uuid + " still present"), timeout);
		}
	}
}
