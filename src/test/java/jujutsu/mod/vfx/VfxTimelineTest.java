package jujutsu.mod.vfx;

import net.minecraft.world.phys.Vec3;

public final class VfxTimelineTest {
	private VfxTimelineTest() {}

	public static void main(String[] args) {
		assertLateCueUsesItsTrueAge();
		assertFutureCueDoesNotRunBackwards();
		assertExpiredCueIsSkipped();
		assertOpeningBeatWindow();
		assertLateCueOffsetsRealtimeChannels();
		assertLateRealtimeWindowCannotEraseFreshState();
		assertStrawDollCueIdsStayStable();
		System.out.println("VfxTimelineTest passed");
	}

	private static void assertLateCueUsesItsTrueAge() {
		VfxCue cue = new VfxCue(NobaraVfxIds.ENLARGE, new Vec3(0.0, 64.0, 0.0), VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 100L, 1L);
		assert VfxTimeline.ageTicks(cue, 106L, 0.5f) == 6.5f : VfxTimeline.ageTicks(cue, 106L, 0.5f);
	}

	private static void assertFutureCueDoesNotRunBackwards() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, Vec3.ZERO, VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 100L, 2L);
		assert VfxTimeline.ageTicks(cue, 98L, 0.75f) == 0.0f : VfxTimeline.ageTicks(cue, 98L, 0.75f);
	}

	private static void assertExpiredCueIsSkipped() {
		VfxCue cue = new VfxCue(NobaraVfxIds.EXPLOSION, Vec3.ZERO, VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 100L, 3L);
		assert !VfxTimeline.isExpired(cue, 117L, 18) : "cue must render through its final active tick";
		assert VfxTimeline.isExpired(cue, 118L, 18) : "cue must stop at its configured duration";
	}

	private static void assertOpeningBeatWindow() {
		assert VfxTimeline.isOpeningBeat(0.0f) : "fresh cues must play one-shot beats";
		assert VfxTimeline.isOpeningBeat(1.999f) : "one-shot beats remain valid through the opening window";
		assert !VfxTimeline.isOpeningBeat(2.0f) : "late cues must not replay elapsed one-shot beats";
	}

	private static void assertLateCueOffsetsRealtimeChannels() {
		assert VfxTimeline.startedAtMillis(1_000L, 4.5f) == 775L : "4.5 ticks must offset realtime channels by 225 ms";
		assert VfxTimeline.startedAtNanos(1_000_000_000L, 4.5f) == 775_000_000L : "4.5 ticks must offset first-person timing by 225 ms";
		assert VfxTimeline.startedAtMillis(1_000L, -2.0f) == 1_000L : "future cues must not start before now";
	}

	private static void assertLateRealtimeWindowCannotEraseFreshState() {
		assert !VfxTimeline.shouldExtendRealtimeWindow(950L, 200, 775L, 150, 1_000L)
				: "an already-expired late cue must not replace a live realtime window";
		assert !VfxTimeline.shouldExtendRealtimeWindow(950L, 200, 900L, 150, 1_000L)
				: "a candidate that ends earlier must not shorten the current effect";
		assert VfxTimeline.shouldExtendRealtimeWindow(950L, 200, 900L, 400, 1_000L)
				: "a candidate that extends the visible window may replace it";
		assert VfxTimeline.shouldExtendRealtimeWindow(0L, 0, 1_000L, 90, 1_000L)
				: "a fresh candidate must start when no prior window exists";
	}

	private static void assertStrawDollCueIdsStayStable() {
		assert NobaraVfxIds.REMNANT_DROP.getPath().equals("nobara/remnant_drop");
		assert NobaraVfxIds.RITUAL_BIND.getPath().equals("nobara/ritual_bind");
		assert NobaraVfxIds.DOLL_STRIKE.getPath().equals("nobara/doll_strike");
		assert NobaraVfxIds.RESONANCE_RELEASE.getPath().equals("nobara/resonance_release");
	}
}
