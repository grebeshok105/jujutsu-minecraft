package jujutsu.mod.cursedspirit.ability;

import java.util.EnumSet;
import java.util.Set;

/**
 * Combat snapshot the brain decides on (Block 3, #86). Pure value: distance to the current
 * target, own HP fraction, whether a live target exists, and which abilities have open windows.
 */
public record CursedSpiritAbilityState(double distance, double hpFraction, boolean hasTarget,
		Set<CursedSpiritAbilityId> activeIds) {
	public CursedSpiritAbilityState {
		activeIds = activeIds.isEmpty() ? EnumSet.noneOf(CursedSpiritAbilityId.class)
				: EnumSet.copyOf(activeIds);
	}

	public static CursedSpiritAbilityState peace(double hpFraction) {
		return new CursedSpiritAbilityState(Double.POSITIVE_INFINITY, hpFraction, false,
				Set.of());
	}
}
