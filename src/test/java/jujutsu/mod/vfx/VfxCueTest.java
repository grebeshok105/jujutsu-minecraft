package jujutsu.mod.vfx;

import io.netty.buffer.Unpooled;
import jujutsu.mod.network.VfxCuePayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public final class VfxCueTest {
	private VfxCueTest() {}

	public static void main(String[] args) {
		assertCuePreservesSharedTransportFields();
		assertNobaraEffectIdsStayStable();
		assertPayloadRoundTripsCue();
		System.out.println("VfxCueTest passed");
	}

	private static void assertCuePreservesSharedTransportFields() {
		Vec3 origin = new Vec3(12.5, 64.25, -8.0);
		Vec3 anchorOffset = new Vec3(0.25, 1.5, -0.75);
		VfxCue cue = new VfxCue(NobaraVfxIds.ENLARGE, origin, 42, anchorOffset, 3, 900L, 12345L);

		assert cue.effectId().equals(NobaraVfxIds.ENLARGE) : cue.effectId();
		assert cue.origin().equals(origin) : cue.origin();
		assert cue.anchorEntityId() == 42 : cue.anchorEntityId();
		assert cue.anchorOffset().equals(anchorOffset) : cue.anchorOffset();
		assert cue.intensity() == 3 : cue.intensity();
		assert cue.startGameTime() == 900L : cue.startGameTime();
		assert cue.seed() == 12345L : cue.seed();
		assert VfxCue.NO_ANCHOR == -1 : VfxCue.NO_ANCHOR;
	}

	private static void assertNobaraEffectIdsStayStable() {
		assert NobaraVfxIds.HAMMER.getPath().equals("nobara/hammer") : NobaraVfxIds.HAMMER;
		assert NobaraVfxIds.RESONANCE_STRIKE.getPath().equals("nobara/resonance_strike") : NobaraVfxIds.RESONANCE_STRIKE;
		assert NobaraVfxIds.FIRST_PERSON_SNAP.getPath().equals("nobara/first_person_snap") : NobaraVfxIds.FIRST_PERSON_SNAP;
	}

	private static void assertPayloadRoundTripsCue() {
		VfxCue expected = new VfxCue(NobaraVfxIds.EXPLOSION, new Vec3(-4.5, 70.0, 2.25), 91, new Vec3(0.0, 1.62, 0.0), 8, 321L, 9876L);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

		VfxCuePayload.STREAM_CODEC.encode(buffer, new VfxCuePayload(expected));
		VfxCue actual = VfxCuePayload.STREAM_CODEC.decode(buffer).cue();

		assert actual.equals(expected) : actual;
	}
}
