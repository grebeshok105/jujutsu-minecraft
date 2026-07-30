package jujutsu.mod.client.vfx.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowWorldEffectsTest {
	private static final float EPSILON = 1.0E-6f;

	@Test
	void openingRadiusExpandsMonotonically() {
		assertEquals(0.26f, ShadowWorldEffects.shadowPoolRadius(true, 0.0f), EPSILON);
		assertEquals(0.94f, ShadowWorldEffects.shadowPoolRadius(true, 1.0f), EPSILON);
		float previous = ShadowWorldEffects.shadowPoolRadius(true, 0.0f);
		for (int step = 1; step <= 20; step++) {
			float radius = ShadowWorldEffects.shadowPoolRadius(true, step / 20.0f);
			assertTrue(radius >= previous, "opening radius must not decrease");
			previous = radius;
		}
	}

	@Test
	void closingRadiusContractsMonotonically() {
		assertEquals(0.94f, ShadowWorldEffects.shadowPoolRadius(false, 0.0f), EPSILON);
		assertEquals(0.26f, ShadowWorldEffects.shadowPoolRadius(false, 1.0f), EPSILON);
		float previous = ShadowWorldEffects.shadowPoolRadius(false, 0.0f);
		for (int step = 1; step <= 20; step++) {
			float radius = ShadowWorldEffects.shadowPoolRadius(false, step / 20.0f);
			assertTrue(radius <= previous, "closing radius must not increase");
			previous = radius;
		}
	}

	@Test
	void opacityCurvesMatchOpenAndCloseContracts() {
		assertEquals(0.88f, ShadowWorldEffects.shadowPoolOpacity(true, 0.0f), EPSILON);
		assertEquals(0.96f, ShadowWorldEffects.shadowPoolOpacity(true, 1.0f), EPSILON);
		assertEquals(0.96f, ShadowWorldEffects.shadowPoolOpacity(false, 0.0f), EPSILON);
		assertEquals(0.0f, ShadowWorldEffects.shadowPoolOpacity(false, 1.0f), EPSILON);
	}

	@Test
	void progressIsClampedForBothCurves() {
		assertEquals(ShadowWorldEffects.shadowPoolRadius(true, 0.0f), ShadowWorldEffects.shadowPoolRadius(true, -1.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolRadius(false, 1.0f), ShadowWorldEffects.shadowPoolRadius(false, 2.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolOpacity(true, 0.0f), ShadowWorldEffects.shadowPoolOpacity(true, -1.0f), EPSILON);
		assertEquals(ShadowWorldEffects.shadowPoolOpacity(false, 1.0f), ShadowWorldEffects.shadowPoolOpacity(false, 2.0f), EPSILON);
	}
}
