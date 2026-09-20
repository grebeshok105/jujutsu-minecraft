package jujutsu.mod.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The pull law of Toad's tongue, tick by tick: the gain per tick, the cap ramp of D9, the steering
 * deflection, and what a released tick gives back. Every rule here runs in plain JUnit — the class
 * under test is pure.
 */
class TonguePullPolicyTest {
	private static final double EPS = 1.0E-6;
	/** An anchor ten blocks along +Z: a horizontal line, the geometry most steering cases use. */
	private static final Vec3 ALONG_Z = new Vec3(0.0, 0.0, 10.0);
	/** An anchor straight overhead: the line with no horizontal direction of its own. */
	private static final Vec3 ABOVE = new Vec3(0.0, 5.0, 0.0);
	private static final Input NONE = Input.EMPTY;
	private static final Input FORWARD = new Input(true, false, false, false, false, false, false);
	private static final Input BACKWARD = new Input(false, true, false, false, false, false, false);
	private static final Input OPPOSITE_LIFT = new Input(true, true, false, false, false, false, false);
	private static final Input LEFT = new Input(false, false, true, false, false, false, false);
	private static final Input RIGHT = new Input(false, false, false, true, false, false, false);
	private static final Input FORWARD_RIGHT = new Input(true, false, false, true, false, false, false);

	@Test
	void capRampsFromTheAttachValueToItsCeilingInFortyHeldTicks() {
		assertEquals(TonguePullPolicy.BASE_SPEED_CAP, TonguePullPolicy.speedCap(0), EPS);
		assertEquals(0.5175, TonguePullPolicy.speedCap(1), EPS);
		assertEquals(0.85, TonguePullPolicy.speedCap(20), EPS);
		// The ramp reaches its ceiling exactly at the documented 40 held ticks...
		assertEquals(TonguePullPolicy.MAX_SPEED_CAP, TonguePullPolicy.speedCap(40), EPS);
		// ...and never grows past it however long the key is held.
		assertEquals(TonguePullPolicy.MAX_SPEED_CAP, TonguePullPolicy.speedCap(400), EPS);
		// A clock that went backwards is a zero-tick hold, not a negative cap.
		assertEquals(TonguePullPolicy.BASE_SPEED_CAP, TonguePullPolicy.speedCap(-7), EPS);
	}

	@Test
	void eachHeldTickAddsTheAccelerationAlongTheLine() {
		// Slow enough that the cap cannot bind, so this reads the raw acceleration.
		Vec3 pulled = TonguePullPolicy.pull(new Vec3(0.0, 0.0, 0.2), Vec3.ZERO, ALONG_Z, 0, NONE);

		assertEquals(0.0, pulled.x, EPS);
		assertEquals(0.0, pulled.y, EPS);
		assertEquals(0.2 + TonguePullPolicy.ACCELERATION_PER_TICK, pulled.z, EPS);
	}

	@Test
	void theCapBoundsTheVelocityAndNoTickAddsMoreThanOneAcceleration() {
		Vec3 velocity = Vec3.ZERO;
		for (int held = 0; held < 60; held++) {
			Vec3 next = TonguePullPolicy.pull(velocity, Vec3.ZERO, ALONG_Z, held, NONE);
			double gain = next.subtract(velocity).length();
			assertTrue(gain <= TonguePullPolicy.ACCELERATION_PER_TICK + EPS,
					"tick " + held + " gained " + gain + " blocks/tick");
			assertTrue(next.length() <= TonguePullPolicy.speedCap(held) + EPS,
					"tick " + held + " ended at " + next.length() + " over cap " + TonguePullPolicy.speedCap(held));
			velocity = next;
		}
		// Held long past the ramp, the body travels at the ceiling and no faster: 1.2 blocks/tick is
		// the number the server's "moved too quickly" watch is calibrated against.
		assertEquals(TonguePullPolicy.MAX_SPEED_CAP, velocity.length(), EPS);
	}

	@Test
	void aFastVelocityIsScaledToTheCapWithoutChangingCourse() {
		Vec3 fast = new Vec3(3.0, 0.0, 0.0);
		Vec3 pulled = TonguePullPolicy.pull(fast, Vec3.ZERO, ALONG_Z, 0, NONE);

		assertEquals(TonguePullPolicy.BASE_SPEED_CAP, pulled.length(), EPS);
		// The clamp is a scale, never a redirection: the course the tick had still points the same way.
		Vec3 expectedCourse = fast.add(new Vec3(0.0, 0.0, TonguePullPolicy.ACCELERATION_PER_TICK)).normalize();
		assertEquals(0.0, angleDegrees(pulled, expectedCourse), 1.0E-4);
	}

	@Test
	void theTongueNeverCancelsAGravityFall() {
		// Anchor under the holder: the pull deepens the fall, it never zeroes it.
		Vec3 downward = TonguePullPolicy.pull(new Vec3(0.0, -0.4, 0.0), Vec3.ZERO, new Vec3(0.0, -5.0, 0.0), 0, NONE);
		assertEquals(-0.4 - TonguePullPolicy.ACCELERATION_PER_TICK, downward.y, EPS);

		// Anchor overhead and a fast fall: the cap may slow the fall down to the cap, but the body is
		// still falling — there is no hidden anti-gravity term anywhere in the law.
		Vec3 overhead = TonguePullPolicy.pull(new Vec3(0.0, -2.0, 0.0), Vec3.ZERO, new Vec3(0.0, 5.0, 0.0), 0, NONE);
		assertEquals(-TonguePullPolicy.BASE_SPEED_CAP, overhead.y, EPS);
		assertTrue(overhead.y < 0.0, "a falling body stays falling under the pull");
	}

