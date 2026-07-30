package jujutsu.mod.client.vfx.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwapWorldEffectsTest {
	@Test
	void missingDimensionsUseReadablePlayerBounds() {
		assertEquals(0.6f, SwapWorldEffects.silhouetteWidth(0.0), 0.0f);
		assertEquals(1.8f, SwapWorldEffects.silhouetteHeight(0.0), 0.0f);
		assertEquals(0.6f, SwapWorldEffects.silhouetteWidth(0.05), 0.0f);
		assertEquals(0.6f, SwapWorldEffects.silhouetteWidth(0.6), 0.0f);
		assertEquals(1.8f, SwapWorldEffects.silhouetteHeight(1.8), 0.0f);
	}

	@Test
	void oversizedBodiesAreClamped() {
		assertEquals(2.0f, SwapWorldEffects.silhouetteWidth(9.0), 0.0f);
		assertEquals(4.0f, SwapWorldEffects.silhouetteHeight(9.0), 0.0f);
	}

	@Test
	void residueHoldsThenFadesWithClampedMonotonicAlpha() {
		assertEquals(1.0f, SwapWorldEffects.silhouetteAlpha(0.0f), 0.0f);
		assertEquals(1.0f, SwapWorldEffects.silhouetteAlpha(0.25f), 0.0f);
		assertEquals(0.0f, SwapWorldEffects.silhouetteAlpha(1.0f), 0.0f);
		assertEquals(1.0f, SwapWorldEffects.silhouetteAlpha(-1.0f), 0.0f);
		assertEquals(0.0f, SwapWorldEffects.silhouetteAlpha(2.0f), 0.0f);

		float previous = Float.MAX_VALUE;
		for (int step = 0; step <= 20; step++) {
			float alpha = SwapWorldEffects.silhouetteAlpha(step / 20.0f);
			assertTrue(alpha <= previous, "the residue must never brighten again at step " + step);
			assertTrue(alpha >= 0.0f, "the residue must never go negative at step " + step);
			previous = alpha;
		}
	}

	@Test
	void facingKeepsTheSilhouetteReadable() {
		assertEquals(1.0f, SwapWorldEffects.facingScale(0.0f, 0.0f), 1.0E-5f);
		assertEquals(1.0f, SwapWorldEffects.facingScale(180.0f, 0.0f), 1.0E-5f);
		assertEquals(0.45f, SwapWorldEffects.facingScale(90.0f, 0.0f), 1.0E-5f);

		for (int degrees = 0; degrees < 360; degrees++) {
			float scale = SwapWorldEffects.facingScale(degrees, 37.0f);
			assertTrue(scale >= 0.45f && scale <= 1.0f,
					"the figure must stay readable at " + degrees + " degrees");
		}
	}
}
