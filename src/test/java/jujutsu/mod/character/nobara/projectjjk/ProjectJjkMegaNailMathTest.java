package jujutsu.mod.character.nobara.projectjjk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-formula tests for Mega Nail damage and knockback.
 *
 * <p>All values are derived from {@link ProjectJjkNobaraProfile} constants and the contract
 * documented in {@link ProjectJjkMegaNailRuntime}. Tests assert the formula shape, boundary
 * behaviour, and monotonicity without requiring a Minecraft server.
 */
final class ProjectJjkMegaNailMathTest {
	private static final float DELTA = 1.0e-6f;

	@Test
	void oneNailDepth1DoesBaseDamage() {
		float weight = ProjectJjkNobaraProfile.nailDepthMultiplier(1);
		float damage = ProjectJjkMegaNailRuntime.megaNailDamage(weight);
		float expected = ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_PER_NAIL * weight;
		assertEquals(expected, damage, DELTA);
	}

	@Test
	void threeNailsDepth1ScaleLinearly() {
		float weight = 3 * ProjectJjkNobaraProfile.nailDepthMultiplier(1);
		float damage = ProjectJjkMegaNailRuntime.megaNailDamage(weight);
		float expected = ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_PER_NAIL * weight;
		assertEquals(expected, damage, DELTA);
	}

	@Test
	void depthWeightScalesDamageAboveFlatCount() {
		// Two nails at depth 3: multiplier 1.75 each, weight = 3.5.
		float depth3 = ProjectJjkNobaraProfile.NAIL_DEPTH_3_MULTIPLIER;
		float weight = 2 * depth3;
		float damage = ProjectJjkMegaNailRuntime.megaNailDamage(weight);
		float expected = ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_PER_NAIL * weight;
		assertEquals(expected, damage, DELTA);
	}

	@Test
	void damageCapsAtFortyTwo() {
		// weight 20 → 4.0 * 20 = 80 → cap at 42.
		float damage = ProjectJjkMegaNailRuntime.megaNailDamage(20.0f);
		assertEquals(ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_CAP, damage, DELTA);
	}

	@Test
	void damageIsMonotonicWithWeight() {
		float prev = 0.0f;
		for (float w = 0.5f; w <= 20.0f; w += 0.5f) {
			float d = ProjectJjkMegaNailRuntime.megaNailDamage(w);
			assertTrue(d >= prev, "Damage must not decrease when weight increases from " + (w - 0.5f) + " to " + w);
			prev = d;
		}
	}

	@Test
	void oneNailKnockback() {
		float kb = ProjectJjkMegaNailRuntime.megaNailKnockback(1);
		float expected = Math.min(
				ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_BASE + ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_PER_NAIL * 1,
				ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP);
		assertEquals(expected, kb, DELTA);
	}

	@Test
	void knockbackGrowsWithCount() {
		float prev = 0.0f;
		for (int count = 1; count <= 5; count++) {
			float kb = ProjectJjkMegaNailRuntime.megaNailKnockback(count);
			assertTrue(kb >= prev, "Knockback must not decrease when count increases from " + (count - 1) + " to " + count);
			prev = kb;
			assertTrue(kb <= ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP + DELTA,
					"Knockback must not exceed cap at count " + count);
		}
	}

	@Test
	void knockbackCapsAtThree() {
		// count 10 → 1.9 + 0.2 * 10 = 3.9 → capped at 3.0.
		float kb = ProjectJjkMegaNailRuntime.megaNailKnockback(10);
		assertEquals(ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP, kb, DELTA);
	}

	@Test
	void zeroNailsReturnsBaseKnockback() {
		float kb = ProjectJjkMegaNailRuntime.megaNailKnockback(0);
		assertEquals(ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_BASE, kb, DELTA);
	}

	@Test
	void strikeDelayConstantIsSixTicks() {
		assertEquals(6, ProjectJjkNobaraProfile.MEGA_NAIL_STRIKE_DELAY_TICKS);
	}
}
