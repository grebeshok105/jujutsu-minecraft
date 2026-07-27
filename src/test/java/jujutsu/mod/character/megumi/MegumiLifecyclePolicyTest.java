package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MegumiLifecyclePolicyTest {
	@Test
	void reconcileIgnoresNestedAndPlannedRemovalCallbacks() {
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.IGNORE,
				MegumiLifecyclePolicy.reconcileAction(true, true, 0));
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.IGNORE,
				MegumiLifecyclePolicy.reconcileAction(true, false, 0));
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.IGNORE,
				MegumiLifecyclePolicy.reconcileAction(false, false, 0));
	}

	@Test
	void finalLossRequiresAnExistingPackWithNoLivingSibling() {
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.RETAIN,
				MegumiLifecyclePolicy.reconcileAction(false, true, 2));
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.RETAIN,
				MegumiLifecyclePolicy.reconcileAction(false, true, 1));
		assertEquals(MegumiLifecyclePolicy.ReconcileAction.FINAL_LOSS,
				MegumiLifecyclePolicy.reconcileAction(false, true, 0));
	}

	@Test
	void teardownAppliesOneReasonCooldownOnlyWhenItRemovedOwnedState() {
		assertFalse(MegumiLifecyclePolicy.shouldApplyTeardownCooldown(false, false));
		assertTrue(MegumiLifecyclePolicy.shouldApplyTeardownCooldown(true, false));
		assertTrue(MegumiLifecyclePolicy.shouldApplyTeardownCooldown(false, true));
		assertTrue(MegumiLifecyclePolicy.shouldApplyTeardownCooldown(true, true));
	}

	@Test
	void authoritativeTeardownHasOneCooldownApplicationPoint() throws Exception {
		String source = Files.readString(Path.of(
				"src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java"));
		String teardown = source.substring(
				source.indexOf("public static void teardown"),
				source.indexOf("private static void broadcastCue"));
		assertEquals(1, occurrences(teardown, "startCooldownIfLonger("),
				"teardown must apply its reason-selected cooldown once after the discard sweep");
	}

	private static int occurrences(String source, String needle) {
		return (source.length() - source.replace(needle, "").length()) / needle.length();
	}
}
