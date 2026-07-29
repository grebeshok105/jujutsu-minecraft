package jujutsu.mod.client.vfx.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class TodoSwapArrivalPayloadTest {
	@Test
	void readsSpeedWidthHeightFromTheirExistingOffsetComponents() {
		VfxCue cue = cue(new Vec3(2.75, 0.62, 1.91), new Vec3(3.0, 4.0, 0.0));

		TodoSwapArrivalPayload payload = TodoSwapArrivalPayload.from(cue);

		assertEquals(2.75, payload.speed());
		assertEquals(0.62, payload.bodyWidth());
		assertEquals(1.91, payload.bodyHeight());
	}

	@Test
	void preservesCueDirectionAfterCueNormalization() {
		VfxCue cue = cue(new Vec3(1.0, 2.0, 3.0), new Vec3(3.0, 4.0, 0.0));

		TodoSwapArrivalPayload payload = TodoSwapArrivalPayload.from(cue);

		assertEquals(new Vec3(0.6, 0.8, 0.0), payload.direction());
		assertSame(cue.direction(), payload.direction(), "The read model must not rewrite the normalized direction");
	}

	@Test
	void zeroDirectionIsSafeAndUnusualValuesAreNotClamped() {
		VfxCue zero = cue(new Vec3(-4.0, -0.75, 99.0), Vec3.ZERO);
		TodoSwapArrivalPayload payload = TodoSwapArrivalPayload.from(zero);

		assertEquals(-4.0, payload.speed());
		assertEquals(-0.75, payload.bodyWidth());
		assertEquals(99.0, payload.bodyHeight());
		assertEquals(Vec3.ZERO, payload.direction());
	}

	private static VfxCue cue(Vec3 offset, Vec3 direction) {
		return new VfxCue(TodoVfxIds.SWAP_ARRIVAL, new Vec3(8.0, 64.0, -3.0), VfxCue.NO_ANCHOR,
				offset, 1, 120L, 55L, direction);
	}
}
