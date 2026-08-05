package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.client.render.ShadowBodySink;

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

	@Test
	void diveAbsentStateIsExactlyZero() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);

		assertEquals(0.0f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, 5_000L), 0.0f);
		assertEquals(0.0f, channel.diveFadeAlpha(DIVE_ENTITY_ID, 5_000L), 0.0f);
	}

	@Test
	void diveSinksToTheFloorAndGlidesToTheUnderHold() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 1_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			assertEquals(0.0f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart), EPSILON);
			float mid = channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS / 2L);
			assertTrue(mid > 0.0f && mid < 0.85f, "mid-sink offset must sit between 0 and the floor: " + mid);
			assertEquals(0.85f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS), EPSILON);

			float gliding = channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 1L);
			assertTrue(gliding > 0.35f && gliding < 0.85f, "the under glide must ease from the bottom: " + gliding);
			assertEquals(0.35f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 3L), EPSILON);
			ShadowBodySink.completeSink(DIVE_ENTITY_ID);
			assertEquals(0.35f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 40L), EPSILON);
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	@Test
	void diveNeverDropsBelowTheFloorAcrossTheWholeSink() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 1_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			for (long tick = 0L; tick <= SINK_TICKS + 10L; tick++) {
				float offset = channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + tick);
				assertTrue(offset >= 0.0f && offset <= 0.8501f,
						"dive offset must stay within 0..0.85 at tick " + tick + ": " + offset);
			}
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	@Test
	void diveRisesFromHalfABlockOverTheEmergeWindowThenClears() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 2_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 3L); // settle on the under hold
			long emergeStart = sinkStart + SINK_TICKS + 3L;
			ShadowBodySink.beginEmerge(DIVE_ENTITY_ID, emergeStart, EMERGE_TICKS);

			assertEquals(0.5f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, emergeStart), EPSILON);
			float mid = channel.diveOffsetBlocks(DIVE_ENTITY_ID, emergeStart + EMERGE_TICKS / 2L);
			assertTrue(mid > 0.0f && mid < 0.5f, "mid-emerge must be rising toward zero: " + mid);
			assertEquals(0.0f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, emergeStart + EMERGE_TICKS), EPSILON);
			assertEquals(0.0f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, emergeStart + EMERGE_TICKS + 5L), EPSILON);
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	@Test
	void diveFadeFollowsTheSameBeatsAsTheCameraOffset() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 3_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			assertEquals(0.0f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart), EPSILON);
			assertEquals(0.75f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart + SINK_TICKS), EPSILON);
			assertEquals(0.25f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 3L), EPSILON);
			assertEquals(0.25f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 40L), EPSILON);

			ShadowBodySink.beginEmerge(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 40L, EMERGE_TICKS);
			assertEquals(0.45f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 40L), EPSILON);
			assertEquals(0.0f, channel.diveFadeAlpha(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 40L + EMERGE_TICKS), EPSILON);
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	@Test
	void cameraAndVeilReadTheSameDiveBeatWithinOneFrame() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 3_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			long under = sinkStart + SINK_TICKS + 3L;
			float offsetFirst = channel.diveOffsetBlocks(DIVE_ENTITY_ID, under);
			float fade = channel.diveFadeAlpha(DIVE_ENTITY_ID, under);
			float offsetSecond = channel.diveOffsetBlocks(DIVE_ENTITY_ID, under);

			assertEquals(offsetFirst, offsetSecond, EPSILON);
			assertEquals(0.35f, offsetFirst, EPSILON);
			assertEquals(0.25f, fade, EPSILON);
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	@Test
	void diveStateDoesNotLeakAcrossCameraEntitiesOrClear() {
		VfxCameraChannel channel = new VfxCameraChannel(() -> 0L);
		long sinkStart = 4_000L;
		ShadowBodySink.beginSink(DIVE_ENTITY_ID, sinkStart, SINK_TICKS);
		try {
			channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS);
			assertEquals(0.0f, channel.diveOffsetBlocks(DIVE_ENTITY_ID + 1, sinkStart + SINK_TICKS), 0.0f);
			assertEquals(0.0f, channel.diveFadeAlpha(DIVE_ENTITY_ID + 1, sinkStart + SINK_TICKS), 0.0f);

			channel.clear();
			// A reset machine re-enters an active sink through the join-mid-under path: settled on the
			// hold, never a stale bottom anchor.
			assertEquals(0.35f, channel.diveOffsetBlocks(DIVE_ENTITY_ID, sinkStart + SINK_TICKS + 30L), EPSILON);
		} finally {
			ShadowBodySink.reset(DIVE_ENTITY_ID);
		}
	}

	private static final int DIVE_ENTITY_ID = 9_001;
	private static final int SINK_TICKS = MegumiProfile.SHADOW_SINK_TICKS;
	private static final int EMERGE_TICKS = MegumiProfile.SHADOW_EMERGE_TICKS;

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
