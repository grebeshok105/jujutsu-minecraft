package jujutsu.mod.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HairpinPackingContractTest {
	@Test
	void depthClampsToSupportedRangeAndRoundTrips() {
		assertEquals(1, NobaraVfxIds.hairpinExplosionDepth(NobaraVfxIds.hairpinExplosionIntensity(0, false)));
		assertEquals(3, NobaraVfxIds.hairpinExplosionDepth(NobaraVfxIds.hairpinExplosionIntensity(4, false)));
		for (int depth = 1; depth <= 3; depth++) {
			assertEquals(depth, NobaraVfxIds.hairpinExplosionDepth(NobaraVfxIds.hairpinExplosionIntensity(depth, false)));
			assertEquals(depth, NobaraVfxIds.hairpinExplosionDepth(NobaraVfxIds.hairpinExplosionIntensity(depth, true)));
		}
	}

	@Test
	void finaleBitIsIndependentAndDoesNotCollide() {
		for (int depth = 1; depth <= 3; depth++) {
			int normal = NobaraVfxIds.hairpinExplosionIntensity(depth, false);
			int finale = NobaraVfxIds.hairpinExplosionIntensity(depth, true);
			assertFalse(NobaraVfxIds.isHairpinFinale(normal));
			assertTrue(NobaraVfxIds.isHairpinFinale(finale));
			assertNotEquals(normal, finale);
		}
	}
}
