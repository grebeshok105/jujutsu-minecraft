package jujutsu.mod.client.character.todo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.VfxCue;

/**
 * Client-only cache for Todo's staged Rhythm indicator.
 *
 * <p>The server sends a caster-only {@code rhythm_state} cue whenever the authoritative state
 * changes. Its x offset is the current Beat (0..4), while its y offset is the revised window's
 * remaining ticks. The deadline is reconstructed once from that cue and then counted against the
 * local client level clock; no heartbeat packet is needed while Revised is active.
 */
public final class TodoRhythmClientState {
	private static final int PEAK_BEAT = 4;
	private static final int REVISED_TTL_TICKS = 120;
	private static final int BEAT_TTL_TICKS = 130;
	private static final long UNSET = Long.MIN_VALUE;

	private static int beat;
	private static long beatExpiresAtClientTick = UNSET;
	private static long revisedEndsAtClientTick = UNSET;
	private static long newestCueGameTime = UNSET;

	private TodoRhythmClientState() {}

	/**
	 * Applies one authoritative rhythm cue. The cue's offset is deliberately treated as data only;
	 * the client never derives Beat from local casts or guesses at server state.
	 */
	public static synchronized void onCue(VfxCue cue) {
		if (cue == null || cue.anchorOffset() == null || cue.startGameTime() < newestCueGameTime) {
			return;
		}
		Vec3 offset = cue.anchorOffset();
		beat = clamp((int) Math.round(offset.x), 0, PEAK_BEAT);
		long revisedRemaining = Math.max(0L, Math.round(offset.y));
		long cueGameTime = cue.startGameTime();
		beatExpiresAtClientTick = cueGameTime + BEAT_TTL_TICKS;
		revisedEndsAtClientTick = revisedRemaining > 0L
				? cueGameTime + revisedRemaining
				: UNSET;
		newestCueGameTime = cueGameTime;
	}

	/**
	 * Returns the last cue-fed Beat. A missed expiry cue cannot leave a permanent HUD chip: after the
	 * short grace TTL the staged indicator falls back to Beat 0.
	 */
	public static synchronized int beat() {
		long now = clientGameTime();
		if (now != UNSET && beatExpiresAtClientTick != UNSET && now >= beatExpiresAtClientTick) {
			beat = 0;
			beatExpiresAtClientTick = UNSET;
			revisedEndsAtClientTick = UNSET;
		}
		return beat;
	}

	/**
	 * Returns Revised's locally derived remaining ticks. This is intentionally calculated from the
	 * stored deadline each read, so the HUD remains accurate at tick 119 even when no new cue arrives.
	 */
	public static synchronized int revisedTicks() {
		if (revisedEndsAtClientTick == UNSET) {
			return 0;
		}
		long now = clientGameTime();
		if (now == UNSET) {
			return REVISED_TTL_TICKS;
		}
		long remaining = revisedEndsAtClientTick - now;
		if (remaining <= 0L) {
			revisedEndsAtClientTick = UNSET;
			return 0;
		}
		return (int) Math.min(REVISED_TTL_TICKS, remaining);
	}

	/** Clears all cue-fed state on disconnect or client-world replacement. */
	public static synchronized void clear() {
		beat = 0;
		beatExpiresAtClientTick = UNSET;
		revisedEndsAtClientTick = UNSET;
		newestCueGameTime = UNSET;
	}

	private static long clientGameTime() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? UNSET : client.level.getGameTime();
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
