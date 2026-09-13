package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The grab kit's pure half: reach, hold length, pin point, throw direction and the leash. Every
 * assertion here is about a number rule, never about a caller — the brain's half is covered by the
 * in-world game tests.
 */
class MegumiToadPolicyTest {
	private static final Vec3 TOAD = new Vec3(0.0, 1.0, 0.0);
	private static final double EPS = 1.0E-9;

	@Test
	void canGrabOnlyInsideReach() {
		assertTrue(MegumiToadPolicy.canGrab(MegumiShikigamiProfile.TOAD_GRAB_RANGE));
		assertFalse(MegumiToadPolicy.canGrab(MegumiShikigamiProfile.TOAD_GRAB_RANGE + 0.01));
	}

	@Test
	void aHeavierVictimIsHeldShorter() {
		int light = MegumiToadPolicy.holdTicksFor(10.0, 1.0, false);
		int medium = MegumiToadPolicy.holdTicksFor(40.0, 1.0, false);
		int heavy = MegumiToadPolicy.holdTicksFor(70.0, 1.0, false);
		assertTrue(light > medium && medium > heavy, "hold must shrink as the victim gets heavier");
	}

	@Test
	void aBulkierVictimIsHeldShorter() {
		int small = MegumiToadPolicy.holdTicksFor(20.0, 1.0, false);
		int bulky = MegumiToadPolicy.holdTicksFor(20.0, 3.0, false);
		assertTrue(bulky < small, "hold must shrink as the victim gets bulkier");
	}

	@Test
	void theHoldAlwaysLandsInsideTheHardBand() {
		for (double health : new double[] {0.0, 1.0, 20.0, 200.0, 10_000.0}) {
			for (double volume : new double[] {0.0, 1.0, 9.0, 1_000.0}) {
				int ticks = MegumiToadPolicy.holdTicksFor(health, volume, false);
				assertTrue(ticks >= MegumiShikigamiProfile.TOAD_GRAB_HOLD_MIN
								&& ticks <= MegumiShikigamiProfile.TOAD_GRAB_HOLD_MAX,
						"hold out of band for hp=" + health + " volume=" + volume + ": " + ticks);
			}
		}
	}

	@Test
	void playersKeepTheirHoldRegardlessOfHitboxVolume() {
		assertEquals(MegumiToadPolicy.holdTicksFor(20.0, 0.0, true),
				MegumiToadPolicy.holdTicksFor(20.0, 5.0, true), "players are exempt from the size penalty");
		assertTrue(MegumiToadPolicy.holdTicksFor(20.0, 5.0, true)
						> MegumiToadPolicy.holdTicksFor(20.0, 5.0, false),
				"a bulky mob of the same health must be held shorter than a player");
	}

	@Test
	void theAnchorHangsInFrontOfTheBodyAtItsOwnFeetLevel() {
		Vec3 look = new Vec3(0.0, 0.7, 1.0);
		Vec3 anchor = MegumiToadPolicy.anchor(TOAD, look, MegumiShikigamiProfile.TOAD_GRIP_OFFSET);
		assertEquals(TOAD.y, anchor.y, EPS, "the pin keeps the body's own level");
		assertEquals(MegumiShikigamiProfile.TOAD_GRIP_OFFSET,
				Math.hypot(anchor.x - TOAD.x, anchor.z - TOAD.z), EPS);
		assertTrue(anchor.z > TOAD.z, "the anchor sits where the body is looking");
	}

	@Test
	void aDegenerateLookFallsBackToTheBodyPosition() {
		Vec3 anchor = MegumiToadPolicy.anchor(TOAD, new Vec3(0.0, 1.0, 0.0), 1.2);
		assertEquals(TOAD.x, anchor.x, EPS);
		assertEquals(TOAD.z, anchor.z, EPS);
	}

	@Test
	void theThrowPointsAwayFromTheOwnerAtExactlyTheThrowSpeed() {
		Vec3 velocity = MegumiToadPolicy.throwVelocity(new Vec3(3.0, 1.0, 4.0), TOAD, 1.6, 0.35);
		Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
		assertEquals(1.6, horizontal.length(), EPS);
		assertTrue(velocity.dot(new Vec3(-3.0, 0.0, -4.0)) > 0.0, "the throw must point away from the owner");
		assertEquals(0.35, velocity.y, EPS);
	}

	@Test
	void aBodyOnTopOfTheOwnerIsOnlyLifted() {
		Vec3 velocity = MegumiToadPolicy.throwVelocity(TOAD, TOAD, 1.6, 0.35);
		assertEquals(0.0, velocity.x, EPS);
		assertEquals(0.0, velocity.z, EPS);
		assertEquals(0.35, velocity.y, EPS);
	}

	@Test
	void theLeashBreaksPastItsRangeAndHoldsOnTheBoundary() {
		double range = MegumiShikigamiProfile.TOAD_GRAB_BIND_RANGE;
		assertFalse(MegumiToadPolicy.bindBroken(range, range), "exactly on the leash still holds");
		assertTrue(MegumiToadPolicy.bindBroken(range + 0.01, range));
		assertFalse(MegumiToadPolicy.bindBroken(0.0, range));
	}

	@Test
	void theTongueCommitsOnTheLastWindupTickOnly() {
		assertTrue(MegumiToadPolicy.strikeTickReached(1));
		assertFalse(MegumiToadPolicy.strikeTickReached(2), "still winding up");
		assertFalse(MegumiToadPolicy.strikeTickReached(0), "windup already over");
	}
}
