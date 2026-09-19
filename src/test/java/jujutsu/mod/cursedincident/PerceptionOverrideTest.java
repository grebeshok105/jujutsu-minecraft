package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedincident.runtime.PerceptionOverrideRuntime;

/** R50/R51/C15 pure server gate oracles. */
final class PerceptionOverrideTest {
	@AfterEach
	void clearRuntime() {
		PerceptionOverrideRuntime.clear();
		IncidentPerceptionBridge.reset();
	}

	@Test
	void preCriticalStageNeverOverrides() {
		assertFalse(PerceptionOverrideRuntime.shouldOverride(IncidentStage.INFESTED, true, false, false));
	}

	@Test
	void criticalInsideOverridesAndOutsideDoesNot() {
		assertTrue(PerceptionOverrideRuntime.shouldOverride(IncidentStage.CRITICAL, true, false, false));
		assertFalse(PerceptionOverrideRuntime.shouldOverride(IncidentStage.CATASTROPHIC, false, false, false));
	}

	@Test
	void sealingOrScarClearsOverride() {
		assertFalse(PerceptionOverrideRuntime.shouldOverride(IncidentStage.CRITICAL, true, true, false));
		assertFalse(PerceptionOverrideRuntime.shouldOverride(IncidentStage.CRITICAL, true, false, true));
	}

	@Test
	void scarredParentSelfSustainingSecondaryStillOverrides() {
		// A cleaned-up parent keeps its self-sustaining secondary alive and spawning —
		// a player inside the node's zone must still perceive the curses it spawns.
		IncidentRecord record = new IncidentRecord();
		record.stage = IncidentStage.CRITICAL;
		record.scarred = true;
		record.center = new net.minecraft.core.BlockPos(0, 64, 0);
		record.radius = 10.0;
		record.secondaries.add(new SecondaryNode(java.util.UUID.randomUUID(),
				new net.minecraft.core.BlockPos(100, 64, 0), 8.0, 0L, true));
		// Player stands inside the secondary's zone, far outside the parent's.
		assertTrue(PerceptionOverrideRuntime.shouldOverride(
				new net.minecraft.core.BlockPos(100, 64, 0),
				net.minecraft.world.level.Level.OVERWORLD, java.util.List.of(record)));
	}

	@Test
	void scarredParentDependentSecondaryDoesNotOverride() {
		// A dependent (non-self-sustaining) node dies with the parent — no override.
		IncidentRecord record = new IncidentRecord();
		record.stage = IncidentStage.CRITICAL;
		record.scarred = true;
		record.center = new net.minecraft.core.BlockPos(0, 64, 0);
		record.radius = 10.0;
		record.secondaries.add(new SecondaryNode(java.util.UUID.randomUUID(),
				new net.minecraft.core.BlockPos(100, 64, 0), 8.0, 0L, false));
		assertFalse(PerceptionOverrideRuntime.shouldOverride(
				new net.minecraft.core.BlockPos(100, 64, 0),
				net.minecraft.world.level.Level.OVERWORLD, java.util.List.of(record)));
	}

	@Test
	void liveParentSecondaryAlsoOverrides() {
		// A live record's secondary zone overrides too — the override is per work
		// center, not only a post-cleanup fallback.
		IncidentRecord record = new IncidentRecord();
		record.stage = IncidentStage.CRITICAL;
		record.center = new net.minecraft.core.BlockPos(0, 64, 0);
		record.radius = 10.0;
		record.secondaries.add(new SecondaryNode(java.util.UUID.randomUUID(),
				new net.minecraft.core.BlockPos(100, 64, 0), 8.0, 0L, true));
		assertTrue(PerceptionOverrideRuntime.shouldOverride(
				new net.minecraft.core.BlockPos(100, 64, 0),
				net.minecraft.world.level.Level.OVERWORLD, java.util.List.of(record)));
	}
}
