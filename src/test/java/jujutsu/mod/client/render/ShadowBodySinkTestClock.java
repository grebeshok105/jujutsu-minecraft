package jujutsu.mod.client.render;

import java.util.function.LongSupplier;

/**
 * Test-only bridge so tests outside this package (the camera channel's dive block) can pin the
 * sink cache's TTL wall clock instead of racing the real one. Mirrors the package-private seam
 * {@link ShadowBodySink#setClockForTests}.
 */
public final class ShadowBodySinkTestClock {

	private ShadowBodySinkTestClock() {
	}

	/** Pins the TTL clock to the supplier. */
	public static void set(LongSupplier clock) {
		ShadowBodySink.setClockForTests(clock);
	}

	/** Restores the real system clock. */
	public static void reset() {
		ShadowBodySink.setClockForTests(null);
	}
}
