package jujutsu.mod.fx;

public final class HairpinTimelineTest {
	private HairpinTimelineTest() {}

	public static void main(String[] args) {
		assertPhaseBoundaries();
		assertProgressClamps();
		assertGameTimeClock();
		System.out.println("HairpinTimelineTest passed");
	}

	private static void assertPhaseBoundaries() {
		assert HairpinTimeline.phaseAt(0) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(79) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(80) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(119) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(120) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(299) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(300) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(519) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(520) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1169) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1170) == HairpinTimeline.Phase.DONE;
	}

	private static void assertProgressClamps() {
		assert HairpinTimeline.progressInPhase(-10) == 0.0f;
		assert HairpinTimeline.progressInPhase(0) == 0.0f;
		assert closeTo(HairpinTimeline.progressInPhase(40), 0.5f);
		assert HairpinTimeline.progressInPhase(1170) == 1.0f;
	}

	private static void assertGameTimeClock() {
		long startGameTime = 1200L;
		assert HairpinTimeline.elapsedMillisFromGameTime(startGameTime, 1198L) == 0L;
		assert HairpinTimeline.elapsedMillisFromGameTime(startGameTime, 1201L) == 50L;
		assert HairpinTimeline.elapsedMillisFromGameTime(startGameTime, 1212L) == 600L;
		assert HairpinTimeline.elapsedMillisFromGameTime(startGameTime, 1212L, 0.5f) == 625L;
		assert HairpinTimeline.elapsedMillisFromGameTime(startGameTime, 1212L, 1.8f) == 650L;
		assert HairpinTimeline.phaseAtGameTime(startGameTime, 1212L) == HairpinTimeline.Phase.AFTERGLOW;
	}

	private static boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.0001f;
	}
}
