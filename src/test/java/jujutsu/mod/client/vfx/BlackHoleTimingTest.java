package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jujutsu.mod.client.vfx.blackhole.BlackHoleTiming;

class BlackHoleTimingTest {
	private static final float EPSILON = 1.0e-6f;

	@Test
	void phasesPartitionTheWholeLifetime() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(42L);
		assertEquals(BlackHoleTiming.Phase.PRELUDE, timing.phase(0.0f));
		assertEquals(BlackHoleTiming.Phase.PRELUDE, timing.phase(BlackHoleTiming.PRELUDE_TICKS - 0.5f));
		assertEquals(BlackHoleTiming.Phase.APPEAR, timing.phase(BlackHoleTiming.PRELUDE_TICKS));
		assertEquals(BlackHoleTiming.Phase.STABLE, timing.phase(timing.stableStart()));
		assertEquals(BlackHoleTiming.Phase.DISAPPEAR, timing.phase(timing.disappearStart()));
		assertEquals(BlackHoleTiming.Phase.AFTERMATH, timing.phase(timing.aftermathStart()));
		assertEquals(BlackHoleTiming.Phase.EXPIRED, timing.phase(timing.totalTicks()));
	}

	@Test
	void appearanceReachesFullIntensityAlmostImmediately() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		// The reveal is a snap: within ~13 ticks of the appearance start the hole must already
		// read as complete — no slow build-up of layers.
		float snapPoint = BlackHoleTiming.PRELUDE_TICKS + BlackHoleTiming.APPEAR_TICKS * 0.18f;
		assertTrue(timing.intensity(snapPoint) >= 0.99f,
				"intensity must be ~1.0 right after the reveal snap, was " + timing.intensity(snapPoint));
		assertEquals(1.0f, timing.intensity(timing.stableStart()), EPSILON);
	}

	@Test
	void preludeIsQuietButPresent() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		float mid = timing.intensity(BlackHoleTiming.PRELUDE_TICKS * 0.5f);
		assertTrue(mid > 0.02f && mid < 0.20f,
				"prelude must be a hint, not the event itself, was " + mid);
	}

	@Test
	void disappearanceImplodesTogetherAndStaysGone() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		float mid = timing.disappearStart() + BlackHoleTiming.DISAPPEAR_TICKS * 0.4f;
		// The world-effect dies WITH the object: intensity tracks the collapse, never snaps to 0
		// while the dome is still shrinking — that desync was the "неодновременно" bug.
		assertTrue(timing.intensity(mid) > 0.2f && timing.intensity(mid) < 1.0f,
				"mid-implosion intensity must ride the collapse, was " + timing.intensity(mid));
		assertTrue(timing.jolt(mid) > 0.5f, "the jolt must peak mid-disappearance, was " + timing.jolt(mid));
		assertEquals(0.0f, timing.jolt(timing.stableStart()), EPSILON);
		// After the implosion the object stays collapsed — the aftermath is shimmer only,
		// never the dome again.
		assertEquals(0.0f, timing.collapse(timing.aftermathStart() + 1.0f), EPSILON);
		assertEquals(0.0f, timing.collapse(timing.totalTicks() - 1.0f), EPSILON);
	}

	@Test
	void silenceCoversTheMandatedTwoSeconds() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		assertTrue(timing.silenceActive(timing.disappearStart()));
		assertTrue(timing.silenceActive(timing.aftermathStart() + BlackHoleTiming.SILENCE_TICKS - 1.0f));
		assertFalse(timing.silenceActive(timing.aftermathStart() + BlackHoleTiming.SILENCE_TICKS + 1.0f));
		assertEquals(1.0f, timing.duckAmount(timing.aftermathStart() + 5.0f), EPSILON);
	}

	@Test
	void aftermathReleasesTheDuck() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		float end = timing.totalTicks() - 1.0f;
		assertTrue(timing.duckAmount(end) < 0.05f,
				"duck must release by the end of the aftermath, was " + timing.duckAmount(end));
	}

	@Test
	void burstsAreIrregularAndBounded() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(1234L);
		float max = 0.0f;
		int activeSamples = 0;
		for (float age = timing.stableStart(); age < timing.disappearStart(); age += 1.0f) {
			float b = timing.burstAt(age);
			assertTrue(b >= 0.0f && b <= 1.0f, "burst out of range: " + b);
			max = Math.max(max, b);
			if (b > 0.05f) {
				activeSamples++;
			}
		}
		assertTrue(max > 0.3f, "a 7 s stable phase must produce at least one real burst, max " + max);
		assertTrue(activeSamples < timing.stableTicks() * 0.6f,
				"bursts must be events, not a constant hum");
		// Outside the stable phase there are no bursts.
		assertEquals(0.0f, timing.burstAt(0.0f), EPSILON);
		assertEquals(0.0f, timing.burstAt(timing.aftermathStart()), EPSILON);
	}

	@Test
	void burstsAreDeterministicPerSeed() {
		BlackHoleTiming a = new BlackHoleTiming(140, 999L);
		BlackHoleTiming b = new BlackHoleTiming(140, 999L);
		BlackHoleTiming c = new BlackHoleTiming(140, 1000L);
		float age = a.stableStart() + 37.0f;
		assertEquals(a.burstAt(age), b.burstAt(age), EPSILON);
		// Different seeds produce different schedules somewhere in the window.
		boolean differs = false;
		for (float t = a.stableStart(); t < a.disappearStart(); t += 0.5f) {
			if (Math.abs(a.burstAt(t) - c.burstAt(t)) > 0.01f) {
				differs = true;
				break;
			}
		}
		assertTrue(differs, "different seeds must yield different burst schedules");
	}

	@Test
	void stableTicksAreClamped() {
		assertEquals(BlackHoleTiming.MIN_STABLE_TICKS, new BlackHoleTiming(0, 1L).stableTicks());
		assertEquals(BlackHoleTiming.MAX_STABLE_TICKS, new BlackHoleTiming(99999, 1L).stableTicks());
	}

	@Test
	void expiresExactlyAtTotalTicks() {
		BlackHoleTiming timing = BlackHoleTiming.defaults(7L);
		assertFalse(timing.isExpired(timing.totalTicks() - 0.001f));
		assertTrue(timing.isExpired(timing.totalTicks()));
	}
}
