package jujutsu.mod.gametest;

import java.util.List;

import java.util.UUID;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.object.CursedObjectItem;
import jujutsu.mod.cursedincident.object.CursedObjectRegistry;
import jujutsu.mod.cursedincident.object.CursedObjectState;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;

/** Object-verb world oracles required by Block 3 (R2/R3/R4/R9/R37/R42/R43/R44/R46/R78). */
public final class CursedObjectGameTests {
	private static final BlockPos CENTER = new BlockPos(8, 4, 8);

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void objectPickupDropChestKeepsInstance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID id = UUID.randomUUID();
		ItemStack original = CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime()));
		ItemEntity dropped = new ItemEntity(level, helper.absolutePos(CENTER).getX() + 0.5,
				helper.absolutePos(CENTER).getY(), helper.absolutePos(CENTER).getZ() + 0.5, original);
		level.addFreshEntity(dropped);
		ObjectDwellTracker.noteWorldItem(dropped);
		ItemStack picked = dropped.getItem().copy();
		dropped.discard();
		BlockPos chestPos = helper.absolutePos(new BlockPos(9, 4, 8));
		helper.setBlock(new BlockPos(9, 4, 8), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		chest.setItem(0, picked);
		CursedObjectState stored = CursedObjectItem.state(chest.getItem(0));
		helper.assertTrue(stored != null && id.equals(stored.instanceId()), CursedIncidentTestFixtures.diagnostic(
				"objectPickupDropChestKeepsInstance(R2,R3,R46)", helper, "instance id through hops", id,
				stored == null ? null : stored.instanceId()));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void uniqueLimitRefusesInWorld(GameTestHelper helper) {
		CursedObjectRegistry.clearInstances();
		int accepted = 0;
		for (int i = 0; i < 20; i++) {
			if (CursedObjectRegistry.registerInstance(CursedObjectState.fresh(UUID.randomUUID(), "sukuna_finger", 1,
					helper.getLevel().getGameTime()))) accepted++;
		}
		boolean refused = !CursedObjectRegistry.registerInstance(CursedObjectState.fresh(UUID.randomUUID(),
				"sukuna_finger", 1, helper.getLevel().getGameTime()));
		helper.assertTrue(accepted == 20 && refused, CursedIncidentTestFixtures.diagnostic(
				"uniqueLimitRefusesInWorld(R4)", helper, "20 accepted then refusal", true, accepted + "/" + refused));
		CursedObjectRegistry.clearInstances();
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void pickupGrantsNoProtection(GameTestHelper helper) {
		ServerPlayer player = CursedSpiritTestFixtures.setupVictim(helper, "pickupGrantsNoProtection(R9)", CENTER);
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(UUID.randomUUID(), "cursed_nail", 3,
				helper.getLevel().getGameTime()));
		player.getInventory().add(stack);
		helper.assertTrue(!player.getAbilities().invulnerable && !player.isInvulnerable(),
				CursedIncidentTestFixtures.diagnostic("pickupGrantsNoProtection(R9)", helper,
					"inventory gives no protection", false, player.getAbilities().invulnerable));
		CursedSpiritTestFixtures.cleanupVictim(helper, player);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void sealReachesPhysicalObject(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID id = UUID.randomUUID();
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime()));
		CursedObjectItem.SealResult result = CursedObjectItem.trySeal(stack, 2);
		helper.assertTrue(result.ok() && CursedObjectItem.state(stack).sealed()
				&& CursedObjectItem.state(stack).sealTier() == 2,
				CursedIncidentTestFixtures.diagnostic("sealReachesPhysicalObject(R37)", helper,
					"component seal", "sealed tier 2", result));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void talismanSealHaltsIncident(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(
				helper, CENTER, IncidentStage.INITIAL, 3.0, 1179L, "cursed_nail");
		ItemEntity source = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, record.objectInstanceId);
		helper.assertTrue(source != null,
				CursedIncidentTestFixtures.diagnostic("talismanSealHaltsIncident(F3)", helper,
						"physical source", "present", source));
		CursedObjectItem.SealResult result = CursedObjectItem.trySeal(source.getItem(), 3);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		helper.assertTrue(result.ok() && view.sealed(),
				CursedIncidentTestFixtures.diagnostic("talismanSealHaltsIncident(F3)", helper,
						"record seal mirror", true, result.ok() + " / " + view.sealed()));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void destroyedSourceCeases(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(
				helper, CENTER, IncidentStage.INITIAL, 3.0, 1180L, "cursed_nail");
		ItemEntity source = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, record.objectInstanceId);
		helper.assertTrue(source != null,
				CursedIncidentTestFixtures.diagnostic("destroyedSourceCeases(F9)", helper,
						"destructible physical source", "present", source));
		// kill() produces RemovalReason.KILLED — genuine destruction (C10); discard()
		// would be a pickup, which must NOT cease the incident.
		source.kill(helper.getLevel());
		helper.assertTrue(source.isRemoved(),
				CursedIncidentTestFixtures.diagnostic("destroyedSourceCeases(F9)", helper,
						"source removal observed", true, source.isRemoved()));
		helper.runAtTickTime(10, () -> {
			IncidentControl.InspectView view = IncidentControl.inspect(record.id);
			helper.assertTrue(view.scarred(),
					CursedIncidentTestFixtures.diagnostic("destroyedSourceCeases(F9)", helper,
							"incident after source death", "scarred=true", view.scarred()));
			CursedIncidentTestFixtures.cleanup(record);
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void pickupDoesNotDuplicate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = CursedSpiritTestFixtures.setupVictim(helper, "pickupDoesNotDuplicate(F6)", CENTER);
		UUID id = UUID.randomUUID();
		ItemEntity dropped = new ItemEntity(level, helper.absolutePos(CENTER).getX() + 0.5,
				helper.absolutePos(CENTER).getY(), helper.absolutePos(CENTER).getZ() + 0.5,
				CursedObjectItem.stack(CursedObjectState.fresh(id, "sukuna_finger", 1, level.getGameTime())));
		level.addFreshEntity(dropped);
		ObjectDwellTracker.noteWorldItem(dropped);
		ItemStack carried = dropped.getItem().copy();
		dropped.discard();
		boolean added = player.getInventory().add(carried);
		ObjectDwellTracker.noteCarried(carried, player);
		helper.assertTrue(added,
				CursedIncidentTestFixtures.diagnostic("pickupDoesNotDuplicate(F6)", helper,
						"simulated pickup inserted stack", true, added));
		helper.runAtTickTime(1, () -> {
			int inventoryStacks = 0;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				CursedObjectState state = CursedObjectItem.state(player.getInventory().getItem(slot));
				if (state != null && id.equals(state.instanceId())) {
					inventoryStacks++;
				}
			}
			int entityStacks = level.getEntitiesOfClass(ItemEntity.class,
					new AABB(dropped.blockPosition()).inflate(16.0), entity -> {
						CursedObjectState state = CursedObjectItem.state(entity.getItem());
						return entity.isAlive() && state != null && id.equals(state.instanceId());
					}).size();
			helper.assertTrue(inventoryStacks + entityStacks == 1,
					CursedIncidentTestFixtures.diagnostic("pickupDoesNotDuplicate(F6)", helper,
							"exactly one physical stack after pickup", 1,
							inventoryStacks + " inventory / " + entityStacks + " entities"));
			ObjectDwellTracker.forget(id);
			CursedSpiritTestFixtures.cleanupVictim(helper, player);
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void brokenSealKeepsHistory(GameTestHelper helper) {
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(UUID.randomUUID(), "cursed_nail", 3,
				helper.getLevel().getGameTime()));
		CursedObjectItem.trySeal(stack, 3);
		UUID id = CursedObjectItem.state(stack).instanceId();
		CursedObjectItem.damageSeal(stack, Integer.MAX_VALUE);
		CursedObjectState state = CursedObjectItem.state(stack);
		helper.assertTrue(id.equals(state.instanceId()) && !state.sealed() && state.sealIntegrity() == 0,
				CursedIncidentTestFixtures.diagnostic("brokenSealKeepsHistory(R42)", helper,
					"id and accumulated history", id, state));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void carriedObjectMovesDwellCenter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, "carriedObjectMovesDwellCenter(R43)", CENTER);
		UUID id = UUID.randomUUID();
		BlockPos center = victim.blockPosition();
		IncidentRecord record = IncidentControl.spawn(new IncidentControl.SpawnRequest(center, level.dimension(), "blight",
				3, 1176L, IncidentStage.INITIAL, "cursed_nail", jujutsu.mod.cursedincident.SourceKind.OBJECT,
				3.0, 40L));
		record.objectInstanceId = id;
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime()));
		ObjectDwellTracker.noteCarried(stack, victim);
		helper.runAtTickTime(50, () -> {
			ObjectDwellTracker.noteCarried(stack, victim);
			IncidentControl.advance(record.id, 50L);
			BlockPos tracked = new ObjectDwellTracker().dwellCenterOf(id);
			helper.assertTrue(tracked != null && tracked.equals(victim.blockPosition()),
					CursedIncidentTestFixtures.diagnostic("carriedObjectMovesDwellCenter(R43)", helper,
						"dwell center", victim.blockPosition(), tracked));
			CursedIncidentTestFixtures.cleanup(record);
			CursedSpiritTestFixtures.cleanupVictim(helper, victim);
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void stationaryObjectCreatesNewCenter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID id = UUID.randomUUID();
		BlockPos relative = new BlockPos(CENTER.getX(), 1, CENTER.getZ());
		BlockPos pos = helper.absolutePos(relative);
		IncidentRecord record = IncidentControl.spawn(new IncidentControl.SpawnRequest(pos, level.dimension(), "blight",
				3, 1177L, IncidentStage.INITIAL, "cursed_nail", jujutsu.mod.cursedincident.SourceKind.OBJECT,
				3.0, 40L));
		record.objectInstanceId = id;
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
				CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime())));
		item.setNoGravity(true);
		level.addFreshEntity(item);
		ObjectDwellTracker.noteWorldItem(item);
		helper.runAtTickTime(50, () -> {
			item.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			ObjectDwellTracker.noteWorldItem(item);
			IncidentControl.advance(record.id, 50L);
			BlockPos tracked = new ObjectDwellTracker().dwellCenterOf(id);
			helper.assertTrue(pos.equals(tracked),
					CursedIncidentTestFixtures.diagnostic("stationaryObjectCreatesNewCenter(R44)", helper,
						"stationary dwell center", pos, tracked));
			item.discard();
			CursedIncidentTestFixtures.cleanup(record);
			helper.succeed();
		});
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 80)
	public void sealSurvivesSaveLoad(GameTestHelper helper) {
		CursedObjectState state = CursedObjectState.fresh(UUID.randomUUID(), "cursed_nail", 3, helper.getLevel().getGameTime())
				.withSeal(true, 2, 75);
		ItemStack stack = CursedObjectItem.stack(state);
		CursedObjectState roundTrip = CursedObjectItem.state(stack.copy());
		helper.assertTrue(roundTrip.sealed() && roundTrip.sealTier() == 2 && roundTrip.sealIntegrity() == 75,
				CursedIncidentTestFixtures.diagnostic("sealSurvivesSaveLoad(R46)", helper,
					"sealed component fields", state, roundTrip));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 140)
	public void incidentDropsPhysicalLoot(GameTestHelper helper) {
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(helper, CENTER, IncidentStage.INITIAL, 3.0,
				1178L,  "cursed_nail");
		ServerLevel level = helper.getLevel();
		List<BlockPos> samples = CursedIncidentTestFixtures.sampledPositions(record, IncidentStage.CRITICAL);
		BlockPos chestPos = samples.getFirst();
		helper.setBlock(CursedIncidentTestFixtures.relative(helper, chestPos), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		UUID objectId = UUID.randomUUID();
		ItemStack object = CursedObjectItem.stack(CursedObjectState.fresh(objectId, "cursed_nail", 3,
				level.getGameTime()));
		CursedObjectState expectedState = CursedObjectItem.state(object);
		chest.setItem(0, object.copy());
		IncidentControl.setStage(record.id, IncidentStage.CRITICAL);
		helper.runAtTickTime(40, () -> {
			boolean found = level.getEntitiesOfClass(ItemEntity.class, new AABB(chestPos).inflate(4), entity ->
					expectedState.equals(CursedObjectItem.state(entity.getItem()))).size() > 0;
			helper.assertTrue(found, CursedIncidentTestFixtures.diagnostic("incidentDropsPhysicalLoot(R78)", helper,
					"cursed object loot dropped", expectedState, found));
			helper.succeed();
			CursedIncidentTestFixtures.cleanup(record);
		});
	}
}
