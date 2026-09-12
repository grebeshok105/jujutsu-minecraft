package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure locomotion maths of the rabbit swarm (issue #78). */
class MegumiRabbitSwarmPolicyTest {

	@Test
	void driftTargetSitsOnTheRingAroundTheOwner() {
		Vec3 east = MegumiRabbitSwarmPolicy.driftTarget(10.0, 64.0, -5.0, 0.0, 2.0);
		assertEquals(12.0, east.x, 1.0E-6);
		assertEquals(64.0, east.y, 1.0E-6);
		assertEquals(-5.0, east.z, 1.0E-6);
		Vec3 north = MegumiRabbitSwarmPolicy.driftTarget(10.0, 64.0, -5.0, Math.PI / 2.0, 2.0);
		assertEquals(10.0, north.x, 1.0E-6);
		assertEquals(-3.0, north.z, 1.0E-6);
	}

	@Test
	void driftRadiusStaysInsideTheProfileBand() {
		assertEquals(MegumiShikigamiProfile.RABBIT_DRIFT_MIN_RADIUS,
				MegumiRabbitSwarmPolicy.driftRadius(0.0), 1.0E-6);
		assertEquals(MegumiShikigamiProfile.RABBIT_DRIFT_MAX_RADIUS,
				MegumiRabbitSwarmPolicy.driftRadius(1.0), 1.0E-6);
		double middle = MegumiRabbitSwarmPolicy.driftRadius(0.5);
		assertTrue(middle > MegumiShikigamiProfile.RABBIT_DRIFT_MIN_RADIUS
				&& middle < MegumiShikigamiProfile.RABBIT_DRIFT_MAX_RADIUS, "midpoint inside the band");
		// Rolls outside [0, 1) clamp rather than leaving the band (a broken random must not fling bodies).
		assertEquals(MegumiShikigamiProfile.RABBIT_DRIFT_MIN_RADIUS,
				MegumiRabbitSwarmPolicy.driftRadius(-3.0), 1.0E-6);
		assertEquals(MegumiShikigamiProfile.RABBIT_DRIFT_MAX_RADIUS,
				MegumiRabbitSwarmPolicy.driftRadius(7.5), 1.0E-6);
	}

	@Test
	void leashBoundaryDecidesDriftVersusReunion() {
		assertTrue(MegumiRabbitSwarmPolicy.shouldDrift(MegumiShikigamiProfile.RABBIT_DRIFT_LEASH));
		assertFalse(MegumiRabbitSwarmPolicy.shouldDrift(MegumiShikigamiProfile.RABBIT_DRIFT_LEASH + 0.01));
	}

	@Test
	void chaseStopsInsideBumpRange() {
		assertTrue(MegumiRabbitSwarmPolicy.shouldChase(MegumiShikigamiProfile.RABBITS_BUMP_RADIUS + 0.5));
		assertFalse(MegumiRabbitSwarmPolicy.shouldChase(MegumiShikigamiProfile.RABBITS_BUMP_RADIUS));
	}
}
