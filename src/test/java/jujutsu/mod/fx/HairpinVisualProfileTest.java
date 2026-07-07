package jujutsu.mod.fx;

import java.util.EnumSet;
import java.util.List;

public final class HairpinVisualProfileTest {
	private HairpinVisualProfileTest() {}

	public static void main(String[] args) {
		assertWarningIsShortEdgeCue();
		assertBloomCarriesBurstFamilies();
		assertAfterglowKeepsResidueOnBurstVectors();
		assertBloomDensityStaysReadable();
		assertBloomFavorsMetalShrapnelOverGlowResidue();
		assertEveryProductionFamilyIsScheduled();
		assertPaletteKeepsBrightRedRestrained();
		System.out.println("HairpinVisualProfileTest passed");
	}

	private static void assertWarningIsShortEdgeCue() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.HAMMER_SNAP);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.WARN_EDGE);
		assert contains(budgets, HairpinVisualProfile.ParticleFamily.IGNITION_TICK);
		assert !contains(budgets, HairpinVisualProfile.ParticleFamily.BURST_RESIDUE);
		assert HairpinVisualProfile.warningDurationTicks() == 6;
		assert !contains(HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.NAIL_IGNITION), HairpinVisualProfile.ParticleFamily.WARN_EDGE);
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
		assert budgets.stream().noneMatch(budget -> budget.countAtTarget() > 0);
		assert HairpinVisualProfile.residueUsesBurstVectors();
	}

	private static void assertBloomDensityStaysReadable() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.HAIRPIN_BLOOM);
		int totalParticlesPerTick = budgets.stream()
				.mapToInt(budget -> budget.countPerNail() * 4 + budget.countAtTarget())
				.sum();
		assert totalParticlesPerTick >= 36 : totalParticlesPerTick;
		assert totalParticlesPerTick <= 52 : totalParticlesPerTick;
	}

	private static void assertBloomFavorsMetalShrapnelOverGlowResidue() {
		List<HairpinVisualProfile.ParticleBudget> budgets = HairpinVisualProfile.budgetsForPhase(HairpinTimeline.Phase.HAIRPIN_BLOOM);
		int metal = totalFor(budgets, HairpinVisualProfile.ParticleFamily.BURST_METAL_SHARD);
		int residue = totalFor(budgets, HairpinVisualProfile.ParticleFamily.BURST_RESIDUE);
		assert metal >= 28 : metal;
		assert residue <= 9 : residue;
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

	private static void assertPaletteKeepsBrightRedRestrained() {
		assert HairpinVisualProfile.brightRedMaxVisiblePercent() <= 4;
		assert HairpinVisualProfile.accentColorRgb() == 0x3a050f;
	}

	private static boolean contains(List<HairpinVisualProfile.ParticleBudget> budgets, HairpinVisualProfile.ParticleFamily family) {
		return budgets.stream().anyMatch(budget -> budget.family() == family);
	}

	private static int totalFor(List<HairpinVisualProfile.ParticleBudget> budgets, HairpinVisualProfile.ParticleFamily family) {
		return budgets.stream()
				.filter(budget -> budget.family() == family)
				.mapToInt(budget -> budget.countPerNail() * 4 + budget.countAtTarget())
				.sum();
	}
}
