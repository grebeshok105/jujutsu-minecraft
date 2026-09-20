package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MegumiShikigamiProfileTest {
	@Test
	void theSharedSicSurfaceStaysInOnePlace() {
		assertEquals(15.0, MegumiShikigamiProfile.SIC_RANGE);
		assertEquals(30, MegumiShikigamiProfile.SIC_COOLDOWN_TICKS);
	}

	@Test
	void theNueBlockIsTheTuningSurfaceForTheFlyer() {
		assertEquals(24.0, MegumiShikigamiProfile.NUE_HEALTH);
		assertEquals(4.0, MegumiShikigamiProfile.NUE_ATTACK_DAMAGE);
		assertEquals(0.38, MegumiShikigamiProfile.NUE_MOVEMENT_SPEED);
		assertEquals(16, MegumiShikigamiProfile.NUE_MATERIALIZE_TICKS);
		assertEquals(12, MegumiShikigamiProfile.NUE_RECALL_TICKS);
		assertEquals(24.0, MegumiShikigamiProfile.NUE_DIVE_TRIGGER_RANGE);
		assertEquals(0.55, MegumiShikigamiProfile.NUE_DIVE_SPEED);
		assertEquals(40, MegumiShikigamiProfile.NUE_DIVE_TIMEOUT_TICKS);
		assertEquals(1.6, MegumiShikigamiProfile.NUE_IMPACT_RADIUS);
		assertEquals(5.0, MegumiShikigamiProfile.NUE_DIVE_DAMAGE);
		assertEquals(60, MegumiShikigamiProfile.NUE_CHARGE_COOLDOWN_TICKS);
	}

	@Test
	void theToadBlockIsTheTuningSurfaceForTheGrab() {
		assertEquals(80.0, MegumiShikigamiProfile.TOAD_HEALTH);
		assertEquals(4.0, MegumiShikigamiProfile.TOAD_ATTACK_DAMAGE);
		assertEquals(0.22, MegumiShikigamiProfile.TOAD_SPEED);
		assertEquals(6.0, MegumiShikigamiProfile.TOAD_FOLLOW_START);
		assertEquals(2.5, MegumiShikigamiProfile.TOAD_FOLLOW_STOP);
		assertEquals(16, MegumiShikigamiProfile.TOAD_MATERIALIZE_TICKS);
		assertEquals(12, MegumiShikigamiProfile.TOAD_RECALL_TICKS);
		assertEquals(12.0, MegumiShikigamiProfile.TOAD_GRAB_RANGE);
		assertEquals(6, MegumiShikigamiProfile.TOAD_GRAB_WINDUP_TICKS);
		assertEquals(200, MegumiShikigamiProfile.TOAD_GRAB_COOLDOWN_TICKS);
		assertEquals(90, MegumiShikigamiProfile.TOAD_GRAB_HOLD_BASE);
		assertEquals(60, MegumiShikigamiProfile.TOAD_GRAB_HOLD_MIN);
		assertEquals(100, MegumiShikigamiProfile.TOAD_GRAB_HOLD_MAX);
		assertEquals(1.6, MegumiShikigamiProfile.TOAD_THROW_SPEED);
		assertEquals(0.35, MegumiShikigamiProfile.TOAD_THROW_LIFT);
		assertEquals(1.2, MegumiShikigamiProfile.TOAD_GRIP_OFFSET);
	}

	@Test
	void theToadHoldBandIsAWindowNotAValue() {
		// The policy clamps into [MIN, MAX]; a base outside its own band would silently mean the
		// clamp, not the number, is the tuning surface.
		assertTrue(MegumiShikigamiProfile.TOAD_GRAB_HOLD_MIN <= MegumiShikigamiProfile.TOAD_GRAB_HOLD_BASE,
				"base is inside the clamp band");
		assertTrue(MegumiShikigamiProfile.TOAD_GRAB_HOLD_BASE <= MegumiShikigamiProfile.TOAD_GRAB_HOLD_MAX,
				"base is inside the clamp band");
		assertTrue(MegumiShikigamiProfile.TOAD_GRAB_HOLD_MIN < MegumiShikigamiProfile.TOAD_GRAB_HOLD_MAX,
				"the band has room to move in");
	}

	@Test
	void theElephantBlockIsTheTuningSurfaceForThePresence() {
		assertEquals(3.5, MegumiShikigamiProfile.ELEPHANT_PRESENCE_RADIUS);
		assertEquals(10, MegumiShikigamiProfile.ELEPHANT_PRESENCE_PERIOD_TICKS);
		assertEquals(1.0, MegumiShikigamiProfile.ELEPHANT_PRESENCE_DAMAGE);
		assertEquals(1.1, MegumiShikigamiProfile.ELEPHANT_PRESENCE_KNOCKBACK);
		assertEquals(0.7, MegumiShikigamiProfile.ELEPHANT_PRESENCE_PUSH);
		assertEquals(100, MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS);
		assertEquals(10, MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_PERIOD_TICKS);
		assertEquals(0.05, MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_MIN_SPEED);
		assertEquals(3, MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_BUDGET);
	}

	@Test
	void losingAShikigamiAlwaysCostsMoreThanRecallingIt() {
		for (MegumiShikigami type : MegumiShikigami.values()) {
			assertTrue(MegumiShikigamiProfile.deathCooldownTicks(type)
					> MegumiShikigamiProfile.recallCooldownTicks(type), type + " death cooldown");
		}
	}

	@Test
	void theCooldownCeilingsTrackTheMostExpensiveType() {
		assertEquals(MegumiShikigamiProfile.RABBITS_RECALL_COOLDOWN_TICKS,
				minimumRecallCooldown());
		assertEquals(MegumiShikigamiProfile.ELEPHANT_RECALL_COOLDOWN_TICKS,
				MegumiShikigamiProfile.maxRecallCooldownTicks());
		assertEquals(MegumiShikigamiProfile.ELEPHANT_DEATH_COOLDOWN_TICKS,
				MegumiShikigamiProfile.maxDeathCooldownTicks());
	}

	@Test
	void theSoakedEscalationAlwaysStrengthensTheShock() {
		assertTrue(MegumiShikigamiProfile.NUE_STUN_TICKS_SOAKED > MegumiShikigamiProfile.NUE_STUN_TICKS);
		assertTrue(MegumiShikigamiProfile.NUE_SLOW_TICKS_SOAKED > MegumiShikigamiProfile.NUE_SLOW_TICKS);
		assertTrue(MegumiShikigamiProfile.NUE_SOAKED_DAMAGE_MULTIPLIER > 1.0);
	}

	private static int minimumRecallCooldown() {
		int min = Integer.MAX_VALUE;
		for (MegumiShikigami type : MegumiShikigami.values()) {
			min = Math.min(min, MegumiShikigamiProfile.recallCooldownTicks(type));
		}
		return min;
	}
}
