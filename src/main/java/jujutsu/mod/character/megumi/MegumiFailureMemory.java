package jujutsu.mod.character.megumi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short per-body memory of recently failed actions (issue #107 §15): a body whose pounce died on a
 * wall should not retry the same launch every tick. Each failure stamps the (body, action) pair;
 * the weight starts at {@code 1.0 - FAILURE_PENALTY·count} (floored at
 * {@link MegumiShikigamiProfile#FAILURE_FLOOR}) and recovers linearly to 1.0 across
 * {@link MegumiShikigamiProfile#FAILURE_WINDOW_TICKS}. This is not learning — the memory is short,
 * local, and forgets completely.
 *
 * <p>Entries are keyed by body id. {@link #clear(UUID)} drops one body's memory (called from
 * teardown/fixture paths); the coordinator prunes entries for bodies that no longer exist, so a
 * dead body never leaks its failures.
 */
public final class MegumiFailureMemory {
	private static final Map<UUID, Map<String, Deque<Long>>> FAILURES = new ConcurrentHashMap<>();

	private MegumiFailureMemory() {}

	/** Notes that {@code actionKey} just failed for this body at {@code gameTime}. */
	public static void recordFailure(UUID bodyId, String actionKey, long gameTime) {
		FAILURES.computeIfAbsent(bodyId, key -> new ConcurrentHashMap<>())
				.computeIfAbsent(actionKey, key -> new ArrayDeque<>())
				.addLast(gameTime);
	}

	/**
	 * The current weight of {@code actionKey} for this body: 1.0 with no remembered failure,
	 * dropping toward the floor while failures are fresh, recovering linearly across the window.
	 */
	public static double weight(UUID bodyId, String actionKey, long gameTime) {
		Map<String, Deque<Long>> perAction = FAILURES.get(bodyId);
		if (perAction == null) {
			return 1.0;
		}
		Deque<Long> stamps = perAction.get(actionKey);
		if (stamps == null) {
			return 1.0;
		}
		long window = MegumiShikigamiProfile.FAILURE_WINDOW_TICKS;
		while (!stamps.isEmpty() && gameTime - stamps.peekFirst() > window) {
			stamps.pollFirst();
		}
		if (stamps.isEmpty()) {
			return 1.0;
		}
		double base = Math.max(MegumiShikigamiProfile.FAILURE_FLOOR,
				1.0 - MegumiShikigamiProfile.FAILURE_PENALTY * stamps.size());
		long newest = stamps.peekLast();
		double progress = Math.min(1.0, (double) (gameTime - newest) / window);
		return Math.min(1.0, base + (1.0 - base) * progress);
	}

	/** Drops every remembered failure of this body (teardown/fixture paths). */
	public static void clear(UUID bodyId) {
		FAILURES.remove(bodyId);
	}

	/** Drops memories of bodies not in {@code liveBodyIds}; called by the coordinator's sweep. */
	static void retainOnly(Set<UUID> liveBodyIds) {
		FAILURES.keySet().retainAll(liveBodyIds);
	}

	/** Drops every remembered failure — the server-stop hook, like every sibling runtime's clear. */
	static void clearAll() {
		FAILURES.clear();
	}
}
