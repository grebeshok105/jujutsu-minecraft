package jujutsu.mod.client.character.todo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import jujutsu.mod.character.todo.TodoProfile;

/**
 * Client-side cache of Todo's live pair selection, fed by the {@code PAIR_MARK} cue recipe and
 * read by the pair HUD chip.
 *
 * <p>An audible mark cue starts (or restarts) the countdown from the cue's authoritative server
 * start time; the silent pulse the server re-emits every {@code PAIR_MARK_PULSE_TICKS} while the
 * selection lives only re-arms a wall-clock hold, never the countdown — so the chip counts down
 * against the real selection deadline. Entries expire lazily by wall clock after the last cue,
 * the {@link jujutsu.mod.client.render.ShadowBodySink} fail-open pattern: a lost commit or expire
 * cue never leaves a stale chip pinned, it simply fades on its own. Cues are caster-only, so this
 * cache only ever sees the local player's own selections.
 */
public final class TodoPairSelectionClientState {
	private static final long MILLIS_PER_TICK = 50L;
	/** Pulse period is 20 ticks; 30 keeps the chip alive between pulses with slack. */
	private static final int HOLD_TTL_TICKS = 30;

	private static final ConcurrentHashMap<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static LongSupplier clock = System::currentTimeMillis;

	private TodoPairSelectionClientState() {}

	private record Entry(long expiresAtGameTime, long lastSeenMillis) {}

	/** An audible mark: the selection's countdown starts at the cue's authoritative start time. */
	public static void mark(int entityId, long startGameTime) {
		ENTRIES.put(entityId, new Entry(startGameTime + TodoProfile.PAIR_SELECTION_TTL_TICKS, clock.getAsLong()));
	}

	/**
	 * A silent server pulse: keeps the entry alive but never extends the countdown. A pulse with no
	 * cached mark (the mark cue was missed) starts a best-effort entry rather than losing the chip.
	 */
	public static void pulse(int entityId, long startGameTime) {
		Entry previous = ENTRIES.get(entityId);
		if (previous == null) {
			mark(entityId, startGameTime);
			return;
		}
		ENTRIES.put(entityId, new Entry(previous.expiresAtGameTime, clock.getAsLong()));
	}

	/** The ids the cache currently knows about, stale or not; the reader filters by TTL. */
	public static Set<Integer> entityIds() {
		return Set.copyOf(ENTRIES.keySet());
	}

	/**
	 * Remaining selection ticks for the marked entity, or -1 when the entry is absent or its
	 * wall-clock hold has lapsed. {@code gameTime} is the client level's game time.
	 */
	public static long remainingTicks(int entityId, long gameTime) {
		Entry entry = ENTRIES.get(entityId);
		if (entry == null || clock.getAsLong() - entry.lastSeenMillis > HOLD_TTL_TICKS * MILLIS_PER_TICK) {
			return -1;
		}
		return Math.max(0L, entry.expiresAtGameTime - gameTime);
	}

	/** Test seam: swaps the wall clock used for the hold TTL; pass {@code null} to restore it. */
	static void setClockForTests(LongSupplier testClock) {
		clock = testClock == null ? System::currentTimeMillis : testClock;
	}

	static void clearForTests() {
		ENTRIES.clear();
	}
}
