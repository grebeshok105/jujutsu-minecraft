package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Max Elephant's presence in numbers (issue #79). The policy is pure on purpose: the area shove and
 * the footprint trigger are the parts a reviewer would otherwise have to launch a game to check,
 * and both are cheap to pin here — the boundary being inclusive, the two periods being separate,
 * the walk trigger ignoring a purely vertical fall, and the push vector never degenerating to NaN
 * when the candidate stands exactly on top of the body.
 */
class MegumiElephantPresencePolicyTest {

	@Test
	void presenceCoversItsRadiusInclusively() {
		double radius = MegumiShikigamiProfile.ELEPHANT_PRESENCE_RADIUS;
		assertTrue(MegumiElephantPresencePolicy.inside(0.0), "a body on top of the elephant is inside");
		assertTrue(MegumiElephantPresencePolicy.inside(radius), "the radius itself is inside");
		assertFalse(MegumiElephantPresencePolicy.inside(radius + 0.01), "past the radius is outside");
	}

	@Test
	void presenceAndFootprintKeepSeparatePeriods() {
		int presence = MegumiShikigamiProfile.ELEPHANT_PRESENCE_PERIOD_TICKS;
		int footprint = MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_PERIOD_TICKS;
		assertTrue(MegumiElephantPresencePolicy.presenceDue(0L), "tick zero is a presence tick");
		assertTrue(MegumiElephantPresencePolicy.presenceDue(presence), "the period repeats");
		assertFalse(MegumiElephantPresencePolicy.presenceDue(presence - 1), "between periods is not due");
		assertTrue(MegumiElephantPresencePolicy.footprintDue(footprint), "the footprint period repeats");
		assertFalse(MegumiElephantPresencePolicy.footprintDue(footprint - 1), "between sweeps is not due");
	}

	@Test
	void footprintNeedsHorizontalMovement() {
		double threshold = MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_MIN_SPEED;
		assertFalse(MegumiElephantPresencePolicy.footprintMoves(Vec3.ZERO), "a standing body sweeps nothing");
		assertFalse(MegumiElephantPresencePolicy.footprintMoves(new Vec3(threshold * 0.99, 0.0, 0.0)),
				"below the threshold is standing still");
		assertTrue(MegumiElephantPresencePolicy.footprintMoves(new Vec3(threshold * 1.01, 0.0, 0.0)),
				"above the threshold is walking");
		assertFalse(MegumiElephantPresencePolicy.footprintMoves(new Vec3(0.0, -2.0, 0.0)),
				"falling is not walking: no ground gets crushed on the way down");
	}

	@Test
	void footprintHeadingIsHorizontalAndSafe() {
		assertEquals(Vec3.ZERO, MegumiElephantPresencePolicy.footprintHeading(Vec3.ZERO),
				"an idle body has no heading");
		assertEquals(Vec3.ZERO, MegumiElephantPresencePolicy.footprintHeading(new Vec3(0.0, -3.0, 0.0)),
				"a purely vertical fall has no heading");
		Vec3 east = MegumiElephantPresencePolicy.footprintHeading(new Vec3(4.0, -0.5, 0.0));
		assertEquals(1.0, east.x, 1.0E-9, "the heading is normalized");
		assertEquals(0.0, east.y, 1.0E-9, "the heading is horizontal");
		assertEquals(0.0, east.z, 1.0E-9, "the heading keeps the walk direction");
	}

	@Test
	void pushLeavesTheBodyHorizontallyAndNeverDegenerates() {
		double speed = MegumiShikigamiProfile.ELEPHANT_PRESENCE_PUSH;
		double lift = MegumiShikigamiProfile.ELEPHANT_PRESENCE_KNOCKBACK;
		Vec3 west = MegumiElephantPresencePolicy.pushVelocity(
				new Vec3(0.0, 0.0, 0.0), new Vec3(-3.0, 1.0, 0.0), speed, lift);
		assertEquals(-speed, west.x, 1.0E-9, "the shove points away from the body");
		assertEquals(lift, west.y, 1.0E-9, "the lift is the profile's");
		assertEquals(0.0, west.z, 1.0E-9, "no push on the axis with no offset");

		Vec3 diagonal = MegumiElephantPresencePolicy.pushVelocity(
				new Vec3(1.0, 0.0, 1.0), new Vec3(2.0, 0.0, 2.0), speed, lift);
		assertEquals(speed, Math.hypot(diagonal.x, diagonal.z), 1.0E-9,
				"the horizontal strength is exactly the profile's push");

		Vec3 stacked = MegumiElephantPresencePolicy.pushVelocity(
				new Vec3(1.0, 0.0, 1.0), new Vec3(1.0, 0.0, 1.0), speed, lift);
		assertEquals(Vec3.ZERO.x, stacked.x, 1.0E-9, "a candidate exactly above the body is not NaN");
		assertEquals(Vec3.ZERO.z, stacked.z, 1.0E-9, "a candidate exactly above the body is not NaN");
		assertEquals(lift, stacked.y, 1.0E-9, "it is lifted instead of shoved");
	}
}
