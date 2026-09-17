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
		IncidentRecord record = IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL));
		assertNotNull(record.id);
		assertEquals(record, store.get(record.id));
		assertEquals(1, sink.stageDeltas);
	}

	@Test
	void advanceUsesSingleForwardTransitionPath() {
		IncidentRecord record = IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL));
		long age = IncidentControl.advance(record.id, 300_000L);
		assertEquals(300_000L, age);
		assertEquals(IncidentStage.CATASTROPHIC, record.stage);
		assertEquals(4, record.transitions.size());
		assertEquals(5, sink.stageDeltas);
	}

	@Test
	void setStageNeverRegresses() {
		IncidentRecord record = IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL));
		assertEquals(IncidentStage.CRITICAL, IncidentControl.setStage(record.id, IncidentStage.CRITICAL));
		assertEquals(IncidentStage.CRITICAL, IncidentControl.setStage(record.id, IncidentStage.GROWING));
		assertEquals(IncidentStage.CRITICAL, record.stage);
	}

	@Test
	void sealRefusalIsMachineReadableAndDoesNotSeal() {
		IncidentRecord record = IncidentControl.spawn(request(42L, 1, IncidentStage.INITIAL));
		IncidentControl.advance(record.id, 1);
		IncidentControl.SealAttempt attempt = IncidentControl.seal(record.id, 1);
		assertFalse(attempt.ok());
		assertEquals(TemplateRollPolicy.sealDifficulty(1), attempt.requiredTier());
		assertEquals("insufficient_tier", attempt.reason());
		assertFalse(record.sealed);
		assertEquals(1, record.sealFailures);
	}

	@Test
	void sealDamageFloorsIntegrityAndUnsealResumes() {
		IncidentRecord record = IncidentControl.spawn(request(42L, 5, IncidentStage.INITIAL));
		assertTrue(IncidentControl.seal(record.id, 1).ok());
		assertEquals(0, IncidentControl.damageSeal(record.id, Integer.MAX_VALUE));
		assertFalse(record.sealed);
		assertTrue(IncidentControl.unseal(record.id) == false);
	}

	@Test
	void relocateLeavesScarAndStopsDependentNode() {
		IncidentRecord record = IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL));
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
		IncidentRecord record = IncidentControl.spawn(request(42L, 3, IncidentStage.INITIAL));
		SecondaryNode node = IncidentControl.forceSecondary(record.id, new BlockPos(9, 3, 4));
		assertTrue(node.selfSustaining());
		IncidentControl.cleanup(record.id);
		assertTrue(record.scarred);
		assertEquals(IncidentStage.INITIAL, record.stage);
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
		IncidentRecord record = IncidentControl.spawn(request(42L, 5, IncidentStage.INITIAL));
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

	private static IncidentControl.SpawnRequest request(long seed, int grade, IncidentStage stage) {
		ResourceKey<Level> dimension = Level.OVERWORLD;
		return new IncidentControl.SpawnRequest(BlockPos.ZERO, dimension, "blight", grade, seed, stage,
				"cursed_eye", SourceKind.OBJECT, 8.0);
	}

	private static final class RecordingSink implements IncidentWorldSink {
		int stageDeltas;

		@Override
		public void applyStageDelta(net.minecraft.server.level.ServerLevel level, IncidentRecord record,
				IncidentStage from, IncidentStage to) {
			stageDeltas++;
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
