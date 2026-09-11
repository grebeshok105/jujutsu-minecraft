package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MegumiShikigamiProfileTest {
	@Test
	void theSharedSicSurfaceStaysInOnePlace() {
		assertEquals(20.0, MegumiShikigamiProfile.SIC_RANGE);
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
