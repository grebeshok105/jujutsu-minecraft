package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import jujutsu.mod.client.vfx.domain.DomainSphereTiming;

class DomainSphereTimingTest {
	private static final double MAX_RADIUS = 30.0;
	private static final double EPSILON = 1.0e-6;

	@Test
	void defaultsCarryThePinnedDurationsAndTotal() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		assertEquals(20, timing.expandTicks());
		assertEquals(220, timing.holdTicks());
		assertEquals(20, timing.fadeTicks());
		assertEquals(260, timing.totalTicks());
		assertEquals(MAX_RADIUS, timing.maxRadius(), EPSILON);
	}

	@Test
	void expansionStartsAtZeroAndReachesMaxRadiusAtTheEnd() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		assertEquals(0.0, timing.radiusAt(0.0f), EPSILON);
		assertEquals(MAX_RADIUS, timing.radiusAt(timing.expandTicks()), EPSILON);
		assertTrue(timing.radiusAt(timing.expandTicks() - 0.5f) < MAX_RADIUS,
				"radius must still be short of max before the expansion ends");
	}

	@Test
	void expansionGrowsMonotonically() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		double previous = timing.radiusAt(0.0f);
		for (int tick = 1; tick <= timing.expandTicks(); tick++) {
			double current = timing.radiusAt(tick);
			assertTrue(current > previous, "radius must grow every tick of the expansion, tick=" + tick);
			previous = current;
		}
	}

	/**
	 * The point of easing: a linear ramp would satisfy every endpoint assertion above. easeOutQuart
	 * is at 0.9375 of max by the midpoint of the expansion, and its per-tick gain keeps shrinking.
	 */
	@Test
	void expansionIsEasedNotLinear() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		float midpoint = timing.expandTicks() * 0.5f;
		assertTrue(timing.radiusAt(midpoint) >= 0.9 * MAX_RADIUS,
				"easeOutQuart must be past 90% at the expansion midpoint, was " + timing.radiusAt(midpoint));

		double previousDelta = Double.POSITIVE_INFINITY;
		for (int tick = (int) Math.ceil(midpoint); tick < timing.expandTicks(); tick++) {
			double delta = timing.radiusAt(tick + 1) - timing.radiusAt(tick);
			assertTrue(delta > 0.0, "second half must still expand, tick=" + tick);
			assertTrue(delta < previousDelta,
					"per-tick gain must decrease over the second half, tick=" + tick + " delta=" + delta);
			previousDelta = delta;
		}
	}

	/**
	 * R6 guard: the radius is a runtime parameter, never baked into the math. A mutant that ignores
	 * {@code maxRadius} and hardcodes the scale passes every single-radius assertion above — this
	 * second radius is the only cheap check that catches it.
	 */
	@Test
	void radiusScalesWithTheRuntimeParameter() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(64.0);
		assertEquals(64.0, timing.radiusAt(timing.expandTicks()), EPSILON);
		assertEquals(0.9375 * 64.0, timing.radiusAt(timing.expandTicks() * 0.5f), EPSILON,
				"easeOutQuart midpoint must scale with the runtime radius");
	}

	@Test
	void radiusStaysConstantThroughTheHold() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		int holdStart = timing.expandTicks();
		int holdEnd = timing.expandTicks() + timing.holdTicks();
		for (int tick = holdStart; tick <= holdEnd; tick++) {
			assertEquals(MAX_RADIUS, timing.radiusAt(tick), EPSILON, "radius must not drift during hold, tick=" + tick);
		}
	}

	/**
	 * The fade is a collapse, not a dissolve: the radius shrinks to zero over the fade window so the
	 * shell, the interior darkening and the ring all converge on the centre point together.
	 */
	@Test
	void radiusCollapsesToZeroOverTheFade() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		int fadeStart = timing.expandTicks() + timing.holdTicks();
		assertEquals(MAX_RADIUS, timing.radiusAt(fadeStart), EPSILON,
				"radius must still be max at the fade boundary");

		double previous = timing.radiusAt(fadeStart);
		for (int tick = fadeStart + 1; tick <= timing.totalTicks(); tick++) {
			double current = timing.radiusAt(tick);
			assertTrue(current < previous, "radius must shrink every fade tick, tick=" + tick);
			previous = current;
		}
		assertEquals(0.0, timing.radiusAt(timing.totalTicks()), EPSILON,
				"radius must reach zero exactly at expiry");
	}

	@Test
	void fadeRunsFromOneToZeroOverTheFadeWindow() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		int fadeStart = timing.expandTicks() + timing.holdTicks();
		assertEquals(1.0f, timing.fadeAt(0.0f), EPSILON);
		assertEquals(1.0f, timing.fadeAt(fadeStart), EPSILON);

		float previous = timing.fadeAt(fadeStart);
		for (int tick = fadeStart + 1; tick <= timing.totalTicks(); tick++) {
			float current = timing.fadeAt(tick);
			assertTrue(current <= previous, "fade must never rise, tick=" + tick);
			previous = current;
		}
		float midpoint = timing.fadeAt(fadeStart + timing.fadeTicks() * 0.5f);
		assertTrue(midpoint > 0.4f && midpoint < 0.6f, "fade midpoint must be near half, was " + midpoint);
		assertEquals(0.0f, timing.fadeAt(timing.totalTicks()), EPSILON);
	}

	/** Second anti-linear guard: a linear fade would be at 0.75 a quarter into the window, smoothstep at 0.84. */
	@Test
	void fadeIsSmoothNotLinear() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		int fadeStart = timing.expandTicks() + timing.holdTicks();
		float quarter = timing.fadeAt(fadeStart + timing.fadeTicks() * 0.25f);
		assertTrue(quarter >= 0.8f, "fade must ease out, was " + quarter + " a quarter into the window");
	}

	@Test
	void progressClampsToUnitRange() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		assertEquals(0.0f, timing.expansionProgress(0.0f), EPSILON);
		assertEquals(0.5f, timing.expansionProgress(timing.expandTicks() * 0.5f), EPSILON);
		assertEquals(1.0f, timing.expansionProgress(timing.totalTicks()), EPSILON);
		assertEquals(1.0f, timing.expansionProgress(timing.totalTicks() * 4.0f), EPSILON);
		assertEquals(0.0f, timing.ageProgress(0.0f), EPSILON);
		assertEquals(1.0f, timing.ageProgress(timing.totalTicks()), EPSILON);
		assertEquals(1.0f, timing.ageProgress(timing.totalTicks() + 500.0f), EPSILON);
	}

	@Test
	void expiresExactlyAtTotalTicks() {
		DomainSphereTiming timing = DomainSphereTiming.defaults(MAX_RADIUS);
		assertFalse(timing.isExpired(timing.totalTicks() - 1.0f));
		assertFalse(timing.isExpired(timing.totalTicks() - 0.001f));
		assertTrue(timing.isExpired(timing.totalTicks()));
		assertTrue(timing.isExpired(timing.totalTicks() + 10.0f));
	}
}
