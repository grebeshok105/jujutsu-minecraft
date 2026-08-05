package jujutsu.mod.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.vfx.VfxCue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure progress/easing math of {@link ShadowBodySink}; the wall clock is injected, no Minecraft boot. */
class ShadowBodySinkTest {
	private static final float EPS = 1.0e-5f;
	private static final int SINK_TICKS = MegumiProfile.SHADOW_SINK_TICKS;
	private static final int EMERGE_TICKS = MegumiProfile.SHADOW_EMERGE_TICKS;

	private final AtomicLong clock = new AtomicLong(0L);

	@BeforeEach
	void bindTestClock() {
		clock.set(0L);
		ShadowBodySink.setClockForTests(clock::get);
	}

	@AfterEach
	void restoreSystemClock() {
		ShadowBodySink.setClockForTests(null);
	}

	@Test
	void sinkProgressStartsAtZeroHitsHalfThenFullAndStaysSunk() {
		ShadowBodySink.beginSink(100, 1_000L, SINK_TICKS);

		assertEquals(0.0f, ShadowBodySink.sinkProgress(100, 1_000L), EPS, "the dive starts at the top");
		assertEquals(0.5f, ShadowBodySink.sinkProgress(100, 1_000L + SINK_TICKS / 2), EPS,
				"halfway through the sink the body is half under");
		assertEquals(1.0f, ShadowBodySink.sinkProgress(100, 1_000L + SINK_TICKS), EPS,
				"the body is fully under at the end of the sink window");
		assertEquals(1.0f, ShadowBodySink.sinkProgress(100, 1_000L + SINK_TICKS + 4), EPS,
				"progress clamps: the body stays fully sunk until a ripple or TTL resolves it");
		assertEquals(-1.0f, ShadowBodySink.emergeProgress(100, 1_000L), EPS,
				"sinking is not emerging");
	}

