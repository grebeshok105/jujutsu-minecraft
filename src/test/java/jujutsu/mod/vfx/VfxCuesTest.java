package jujutsu.mod.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import jujutsu.mod.network.VfxCuePayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class VfxCuesTest {
	private static final Vec3 ORIGIN = new Vec3(12.5, 64.25, -8.0);
	private static final int INTENSITY = 3;
	private static final long GAME_TIME = 900L;
	private static final long SEED = 12345L;

	@Test
	void worldFixedKeepsWorldOriginAndSharedFields() {
		VfxCue cue = VfxCues.worldFixed(NobaraVfxIds.HAMMER, ORIGIN, INTENSITY, GAME_TIME, SEED);

		assertEquals(NobaraVfxIds.HAMMER, cue.effectId());
		assertEquals(ORIGIN, cue.origin());
		assertEquals(VfxCue.NO_ANCHOR, cue.anchorEntityId());
		assertEquals(Vec3.ZERO, cue.anchorOffset());
		assertEquals(INTENSITY, cue.intensity());
		assertEquals(GAME_TIME, cue.startGameTime());
		assertEquals(SEED, cue.seed());
		assertEquals(Vec3.ZERO, cue.direction());
	}

	@Test
	void worldFixedDirectedNormalizesOrientationWithoutAnOffset() {
		Vec3 inputDirection = new Vec3(8.0, 0.0, 0.0);
		VfxCue cue = VfxCues.worldFixedDirected(NobaraVfxIds.HAMMER, ORIGIN, INTENSITY, GAME_TIME, SEED, inputDirection);

		assertEquals(ORIGIN, cue.origin());
		assertEquals(VfxCue.NO_ANCHOR, cue.anchorEntityId());
		assertEquals(Vec3.ZERO, cue.anchorOffset());
		assertEquals(new Vec3(1.0, 0.0, 0.0), cue.direction());
	}

	@Test
	void worldFixedDisplacementKeepsFullTravelOutsideNormalizedDirection() {
		Vec3 displacement = new Vec3(8.0, 3.0, -4.0);
		VfxCue cue = VfxCues.worldFixedDisplacement(NobaraVfxIds.EXPLOSION, ORIGIN, INTENSITY, GAME_TIME, SEED, displacement);

		assertEquals(displacement, cue.anchorOffset());
		assertEquals(new Vec3(20.5, 67.25, -12.0), ORIGIN.add(cue.anchorOffset()));
		assertEquals(1.0, cue.direction().length(), 1.0E-9);
		assertNotEquals(displacement.length(), cue.direction().length());
	}

	@Test
	void zeroWorldFixedDisplacementHasNoDirection() {
		VfxCue cue = VfxCues.worldFixedDisplacement(NobaraVfxIds.EXPLOSION, ORIGIN, INTENSITY, GAME_TIME, SEED, Vec3.ZERO);

		assertEquals(Vec3.ZERO, cue.anchorOffset());
		assertEquals(Vec3.ZERO, cue.direction());
	}

	@Test
	void anchoredComputesOffsetFromAnchorPosition() {
		Vec3 anchorPosition = new Vec3(10.0, 63.0, -12.0);
		VfxCue cue = VfxCues.anchored(NobaraVfxIds.ENLARGE, ORIGIN, 42, anchorPosition, INTENSITY, GAME_TIME, SEED);

		assertEquals(42, cue.anchorEntityId());
		assertEquals(ORIGIN.subtract(anchorPosition), cue.anchorOffset());
		assertEquals(ORIGIN, anchorPosition.add(cue.anchorOffset()));
		assertEquals(INTENSITY, cue.intensity());
		assertEquals(GAME_TIME, cue.startGameTime());
		assertEquals(SEED, cue.seed());
	}

	@Test
	void anchoredWithOffsetPreservesAnExplicitTransportOffset() {
		Vec3 offset = new Vec3(0.0, 1.4, 0.0);
		VfxCue cue = VfxCues.anchoredWithOffset(NobaraVfxIds.ENLARGE, ORIGIN, 42, offset,
				INTENSITY, GAME_TIME, SEED);

		assertEquals(42, cue.anchorEntityId());
		assertEquals(offset, cue.anchorOffset());
		assertEquals(INTENSITY, cue.intensity());
	}

	@Test
	void anchoredDirectedPreservesAnchorOffsetAndNormalizesOrientation() {
		Vec3 anchorPosition = new Vec3(10.0, 63.0, -12.0);
		VfxCue cue = VfxCues.anchoredDirected(
				NobaraVfxIds.ENLARGE, ORIGIN, 42, anchorPosition, INTENSITY, GAME_TIME, SEED, new Vec3(0.0, 4.0, 0.0));

		assertEquals(42, cue.anchorEntityId());
		assertEquals(ORIGIN.subtract(anchorPosition), cue.anchorOffset());
		assertEquals(new Vec3(0.0, 1.0, 0.0), cue.direction());
	}

	@Test
	void anchoredFactoriesRejectTheWorldFixedSentinel() {
		assertThrows(IllegalArgumentException.class, () -> VfxCues.anchored(
				NobaraVfxIds.ENLARGE, ORIGIN, VfxCue.NO_ANCHOR, Vec3.ZERO, INTENSITY, GAME_TIME, SEED));
		assertThrows(IllegalArgumentException.class, () -> VfxCues.anchoredDirected(
				NobaraVfxIds.ENLARGE, ORIGIN, VfxCue.NO_ANCHOR, Vec3.ZERO, INTENSITY, GAME_TIME, SEED, Vec3.ZERO));
		assertThrows(IllegalArgumentException.class, () -> VfxCues.anchoredWithOffset(
				NobaraVfxIds.ENLARGE, ORIGIN, VfxCue.NO_ANCHOR, Vec3.ZERO, INTENSITY, GAME_TIME, SEED));
	}

	@Test
	void intensityIsClampedToTheExistingMinimum() {
		assertEquals(1, VfxCues.worldFixed(NobaraVfxIds.HAMMER, ORIGIN, 0, GAME_TIME, SEED).intensity());
		assertEquals(1, VfxCues.worldFixed(NobaraVfxIds.HAMMER, ORIGIN, -4, GAME_TIME, SEED).intensity());
		assertEquals(INTENSITY, VfxCues.worldFixed(NobaraVfxIds.HAMMER, ORIGIN, INTENSITY, GAME_TIME, SEED).intensity());
		assertEquals(128, VfxCues.worldFixed(NobaraVfxIds.HAMMER, ORIGIN, 128, GAME_TIME, SEED).intensity());
	}

	@Test
	void displacementCueRoundTripsThroughTheRealPayloadCodec() {
		// The legacy JavaExec test covers raw VfxCue construction; this pins the factory's displacement shape.
		VfxCue expected = VfxCues.worldFixedDisplacement(
				NobaraVfxIds.EXPLOSION, ORIGIN, 8, 321L, 9876L, new Vec3(8.0, 3.0, -4.0));
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			VfxCuePayload.STREAM_CODEC.encode(buffer, new VfxCuePayload(expected));
			assertEquals(expected, VfxCuePayload.STREAM_CODEC.decode(buffer).cue());
		} finally {
			buffer.release();
		}
	}
}
