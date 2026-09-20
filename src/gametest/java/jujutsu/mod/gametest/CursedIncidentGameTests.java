package jujutsu.mod.gametest;
import com.mojang.serialization.JsonOps;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.persist.IncidentSavedData;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;
import jujutsu.mod.cursedincident.infection.InfectionQueue;
import jujutsu.mod.cursedincident.infection.InfectionSink;
import jujutsu.mod.cursedincident.runtime.PerceptionOverrideRuntime;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.cursedincident.object.CursedObjectItem;


/** Issue #110 world oracles. Every method names the requirements it defends. */
public final class CursedIncidentGameTests {
	private static final BlockPos CENTER = new BlockPos(8, 4, 8);
	private static final double RADIUS = 3.0;

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void spawnCreatesZoneAndSource(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(helper, CENTER, IncidentStage.INITIAL,
				RADIUS, 1101L, "sukuna_finger");
		helper.assertTrue(record != null && IncidentControl.inspect(record.id).center().equals(helper.absolutePos(CENTER)),
				CursedIncidentTestFixtures.diagnostic("spawnCreatesZoneAndSource(R2,R16)", helper,
						"record and centre", "registered", record));
		ItemEntity object = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, record.objectInstanceId);
		helper.assertTrue(object != null && CursedObjectItem.state(object.getItem()) != null,
				CursedIncidentTestFixtures.diagnostic("spawnCreatesZoneAndSource(R2,R16)", helper,
						"physical source", "ItemEntity with CURSED_OBJECT_STATE", object));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void stageAdvanceChangesBlocks(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INITIAL, RADIUS, 1102L);
		ServerLevel level = helper.getLevel();
		List<BlockPos> samples = CursedIncidentTestFixtures.sampledPositions(record, IncidentStage.GROWING);
		for (BlockPos sample : samples) {
			helper.setBlock(CursedIncidentTestFixtures.relative(helper, sample), Blocks.GRASS_BLOCK);
		}
		IncidentControl.setStage(record.id, IncidentStage.GROWING);
		helper.runAtTickTime(20, () -> {
			long changed = samples.stream().filter(pos -> level.getBlockState(pos).is(Blocks.COARSE_DIRT)).count();
			helper.assertTrue(changed > 0, CursedIncidentTestFixtures.diagnostic("stageAdvanceChangesBlocks(R36)", helper,
					"seeded sample changed", ">0", changed));
			CursedIncidentTestFixtures.cleanup(record);
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void infectionDestroysPlayerBlocks(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INITIAL, RADIUS, 1103L);
		ServerLevel level = helper.getLevel();
		List<BlockPos> samples = CursedIncidentTestFixtures.sampledPositions(record, IncidentStage.CRITICAL);
		for (int i = 0; i < samples.size(); i++) {
			BlockPos relative = CursedIncidentTestFixtures.relative(helper, samples.get(i));
			helper.setBlock(relative, i == 0 ? Blocks.OAK_PLANKS : i == 1 ? Blocks.CHEST : Blocks.BEACON);
		}
		IncidentControl.setStage(record.id, IncidentStage.CRITICAL);
		helper.runAtTickTime(20, () -> {
			long air = samples.stream().filter(pos -> level.getBlockState(pos).isAir()).count();
			helper.assertTrue(air > 0, CursedIncidentTestFixtures.diagnostic("infectionDestroysPlayerBlocks(R26,R30)", helper,
					"player blocks destroyed", ">0", air));
			helper.succeed();
			CursedIncidentTestFixtures.cleanup(record);
		});
	}
	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 160)
	public void containerDropsContents(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INITIAL, RADIUS, 1104L);
		ServerLevel level = helper.getLevel();
		List<BlockPos> samples = CursedIncidentTestFixtures.sampledPositions(record, IncidentStage.CRITICAL);
		BlockPos chestPos = samples.getFirst();
		helper.setBlock(CursedIncidentTestFixtures.relative(helper, chestPos), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		ItemStack expected = new ItemStack(Items.DIAMOND, 3);
		chest.setItem(0, expected.copy());
		IncidentControl.setStage(record.id, IncidentStage.CRITICAL);
		helper.runAtTickTime(40, () -> {
			boolean dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(chestPos).inflate(4), item ->
					item.getItem().is(Items.DIAMOND) && item.getItem().getCount() == expected.getCount()).size() > 0;
			helper.assertTrue(dropped, CursedIncidentTestFixtures.diagnostic("containerDropsContents(R31)", helper,
					"container contents preserved", expected, dropped));
			helper.succeed();
			CursedIncidentTestFixtures.cleanup(record);
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 80)
	public void killOnlyCleanupKeepsScar(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1105L);
		IncidentControl.cleanup(record.id);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		helper.assertTrue(view.scarred(), CursedIncidentTestFixtures.diagnostic("killOnlyCleanupKeepsScar(R26,R30)", helper,
				"scar flag", true, view.scarred()));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 80)
	public void relocateStopsDependentKeepsScar(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INFESTED, RADIUS, 1106L);
		BlockPos old = record.center;
		BlockPos next = helper.absolutePos(new BlockPos(10, 4, 10));
		IncidentControl.relocate(record.id, next);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		helper.assertTrue(view.center().equals(next) && view.scars().contains(old),
				CursedIncidentTestFixtures.diagnostic("relocateStopsDependentKeepsScar(R34)", helper,
					"new centre and scar", next, view));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 80)
	public void secondarySurvivesSourceRemoval(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1107L);
		IncidentControl.forceSecondary(record.id, helper.absolutePos(new BlockPos(11, 4, 11)));
		IncidentControl.cleanup(record.id);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		helper.assertTrue(!view.secondaries().isEmpty(), CursedIncidentTestFixtures.diagnostic(
				"secondarySurvivesSourceRemoval(R24)", helper, "secondary record", ">0", view.secondaries().size()));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void unloadedCenterStillAges(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(new BlockPos(160, 4, 160));
		boolean before = level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4);
		IncidentRecord record = CursedIncidentTestFixtures.created(IncidentControl.spawn(
				new IncidentControl.SpawnRequest(far, level.dimension(), "blight",
						3, 1108L, IncidentStage.INITIAL, null, SourceKind.FREE, RADIUS)));
		IncidentControl.advance(record.id, 40L);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		long blocksChanged = view.workCounters().getOrDefault("blocks_changed", 0L);
		boolean pendingTransition = !record.pendingDeltas.isEmpty();
		helper.assertTrue(!before && !level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4)
				&& view.ageTicks() >= 40L && blocksChanged == 0L && pendingTransition,
				CursedIncidentTestFixtures.diagnostic("unloadedCenterStillAges(R56)", helper,
						"logical age without loading", "age>=40, blocks_changed=0, pending_deltas>0", view));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void saveLoadRoundTrip(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INFESTED,
				RADIUS, 1109L);
		record.dwellTicks = 17L;
		IncidentControl.reseed(record.id, 9911L);

		IncidentSavedData before = new IncidentSavedData(Map.of(record.id, record), 23L);
		var encoded = IncidentSavedData.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow();
		IncidentSavedData after = IncidentSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
		IncidentRecord loaded = after.get(record.id);
		helper.assertTrue(loaded != null
				&& loaded.seed == 9911L
				&& loaded.stage == IncidentStage.INFESTED
				&& loaded.center.equals(record.center)
				&& loaded.radius == RADIUS
				&& loaded.dwellTicks == 17L
				&& after.pressure() == 23L,
				CursedIncidentTestFixtures.diagnostic("saveLoadRoundTrip(R54,R55)", helper,
						"IncidentSavedData codec round-trip", "record fields and pressure retained", loaded));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void offlineCatchUpEqualsStepwise(GameTestHelper helper) {
		IncidentRecord first = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INITIAL, RADIUS, 1110L);
		IncidentRecord second = CursedIncidentTestFixtures.spawnFree(helper, new BlockPos(11, 4, 8), IncidentStage.INITIAL, RADIUS, 1111L);
		IncidentControl.advance(first.id, 120000L);
		for (int i = 0; i < 12; i++) IncidentControl.advance(second.id, 10000L);
		helper.assertTrue(IncidentControl.inspect(first.id).stage() == IncidentControl.inspect(second.id).stage(),
				CursedIncidentTestFixtures.diagnostic("offlineCatchUpEqualsStepwise(R55)", helper,
					"same transition engine", "equal stages", first.stage + "/" + second.stage));
		CursedIncidentTestFixtures.cleanup(first);
		CursedIncidentTestFixtures.cleanup(second);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void criticalZoneNonMagePerceives(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1112L);
		var victim = CursedSpiritTestFixtures.setupVictim(helper, "criticalZoneNonMagePerceives(R51)", CENTER);
		PerceptionOverrideRuntime.tick(helper.getLevel().getServer());
		helper.assertTrue(CursePerception.perceives(victim), CursedIncidentTestFixtures.diagnostic(
				"criticalZoneNonMagePerceives(R51)", helper, "critical override", true, CursePerception.perceives(victim)));
		CursedSpiritTestFixtures.cleanupVictim(helper, victim);
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void perTickBudgetBounded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		InfectionSink sink = new InfectionSink();
		IncidentStage from = IncidentStage.GROWING;
		IncidentStage next = from.next();
		IncidentRecord[] records = new IncidentRecord[3];
		for (int i = 0; i < records.length; i++) {
			records[i] = CursedIncidentTestFixtures.spawnFree(
					helper, new BlockPos(3 + i * 5, 4, 8), from, 6.0, 1113L + i);
			for (BlockPos sample : CursedIncidentTestFixtures.sampledPositions(records[i], next)) {
				helper.setBlock(CursedIncidentTestFixtures.relative(helper, sample), Blocks.GRASS_BLOCK);
			}
			// The fixture's INITIAL -> GROWING delta is not the pass under test.
			InfectionQueue.forIncident(records[i]).clear();
			sink.applyStageDelta(level, records[i], from, next);
		}
		long before = 0L;
		for (IncidentRecord record : records) {
			before += record.counters.blocksChanged;
		}
		for (IncidentRecord record : records) {
			sink.tickZone(level, record, record.center, null, InfectionSink.PER_TICK_BLOCK_BUDGET);
		}
		long after = 0L;
		for (IncidentRecord record : records) {
			after += record.counters.blocksChanged;
		}
		long applied = after - before;
		helper.assertTrue(applied > 0 && applied <= InfectionSink.PER_TICK_BLOCK_BUDGET,
				CursedIncidentTestFixtures.diagnostic("perTickBudgetBounded(R70,R71)", helper,
						"mappable edits in one shared drain pass", "1..64", applied));
		for (IncidentRecord record : records) {
			CursedIncidentTestFixtures.cleanup(record);
		}
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void noMapMarkerTripwire(GameTestHelper helper) {
		Set<?> expectedLive = Set.of(
				CursedIncidentVfxIds.ZONE_AMBIENT,
				CursedIncidentVfxIds.STAGE_PULSE,
				CursedIncidentVfxIds.SEAL_APPLIED,
				CursedIncidentVfxIds.SEAL_DEGRADE,
				CursedIncidentVfxIds.SEAL_BREAK,
				CursedIncidentVfxIds.SECONDARY_BIRTH);
		Set<?> expectedPhysical = Set.of(
				CursedIncidentVfxIds.STAGE_PULSE,
				CursedIncidentVfxIds.SEAL_APPLIED,
				CursedIncidentVfxIds.SEAL_BREAK,
				CursedIncidentVfxIds.SECONDARY_BIRTH);
		Set<?> expectedCurse = Set.of(
				CursedIncidentVfxIds.ZONE_AMBIENT,
				CursedIncidentVfxIds.SEAL_DEGRADE);
		helper.assertTrue(CursedIncidentVfxIds.LIVE.equals(expectedLive),
				CursedIncidentTestFixtures.diagnostic("noMapMarkerTripwire(R47)", helper,
						"live incident cues", expectedLive, CursedIncidentVfxIds.LIVE));
		helper.assertTrue(CursedIncidentVfxIds.PHYSICAL.equals(expectedPhysical)
				&& CursedIncidentVfxIds.CURSE.equals(expectedCurse)
				&& CursedIncidentVfxIds.LIVE.containsAll(CursedIncidentVfxIds.PHYSICAL)
				&& CursedIncidentVfxIds.LIVE.containsAll(CursedIncidentVfxIds.CURSE),
				CursedIncidentTestFixtures.diagnostic("noMapMarkerTripwire(R47)", helper,
						"cue classification without map marker", expectedPhysical + " / " + expectedCurse,
						CursedIncidentVfxIds.PHYSICAL + " / " + CursedIncidentVfxIds.CURSE));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void cleanupRemovesTaggedSpirits(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(
				helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1116L);
		String tag = "jujutsumod:incident/" + record.id;
		CursedSpiritEntity spirit = helper.spawn(
				JujutsuEntities.LESSER_CURSED_SPIRIT, CursedIncidentTestFixtures.relative(helper, record.center));
		spirit.addTag(tag);
		int before = helper.getLevel().getEntitiesOfClass(CursedSpiritEntity.class,
				new AABB(record.center).inflate(32.0), entity -> entity.getTags().contains(tag)).size();
		helper.assertTrue(before > 0,
				CursedIncidentTestFixtures.diagnostic("cleanupRemovesTaggedSpirits(F2)", helper,
						"tagged spirit before cleanup", ">0", before));
		IncidentControl.cleanup(record.id);
		helper.runAtTickTime(2, () -> {
			int remaining = helper.getLevel().getEntitiesOfClass(CursedSpiritEntity.class,
					new AABB(record.center).inflate(32.0), entity -> entity.getTags().contains(tag)).size();
			IncidentControl.InspectView view = IncidentControl.inspect(record.id);
			helper.assertTrue(remaining == 0 && view.scarred(),
					CursedIncidentTestFixtures.diagnostic("cleanupRemovesTaggedSpirits(F2)", helper,
							"tagged spirits and scar flag after cleanup", "0 / true",
							remaining + " / " + view.scarred()));
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void sealedIncidentAppliesNoEdits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(
				helper, CENTER, IncidentStage.INITIAL, 6.0, 1117L, "cursed_nail");
		ItemEntity source = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, record.objectInstanceId);
		helper.assertTrue(source != null,
				CursedIncidentTestFixtures.diagnostic("sealedIncidentAppliesNoEdits(F7)", helper,
						"physical source", "present", source));
		List<BlockPos> samples = CursedIncidentTestFixtures.sampledPositions(record, IncidentStage.GROWING);
		for (BlockPos sample : samples) {
			helper.setBlock(CursedIncidentTestFixtures.relative(helper, sample), Blocks.GRASS_BLOCK);
		}
		long before = record.counters.blocksChanged;
		CursedObjectItem.SealResult result = CursedObjectItem.trySeal(source.getItem(), 3);
		IncidentControl.InspectView sealed = IncidentControl.inspect(record.id);
		helper.assertTrue(result.ok() && sealed.sealed(),
				CursedIncidentTestFixtures.diagnostic("sealedIncidentAppliesNoEdits(F3,F7)", helper,
						"record mirrors physical seal", true, result.ok() + " / " + sealed.sealed()));
		IncidentControl.advance(record.id, 96_000L);
		new InfectionSink().tickZone(level, record, record.center, null, InfectionSink.PER_TICK_BLOCK_BUDGET);
		long changedBlocks = samples.stream()
				.filter(pos -> !level.getBlockState(pos).is(Blocks.GRASS_BLOCK)).count();
		long applied = record.counters.blocksChanged - before;
		helper.assertTrue(applied == 0L && changedBlocks == 0L,
				CursedIncidentTestFixtures.diagnostic("sealedIncidentAppliesNoEdits(F7)", helper,
						"sealed zone remains unchanged after advance and drain", "0 / 0",
						applied + " / " + changedBlocks));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void curseTopUpRespectsCap(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1114L);
		InfectionSink sink = new InfectionSink();
		AABB area = new AABB(record.center).inflate(CursedSpiritProfile.CROWD_RADIUS);
		int before = helper.getLevel().getEntitiesOfClass(CursedSpiritEntity.class, area).size();
		sink.tickZone(helper.getLevel(), record, record.center, null, 64);
		int after = helper.getLevel().getEntitiesOfClass(CursedSpiritEntity.class, area).size();
		boolean respected = before >= CursedSpiritProfile.MAX_SPIRITS_NEARBY
				? after == before
				: after <= CursedSpiritProfile.MAX_SPIRITS_NEARBY;
		helper.assertTrue(respected, CursedIncidentTestFixtures.diagnostic("curseTopUpRespectsCap(R69)", helper,
				"incident crowd cap", "no spawn at/above cap; otherwise <=cap", before + "->" + after));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void animalsCulledInZone(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CATASTROPHIC, RADIUS, 1115L);
		for (int i = 0; i < 12; i++) helper.spawn(EntityType.COW, new BlockPos(6 + i % 4, 1, 6 + i / 4));
		AABB area = new AABB(record.center).inflate(record.radius);
		int before = helper.getLevel().getEntitiesOfClass(Cow.class, area, Cow::isAlive).size();
		// Deterministic drive (same pattern as curseTopUpRespectsCap): the runtime loop's
		// 400-tick cadence plus the per-cow 0.45 roll made the old wait-500 version episodic —
		// cows could wander out of the 3-block box between passes, and a bad gameTime seed
		// could blank a whole pass. Driving tickZone directly with a re-armed cadence tests
		// the same cull path (due-gate + cursed_zone damage + counter) without the wait.
		InfectionSink sink = new InfectionSink();
		long culled = 0L;
		for (int attempt = 0; attempt < 5 && culled == 0L; attempt++) {
			record.lastCullGameTime = Long.MIN_VALUE;
			sink.tickZone(helper.getLevel(), record, record.center, null, 64);
			culled = record.counters.animalsCulled;
		}
		final long finalCulled = culled;
		helper.runAtTickTime(5, () -> {
			helper.assertTrue(before > 0 && finalCulled > 0L, CursedIncidentTestFixtures.diagnostic("animalsCulledInZone(R32)", helper,
					"cull cadence and cursed_zone damage", "animals>0 and counter>0", before + "->" + finalCulled));
			CursedIncidentTestFixtures.cleanup(record);
			helper.succeed();
		});
	}

	/**
	 * Zone-state broadcasts reach only perceiving players — a non-mage client must never
	 * learn a zone exists (spec §12 perception contract, C5 CURSE classification).
	 */
	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 80)
	public void zoneStateAudienceIsPerceiversOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.GROWING, RADIUS, 1120L);
		var none = CursedSpiritTestFixtures.setupVictim(helper, "zoneStateAudienceIsPerceiversOnly", new BlockPos(6, 4, 8));
		var mage = CursedSpiritTestFixtures.setupVictim(helper, "zoneStateAudienceIsPerceiversOnly", new BlockPos(10, 4, 8));
		jujutsu.mod.character.CharacterSelectionManager.select(mage, jujutsu.mod.character.JujutsuCharacter.MEGUMI);
		helper.runAtTickTime(5, () -> {
			try {
				List<net.minecraft.server.level.ServerPlayer> audience =
						jujutsu.mod.cursedincident.runtime.IncidentZoneSync.recipients(level, record.center);
				helper.assertTrue(audience.contains(mage) && !audience.contains(none),
						CursedIncidentTestFixtures.diagnostic("zoneStateAudienceIsPerceiversOnly(R-perception)", helper,
								"zone audience = perceivers", "[mage]", audience));
			} finally {
				CursedSpiritTestFixtures.cleanupVictim(helper, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
				CursedIncidentTestFixtures.cleanup(record);
			}
			helper.succeed();
		});
	}

	/**
	 * The demo command produces a fully dressed incident in one call — object source,
	 * zone record, spawn wave — at the invoking player's position (spec §15).
	 */
	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void demoCommandSpawnsDressedIncident(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos playerPos = helper.absolutePos(CENTER);
		player.teleportTo(level, playerPos.getX() + 0.5, playerPos.getY(),
				playerPos.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
		helper.runAtTickTime(5, () -> {
			try {
				Set<java.util.UUID> before = new java.util.HashSet<>();
				for (IncidentRecord existing : IncidentControl.recordsForRuntime()) {
					before.add(existing.id);
				}
				int result;
				try {
					result = level.getServer().getCommands().getDispatcher().execute(
							"jujutsu incident demo", player.createCommandSourceStack().withPermission(2));
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
					helper.assertTrue(false, CursedIncidentTestFixtures.diagnostic(
							"demoCommandSpawnsDressedIncident(§15)", helper,
							"real demo command dispatch", "no CommandSyntaxException", failure));
					return;
				}
				List<IncidentRecord> created = IncidentControl.recordsForRuntime().stream()
						.filter(record -> !before.contains(record.id))
						.toList();
				helper.assertTrue(result == 1 && created.size() == 1,
						CursedIncidentTestFixtures.diagnostic("demoCommandSpawnsDressedIncident(§15)", helper,
								"real demo command dispatch", "result=1 and one new record", result + "/" + created));
				IncidentRecord record = created.getFirst();
				helper.assertTrue(record.objectInstanceId != null && record.sourceKind == SourceKind.OBJECT,
						CursedIncidentTestFixtures.diagnostic("demoCommandSpawnsDressedIncident(§15)", helper,
								"object source minted", "objectInstanceId set", record.objectInstanceId));
				helper.assertTrue(record.stage == IncidentStage.GROWING,
						CursedIncidentTestFixtures.diagnostic("demoCommandSpawnsDressedIncident(§15)", helper,
								"stage applied", "GROWING", record.stage));
				helper.assertTrue(record.center.equals(player.blockPosition()),
						CursedIncidentTestFixtures.diagnostic("demoCommandSpawnsDressedIncident(§15)", helper,
								"command uses invoking player position", player.blockPosition(), record.center));
				CursedIncidentTestFixtures.cleanup(record);
			} finally {
				CursedSpiritTestFixtures.cleanupVictim(helper, player);
			}
			helper.succeed();
		});
	}
}
