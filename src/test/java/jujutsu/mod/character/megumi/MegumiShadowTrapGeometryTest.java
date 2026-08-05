package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The trap pool's capture cylinder: a body is gripped while its feet are within the horizontal
 * radius and inside the vertical band. Both bounds are inclusive on purpose — a body standing
 * exactly at the rim is still inside.
 */
class MegumiShadowTrapGeometryTest {
	private static final Vec3 CENTER = new Vec3(0.0, 0.0, 0.0);

	@Test
	void horizontalRadiusIsInclusiveAtTheRim() {
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(MegumiProfile.SHADOW_TRAP_RADIUS, 0.0, 0.0)));
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(0.0, 0.0, MegumiProfile.SHADOW_TRAP_RADIUS)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(MegumiProfile.SHADOW_TRAP_RADIUS + 0.1, 0.0, 0.0)));
	}

	@Test
	void verticalBandReachesOneJumpUpAndOneBlockDown() {
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(0.0, MegumiProfile.SHADOW_TRAP_VERTICAL_REACH, 0.0)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(0.0, MegumiProfile.SHADOW_TRAP_VERTICAL_REACH + 0.1, 0.0)));
		// The runtime's downward reach is a bare -1.0 literal; the test pins it as contract.
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(CENTER, new Vec3(0.0, -1.0, 0.0)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(CENTER, new Vec3(0.0, -1.1, 0.0)));
	}

	@Test
	void diagonalAtTheRimIsInsideAndJustPastItIsNot() {
		double rim = MegumiProfile.SHADOW_TRAP_RADIUS / Math.sqrt(2.0);
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(CENTER, new Vec3(rim, 0.0, rim)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(CENTER, new Vec3(rim + 0.1, 0.0, rim + 0.1)));
	}

	@Test
	void theCylinderIsCentredOnThePoolNotTheWorldOrigin() {
		Vec3 center = new Vec3(12.0, 64.0, -8.0);
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(center, center.add(1.0, 0.5, 1.0)));
		assertTrue(MegumiShadowTrapRuntime.insideCylinder(
				center, new Vec3(center.x + MegumiProfile.SHADOW_TRAP_RADIUS, center.y, center.z)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(
				center, new Vec3(center.x + MegumiProfile.SHADOW_TRAP_RADIUS + 0.1, center.y, center.z)));
	}

	@Test
	void insideTheRadiusButAboveTheBandIsStillOutside() {
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(
				CENTER, new Vec3(1.0, MegumiProfile.SHADOW_TRAP_VERTICAL_REACH + 0.1, 0.0)));
		assertFalse(MegumiShadowTrapRuntime.insideCylinder(CENTER, new Vec3(1.0, -1.5, 0.0)));
	}
}
