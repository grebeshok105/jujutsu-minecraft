package jujutsu.mod.client.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.vfx.VfxCue;

/**
 * Client-side sink/emerge progress cache for Megumi's shadow move, fed by the dive/ripple/emerge cue
 * recipes and read by the third-person body dive ({@code CharacterSkinAnimationMixin}) and the
 * first-person camera dive.
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
	/** The sink window itself plus one full hold TTL of slack for a slightly late first ripple. */
	private static final long SINK_TTL_MILLIS = (MegumiProfile.SHADOW_SINK_TICKS + UNDER_TTL_TICKS) * MILLIS_PER_TICK;
	/** The emerge window plus slack; at its end the body is fully risen anyway. */
	private static final long EMERGE_TTL_MILLIS = (MegumiProfile.SHADOW_EMERGE_TICKS + UNDER_TTL_TICKS) * MILLIS_PER_TICK;

	private static final ConcurrentHashMap<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static LongSupplier clock = System::currentTimeMillis;

	private ShadowBodySink() {}

	private enum State { SINKING, UNDER, EMERGING }

	private record Entry(State state, long startGameTime, long expireMillis) {}

	/** Starts the dive: progress 0→1 over {@link MegumiProfile#SHADOW_SINK_TICKS} from the cue's time. */
	public static void beginSink(int entityId, long startGameTime) {
		if (entityId == VfxCue.NO_ANCHOR) {
			return;
		}
		ENTRIES.put(entityId, new Entry(State.SINKING, startGameTime, clock.getAsLong() + SINK_TTL_MILLIS));
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
		ENTRIES.put(entityId, new Entry(State.UNDER, entry == null ? 0L : entry.startGameTime(),
				clock.getAsLong() + UNDER_TTL_TICKS * MILLIS_PER_TICK));
	}

	/** Starts the rise: progress 1→0 over {@link MegumiProfile#SHADOW_EMERGE_TICKS} from the cue's time. */
	public static void beginEmerge(int entityId, long startGameTime) {
		if (entityId == VfxCue.NO_ANCHOR) {
			return;
		}
		Entry entry = ENTRIES.get(entityId);
		// Nothing is sunk on this client, so there is nothing to rise — fail open to visible.
		if (entry == null || entry.state() == State.EMERGING) {
			return;
		}
		ENTRIES.put(entityId, new Entry(State.EMERGING, startGameTime, clock.getAsLong() + EMERGE_TTL_MILLIS));
	}

	public static void reset(int entityId) {
		if (entityId != VfxCue.NO_ANCHOR) {
			ENTRIES.remove(entityId);
		}
	}

	/** 0..1 while sinking, 1 while under, -1 when the entry is absent or expired. */
	public static float sinkProgress(int entityId, long gameTime) {
		Entry entry = entryOrExpired(entityId);
		if (entry == null) {
			return -1.0f;
		}
		return switch (entry.state()) {
			case SINKING -> clamp01((gameTime - entry.startGameTime()) / (float) MegumiProfile.SHADOW_SINK_TICKS);
			case UNDER -> 1.0f;
			case EMERGING -> -1.0f;
		};
	}

	/** 1 at the start of the rise down to 0 when fully risen, -1 when the entry is absent or expired. */
	public static float emergeProgress(int entityId, long gameTime) {
		Entry entry = entryOrExpired(entityId);
		if (entry == null) {
			return -1.0f;
		}
		return switch (entry.state()) {
			case EMERGING -> 1.0f - clamp01((gameTime - entry.startGameTime()) / (float) MegumiProfile.SHADOW_EMERGE_TICKS);
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
