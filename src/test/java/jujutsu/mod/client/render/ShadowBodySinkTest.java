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
