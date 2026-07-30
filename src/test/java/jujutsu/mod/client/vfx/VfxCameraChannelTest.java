package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

final class VfxCameraChannelTest {
	private static final float EPSILON = 0.0001f;

	@Test
	void publicConstructorUsesTheSystemClock() throws ReflectiveOperationException {
		VfxCameraChannel channel = new VfxCameraChannel();
		Field clockField = VfxCameraChannel.class.getDeclaredField("currentTimeMillis");
		assertTrue(clockField.trySetAccessible());
		LongSupplier clock = (LongSupplier) clockField.get(channel);
		long now = System.currentTimeMillis();

		assertTrue(Math.abs(clock.getAsLong() - now) < 1_000L,
				"the public constructor must bind the production system clock");
	}

	@Test
	void exactStartHasNoCameraOffset() {
		AtomicLong clock = new AtomicLong(1_000L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);

		channel.triggerHeavyImpact(1, 1.0f, 0.0f);

		assertOffsetsAreZero(channel);
		assertFinite(channel);
	}

	@Test
	void oneMillisecondHasNonZeroProgress() {
		AtomicLong clock = new AtomicLong(1_000L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		channel.triggerHeavyImpact(1, 1.0f, 0.0f);

		clock.set(1_001L);

		assertTrue(Math.abs(channel.yawOffset()) > 0.0f);
		assertFinite(channel);
	}

	@Test
	void swapSnapIsActiveThrough239MillisecondsAndExpiresAt240() {
		AtomicLong clock = new AtomicLong(1_000L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		channel.triggerSwapSnap(1, 1.0f, 0.0f);

		clock.set(1_239L);
		assertTrue(Math.abs(channel.fovOffset()) > 0.0f, "the final 170 ms FOV impulse is active at 239 ms");
		assertFinite(channel);

		clock.set(1_240L);
		assertOffsetsAreZero(channel);
		assertFinite(channel);
	}

	@Test
	void negativeInitialAgeClampsToTheTriggerInstant() {
		AtomicLong clock = new AtomicLong(1_000L);
		VfxCameraChannel negativeAge = new VfxCameraChannel(clock::get);
		VfxCameraChannel zeroAge = new VfxCameraChannel(clock::get);
		negativeAge.triggerHeavyImpact(1, 1.0f, -2.0f);
		zeroAge.triggerHeavyImpact(1, 1.0f, 0.0f);

		assertSameOffsets(negativeAge, zeroAge);
		assertFinite(negativeAge);
		assertFinite(zeroAge);

		clock.set(1_001L);
		assertSameOffsets(negativeAge, zeroAge);
		assertTrue(Math.abs(negativeAge.yawOffset()) > 0.0f);
		assertFinite(negativeAge);
		assertFinite(zeroAge);
	}

	@Test
	void lateStartMatchesAnEffectThatStartedTwoTicksEarlier() {
		AtomicLong lateClock = new AtomicLong(900L);
		VfxCameraChannel late = new VfxCameraChannel(lateClock::get);
		late.triggerHeavyImpact(1, 1.0f, 0.0f);
		lateClock.set(1_000L);

		AtomicLong ageClock = new AtomicLong(1_000L);
		VfxCameraChannel aged = new VfxCameraChannel(ageClock::get);
		aged.triggerHeavyImpact(1, 1.0f, 2.0f);

		assertEquals(late.yawOffset(), aged.yawOffset(), EPSILON);
		assertEquals(late.pitchOffset(), aged.pitchOffset(), EPSILON);
		assertEquals(late.fovOffset(), aged.fovOffset(), EPSILON);
		assertFinite(late);
		assertFinite(aged);
	}

	@Test
	void overlappingImpulsesAddRotationalOffsetsWithoutReplacingEitherEffect() {
		AtomicLong clock = new AtomicLong(0L);
		VfxCameraChannel overlap = new VfxCameraChannel(clock::get);
		VfxCameraChannel firstOnly = new VfxCameraChannel(clock::get);
		VfxCameraChannel secondOnly = new VfxCameraChannel(clock::get);

		overlap.triggerSwapSnap(1, 0.25f, 0.0f);
		firstOnly.triggerSwapSnap(1, 0.25f, 0.0f);
		clock.set(40L);
		overlap.triggerHeavyImpact(1, 0.25f, 0.0f);
		secondOnly.triggerHeavyImpact(1, 0.25f, 0.0f);
		clock.set(60L);

		float overlapYaw = overlap.yawOffset();
		float overlapPitch = overlap.pitchOffset();
		float firstYaw = firstOnly.yawOffset();
		float firstPitch = firstOnly.pitchOffset();
		float secondYaw = secondOnly.yawOffset();
		float secondPitch = secondOnly.pitchOffset();
		float overlapFov = overlap.fovOffset();
		float firstFov = firstOnly.fovOffset();
		float secondFov = secondOnly.fovOffset();

		assertEquals(firstYaw + secondYaw, overlapYaw, EPSILON);
		assertEquals(firstPitch + secondPitch, overlapPitch, EPSILON);
		assertTrue(Float.isFinite(overlapFov));
		assertTrue(overlapFov >= -18.0f && overlapFov <= 20.0f);
		assertTrue(Math.abs(overlapFov - firstFov) > EPSILON);
		assertTrue(Math.abs(overlapFov - secondFov) > EPSILON);
		assertFinite(overlap);
		assertFinite(firstOnly);
		assertFinite(secondOnly);
	}

	@Test
	void positiveYawNegativePitchAndUpperFovClampsRemainExact() {
		AtomicLong clock = new AtomicLong(0L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		// Four simultaneous impacts intentionally reach the existing clamp surfaces without retuning curves.
		repeat(4, () -> channel.triggerHeavyImpact(1, 1.0f, 0.0f));
		clock.set(6L);

		assertEquals(9.0f, channel.yawOffset(), 0.0f);
		assertEquals(-7.0f, channel.pitchOffset(), 0.0f);
		assertFinite(channel);

		clock.set(95L);
		assertEquals(20.0f, channel.fovOffset(), 0.0f);
		assertFinite(channel);
	}

	@Test
	void negativeYawPositivePitchAndLowerFovClampsRemainExact() {
		AtomicLong clock = new AtomicLong(0L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		// Four simultaneous Black Flash impulses intentionally reach the opposite clamp surfaces.
		repeat(4, () -> channel.triggerBlackFlash(1, 1.0f, 0.0f));
		clock.set(50L);

		assertEquals(-9.0f, channel.yawOffset(), 0.0f);
		assertEquals(7.0f, channel.pitchOffset(), 0.0f);
		assertEquals(-18.0f, channel.fovOffset(), 0.0f);
		assertFinite(channel);
	}

	@Test
	void relativeRotationalStrengthPreservesEffectOrder() {
		float swap = peak(channel -> channel.triggerSwapSnap(1, 1.0f, 0.0f));
		float explosion = peak(channel -> channel.triggerExplosion(1, 1.0f, 0.0f));
		float heavy = peak(channel -> channel.triggerHeavyImpact(1, 1.0f, 0.0f));
		float blackFlash = peak(channel -> channel.triggerBlackFlash(1, 1.0f, 0.0f));

		assertTrue(swap > 0.0f);
		assertTrue(swap < explosion, () -> strengths(swap, explosion, heavy, blackFlash));
		assertTrue(explosion < heavy, () -> strengths(swap, explosion, heavy, blackFlash));
		assertTrue(heavy < blackFlash, () -> strengths(swap, explosion, heavy, blackFlash));
		assertTrue(swap < 0.5f * heavy, () -> strengths(swap, explosion, heavy, blackFlash));
		assertTrue(swap < 0.25f * blackFlash, () -> strengths(swap, explosion, heavy, blackFlash));
	}

	@Test
	void clearRemovesAllCameraState() {
		AtomicLong clock = new AtomicLong(0L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		channel.triggerHeavyImpact(1, 1.0f, 0.0f);
		clock.set(50L);

		channel.clear();

		assertOffsetsAreZero(channel);
		assertFinite(channel);
	}

	private static float peak(Consumer<VfxCameraChannel> trigger) {
		AtomicLong clock = new AtomicLong(0L);
		VfxCameraChannel channel = new VfxCameraChannel(clock::get);
		trigger.accept(channel);
		float peak = 0.0f;
		// The longest Black Flash FOV lifecycle is 250 ms + 450 ms, so scan the full 0..700 ms window.
		for (long elapsed = 0L; elapsed <= 700L; elapsed++) {
			clock.set(elapsed);
			peak = Math.max(peak, (float) Math.hypot(channel.yawOffset(), channel.pitchOffset()));
		}
		return peak;
	}

	private static String strengths(float swap, float explosion, float heavy, float blackFlash) {
		return "swap=" + swap + ", explosion=" + explosion + ", heavy=" + heavy + ", blackFlash=" + blackFlash;
	}

	private static void repeat(int count, Runnable action) {
		for (int index = 0; index < count; index++) {
			action.run();
		}
	}

	private static void assertOffsetsAreZero(VfxCameraChannel channel) {
		assertEquals(0.0f, channel.yawOffset(), 0.0f);
		assertEquals(0.0f, channel.pitchOffset(), 0.0f);
		assertEquals(0.0f, channel.fovOffset(), 0.0f);
	}

	private static void assertSameOffsets(VfxCameraChannel first, VfxCameraChannel second) {
		assertEquals(first.yawOffset(), second.yawOffset(), EPSILON);
		assertEquals(first.pitchOffset(), second.pitchOffset(), EPSILON);
		assertEquals(first.fovOffset(), second.fovOffset(), EPSILON);
	}

	private static void assertFinite(VfxCameraChannel channel) {
		assertTrue(Float.isFinite(channel.yawOffset()));
		assertTrue(Float.isFinite(channel.pitchOffset()));
		assertTrue(Float.isFinite(channel.fovOffset()));
	}
}
