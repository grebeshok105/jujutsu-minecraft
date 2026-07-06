package jujutsu.mod.fx;

public final class HairpinTimelineTest {
	private HairpinTimelineTest() {}

	public static void main(String[] args) {
		assertPhaseBoundaries();
		assertProgressClamps();
		System.out.println("HairpinTimelineTest passed");
	}

	private static void assertPhaseBoundaries() {
		assert HairpinTimeline.phaseAt(0) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(179) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(180) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(239) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(240) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(559) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(560) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(899) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(900) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1799) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1800) == HairpinTimeline.Phase.DONE;
	}

	private static void assertProgressClamps() {
		assert HairpinTimeline.progressInPhase(-10) == 0.0f;
		assert HairpinTimeline.progressInPhase(0) == 0.0f;
		assert closeTo(HairpinTimeline.progressInPhase(90), 0.5f);
		assert HairpinTimeline.progressInPhase(1800) == 1.0f;
	}

	private static boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.0001f;
	}
}
