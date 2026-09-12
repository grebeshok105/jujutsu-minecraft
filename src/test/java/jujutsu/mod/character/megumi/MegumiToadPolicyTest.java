package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiToadPolicyTest {
	private static final Vec3 TOAD = new Vec3(0.0, 1.0, 0.0);

	@Test
	void thePullPointsAtTheToadAtExactlyTheProfileSpeed() {
		Vec3 velocity = MegumiToadPolicy.pullVelocity(new Vec3(3.0, 1.0, 4.0), TOAD);
		Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
		assertEquals(MegumiShikigamiProfile.TOAD_TONGUE_PULL_SPEED, horizontal.length(), 1.0E-9);
		assertTrue(velocity.dot(new Vec3(-3.0, 0.0, -4.0)) > 0.0, "the pull must point at the toad");
	}

	@Test
	void thePullPopsTheTargetUpward() {
		Vec3 velocity = MegumiToadPolicy.pullVelocity(new Vec3(3.0, 1.0, 4.0), TOAD);
		assertEquals(MegumiShikigamiProfile.TOAD_TONGUE_PULL_UP, velocity.y, 1.0E-9);
	}

	@Test
	void aTargetOnTopOfTheBodyGetsNoHorizontalPullButKeepsThePop() {
		Vec3 velocity = MegumiToadPolicy.pullVelocity(TOAD, TOAD);
		assertEquals(0.0, velocity.x, 1.0E-9);
		assertEquals(0.0, velocity.z, 1.0E-9);
		assertEquals(MegumiShikigamiProfile.TOAD_TONGUE_PULL_UP, velocity.y, 1.0E-9);
	}

	@Test
	void theTongueReachesItsRangeBoundaryAndNotPastIt() {
		assertTrue(MegumiToadPolicy.canTongue(MegumiShikigamiProfile.TOAD_TONGUE_RANGE));
		assertFalse(MegumiToadPolicy.canTongue(MegumiShikigamiProfile.TOAD_TONGUE_RANGE + 0.01));
	}

	@Test
	void theStrikeLandsOnTheLastWindupTickOnly() {
		assertTrue(MegumiToadPolicy.strikeTickReached(1));
		assertFalse(MegumiToadPolicy.strikeTickReached(2), "still winding up");
		assertFalse(MegumiToadPolicy.strikeTickReached(0), "windup already over");
	}
}
