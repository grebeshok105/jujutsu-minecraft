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
}
