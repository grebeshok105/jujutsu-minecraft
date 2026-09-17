package jujutsu.mod.character.megumi;

import java.util.UUID;

/**
 * Short per-body memory of recently failed actions (issue #107 §15): a body whose pounce died on a
 * wall should not retry the same launch every tick. Contract skeleton — the window/floor/recovery
 * maths lands with the coordination block; until then every action weighs full.
 */
public final class MegumiFailureMemory {
	private MegumiFailureMemory() {}

	/** Notes that {@code actionKey} just failed for this body at {@code gameTime}. */
	public static void recordFailure(UUID bodyId, String actionKey, long gameTime) {}

	/**
	 * The current weight of {@code actionKey} for this body: 1.0 when no recent failure is
	 * remembered, decaying toward a floor while failures are fresh, recovering after the window.
	 */
	public static double weight(UUID bodyId, String actionKey, long gameTime) {
		return 1.0;
	}

	/** Drops every remembered failure of this owner (teardown/fixture paths). */
	public static void clear(UUID ownerId) {}
}
