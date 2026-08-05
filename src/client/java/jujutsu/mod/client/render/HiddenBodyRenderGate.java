package jujutsu.mod.client.render;

import java.util.concurrent.ConcurrentHashMap;
import jujutsu.mod.vfx.VfxCue;

/**
 * Client-side render-hide gate for whole bodies, fed by cue recipes (currently Megumi's shadow move).
 *
 * <p>Entries are entity id to absolute expiry millis. A fresh {@link #markHidden} refreshes the
 * window; {@link #markRevealed} clears it immediately (emerge). Lookups expire lazily, so a lost
 * cue packet fails open: the body becomes visible again, never the other way round.
 */
public final class HiddenBodyRenderGate {
	private static final long MILLIS_PER_TICK = 50L;
	private static final ConcurrentHashMap<Integer, Long> HIDDEN_UNTIL_MILLIS = new ConcurrentHashMap<>();

	private HiddenBodyRenderGate() {}

	public static void markHidden(int entityId, int ttlTicks) {
		if (entityId == VfxCue.NO_ANCHOR || ttlTicks <= 0) {
			return;
		}
		HIDDEN_UNTIL_MILLIS.put(entityId, System.currentTimeMillis() + ttlTicks * MILLIS_PER_TICK);
	}

	public static void markRevealed(int entityId) {
		if (entityId != VfxCue.NO_ANCHOR) {
			HIDDEN_UNTIL_MILLIS.remove(entityId);
		}
	}

	public static boolean isHidden(int entityId) {
		Long untilMillis = HIDDEN_UNTIL_MILLIS.get(entityId);
		if (untilMillis == null) {
			return false;
		}
		if (untilMillis <= System.currentTimeMillis()) {
			HIDDEN_UNTIL_MILLIS.remove(entityId, untilMillis);
			return false;
		}
		return true;
	}
}
