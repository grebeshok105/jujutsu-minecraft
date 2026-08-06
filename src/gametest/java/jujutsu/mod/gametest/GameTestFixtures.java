package jujutsu.mod.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Minimal deterministic helpers for the Stage A server canaries (issue #42).
 *
 * <p>Deliberately tiny: no test driver, no capability framework, no gameplay DSL — just spawn
 * validation, removal verification, and readable failure messages. Entity handles are passed as
 * objects; network ids are never stored as durable identity.
 */
public final class GameTestFixtures {

	private GameTestFixtures() {}

	/**
	 * Spawns a mob of {@code type} at the fixture-local {@code relativePos} — with no AI, the
	 * vanilla {@code spawnWithNoFreeWill} determinism switch — and asserts it is alive in the level
	 * immediately after spawning.
	 *
	 * <p>Every {@code GameTestHelper} position, this one included, is structure-relative; the
	 * helper converts to world coordinates internally. Never pre-convert with {@code absolutePos}
	 * here — a double conversion strands the mob outside the fixture.
	 */
	public static <E extends Mob> E spawnMob(GameTestHelper helper, String fixture, EntityType<E> type, BlockPos relativePos) {
		E entity = helper.spawnWithNoFreeWill(type, relativePos);
		helper.assertTrue(entity.isAlive(), diagnostic(fixture, helper.getTick(),
				"spawn " + type + " at " + relativePos, "alive entity", "alive=" + entity.isAlive()));
		return entity;
	}

	/**
	 * Discards {@code entity} at test tick {@code discardTick} and, ten ticks later, verifies the
	 * level no longer contains it at {@code relativePos}.
	 *
	 * <p>{@code discard()} marks the entity removed synchronously (the level's removal callback
	 * fires from {@code setRemoved}), but the world's entity lookup flushes removal on the tick
	 * loop, so the absence assert is deferred 10 ticks — deterministic and far inside the canary's
	 * {@code maxTicks}. Both callbacks are registered from the calling (body) context, never from
	 * inside another scheduled callback. The absence check pairs a fixture-framed diagnostic on the
	 * handle with the vanilla position-scoped assert, so a red run names the fixture in the JUnit
	 * report while keeping the stock message's tick and relative position.
	 */
	public static <E extends Entity> void removeAndVerifyGone(GameTestHelper helper, String fixture, E entity,
			EntityType<E> type, BlockPos relativePos, long discardTick) {
		helper.runAtTickTime(discardTick, () -> {
			entity.discard();
			helper.assertTrue(entity.isRemoved(), diagnostic(fixture, helper.getTick(),
					"discard " + type, "isRemoved=true", "isRemoved=" + entity.isRemoved()));
		});
		helper.runAtTickTime(discardTick + 10, () -> {
			helper.assertTrue(entity.isRemoved(), diagnostic(fixture, helper.getTick(),
					"entity gone after discard", "isRemoved=true", "isRemoved=" + entity.isRemoved()));
			helper.assertEntityNotPresent(type, relativePos);
		});
	}

	/**
	 * Builds a failure message naming the fixture, the tick the failure was observed at, and the
	 * expected vs actual state — so a red canary explains itself in the JUnit report.
	 */
	public static Component diagnostic(String fixture, long tick, String what, Object expected, Object actual) {
		return Component.literal("[" + fixture + " @tick " + tick + "] " + what + ": expected <" + expected + ">, actual <" + actual + ">");
	}
}
