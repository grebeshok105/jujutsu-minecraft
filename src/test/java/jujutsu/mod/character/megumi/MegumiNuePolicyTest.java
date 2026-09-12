package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiNuePolicyTest {
	private static final Vec3 ORIGIN = new Vec3(0.0, 10.0, 0.0);

	@Test
	void aDiveAlwaysFliesAtExactlyTheProfileSpeed() {
		Vec3 velocity = MegumiNuePolicy.diveVelocity(ORIGIN, new Vec3(3.0, 11.0, 4.0));
		assertEquals(MegumiShikigamiProfile.NUE_DIVE_SPEED, velocity.length());
		assertTrue(velocity.dot(new Vec3(3.0, 1.0, 4.0)) > 0.0, "the dive must point at the target");
	}

	@Test
	void aTargetOnTopOfTheBodyProducesNoDive() {
		assertEquals(Vec3.ZERO, MegumiNuePolicy.diveVelocity(ORIGIN, ORIGIN));
	}

	@Test
	void impactTriggersInsideTheRadiusAndNotPastIt() {
		double radius = MegumiShikigamiProfile.NUE_IMPACT_RADIUS;
		assertTrue(MegumiNuePolicy.impactReachedSq(radius * radius));
		assertFalse(MegumiNuePolicy.impactReachedSq((radius + 0.01) * (radius + 0.01)));
	}

	@Test
	void aDiveNeedsReadinessLineOfSightAndRange() {
		double inRange = MegumiShikigamiProfile.NUE_DIVE_TRIGGER_RANGE - 1.0;
		assertTrue(MegumiNuePolicy.canStartDive(inRange, true, true));
		assertFalse(MegumiNuePolicy.canStartDive(inRange, false, true), "no line of sight");
		assertFalse(MegumiNuePolicy.canStartDive(inRange, true, false), "still charging");
		assertFalse(MegumiNuePolicy.canStartDive(
				MegumiShikigamiProfile.NUE_DIVE_TRIGGER_RANGE + 0.01, true, true), "out of range");
	}

	@Test
	void soakedTargetsTakeTheCanonSoakAndZap() {
		assertEquals(MegumiShikigamiProfile.NUE_DIVE_DAMAGE, MegumiNuePolicy.impactDamage(false));
		assertEquals(MegumiShikigamiProfile.NUE_DIVE_DAMAGE * MegumiShikigamiProfile.NUE_SOAKED_DAMAGE_MULTIPLIER,
				MegumiNuePolicy.impactDamage(true));
		assertEquals(MegumiShikigamiProfile.NUE_STUN_TICKS, MegumiNuePolicy.stunTicks(false));
		assertEquals(MegumiShikigamiProfile.NUE_STUN_TICKS_SOAKED, MegumiNuePolicy.stunTicks(true));
		assertEquals(MegumiShikigamiProfile.NUE_SLOW_TICKS, MegumiNuePolicy.slowTicks(false));
		assertEquals(MegumiShikigamiProfile.NUE_SLOW_TICKS_SOAKED, MegumiNuePolicy.slowTicks(true));
	}
}
