package jujutsu.mod.cursedincident.object;

import java.util.List;

/** Pure integrity curves and readable degradation signals for a physical seal. */
public final class SealState {
    public enum DegradationSignal {
        NONE,
        FAINT_PARTICLES,
        CRACKING_SOUND,
        UNSTABLE_GLOW,
        FAILING
    }

    private SealState() {
    }

    public static int integrityMax(SealTier tier) {
        return integrityMax(tier == null ? 1 : tier.value());
    }

    public static int integrityMax(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> 100;
            case 2 -> 200;
            default -> 400;
        };
    }

    public static int decayPerDay(SealTier tier) {
        return decayPerDay(tier == null ? 1 : tier.value());
    }

    public static int decayPerDay(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> 8;
            case 2 -> 4;
            default -> 2;
        };
    }

    /**
     * Maps a normalized integrity ratio to the strongest currently visible signal.
     * Values above one are accepted as percentage points for callers using 0..100.
     */
    public static List<DegradationSignal> degradationSignals(double integrityPct) {
        double ratio = integrityPct > 1.0 ? integrityPct / 100.0 : integrityPct;
        if (ratio <= 0.0) {
            return List.of(DegradationSignal.FAILING);
        }
        if (ratio < 0.25) {
            return List.of(DegradationSignal.UNSTABLE_GLOW);
        }
        if (ratio < 0.50) {
            return List.of(DegradationSignal.CRACKING_SOUND);
        }
        if (ratio < 0.75) {
            return List.of(DegradationSignal.FAINT_PARTICLES);
        }
        return List.of(DegradationSignal.NONE);
    }

    public static List<DegradationSignal> degradationSignals(int integrity, SealTier tier) {
        int maximum = integrityMax(tier);
        return degradationSignals(maximum == 0 ? 0.0 : (double) integrity / maximum);
    }

    public static DegradationSignal signalAt(double integrityPct) {
        return degradationSignals(integrityPct).getFirst();
    }

    public static boolean isFailing(int integrity, SealTier tier) {
        return integrity <= 0 || signalAt((double) integrity / integrityMax(tier)) == DegradationSignal.FAILING;
    }
}
