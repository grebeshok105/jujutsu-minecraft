package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiNueBrainTest {
	private static final double DELTA = 1.0E-6;

	@Test
	void eastboundFlightFacesEastNotWest() {
		assertEquals(-90.0, MegumiNueBrain.yawTo(new Vec3(1.0, 0.0, 0.0)), DELTA,
				"the unnegated atan2(x, z) form faces pure-X flight exactly backwards");
	}

	@Test
	void westboundFlightFacesWest() {
		assertEquals(90.0, MegumiNueBrain.yawTo(new Vec3(-1.0, 0.0, 0.0)), DELTA);
	}

	@Test
	void northAndSouthFlightFaceAlongZ() {
		assertEquals(0.0, MegumiNueBrain.yawTo(new Vec3(0.0, 0.0, 1.0)), DELTA);
		assertEquals(180.0, Math.abs(MegumiNueBrain.yawTo(new Vec3(0.0, 0.0, -1.0))), DELTA);
	}

	@Test
	void diagonalFlightBisectsTheAxes() {
		assertEquals(-45.0, MegumiNueBrain.yawTo(new Vec3(1.0, 0.0, 1.0)), DELTA);
		assertEquals(45.0, MegumiNueBrain.yawTo(new Vec3(-1.0, 0.0, 1.0)), DELTA);
	}

	@Test
	void verticalVelocityHasNoFacing() {
		assertTrue(Float.isNaN(MegumiNueBrain.yawTo(new Vec3(0.0, 1.0, 0.0))),
				"a pure-vertical dive keeps its current facing instead of snapping");
	}

	@Test
	void altitudeChangeDoesNotAffectTheYaw() {
		assertEquals(-90.0, MegumiNueBrain.yawTo(new Vec3(1.0, -5.0, 0.0)), DELTA,
				"only the horizontal components steer the model");
	}
}
