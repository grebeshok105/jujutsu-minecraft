package jujutsu.mod.client.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import jujutsu.mod.vfx.VfxCue;

/**
 * Client-side sink/emerge progress cache for a body diving into shadow, fed by a vessel's cue
 * recipes and read by the third-person body dive ({@code CharacterSkinAnimationMixin}) and the
 * first-person camera dive. The recipes pass their own window lengths, so this cache knows no
 * vessel: it is a mechanism, like {@link HiddenBodyRenderGate}, not a kit.
 *
 * <p>Progress is computed purely from the cue's authoritative server start time against the client
 * level's game time, so the body moves on the same clock the server used. Entries carry a wall-clock
 * TTL like {@link HiddenBodyRenderGate} and expire lazily, so a lost emerge cue fails open: the body
 * drifts back to visible, never stuck under the floor.
 */
public final class ShadowBodySink {
	private static final long MILLIS_PER_TICK = 50L;
	/** Ripple cues re-arm the hold every SHADOW_RIPPLE_PERIOD_TICKS; 8 ticks keeps it with slack. */
	private static final int UNDER_TTL_TICKS = 8;

	private static final ConcurrentHashMap<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static LongSupplier clock = System::currentTimeMillis;

	private ShadowBodySink() {}

	private enum State { SINKING, UNDER, EMERGING }

	private record Entry(State state, long startGameTime, int durationTicks, long expireMillis) {}

	/** The window itself plus one full hold TTL of slack for a slightly late follow-up cue. */
	private static long windowTtlMillis(int durationTicks) {
		return (durationTicks + UNDER_TTL_TICKS) * MILLIS_PER_TICK;
	}

	/** Starts the dive: progress 0→1 over {@code sinkTicks} from the cue's authoritative time. */
	public static void beginSink(int entityId, long startGameTime, int sinkTicks) {
		if (entityId == VfxCue.NO_ANCHOR) {
			return;
		}
		ENTRIES.put(entityId, new Entry(State.SINKING, startGameTime, Math.max(1, sinkTicks),
				clock.getAsLong() + windowTtlMillis(sinkTicks)));
	}

	/**
	 * Marks the body fully under, as each ripple cue does. Idempotent: re-arms the hold TTL. A ripple
	 * after the rise (packet reorder) must never yank the body back under.
	 */
	public static void completeSink(int entityId) {
		if (entityId == VfxCue.NO_ANCHOR) {
			return;
		}
		Entry entry = ENTRIES.get(entityId);
		if (entry != null && entry.state() == State.EMERGING) {
			return;
		}
		ENTRIES.put(entityId, new Entry(State.UNDER, entry == null ? 0L : entry.startGameTime(), 1,
				clock.getAsLong() + UNDER_TTL_TICKS * MILLIS_PER_TICK));
	}

	/** Starts the rise: progress 1→0 over {@code emergeTicks} from the cue's authoritative time. */
	public static void beginEmerge(int entityId, long startGameTime, int emergeTicks) {
		if (entityId == VfxCue.NO_ANCHOR) {
			return;
		}
		Entry entry = ENTRIES.get(entityId);
		// Nothing is sunk on this client, so there is nothing to rise — fail open to visible.
		if (entry == null || entry.state() == State.EMERGING) {
			return;
		}
		ENTRIES.put(entityId, new Entry(State.EMERGING, startGameTime, Math.max(1, emergeTicks),
				clock.getAsLong() + windowTtlMillis(emergeTicks)));
	}

	public static void reset(int entityId) {
		if (entityId != VfxCue.NO_ANCHOR) {
			ENTRIES.remove(entityId);
		}
	}

	/**
	 * 0..1 while sinking, 1 while under, -1 when the entry is absent or expired. {@code frameTime} is
	 * the level game time plus the frame's partial tick, so per-frame readers move continuously
	 * instead of stepping once per tick.
	 */
	public static float sinkProgress(int entityId, float frameTime) {
		Entry entry = entryOrExpired(entityId);
		if (entry == null) {
			return -1.0f;
		}
		return switch (entry.state()) {
			case SINKING -> clamp01((frameTime - entry.startGameTime()) / entry.durationTicks());
			case UNDER -> 1.0f;
			case EMERGING -> -1.0f;
		};
	}

	/** 1 at the start of the rise down to 0 when fully risen, -1 when the entry is absent or expired. */
	public static float emergeProgress(int entityId, float frameTime) {
		Entry entry = entryOrExpired(entityId);
		if (entry == null) {
			return -1.0f;
		}
		return switch (entry.state()) {
			case EMERGING -> 1.0f - clamp01((frameTime - entry.startGameTime()) / entry.durationTicks());
			default -> -1.0f;
		};
	}

	/** Standard smoothstep easing used for the body dive; pure so tests can pin it. */
	public static float smoothstep(float t) {
		float x = clamp01(t);
		return x * x * (3.0f - 2.0f * x);
	}

	/** Test seam: swaps the wall clock used for TTL expiry; pass {@code null} to restore the system clock. */
	static void setClockForTests(LongSupplier testClock) {
		clock = testClock == null ? System::currentTimeMillis : testClock;
	}

	private static Entry entryOrExpired(int entityId) {
		Entry entry = ENTRIES.get(entityId);
		if (entry == null) {
			return null;
		}
		if (entry.expireMillis() <= clock.getAsLong()) {
			ENTRIES.remove(entityId, entry);
			return null;
		}
		return entry;
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
