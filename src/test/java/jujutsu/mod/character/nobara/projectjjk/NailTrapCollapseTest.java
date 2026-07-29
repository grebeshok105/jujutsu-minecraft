package jujutsu.mod.character.nobara.projectjjk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.netty.buffer.Unpooled;
import jujutsu.mod.network.VfxCuePayload;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class NailTrapCollapseTest {
	private static final Vec3 SHORT_FROM = new Vec3(12.5, 64.25, -8.0);
	private static final Vec3 SHORT_TO = new Vec3(13.5, 64.75, -7.75);

	@Test
	void shortCollapseKeepsOriginEndpointAndTransportFields() {
		VfxCue cue = NailTrapRuntime.collapseCue(SHORT_FROM, SHORT_TO, 2, 900L, 12345L);

		assertEquals(NobaraVfxIds.NAIL_TRAP_COLLAPSE, cue.effectId());
		assertEquals(SHORT_FROM, cue.origin());
		assertEquals(VfxCue.NO_ANCHOR, cue.anchorEntityId());
		assertEquals(SHORT_TO.subtract(SHORT_FROM), cue.anchorOffset());
		assertEquals(SHORT_TO, cue.origin().add(cue.anchorOffset()));
		assertEquals(SHORT_TO.subtract(SHORT_FROM).normalize(), cue.direction());
		assertEquals(2, cue.intensity());
		assertEquals(900L, cue.startGameTime());
		assertEquals(12345L, cue.seed());
	}

	@Test
	void longCollapseKeepsFullTravelWhileDirectionStaysNormalized() {
		Vec3 from = new Vec3(-40.0, 10.0, 22.0);
		Vec3 to = new Vec3(160.0, 110.0, 122.0);
		Vec3 displacement = to.subtract(from);
		VfxCue cue = NailTrapRuntime.collapseCue(from, to, 4, 1200L, 54321L);

		assertEquals(displacement, cue.anchorOffset());
		assertEquals(to, cue.origin().add(cue.anchorOffset()));
		assertEquals(1.0, cue.direction().length(), 1.0E-9);
		assertNotEquals(displacement.length(), cue.direction().length());
	}

	@Test
	void zeroDistanceCollapseIsZeroVectorWithoutNaN() {
		VfxCue cue = NailTrapRuntime.collapseCue(SHORT_FROM, SHORT_FROM, 1, 1500L, 67890L);

		assertEquals(Vec3.ZERO, cue.anchorOffset());
		assertEquals(Vec3.ZERO, cue.direction());
	}

	@Test
	void productionCollapseCueRoundTripsThroughTheRealPayloadCodec() {
		Vec3 from = new Vec3(3.25, 70.0, -11.5);
		Vec3 to = new Vec3(48.75, 73.5, 9.0);
		VfxCue expected = NailTrapRuntime.collapseCue(from, to, 3, 321L, 9876L);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			VfxCuePayload.STREAM_CODEC.encode(buffer, new VfxCuePayload(expected));
			VfxCue decoded = VfxCuePayload.STREAM_CODEC.decode(buffer).cue();
			assertEquals(expected, decoded);
			assertEquals(to, decoded.origin().add(decoded.anchorOffset()));
			assertEquals(VfxCue.NO_ANCHOR, decoded.anchorEntityId());
			assertEquals(1.0, decoded.direction().length(), 1.0E-9);
		} finally {
			buffer.release();
		}
	}
}