	@Test
	void completeSinkHoldsTheBodyUnderAndReArmsIdempotently() {
		ShadowBodySink.beginSink(200, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(200);

		assertEquals(1.0f, ShadowBodySink.sinkProgress(200, 999L), EPS, "the hold reports fully under");

		ShadowBodySink.completeSink(200); // idempotent re-arm of the hold TTL
		clock.addAndGet(8L * 50L - 1L);
		assertEquals(1.0f, ShadowBodySink.sinkProgress(200, 999L), EPS, "the re-armed hold is still live");

		clock.addAndGet(1L);
		assertEquals(-1.0f, ShadowBodySink.sinkProgress(200, 999L), EPS,
				"a lost emerge cue fails open to visible once the hold TTL lapses");
	}

	@Test
	void emergeProgressFallsFromOneToZeroThenExpires() {
		ShadowBodySink.beginSink(300, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(300);
		ShadowBodySink.beginEmerge(300, 2_000L, EMERGE_TICKS);

		assertEquals(1.0f, ShadowBodySink.emergeProgress(300, 2_000L), EPS, "the rise starts fully under");
		assertEquals(0.5f, ShadowBodySink.emergeProgress(300, 2_000L + EMERGE_TICKS / 2), EPS,
				"halfway through the emerge the body is half risen");
		assertEquals(0.0f, ShadowBodySink.emergeProgress(300, 2_000L + EMERGE_TICKS), EPS,
				"the body is fully risen at the end of the emerge window");
		assertEquals(-1.0f, ShadowBodySink.sinkProgress(300, 2_000L), EPS,
				"the rise replaces the hold; a body never sinks and rises at once");

		clock.addAndGet(((long) EMERGE_TICKS + 8L) * 50L);
		assertEquals(-1.0f, ShadowBodySink.emergeProgress(300, 2_000L + EMERGE_TICKS), EPS,
				"the entry expires after the emerge window");
	}

	@Test
	void sinkWindowExpiresFailOpenWhenTheFirstRippleNeverArrives() {
		ShadowBodySink.beginSink(400, 0L, SINK_TICKS);

		clock.addAndGet(((long) SINK_TICKS + 8L) * 50L - 1L);
		assertEquals(1.0f, ShadowBodySink.sinkProgress(400, 100L), EPS, "still inside the sink TTL");

		clock.addAndGet(1L);
		assertEquals(-1.0f, ShadowBodySink.sinkProgress(400, 100L), EPS,
				"without a ripple the entry expires and the body is visible again");
	}

	@Test
	void resetDropsTheEntryImmediately() {
		ShadowBodySink.beginSink(500, 0L, SINK_TICKS);
		ShadowBodySink.reset(500);

		assertEquals(-1.0f, ShadowBodySink.sinkProgress(500, 0L), EPS);
		assertEquals(-1.0f, ShadowBodySink.emergeProgress(500, 0L), EPS);
	}

	@Test
	void emergeWithoutAPriorSinkIsIgnored() {
		ShadowBodySink.beginEmerge(600, 0L, EMERGE_TICKS);

		assertEquals(-1.0f, ShadowBodySink.emergeProgress(600, 0L), EPS,
				"nothing sunk on this client, so there is nothing to rise");
		assertEquals(-1.0f, ShadowBodySink.sinkProgress(600, 0L), EPS);
	}

	@Test
	void rippleAfterTheRiseNeverYanksTheBodyBackUnder() {
		ShadowBodySink.beginSink(700, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(700);
		ShadowBodySink.beginEmerge(700, 100L, EMERGE_TICKS);

		ShadowBodySink.completeSink(700); // reordered ripple lands after the rise began

		assertEquals(0.5f, ShadowBodySink.emergeProgress(700, 100L + EMERGE_TICKS / 2), EPS,
				"the rise keeps falling; a late ripple must never recreate the hold");
		assertEquals(-1.0f, ShadowBodySink.sinkProgress(700, 100L), EPS,
				"the reordered ripple must not flip the entry back to sinking");
	}

	@Test
	void rippleBeforeTheDiveSnapsTheBodyUnder() {
		ShadowBodySink.completeSink(800); // late join: the first cue this client sees is a ripple

		assertEquals(1.0f, ShadowBodySink.sinkProgress(800, 12_345L), EPS,
				"with no dive to animate the hold snaps the body fully under");
	}

	@Test
	void reDiveOverALiveHoldRestartsTheDive() {
		ShadowBodySink.beginSink(900, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(900);

		ShadowBodySink.beginSink(900, 50L, SINK_TICKS);

		assertEquals(0.0f, ShadowBodySink.sinkProgress(900, 50L), EPS,
				"a re-dive over the hidden hold restarts from the top");
		assertEquals(1.0f, ShadowBodySink.sinkProgress(900, 50L + SINK_TICKS), EPS);
	}

	@Test
	void emergeFromAPartialSinkRisesFromTheCurrentDepth() {
		ShadowBodySink.beginSink(1_000, 0L, SINK_TICKS);

		// The server cancels the dive halfway down (damage during SINK): depth 0.5 at tick 4.
		ShadowBodySink.beginEmerge(1_000, SINK_TICKS / 2, EMERGE_TICKS);

		assertEquals(0.5f, ShadowBodySink.emergeProgress(1_000, SINK_TICKS / 2), EPS,
				"the rise starts at the interrupted depth, never a snap to fully under");
		assertEquals(0.0f, ShadowBodySink.emergeProgress(1_000, SINK_TICKS / 2 + EMERGE_TICKS / 2), EPS,
				"half the depth costs half the emerge window");
	}

	@Test
	void reCastOverAMidRiseDivesFromTheCurrentDepth() {
		ShadowBodySink.beginSink(1_100, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(1_100);
		ShadowBodySink.beginEmerge(1_100, 100L, EMERGE_TICKS);

		// An instant re-cast catches the body half risen: depth 0.5 at tick 103.
		ShadowBodySink.beginSink(1_100, 100L + EMERGE_TICKS / 2, SINK_TICKS);

		assertEquals(0.5f, ShadowBodySink.sinkProgress(1_100, 100L + EMERGE_TICKS / 2), EPS,
				"the dive resumes at the current depth, never a snap back to the surface");
		assertEquals(1.0f, ShadowBodySink.sinkProgress(1_100, 100L + EMERGE_TICKS / 2 + SINK_TICKS / 2), EPS,
				"half the remaining depth costs half the sink window");
	}

	@Test
	void noAnchorIsNeverTracked() {
		ShadowBodySink.beginSink(VfxCue.NO_ANCHOR, 0L, SINK_TICKS);
		ShadowBodySink.completeSink(VfxCue.NO_ANCHOR);
		ShadowBodySink.beginEmerge(VfxCue.NO_ANCHOR, 0L, EMERGE_TICKS);

		assertEquals(-1.0f, ShadowBodySink.sinkProgress(VfxCue.NO_ANCHOR, 0L), EPS);
		assertEquals(-1.0f, ShadowBodySink.emergeProgress(VfxCue.NO_ANCHOR, 0L), EPS);
	}

	@Test
	void smoothstepIsMonotoneBetweenItsEndpoints() {
		assertEquals(0.0f, ShadowBodySink.smoothstep(0.0f), EPS);
		assertEquals(1.0f, ShadowBodySink.smoothstep(1.0f), EPS);
		assertEquals(0.5f, ShadowBodySink.smoothstep(0.5f), EPS);
		assertTrue(ShadowBodySink.smoothstep(0.25f) < ShadowBodySink.smoothstep(0.5f));
		assertTrue(ShadowBodySink.smoothstep(0.75f) < ShadowBodySink.smoothstep(1.0f));
		assertTrue(ShadowBodySink.smoothstep(-0.5f) == 0.0f);
		assertTrue(ShadowBodySink.smoothstep(1.5f) == 1.0f);
	}
}
