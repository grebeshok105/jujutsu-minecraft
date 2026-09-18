package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import jujutsu.mod.cursedincident.policy.TemplateRollPolicy;
import jujutsu.mod.cursedincident.policy.StagePolicy;
import jujutsu.mod.cursedincident.infection.ZoneGeometry;
import jujutsu.mod.cursedincident.persist.IncidentSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R19/R24/R30/R34/R35/R38/R41 — one API owns all logical mutations. */
class IncidentControlContractTest {
	private IncidentSavedData store;
	private RecordingSink sink;

	@BeforeEach
	void bindFreshRuntime() {
		store = new IncidentSavedData();
		sink = new RecordingSink();
		IncidentControl.bindStore(() -> store);
		IncidentControl.bindWorldSink(sink);
		IncidentControl.bindDwellProvider(DwellProvider.NONE);
	}

	@AfterEach
	void clearRuntime() {
		IncidentControl.clearRuntimeState();
	}

	@Test
	void spawnRegistersAndInitialDeltaUsesBoundStore() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		assertNotNull(record.id);
		assertEquals(record, store.get(record.id));
		assertEquals(1, sink.stageDeltas);
	}

	@Test
	void advanceUsesSingleForwardTransitionPath() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		long age = IncidentControl.advance(record.id, 300_000L);
		assertEquals(300_000L, age);
		assertEquals(IncidentStage.CATASTROPHIC, record.stage);
		assertEquals(4, record.transitions.size());
		assertEquals(5, sink.stageDeltas);
	}

	@Test
	void setStageNeverRegresses() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		assertEquals(IncidentStage.CRITICAL, IncidentControl.setStage(record.id, IncidentStage.CRITICAL));
		assertEquals(IncidentStage.CRITICAL, IncidentControl.setStage(record.id, IncidentStage.GROWING));
		assertEquals(IncidentStage.CRITICAL, record.stage);
	}

	@Test
	void sealRefusalIsMachineReadableAndDoesNotSeal() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 1, IncidentStage.INITIAL)));
		record.objectGrade = 1;
		IncidentControl.advance(record.id, 1);
		IncidentControl.SealAttempt attempt = IncidentControl.seal(record.id, 1);
		assertFalse(attempt.ok());
		assertEquals(TemplateRollPolicy.sealDifficulty(1), attempt.requiredTier());
		assertEquals("insufficient_tier", attempt.reason());
		assertFalse(record.sealed);
		assertEquals(1, record.sealFailures);
	}
	@Test
	void frontierDoesNotReplayAlreadyProcessedThresholds() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		IncidentControl.advance(record.id, 300_000L);
		int transitions = record.transitions.size();
		int deltas = sink.stageDeltas;
		IncidentControl.advanceTo(record, 300_000L);
		assertEquals(deltas, sink.stageDeltas);
		assertEquals(300_000L, record.lastProcessedAgeTicks);
	}

	@Test
	void sealedFreezeLeavesFrontierUntilUnsealCatchUp() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 5, IncidentStage.INITIAL)));
		record.objectGrade = 5;
		assertTrue(IncidentControl.seal(record.id, 1).ok());
		IncidentControl.advance(record.id, 600_000L);
		assertEquals(IncidentStage.INITIAL, record.stage);
		assertEquals(0L, record.lastProcessedAgeTicks);
		assertTrue(IncidentControl.unseal(record.id));
		IncidentControl.advance(record.id, 600_000L);
		assertEquals(IncidentStage.CATASTROPHIC, record.stage);
		assertEquals(4, record.transitions.size());
	}

	@Test
	void activeZonesExcludesScarredAndSealedRecords() {
		IncidentRecord active = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		IncidentRecord sealed = created(IncidentControl.spawn(request(43L, 5, IncidentStage.INITIAL)));
		sealed.objectGrade = 5;
		assertTrue(IncidentControl.seal(sealed.id, 1).ok());
		IncidentRecord scarred = created(IncidentControl.spawn(request(44L, 3, IncidentStage.INITIAL)));
		IncidentControl.cleanup(scarred.id);
		assertEquals(1, IncidentControl.activeZones());
		IncidentControl.cleanup(active.id);
		IncidentControl.cleanup(sealed.id);
	}

	@Test
	void objectSpawnWithoutSpawnerReturnsRefusedAndDoesNotStoreRecord() {
		IncidentControl.SpawnOutcome outcome = IncidentControl.spawn(new IncidentControl.SpawnRequest(
				BlockPos.ZERO, Level.OVERWORLD, "blight", 3, 42L, IncidentStage.INITIAL,
				"cursed_eye", SourceKind.OBJECT, 8.0));
		assertTrue(outcome instanceof IncidentControl.SpawnOutcome.Refused);
		assertTrue(store.incidents().isEmpty());
	}

	@Test
	void requestedStartStageReplaysLadderAndSetsFrontier() {
		IncidentControl.SpawnOutcome outcome = IncidentControl.spawn(new IncidentControl.SpawnRequest(
				BlockPos.ZERO, Level.OVERWORLD, "blight", 3, 42L, IncidentStage.CRITICAL,
				null, SourceKind.FREE, 8.0));
		IncidentRecord record = created(outcome);
		assertEquals(IncidentStage.CRITICAL, record.stage);
		assertEquals(3, record.transitions.size());
		assertEquals(StagePolicy.thresholdFor(IncidentStage.CRITICAL, record.params.escalationSpeedMul()),
				record.lastProcessedAgeTicks);
		assertEquals(4, sink.stageDeltas);
	}

	@Test
	void sealDamageFloorsIntegrityAndUnsealResumes() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 5, IncidentStage.INITIAL)));
		record.objectGrade = 5;
		assertTrue(IncidentControl.seal(record.id, 1).ok());
		assertEquals(0, IncidentControl.damageSeal(record.id, Integer.MAX_VALUE));
		assertFalse(record.sealed);
		assertTrue(IncidentControl.unseal(record.id) == false);
	}

	@Test
	void secondaryPlacementIsDeterministicDistinctAndBounded() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		record.radius = 8.0;
		BlockPos first = ZoneGeometry.secondaryCenter(null, record, 0);
		BlockPos repeat = ZoneGeometry.secondaryCenter(null, record, 0);
		assertEquals(first, repeat);
		assertFalse(first.equals(record.center));
		assertTrue(first.distSqr(record.center) <= record.radius * record.radius * 4.0);
	}

	@Test
	void workCentersIncludeParentAndOnlyNonScarredSecondaries() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		SecondaryNode dependent = new SecondaryNode(UUID.randomUUID(), new BlockPos(8, 3, 4), 4, 1, false);
		SecondaryNode selfSustaining = new SecondaryNode(UUID.randomUUID(), new BlockPos(10, 3, 4), 4, 1, true);
		SecondaryNode scarred = new SecondaryNode(UUID.randomUUID(), new BlockPos(12, 3, 4), 4, 1, true, true);
		record.secondaries.add(dependent);
		record.secondaries.add(selfSustaining);
		record.secondaries.add(scarred);
		var centers = IncidentControl.workCenters(record);
		assertEquals(3, centers.size());
		assertTrue(centers.get(0).isParent() && centers.get(0).center().equals(record.center));
		assertTrue(centers.stream().anyMatch(center -> dependent.nodeId().equals(center.nodeId())));
		assertTrue(centers.stream().anyMatch(center -> selfSustaining.nodeId().equals(center.nodeId())));
		assertFalse(centers.stream().anyMatch(center -> scarred.nodeId().equals(center.nodeId())));
	}

	@Test
	void forceSecondaryDoesNotReturnDependentNode() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		SecondaryNode dependent = new SecondaryNode(UUID.randomUUID(), new BlockPos(2, 3, 4), 4, 1, false);
		record.secondaries.add(dependent);
		SecondaryNode result = IncidentControl.forceSecondary(record.id, new BlockPos(9, 3, 4));
		assertTrue(result.selfSustaining());
		assertFalse(result.nodeId().equals(dependent.nodeId()));
		assertEquals(2, record.secondaries.size());
	}

	@Test
	void relocateLeavesScarAndStopsDependentNode() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		SecondaryNode selfSustaining = IncidentControl.forceSecondary(record.id, new BlockPos(8, 3, 4));
		SecondaryNode dependent = new SecondaryNode(UUID.randomUUID(), new BlockPos(2, 3, 4), 4, 1, false);
		record.secondaries.add(dependent);
		IncidentControl.relocate(record.id, new BlockPos(20, 3, 4));
		assertEquals(new BlockPos(20, 3, 4), record.center);
		assertTrue(record.scars.contains(BlockPos.ZERO));
		assertFalse(record.secondaries.contains(dependent));
		assertTrue(record.secondaries.contains(selfSustaining));
	}

	@Test
	void forceSecondaryIsSelfSustainingAndCleanupOnlyScars() {
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL)));
		SecondaryNode node = IncidentControl.forceSecondary(record.id, new BlockPos(9, 3, 4));
		assertTrue(node.selfSustaining());
		IncidentControl.cleanup(record.id);
		assertTrue(record.scarred);
		assertEquals(IncidentStage.INITIAL, record.stage);
		assertTrue(record.secondaries.contains(node));
		assertEquals(1, IncidentControl.workCenters(record).size());
	}

	@Test
	void pressureMutatesSavedDataOnlyThroughFacade() {
		assertEquals(0, IncidentControl.cursedPressure());
		assertEquals(4, IncidentControl.addPressure(4));
		assertEquals(4, store.pressure());
		IncidentControl.resetPressure();
		assertEquals(0, store.pressure());
	}

	@Test
	void objectSealOperationsWriteThroughDwellProvider() {
		RecordingDwell dwell = new RecordingDwell();
		IncidentControl.bindDwellProvider(dwell);
		IncidentRecord record = created(IncidentControl.spawn(request(42L, 5, IncidentStage.INITIAL)));
		record.sourceKind = SourceKind.OBJECT;
		record.objectGrade = 5;
		record.objectInstanceId = UUID.randomUUID();
		store.put(record);
		assertTrue(IncidentControl.seal(record.id, 1).ok());
		assertEquals(1, dwell.calls);
		assertTrue(dwell.sealed);
		IncidentControl.identify(record.id, KnowledgeLevel.NAME);
		assertEquals(2, dwell.calls);
		assertEquals(KnowledgeLevel.NAME, dwell.knowledge);
		assertTrue(IncidentControl.unseal(record.id));
		assertEquals(3, dwell.calls);
		assertFalse(dwell.sealed);
	}

	@Test
	void missingIncidentHasDedicatedException() {
		assertThrows(IncidentControl.IncidentNotFoundException.class,
				() -> IncidentControl.inspect(UUID.randomUUID()));
	}

	@Test
	void controlHasNoForbiddenCrossBlockImports() throws Exception {
		Path source = Path.of("src/main/java/jujutsu/mod/cursedincident/IncidentControl.java");
		String text = Files.readString(source);
		assertFalse(text.contains("import jujutsu.mod.cursedincident.object"));
		assertFalse(text.contains("import jujutsu.mod.cursedincident.infection"));
		assertFalse(text.contains("import jujutsu.mod.client"));
		assertFalse(text.contains("import jujutsu.mcpdev"));
	}

	private static IncidentRecord created(IncidentControl.SpawnOutcome outcome) {
		return ((IncidentControl.SpawnOutcome.Created) outcome).record();
	}

	private static IncidentControl.SpawnRequest request(long seed, int grade, IncidentStage stage) {
		ResourceKey<Level> dimension = Level.OVERWORLD;
		return new IncidentControl.SpawnRequest(BlockPos.ZERO, dimension, "blight", grade, seed, stage,
				null, SourceKind.FREE, 8.0);
	}

	private static final class RecordingSink implements IncidentWorldSink {
		int stageDeltas;
		int tickZones;

		@Override
		public void applyStageDelta(net.minecraft.server.level.ServerLevel level, IncidentRecord record,
				IncidentStage from, IncidentStage to) {
			stageDeltas++;
		}

		@Override
		public void tickZone(net.minecraft.server.level.ServerLevel level, IncidentRecord record,
				BlockPos center, UUID nodeId, int tickBudget) {
			tickZones++;
		}
	}

	private static final class RecordingDwell implements DwellProvider {
		int calls;
		boolean sealed;
		KnowledgeLevel knowledge;

		@Override
		public void applySealState(UUID objectInstanceId, boolean sealed, int sealTier,
				int sealIntegrity, KnowledgeLevel knowledge) {
			calls++;
			this.sealed = sealed;
			this.knowledge = knowledge;
		}
	}
}
