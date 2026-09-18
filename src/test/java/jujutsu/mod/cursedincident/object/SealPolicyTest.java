package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.cursedincident.policy.SealPolicy;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

final class SealPolicyTest {
    @Test
    void requiredTierTableMakesStrongObjectsHarderToSeal() {
        assertEquals(3, SealPolicy.requiredTier(1));
        assertEquals(3, SealPolicy.requiredTier(2));
        assertEquals(2, SealPolicy.requiredTier(3));
        assertEquals(2, SealPolicy.requiredTier(4));
        assertEquals(1, SealPolicy.requiredTier(5));
    }

    @Test
    void catastrophicFailureIsRareButNeverAbsentAcrossSeededTrials() {
        int fullIntegrityFailures = failures(100);
        int lowIntegrityFailures = failures(10);
        assertTrue(fullIntegrityFailures > 0, "a fresh seal still has a rare failure path");
        assertTrue(fullIntegrityFailures < 1_500, "fresh-seal failure must stay rare");
        assertTrue(lowIntegrityFailures >= fullIntegrityFailures,
                "failure probability must not decrease as integrity falls");
    }

    @Test
    void catastrophicChanceIsNormalizedPerTierNotAgainstTierThree() {
        // A full tier-1 seal (100/100) must read as undamaged, not as 25% of tier-3's 400.
        assertEquals(SealPolicy.catastrophicFailureChance(1, 100),
                SealPolicy.catastrophicFailureChance(3, 400));
        assertEquals(SealPolicy.catastrophicFailureChance(2, 200),
                SealPolicy.catastrophicFailureChance(3, 400));
        // Half integrity means the same damage fraction on every tier.
        assertEquals(SealPolicy.catastrophicFailureChance(1, 50),
                SealPolicy.catastrophicFailureChance(3, 200));
        assertTrue(SealPolicy.catastrophicFailureChance(1, 0)
                > SealPolicy.catastrophicFailureChance(1, 100));
        // The legacy single-argument overload keeps the tier-3 scale.
        assertEquals(SealPolicy.catastrophicFailureChance(3, 200),
                SealPolicy.catastrophicFailureChance(200));
    }

    @Test
    void sealIntegrityCurvesAndSignalsHaveStableBoundaries() {
        assertEquals(100, SealState.integrityMax(SealTier.TALISMAN));
        assertEquals(200, SealState.integrityMax(SealTier.INSCRIBED));
        assertEquals(400, SealState.integrityMax(SealTier.PRISMATIC));
        assertEquals(8, SealState.decayPerDay(1));
        assertEquals(4, SealState.decayPerDay(2));
        assertEquals(2, SealState.decayPerDay(3));
        assertEquals(SealState.DegradationSignal.NONE, SealState.signalAt(1.0));
        assertEquals(SealState.DegradationSignal.FAINT_PARTICLES, SealState.signalAt(0.74));
        assertEquals(SealState.DegradationSignal.CRACKING_SOUND, SealState.signalAt(0.49));
        assertEquals(SealState.DegradationSignal.UNSTABLE_GLOW, SealState.signalAt(0.24));
        assertEquals(SealState.DegradationSignal.FAILING, SealState.signalAt(0.0));
        assertTrue(SealState.isFailing(0, SealTier.TALISMAN));
    }

    private static int failures(int integrity) {
        int failures = 0;
        for (long seed = 0; seed < 10_000; seed++) {
            if (SealPolicy.maybeCatastrophicFail(RandomSource.create(seed), integrity)) {
                failures++;
            }
        }
        return failures;
    }
}
