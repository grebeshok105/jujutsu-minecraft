package jujutsu.mod.vfx;

public final class VfxTimeline {
	private VfxTimeline() {}

	public static float ageTicks(VfxCue cue, long gameTime, float partialTick) {
		return Math.max(0.0f, gameTime - cue.startGameTime() + partialTick);
	}

	public static boolean isExpired(VfxCue cue, long gameTime, int durationTicks) {
		return ageTicks(cue, gameTime, 0.0f) >= durationTicks;
	}
}
