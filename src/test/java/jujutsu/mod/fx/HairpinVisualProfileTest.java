package jujutsu.mod.fx;

import java.util.EnumSet;
import java.util.List;

public final class HairpinVisualProfileTest {
	private HairpinVisualProfileTest() {}

	public static void main(String[] args) {
		assertWarningIsShortEdgeCue();
		assertBloomCarriesBurstFamilies();
		assertAfterglowKeepsResidueOnBurstVectors();
		assertEveryProductionFamilyIsScheduled();
		assertPaletteKeepsFuchsiaRestrained();
		System.out.println("HairpinVisualProfileTest passed");
	}

	private static void assertWarningIsShortEdgeCue() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.HAMMER_SNAP);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.WARN_EDGE);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.IGNITION_TICK);
		assert !contains(budgets, HairpinVisualProfile.ParticleFamily.BURST_RESIDUE);
		assert HairpinVisualProfile.warningDurationTicks() >= 6;
	}

	private static void assertBloomCarriesBurstFamilies() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.HAIRPIN_BLOOM);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.SNAP_CRACK);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.BURST_RESIDUE);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.BURST_METAL_SHARD);
	}

	private static void assertAfterglowKeepsResidueOnBurstVectors() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.AFTERGLOW);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.BURST_RESIDUE);
		assert !contains(budgets, HairpinVisualProfile.ParticleFamily.WARN_EDGE);
		assert !contains(budgets, HairpinVisualProfile.ParticleFamily.SNAP_CRACK);
		assert HairpinVisualProfile.residueUsesBurstVectors();
	}

	private static void assertEveryProductionFamilyIsScheduled() {
		EnumSet<HairpinVisualProfile.ParticleFamily> scheduled = EnumSet.noneOf(HairpinVisualProfile.ParticleFamily.class);
		for (HairpinTimeline.Phase phase : HairpinTimeline.Phase.values()) {
			for (HairpinVisualProfile.ParticleBudget budget : HairpinVisualProfile.budgetsForPhase(phase)) {
				scheduled.add(budget.family());
			}
		}
		assert scheduled.equals(EnumSet.allOf(HairpinVisualProfile.ParticleFamily.class));
	}

	private static void assertPaletteKeepsFuchsiaRestrained() {
		assert HairpinVisualProfile.dirtyFuchsiaMaxVisiblePercent() <= 8;
		assert HairpinVisualProfile.accentColorRgb() == 0x8a2f58;
	}

	private static boolean contains(List<HairpinVisualProfile.ParticleBudget> budgets, HairpinVisualProfile.ParticleFamily family) {
		return budgets.stream().anyMatch(budget -> budget.family() == family);
	}
}
