package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MegumiLifecyclePolicyTest {
	private static final Path SUMMON_RUNTIME_SOURCE = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java");
	private static final Path DOG_ENTITY_SOURCE = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiDivineDogEntity.java");

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
	void onlyManualRecallDelaysDogRemoval() {
		assertEquals(MegumiLifecyclePolicy.DogCleanupAction.BEGIN_RECALL,
				MegumiLifecyclePolicy.dogCleanupAction(true, true));
		assertEquals(MegumiLifecyclePolicy.DogCleanupAction.HARD_DISCARD,
				MegumiLifecyclePolicy.dogCleanupAction(true, false));
		assertEquals(MegumiLifecyclePolicy.DogCleanupAction.HARD_DISCARD,
				MegumiLifecyclePolicy.dogCleanupAction(false, true));
		assertEquals(MegumiLifecyclePolicy.DogCleanupAction.HARD_DISCARD,
				MegumiLifecyclePolicy.dogCleanupAction(false, false));
	}

	@Test
	void recallingPresentationBodyDoesNotOwnAnotherTeardownCooldown() {
		assertTrue(MegumiLifecyclePolicy.dogOwnsTeardownCooldown(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING));
		assertTrue(MegumiLifecyclePolicy.dogOwnsTeardownCooldown(
				MegumiDogPresentationPolicy.Phase.ACTIVE));
		assertFalse(MegumiLifecyclePolicy.dogOwnsTeardownCooldown(
				MegumiDogPresentationPolicy.Phase.RECALLING));
	}

	@Test
	void authoritativeTeardownHasOneCooldownApplicationPoint() throws Exception {
		String source = Files.readString(SUMMON_RUNTIME_SOURCE);
		String teardown = source.substring(
				source.indexOf("public static void teardown"),
				source.indexOf("private static void broadcastCue"));
		assertEquals(1, occurrences(teardown, "startCooldownIfLonger("),
				"teardown must apply its reason-selected cooldown once after the discard sweep");
	}

	@Test
	void runtimeWiresRecallAsTheOnlyDelayedRemovalPath() throws Exception {
		String runtime = Files.readString(SUMMON_RUNTIME_SOURCE);
		String teardown = runtime.substring(
				runtime.indexOf("public static void teardown"),
				runtime.indexOf("private static void broadcastCue"));
		assertTrue(teardown.contains(
				"dogCleanupAction(reason == TeardownReason.RECALL, belongedToRemovedPack)"));
		assertTrue(teardown.contains("dog.beginRecall()"),
				"Manual recall must transition the real dog instead of discarding it immediately");
		assertTrue(teardown.contains("dog.discard()"),
				"Every non-recall teardown must retain immediate destructive cleanup");

		assertTrue(runtime.contains("pack == null && dog.canFinishRecallWithoutPack()"),
				"Only a recalling dog whose pack record is gone may outlive stale-pack validation");
		String entity = Files.readString(DOG_ENTITY_SOURCE);
		assertTrue(entity.contains("recallDimension = level().dimension()"));
		assertTrue(entity.contains("canFinishRecallWithoutPack()"),
				"A recalling dog must still hard-discard if it leaves its validated dimension");
	}

	@Test
	void entitySynchronizesPhaseAndAgeAndOwnsEveryCombatGate() throws Exception {
		String entity = Files.readString(DOG_ENTITY_SOURCE);
		assertEquals(2, occurrences(entity, "SynchedEntityData.defineId("),
				"Phase and phase age must be the only new synchronized presentation fields");
		assertTrue(entity.contains("defineSynchedData(SynchedEntityData.Builder builder)"));
		assertTrue(entity.contains("MegumiDogPresentationPolicy.combatEnabled(presentationPhase())"));
		assertTrue(entity.contains("doHurtTarget(ServerLevel level, Entity target)"));
		assertTrue(entity.contains("hurtServer(ServerLevel level, DamageSource source, float amount)"));
		assertTrue(entity.contains("isPickable()"));
		assertTrue(entity.contains("isPushable()"));
		assertTrue(entity.contains("canCollideWith(Entity other)"));

		String runtime = Files.readString(SUMMON_RUNTIME_SOURCE);
		assertTrue(runtime.contains(".filter(MegumiDivineDogEntity::acceptsSicCommand)"),
				"Sic must not confirm, emit or start cooldown while every living dog is still inert");
	}

	private static int occurrences(String source, String needle) {
		return (source.length() - source.replace(needle, "").length()) / needle.length();
	}
}
