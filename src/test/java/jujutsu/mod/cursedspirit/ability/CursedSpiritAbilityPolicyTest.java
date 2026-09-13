package jujutsu.mod.cursedspirit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Profile tables and pool-roll policy (Block 3, Step 1). All statistics run on fixed
 * seed ranges, so every number below is exact, not sampled: there is no flake margin
 * by construction.
 *
 * <p>Red-proofs: zero a weight → pool-size/distinct red; drop the +2 bias arm →
 * variant-bias red; flatten nasty weights → grade-bias red; gate an id at rank 2 →
 * eligibility red.
 */
final class CursedSpiritAbilityPolicyTest {
	private static final List<CursedSpiritGrade> GRADES = List.of(CursedSpiritGrade.GRADE_5,
			CursedSpiritGrade.GRADE_4, CursedSpiritGrade.GRADE_3);

	@Test
	void profileCoversEveryIdAtEverySpawnableGrade() {
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			for (CursedSpiritGrade grade : GRADES) {
				CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(id, grade);
				assertTrue(params.cooldownTicks() >= 0, id + "/" + grade);
				assertTrue(params.weight() > 0, id + "/" + grade);
				assertTrue(params.durationTicks() >= 0, id + "/" + grade);
				assertTrue(params.radius() >= 0.0, id + "/" + grade);
				assertTrue(params.damage() >= 0.0, id + "/" + grade);
				assertEquals(1, CursedSpiritAbilityProfile.requiredPowerRank(id));
			}
		}
	}

	@Test
	void variantBiasTableMatchesTheFrozenPairs() {
		assertEquals(2, CursedSpiritAbilityProfile.variantBias(CursedSpiritAbilityId.DASH,
				CursedSpiritVariant.PROWLER));
		assertEquals(0, CursedSpiritAbilityProfile.variantBias(CursedSpiritAbilityId.FEAR,
				CursedSpiritVariant.PROWLER));
		assertEquals(2, CursedSpiritAbilityProfile.variantBias(CursedSpiritAbilityId.ACID_SPIT,
				CursedSpiritVariant.WISTIVER));
		assertEquals(2, CursedSpiritAbilityProfile.variantBias(CursedSpiritAbilityId.ARMOR,
				CursedSpiritVariant.KELVIN));
		assertEquals(2, CursedSpiritAbilityProfile.variantBias(CursedSpiritAbilityId.GRAB_RUNNER,
				CursedSpiritVariant.GUZZLER));
	}

	@Test
	void rollThreeIsAlwaysThreeDistinctEligibleIds() {
		for (long seed = 0; seed < 10000; seed++) {
			List<CursedSpiritAbilityId> pool = CursedSpiritAbilityPolicy.rollThree(
					RandomSource.create(seed), CursedSpiritGrade.GRADE_4, CursedSpiritVariant.BLUD);
			assertEquals(3, pool.size(), "seed " + seed);
			assertEquals(3, new HashSet<>(pool).size(), "seed " + seed + ": " + pool);
		}
	}

	@Test
	void passivesCanCoexistInOnePool() {
		Set<CursedSpiritAbilityId> seen = EnumSet.noneOf(CursedSpiritAbilityId.class);
		for (long seed = 0; seed < 20000 && seen.size() < 8; seed++) {
			seen.addAll(CursedSpiritAbilityPolicy.rollThree(RandomSource.create(seed),
					CursedSpiritGrade.GRADE_4, CursedSpiritVariant.BLUD));
		}
		assertTrue(seen.contains(CursedSpiritAbilityId.ARMOR), "armor rolled: " + seen);
		assertTrue(seen.contains(CursedSpiritAbilityId.REGEN), "regen rolled: " + seen);
		assertTrue(seen.contains(CursedSpiritAbilityId.BERSERK), "berserk rolled: " + seen);
	}

	@Test
	void variantBiasIsMeasurable() {
		double prowlerDash = share(CursedSpiritGrade.GRADE_4, CursedSpiritVariant.PROWLER,
				CursedSpiritAbilityId.DASH, 20000);
		double mean = 0.0;
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			mean += share(CursedSpiritGrade.GRADE_4, variant, CursedSpiritAbilityId.DASH, 20000);
		}
		mean /= CursedSpiritVariant.values().length;
		assertTrue(prowlerDash - mean >= 0.02,
				"prowler dash " + prowlerDash + " vs mean " + mean);
	}

	@Test
	void gradeBiasLiftsNastyAbilities() {
		double low = share(CursedSpiritGrade.GRADE_5, CursedSpiritVariant.KELVIN,
				CursedSpiritAbilityId.ACID_SPIT, 20000);
		double high = share(CursedSpiritGrade.GRADE_3, CursedSpiritVariant.KELVIN,
				CursedSpiritAbilityId.ACID_SPIT, 20000);
		assertTrue(high - low >= 0.03, "acid g3 " + high + " vs g5 " + low);
	}

	@Test
	void pickRespectsExplicitWeights() {
		assertTrue(CursedSpiritAbilityPolicy.pick(RandomSource.create(1), Map.of()).isEmpty());
		Map<CursedSpiritAbilityId, Integer> single = new EnumMap<>(CursedSpiritAbilityId.class);
		single.put(CursedSpiritAbilityId.FEAR, 5);
		assertEquals(CursedSpiritAbilityId.FEAR,
				CursedSpiritAbilityPolicy.pick(RandomSource.create(1), single).orElseThrow());
	}

	private static double share(CursedSpiritGrade grade, CursedSpiritVariant variant,
			CursedSpiritAbilityId id, int samples) {
		int hits = 0;
		for (long seed = 0; seed < samples; seed++) {
			if (CursedSpiritAbilityPolicy.rollThree(RandomSource.create(seed), grade, variant)
					.contains(id)) {
				hits++;
			}
		}
		return (double) hits / samples;
	}
}
