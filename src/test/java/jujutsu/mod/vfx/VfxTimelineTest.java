package jujutsu.mod.vfx;

import net.minecraft.world.phys.Vec3;

public final class VfxTimelineTest {
	private VfxTimelineTest() {}

	public static void main(String[] args) {
		assertLateCueUsesItsTrueAge();
		assertFutureCueDoesNotRunBackwards();
		assertExpiredCueIsSkipped();
		System.out.println("VfxTimelineTest passed");
	}

	private static void assertLateCueUsesItsTrueAge() {
		VfxCue cue = new VfxCue(NobaraVfxIds.ENLARGE, new Vec3(0.0, 64.0, 0.0), VfxCue.NO_ANCHOR, 1, 100L, 1L);
		assert VfxTimeline.ageTicks(cue, 106L, 0.5f) == 6.5f : VfxTimeline.ageTicks(cue, 106L, 0.5f);
	}

	private static void assertFutureCueDoesNotRunBackwards() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, Vec3.ZERO, VfxCue.NO_ANCHOR, 1, 100L, 2L);
		assert VfxTimeline.ageTicks(cue, 98L, 0.75f) == 0.0f : VfxTimeline.ageTicks(cue, 98L, 0.75f);
	}

	private static void assertExpiredCueIsSkipped() {
		VfxCue cue = new VfxCue(NobaraVfxIds.EXPLOSION, Vec3.ZERO, VfxCue.NO_ANCHOR, 1, 100L, 3L);
		assert !VfxTimeline.isExpired(cue, 117L, 18) : "cue must render through its final active tick";
		assert VfxTimeline.isExpired(cue, 118L, 18) : "cue must stop at its configured duration";
	}
}
