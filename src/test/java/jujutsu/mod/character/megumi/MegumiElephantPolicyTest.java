package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiElephantPolicyTest {
	private static final Vec3 ORIGIN = new Vec3(0.0, 2.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	private static final double LENGTH = MegumiShikigamiProfile.ELEPHANT_JET_LENGTH;
	private static final double HALF = MegumiShikigamiProfile.ELEPHANT_JET_HALF_WIDTH;

	@Test
	void aTargetDownTheJetTakesTheWater() {
		assertTrue(MegumiElephantPolicy.inJetCorridor(new Vec3(5.0, 2.0, 0.0), ORIGIN, EAST, LENGTH, HALF));
		assertTrue(MegumiElephantPolicy.inJetCorridor(ORIGIN, ORIGIN, EAST, LENGTH, HALF),
				"the trunk itself is longitudinal zero, inside the corridor");
	}

	@Test
	void theCorridorEndsAtItsLengthAndWidth() {
		assertFalse(MegumiElephantPolicy.inJetCorridor(new Vec3(LENGTH + 0.01, 2.0, 0.0), ORIGIN, EAST, LENGTH, HALF),
				"beyond the jet's reach");
		assertTrue(MegumiElephantPolicy.inJetCorridor(new Vec3(LENGTH, 2.0, 0.0), ORIGIN, EAST, LENGTH, HALF),
				"the far edge still counts");
		assertFalse(MegumiElephantPolicy.inJetCorridor(new Vec3(5.0, 2.0, HALF + 0.01), ORIGIN, EAST, LENGTH, HALF),
				"too far to the side");
		assertTrue(MegumiElephantPolicy.inJetCorridor(new Vec3(5.0, 2.0, HALF), ORIGIN, EAST, LENGTH, HALF),
				"the side edge still counts");
	}

	@Test
	void nothingSitsBehindTheTrunk() {
		assertFalse(MegumiElephantPolicy.inJetCorridor(new Vec3(-0.5, 2.0, 0.0), ORIGIN, EAST, LENGTH, HALF));
	}

	@Test
	void aDegenerateDirectionContainsNothing() {
		assertFalse(MegumiElephantPolicy.inJetCorridor(new Vec3(5.0, 2.0, 0.0), ORIGIN, Vec3.ZERO, LENGTH, HALF));
	}

	@Test
	void theShoveRunsAlongTheJetOnTheGroundPlane() {
		Vec3 shove = MegumiElephantPolicy.knockbackVector(new Vec3(3.0, -1.0, 0.0));
		assertEquals(1.0, shove.length(), 1.0E-9);
		assertEquals(0.0, shove.y, 1.0E-9);
		assertTrue(shove.dot(new Vec3(1.0, 0.0, 0.0)) > 0.0, "the shove must point down the jet");
	}

	@Test
	void aDegenerateDirectionShovesNowhere() {
		assertEquals(Vec3.ZERO, MegumiElephantPolicy.knockbackVector(Vec3.ZERO));
		assertEquals(Vec3.ZERO, MegumiElephantPolicy.knockbackVector(new Vec3(0.0, 2.0, 0.0)),
				"a purely vertical jet has no horizontal shove");
	}
}
