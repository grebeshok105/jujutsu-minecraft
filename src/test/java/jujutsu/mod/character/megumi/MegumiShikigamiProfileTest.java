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
	void theSerpentBlockIsTheTuningSurfaceForTheAmbushBind() {
		assertEquals(50.0, MegumiShikigamiProfile.SERPENT_HEALTH);
		assertEquals(4.0, MegumiShikigamiProfile.SERPENT_ATTACK_DAMAGE);
		assertEquals(0.30, MegumiShikigamiProfile.SERPENT_SPEED);
		assertEquals(16, MegumiShikigamiProfile.SERPENT_MATERIALIZE_TICKS);
		assertEquals(12, MegumiShikigamiProfile.SERPENT_RECALL_TICKS);
		assertEquals(200, MegumiShikigamiProfile.SERPENT_RECALL_COOLDOWN_TICKS);
		assertEquals(340, MegumiShikigamiProfile.SERPENT_DEATH_COOLDOWN_TICKS);
		assertEquals(40, MegumiShikigamiProfile.SERPENT_AMBUSH_SCAN_TICKS);
		assertEquals(14.0, MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE);
		assertEquals(10, MegumiShikigamiProfile.SERPENT_PREPARE_TICKS);
		assertEquals(100, MegumiShikigamiProfile.SERPENT_SUBMERGED_MAX_TICKS);
		assertEquals(8, MegumiShikigamiProfile.SERPENT_EMERGE_TICKS);
		assertEquals(120, MegumiShikigamiProfile.SERPENT_BIND_BASE_TICKS);
		assertEquals(80, MegumiShikigamiProfile.SERPENT_BIND_MIN_TICKS);
		assertEquals(160, MegumiShikigamiProfile.SERPENT_BIND_MAX_TICKS);
		assertEquals(1.0, MegumiShikigamiProfile.SERPENT_BIND_HP_PENALTY);
		assertEquals(8.0, MegumiShikigamiProfile.SERPENT_BIND_SIZE_PENALTY);
		assertEquals(16.0, MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE);
		assertEquals(240, MegumiShikigamiProfile.SERPENT_BIND_COOLDOWN_TICKS);
		assertEquals(0.9, MegumiShikigamiProfile.SERPENT_TOSS_SPEED);
		assertEquals(0.3, MegumiShikigamiProfile.SERPENT_TOSS_LIFT);
	}

	@Test
	void theDeerBlockIsTheTuningSurfaceForTheHealCadence() {
		assertEquals(45.0, MegumiShikigamiProfile.DEER_HEALTH);
		assertEquals(1.0, MegumiShikigamiProfile.DEER_ATTACK_DAMAGE);
		assertEquals(0.28, MegumiShikigamiProfile.DEER_SPEED);
		assertEquals(5.0, MegumiShikigamiProfile.DEER_FOLLOW_START);
		assertEquals(2.0, MegumiShikigamiProfile.DEER_FOLLOW_STOP);
		assertEquals(14, MegumiShikigamiProfile.DEER_MATERIALIZE_TICKS);
		assertEquals(12, MegumiShikigamiProfile.DEER_RECALL_TICKS);
		assertEquals(180, MegumiShikigamiProfile.DEER_RECALL_COOLDOWN_TICKS);
		assertEquals(320, MegumiShikigamiProfile.DEER_DEATH_COOLDOWN_TICKS);
		assertEquals(40, MegumiShikigamiProfile.DEER_HEAL_SCAN_TICKS);
		assertEquals(12.0, MegumiShikigamiProfile.DEER_HEAL_RANGE);
		assertEquals(2.0, MegumiShikigamiProfile.DEER_HEAL_MIN);
		assertEquals(6.0, MegumiShikigamiProfile.DEER_HEAL_MAX);
		assertEquals(10, MegumiShikigamiProfile.DEER_HEAL_ACTION_TICKS);
		assertEquals(0.5, MegumiShikigamiProfile.DEER_SELF_HEAL_FACTOR);
		assertEquals(60, MegumiShikigamiProfile.DEER_CLEANSE_SCAN_TICKS);
		assertEquals(2.0, MegumiShikigamiProfile.DEER_ANTLER_RANGE);
		assertEquals(0.5, MegumiShikigamiProfile.DEER_ANTLER_KNOCKBACK);
		assertEquals(40, MegumiShikigamiProfile.DEER_ANTLER_COOLDOWN_TICKS);
		assertEquals(3.0, MegumiShikigamiProfile.DEER_INTERPOSE_RADIUS);
	}

	@Test
	void theOxBlockIsTheTuningSurfaceForTheCommittedCharge() {
		assertEquals(80.0, MegumiShikigamiProfile.OX_HEALTH);
		assertEquals(2.0, MegumiShikigamiProfile.OX_ATTACK_DAMAGE);
		assertEquals(0.26, MegumiShikigamiProfile.OX_SPEED);
		assertEquals(7.0, MegumiShikigamiProfile.OX_FOLLOW_START);
		assertEquals(3.0, MegumiShikigamiProfile.OX_FOLLOW_STOP);
		assertEquals(20, MegumiShikigamiProfile.OX_MATERIALIZE_TICKS);
		assertEquals(14, MegumiShikigamiProfile.OX_RECALL_TICKS);
		assertEquals(240, MegumiShikigamiProfile.OX_RECALL_COOLDOWN_TICKS);
		assertEquals(520, MegumiShikigamiProfile.OX_DEATH_COOLDOWN_TICKS);
		assertEquals(15.0, MegumiShikigamiProfile.OX_ACQUIRE_RANGE);
		assertEquals(8.0, MegumiShikigamiProfile.OX_ALIGN_YAW_TOLERANCE_DEG);
		assertEquals(14, MegumiShikigamiProfile.OX_WINDUP_TICKS);
		assertEquals(0.75, MegumiShikigamiProfile.OX_CHARGE_SPEED);
		assertEquals(60, MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS);
		assertEquals(20.0, MegumiShikigamiProfile.OX_CHARGE_MAX_DISTANCE);
		assertEquals(2.0, MegumiShikigamiProfile.OX_IMPACT_BASE);
		assertEquals(0.35, MegumiShikigamiProfile.OX_IMPACT_SLOPE);
		assertEquals(2.0, MegumiShikigamiProfile.OX_IMPACT_MIN);
		assertEquals(12.0, MegumiShikigamiProfile.OX_IMPACT_MAX);
		assertEquals(0.8, MegumiShikigamiProfile.OX_KNOCKBACK_BASE);
		assertEquals(0.1, MegumiShikigamiProfile.OX_KNOCKBACK_PER_POWER);
		assertEquals(20, MegumiShikigamiProfile.OX_WALL_STAGGER_TICKS);
		assertEquals(24, MegumiShikigamiProfile.OX_RECOVERY_TICKS);
		assertEquals(240, MegumiShikigamiProfile.OX_CHARGE_COOLDOWN_TICKS);
		assertEquals(0.75, MegumiShikigamiProfile.OX_CORRIDOR_SAMPLE_STEP);
	}

	@Test
	void theTigerBlockIsTheTuningSurfaceForTheCommittedCombo() {
		assertEquals(100.0, MegumiShikigamiProfile.TIGER_HEALTH);
		assertEquals(8.0, MegumiShikigamiProfile.TIGER_ATTACK_DAMAGE);
		assertEquals(0.24, MegumiShikigamiProfile.TIGER_SPEED);
		assertEquals(6.0, MegumiShikigamiProfile.TIGER_FOLLOW_START);
		assertEquals(2.5, MegumiShikigamiProfile.TIGER_FOLLOW_STOP);
		assertEquals(24, MegumiShikigamiProfile.TIGER_MATERIALIZE_TICKS);
		assertEquals(14, MegumiShikigamiProfile.TIGER_RECALL_TICKS);
		assertEquals(250, MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS);
		assertEquals(560, MegumiShikigamiProfile.TIGER_DEATH_COOLDOWN_TICKS);
		assertEquals(15.0, MegumiShikigamiProfile.TIGER_APPROACH_RANGE);
		assertEquals(2.2, MegumiShikigamiProfile.TIGER_APPROACH_STOP);
		assertEquals(12, MegumiShikigamiProfile.TIGER_COMBO_WINDUP_TICKS);
		assertEquals(6, MegumiShikigamiProfile.TIGER_STRIKE1_RESOLVE_TICKS);
		assertEquals(2.6, MegumiShikigamiProfile.TIGER_STRIKE1_RANGE);
		assertEquals(70.0, MegumiShikigamiProfile.TIGER_STRIKE1_ARC_DEG);
		assertEquals(5.0, MegumiShikigamiProfile.TIGER_STRIKE1_DAMAGE);
		assertEquals(6, MegumiShikigamiProfile.TIGER_STRIKE1_STAGGER_TICKS);
		assertEquals(8, MegumiShikigamiProfile.TIGER_STRIKE2_RESOLVE_TICKS);
		assertEquals(2.8, MegumiShikigamiProfile.TIGER_STRIKE2_RANGE);
		assertEquals(70.0, MegumiShikigamiProfile.TIGER_STRIKE2_ARC_DEG);
		assertEquals(7.0, MegumiShikigamiProfile.TIGER_STRIKE2_DAMAGE);
		assertEquals(8, MegumiShikigamiProfile.TIGER_STRIKE2_STAGGER_TICKS);
		assertEquals(12, MegumiShikigamiProfile.TIGER_FINISHER_RESOLVE_TICKS);
		assertEquals(3.0, MegumiShikigamiProfile.TIGER_FINISHER_RANGE);
		assertEquals(80.0, MegumiShikigamiProfile.TIGER_FINISHER_ARC_DEG);
		assertEquals(12.0, MegumiShikigamiProfile.TIGER_FINISHER_DAMAGE);
		assertEquals(20, MegumiShikigamiProfile.TIGER_FINISHER_STAGGER_TICKS);
		assertEquals(1.2, MegumiShikigamiProfile.TIGER_FINISHER_KNOCKBACK);
		assertEquals(0.25, MegumiShikigamiProfile.TIGER_FINISHER_LIFT);
		assertEquals(30, MegumiShikigamiProfile.TIGER_RECOVERY_TICKS);
		assertEquals(160, MegumiShikigamiProfile.TIGER_COMBO_COOLDOWN_TICKS);
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
