package jujutsu.mod.gametest;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;
import jujutsu.mod.cursedincident.infection.InfectionSink;
import jujutsu.mod.cursedincident.infection.ZoneGeometry;
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
		IncidentRecord record = IncidentControl.spawn(new IncidentControl.SpawnRequest(far, level.dimension(), "blight",
				3, 1108L, IncidentStage.INITIAL, null, SourceKind.FREE, RADIUS));
		IncidentControl.advance(record.id, 40L);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		boolean countersZero = view.workCounters().values().stream().allMatch(value -> value == 0L);
		helper.assertTrue(!before && !level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4)
				&& view.ageTicks() >= 40L && countersZero,
				CursedIncidentTestFixtures.diagnostic("unloadedCenterStillAges(R56)", helper,
						"logical age without loading", "age>=40 and counters=0", view));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void saveLoadRoundTrip(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.INFESTED, RADIUS, 1109L);
		record.dwellTicks = 17L;
		IncidentControl.reseed(record.id, 9911L);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		helper.assertTrue(view.seed() == 9911L && view.stage() == IncidentStage.INFESTED,
				CursedIncidentTestFixtures.diagnostic("saveLoadRoundTrip(R54,R55)", helper,
					"persisted logical fields", "seed/stage retained", view));
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
		IncidentRecord[] records = new IncidentRecord[3];
		for (int i = 0; i < records.length; i++) {
			records[i] = CursedIncidentTestFixtures.spawnFree(helper, new BlockPos(4 + i * 4, 4, 8), IncidentStage.CATASTROPHIC,
					RADIUS, 1113L + i);
			sink.applyStageDelta(level, records[i], IncidentStage.INFESTED, IncidentStage.CATASTROPHIC);
		}
		long before = 0L;
		for (IncidentRecord record : records) before += record.counters.blocksChanged;
		for (IncidentRecord record : records) sink.tickZone(level, record, 64);
		long after = 0L;
		for (IncidentRecord record : records) after += record.counters.blocksChanged;
		helper.assertTrue(after - before <= InfectionSink.PER_TICK_BLOCK_BUDGET,
				CursedIncidentTestFixtures.diagnostic("perTickBudgetBounded(R70,R71)", helper,
					"shared block budget", "<=64", after - before));
		for (IncidentRecord record : records) CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void noMapMarkerTripwire(GameTestHelper helper) {
		helper.assertTrue(CursedIncidentVfxIds.LIVE.size() == 5,
				CursedIncidentTestFixtures.diagnostic("noMapMarkerTripwire(R47)", helper,
					"incident presentation has no map marker", 5, CursedIncidentVfxIds.LIVE.size()));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void curseTopUpRespectsCap(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CRITICAL, RADIUS, 1114L);
		InfectionSink sink = new InfectionSink();
		sink.tickZone(helper.getLevel(), record, 64);
		int nearby = helper.getLevel().getEntitiesOfClass(CursedSpiritEntity.class,
				new AABB(record.center).inflate(CursedSpiritProfile.CROWD_RADIUS)).size();
		helper.assertTrue(nearby <= CursedSpiritProfile.MAX_SPIRITS_NEARBY,
				CursedIncidentTestFixtures.diagnostic("curseTopUpRespectsCap(R69)", helper,
						"incident crowd cap", "<=" + CursedSpiritProfile.MAX_SPIRITS_NEARBY, nearby));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 520)
	public void animalsCulledInZone(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnFree(helper, CENTER, IncidentStage.CATASTROPHIC, RADIUS, 1115L);
		for (int i = 0; i < 12; i++) helper.spawn(EntityType.COW, new BlockPos(6 + i % 4, 1, 6 + i / 4));
		helper.runAtTickTime(500, () -> {
			long dead = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(record.center).inflate(record.radius), cow -> cow.isDeadOrDying()).size();
			helper.assertTrue(dead > 0L, CursedIncidentTestFixtures.diagnostic("animalsCulledInZone(R32)", helper,
					"cull cadence", ">0 dead animals", dead));
			CursedIncidentTestFixtures.cleanup(record);
		});
	}
}
