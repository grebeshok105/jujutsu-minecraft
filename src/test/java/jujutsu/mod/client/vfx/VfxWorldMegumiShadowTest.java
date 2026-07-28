package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import jujutsu.mod.vfx.VfxCue;

class VfxWorldMegumiShadowTest {
	@Test
	void shadowPoolsStayWorldFixedBoundedAndClearWithTheSharedChannel() {
		assertTrue(VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE.isWorldFixed());
		assertEquals(0.26f, VfxWorldChannel.shadowPoolRadius(true, 0.0f));
		assertEquals(0.94f, VfxWorldChannel.shadowPoolRadius(true, 1.0f));
		assertEquals(0.94f, VfxWorldChannel.shadowPoolRadius(false, 0.0f));
		assertEquals(0.26f, VfxWorldChannel.shadowPoolRadius(false, 1.0f));
		assertEquals(1.0f, VfxWorldChannel.shadowPoolAlpha(0.0f));
		assertEquals(0.0f, VfxWorldChannel.shadowPoolAlpha(1.0f));

		VfxWorldChannel channel = new VfxWorldChannel();
		VfxCue cue = new VfxCue(ResourceLocation.parse("jujutsumod:megumi/dogs_summon"),
				Vec3.ZERO, VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 0L, 0L, Vec3.ZERO);
		for (int index = 0; index < 64; index++) {
			channel.triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, 16);
		}
		assertEquals(48, channel.activeEffectCount(),
				"Megumi pools must reuse the shared bounded world-effect list");
		channel.clear();
		assertEquals(0, channel.activeEffectCount(), "Disconnect/level cleanup must remove Megumi pools too");
	}
}