	@Test
	void steeringDeflectsThePullByTwentyDegreesInBothPairs() {
		// Line along +Z with the body heading east: W/S aim the pull up and down, A/D swing it
		// sideways — all four bend the same bounded angle off the line.
		Vec3 heading = new Vec3(0.05, 0.0, 0.0);
		assertEquals(TonguePullPolicy.STEER_MAX_DEGREES, deflectionDegrees(heading, FORWARD, ALONG_Z), 1.0E-4);
		assertEquals(TonguePullPolicy.STEER_MAX_DEGREES, deflectionDegrees(heading, BACKWARD, ALONG_Z), 1.0E-4);
		assertEquals(TonguePullPolicy.STEER_MAX_DEGREES, deflectionDegrees(heading, LEFT, ALONG_Z), 1.0E-4);
		assertEquals(TonguePullPolicy.STEER_MAX_DEGREES, deflectionDegrees(heading, RIGHT, ALONG_Z), 1.0E-4);

		// And each key bends its own way: W lifts, S drops, A swings toward -X, D toward +X. The
		// sideways reference is the line's own heading (+Z), whose right is -X.
		assertTrue(addedPull(heading, FORWARD, ALONG_Z).y > 0.0, "W lifts the pull");
		assertTrue(addedPull(heading, BACKWARD, ALONG_Z).y < 0.0, "S drops the pull");
		assertTrue(addedPull(heading, LEFT, ALONG_Z).x > 0.0, "A swings toward +X");
		assertTrue(addedPull(heading, RIGHT, ALONG_Z).x < 0.0, "D swings toward -X");

		// No keys, no deflection; opposite keys cancel; two keys stay inside the single-key bound.
		assertEquals(0.0, deflectionDegrees(heading, NONE, ALONG_Z), EPS);
		assertEquals(0.0, deflectionDegrees(heading, OPPOSITE_LIFT, ALONG_Z), EPS);
		assertTrue(deflectionDegrees(heading, FORWARD_RIGHT, ALONG_Z) <= TonguePullPolicy.STEER_MAX_DEGREES + 1.0E-4);
	}

	@Test
	void aVerticalLineOverARestingBodyDoesNotSteer() {
		// No horizontal reference exists for this geometry, so neither pair has anywhere to bend and
		// the pull stays exactly vertical whatever is held.
		Vec3 pulled = TonguePullPolicy.pull(Vec3.ZERO, Vec3.ZERO, ABOVE, 0, FORWARD_RIGHT);

		assertEquals(0.0, pulled.x, EPS);
		assertEquals(TonguePullPolicy.ACCELERATION_PER_TICK, pulled.y, EPS);
		assertEquals(0.0, pulled.z, EPS);
		// W/S have nothing to lift or drop on a line that is already vertical.
		assertEquals(0.0, deflectionDegrees(Vec3.ZERO, FORWARD, ABOVE), EPS);
	}

	@Test
	void aVerticalLineStillSwingsSidewaysForAMovingBody() {
		// Hanging under a ceiling anchor while moving: the sideways pair takes its reference from the
		// body's own heading (every horizontal direction is perpendicular to a vertical line), so an
		// arc stays steerable. Heading east (+X), whose right is +Z.
		Vec3 heading = new Vec3(0.4, 0.0, 0.0);
		Vec3 swung = addedPull(heading, RIGHT, ABOVE);

		assertEquals(TonguePullPolicy.STEER_MAX_DEGREES, angleDegrees(swung, ABOVE), 1.0E-4);
		assertTrue(swung.z > 0.0, "D swings toward +Z for an eastward heading");
		assertTrue(addedPull(heading, LEFT, ABOVE).z < 0.0, "A swings toward -Z");
	}

	@Test
	void aReleasedTickHandsTheVelocityBackVerbatim() {
		Vec3 velocity = new Vec3(0.31, -0.42, 0.07);

		// Identity, and the very same instance: the travel mixin relies on this to skip the write and
		// the allocation on every tick that no tongue is attached.
		assertSame(velocity, TonguePullPolicy.release(velocity));
	}

	@Test
	void aBodySittingOnItsAnchorHasNoLineToAccelerateAlong() {
		Vec3 velocity = new Vec3(0.2, 0.0, 0.1);

		Vec3 pulled = TonguePullPolicy.pull(velocity, new Vec3(4.0, 64.0, 4.0), new Vec3(4.0, 64.0, 4.0), 12, NONE);

		assertEquals(velocity, pulled);
	}


	/** Deflection of the applied pull from the line to the anchor, in degrees. */
	private static double deflectionDegrees(Vec3 velocity, Input input, Vec3 anchor) {
		return angleDegrees(addedPull(velocity, input, anchor), anchor);
	}

	/** The vector this tick's pull added to the velocity — the whole of the tongue's doing. */
	private static Vec3 addedPull(Vec3 velocity, Input input, Vec3 anchor) {
		return TonguePullPolicy.pull(velocity, Vec3.ZERO, anchor, 0, input).subtract(velocity);
	}

	private static double angleDegrees(Vec3 a, Vec3 b) {
		double cosine = Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0);
		return Math.toDegrees(Math.acos(cosine));
	}

}
