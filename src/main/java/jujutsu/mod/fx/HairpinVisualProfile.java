package jujutsu.mod.fx;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HairpinVisualProfile {
	private static final int DIRTY_FUCHSIA = 0x8a2f58;
	private static final int DIRTY_FUCHSIA_MAX_VISIBLE_PERCENT = 8;
	private static final int WARNING_DURATION_TICKS = 6;
	private static final boolean RESIDUE_USES_BURST_VECTORS = true;
	private static final Map<HairpinTimeline.Phase, List<ParticleBudget>> BUDGETS = createBudgets();

	private HairpinVisualProfile() {}

	public enum ParticleFamily {
		MARK_STAIN,
		WARN_EDGE,
		COMPRESSION_MOTE,
		SNAP_CRACK,
		BURST_RESIDUE,
		BURST_METAL_SHARD,
		IGNITION_TICK
	}

	public record ParticleBudget(ParticleFamily family, int countPerNail, int countAtTarget) {}

	public static List<ParticleBudget> budgetsForPhase(HairpinTimeline.Phase phase) {
		return BUDGETS.getOrDefault(phase, List.of());
	}

	public static int warningDurationTicks() {
		return WARNING_DURATION_TICKS;
	}

	public static boolean residueUsesBurstVectors() {
		return RESIDUE_USES_BURST_VECTORS;
	}

	public static int dirtyFuchsiaMaxVisiblePercent() {
		return DIRTY_FUCHSIA_MAX_VISIBLE_PERCENT;
	}

	public static int accentColorRgb() {
		return DIRTY_FUCHSIA;
	}

	private static Map<HairpinTimeline.Phase, List<ParticleBudget>> createBudgets() {
		EnumMap<HairpinTimeline.Phase, List<ParticleBudget>> budgets = new EnumMap<>(HairpinTimeline.Phase.class);
		budgets.put(HairpinTimeline.Phase.PREP_FREEZE, List.of(
				new ParticleBudget(ParticleFamily.MARK_STAIN, 2, 0)
		));
		budgets.put(HairpinTimeline.Phase.HAMMER_SNAP, List.of(
				new ParticleBudget(ParticleFamily.WARN_EDGE, 2, 0),
				new ParticleBudget(ParticleFamily.IGNITION_TICK, 2, 0)
		));
		budgets.put(HairpinTimeline.Phase.NAIL_IGNITION, List.of(
				new ParticleBudget(ParticleFamily.WARN_EDGE, 1, 0),
				new ParticleBudget(ParticleFamily.COMPRESSION_MOTE, 5, 0),
				new ParticleBudget(ParticleFamily.IGNITION_TICK, 1, 0)
		));
		budgets.put(HairpinTimeline.Phase.HAIRPIN_BLOOM, List.of(
				new ParticleBudget(ParticleFamily.SNAP_CRACK, 2, 2),
				new ParticleBudget(ParticleFamily.BURST_RESIDUE, 7, 8),
				new ParticleBudget(ParticleFamily.BURST_METAL_SHARD, 4, 4)
		));
		budgets.put(HairpinTimeline.Phase.AFTERGLOW, List.of(
				new ParticleBudget(ParticleFamily.BURST_RESIDUE, 3, 0)
		));
		budgets.put(HairpinTimeline.Phase.DONE, List.of());
		return Map.copyOf(budgets);
	}
}
