package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jujutsu.mod.cursedincident.persist.IncidentCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** R54/R59/R60 — every durable record field round-trips and corrupt enums are safe. */
class IncidentCodecTest {
	private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID NODE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID OBJECT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void fullRecordRoundTripKeepsDistinctFields() {
		IncidentRecord before = populated();
		var encoded = IncidentCodec.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow();
		IncidentRecord after = IncidentCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
		assertEquals(before.id, after.id);
		assertEquals(before.seed, after.seed);
		assertEquals(before.createdGameTime, after.createdGameTime);
		assertEquals(before.lastUpdateGameTime, after.lastUpdateGameTime);
		assertEquals(before.lastProcessedAgeTicks, after.lastProcessedAgeTicks);
		assertEquals(before.bonusAgeTicks, after.bonusAgeTicks);
		assertEquals(before.dimension, after.dimension);
		assertEquals(before.center, after.center);
		assertEquals(before.radius, after.radius);
		assertEquals(before.stage, after.stage);
		assertEquals(before.scarred, after.scarred);
		assertEquals(before.sourceKind, after.sourceKind);
		assertEquals(before.objectInstanceId, after.objectInstanceId);
		assertEquals(before.objectTypeId, after.objectTypeId);
		assertEquals(before.objectGrade, after.objectGrade);
		assertEquals(before.sourcePos, after.sourcePos);
		assertEquals(before.sourceContainer, after.sourceContainer);
		assertEquals(before.templateId, after.templateId);
		assertEquals(before.params, after.params);
		assertEquals(before.secondaries, after.secondaries);
		assertEquals(before.pendingDeltas, after.pendingDeltas);
		assertEquals(before.pendingEdits, after.pendingEdits);
		assertEquals(before.transitions, after.transitions);
		assertEquals(before.scars, after.scars);
		assertEquals(before.sealed, after.sealed);
		assertEquals(before.sealIntegrity, after.sealIntegrity);
		assertEquals(before.sealTier, after.sealTier);
		assertEquals(before.sealFailures, after.sealFailures);
		assertEquals(before.knowledge, after.knowledge);
		assertEquals(before.dwellTicks, after.dwellTicks);
		assertEquals(before.lastTopUpGameTime, after.lastTopUpGameTime);
		assertEquals(before.lastContainerScanGameTime, after.lastContainerScanGameTime);
		assertEquals(before.lastCullGameTime, after.lastCullGameTime);
		assertEquals(before.lastAmbientGameTime, after.lastAmbientGameTime);
		assertEquals(before.dwellAnchor, after.dwellAnchor);
		assertEquals(before.counters.blocksChanged, after.counters.blocksChanged);
		assertEquals(before.counters.cursesSpawned, after.counters.cursesSpawned);
		assertEquals(before.counters.animalsCulled, after.counters.animalsCulled);
		assertEquals(before.counters.chunkEditsDeferred, after.counters.chunkEditsDeferred);
	}

	@Test
	void unknownStageAndEnumsUseSafeDefaults() {
		JsonObject object = IncidentCodec.CODEC.encodeStart(JsonOps.INSTANCE, populated())
				.result().orElseThrow().getAsJsonObject().deepCopy();
		object.addProperty("stage", "not_a_stage");
		object.addProperty("source_kind", "not_a_source");
		object.addProperty("knowledge", "not_knowledge");
		IncidentRecord decoded = IncidentCodec.CODEC.parse(JsonOps.INSTANCE, object).result().orElseThrow();
		assertEquals(IncidentStage.INITIAL, decoded.stage);
		assertEquals(SourceKind.FREE, decoded.sourceKind);
		assertEquals(KnowledgeLevel.UNKNOWN, decoded.knowledge);
	}

	@Test
	void missingIdIsReportedAsCorrupt() {
		JsonObject corrupt = new JsonObject();
		assertTrue(IncidentCodec.CODEC.parse(JsonOps.INSTANCE, corrupt).error().isPresent());
	}

	private static IncidentRecord populated() {
		IncidentRecord record = new IncidentRecord();
		record.id = ID;
		record.seed = 0x1234_5678L;
		record.createdGameTime = 11;
		record.lastUpdateGameTime = 22;
		record.bonusAgeTicks = 33;
		record.dimension = Level.OVERWORLD;
		record.center = new BlockPos(10, 64, -4);
		record.radius = 17.5;
		record.stage = IncidentStage.CRITICAL;
		record.scarred = true;
		record.sourceKind = SourceKind.OBJECT;
		record.objectInstanceId = OBJECT_ID;
		record.objectTypeId = "cursed_eye";
		record.objectGrade = 2;
		record.sourcePos = new BlockPos(11, 65, -3);
		record.sourceContainer = new BlockPos(12, 65, -2);
		record.templateId = "cataclysm";
		record.params = new IncidentParams("column", 17.5, Map.of("lesser", 4, "greater", 9),
				"ash_fall", List.of("investigate", "seal"), 0.85, true, true, 9876);
		record.secondaries.add(new SecondaryNode(NODE_ID, new BlockPos(20, 64, 20), 5.5, 44, true));
		record.transitions.add(new IncidentRecord.Transition(IncidentStage.INITIAL, IncidentStage.GROWING, 55));
		record.lastProcessedAgeTicks = 66;
		record.pendingDeltas.add(new IncidentRecord.PendingDelta(IncidentStage.GROWING, IncidentStage.INFESTED));
		record.pendingEdits.add(new IncidentRecord.PendingEdit(
				new BlockPos(14, 67, 0), Blocks.COARSE_DIRT.defaultBlockState(), true, null));
		record.lastTopUpGameTime = 77;
		record.lastContainerScanGameTime = 88;
		record.lastCullGameTime = 99;
		record.lastAmbientGameTime = 111;
		record.scars.add(new BlockPos(1, 2, 3));
		record.sealed = true;
		record.sealIntegrity = 199;
		record.sealTier = 2;
		record.sealFailures = 3;
		record.knowledge = KnowledgeLevel.SEALING_METHODS;
		record.dwellTicks = 123;
		record.dwellAnchor = new BlockPos(13, 66, -1);
		record.counters.blocksChanged = 7;
		record.counters.cursesSpawned = 8;
		record.counters.animalsCulled = 9;
		record.counters.chunkEditsDeferred = 10;
		return record;
	}
}
