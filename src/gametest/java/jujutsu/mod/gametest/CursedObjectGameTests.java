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
		// The durable index counts toward the cap since C10; isolate this oracle from
		// earlier tests' minted entries by binding a fresh store for the duration.
		CursedObjectRegistry.bind(new jujutsu.mod.cursedincident.persist.IncidentSavedData());
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
		CursedObjectRegistry.bind(jujutsu.mod.cursedincident.persist.IncidentSavedData.get(
				helper.getLevel().getServer().overworld()));
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
		IncidentRecord record = CursedIncidentTestFixtures.created(IncidentControl.spawn(
				new IncidentControl.SpawnRequest(center, level.dimension(), "blight",
						3, 1176L, IncidentStage.INITIAL, "cursed_nail", jujutsu.mod.cursedincident.SourceKind.OBJECT,
						3.0, 40L)));
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
		IncidentRecord record = CursedIncidentTestFixtures.created(IncidentControl.spawn(
				new IncidentControl.SpawnRequest(pos, level.dimension(), "blight",
						3, 1177L, IncidentStage.INITIAL, "cursed_nail", jujutsu.mod.cursedincident.SourceKind.OBJECT,
						3.0, 40L)));
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

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void sealDamageBreaksSealThroughContract(GameTestHelper helper) {
		// C16: the single damage contract — stack damage routes through IncidentControl and
		// writes the broken seal back onto the physical stack.
		IncidentRecord record = CursedIncidentTestFixtures.spawnObject(
				helper, CENTER, IncidentStage.INITIAL, 3.0, 1181L, "cursed_nail");
		ItemEntity source = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, record.objectInstanceId);
		helper.assertTrue(source != null,
				CursedIncidentTestFixtures.diagnostic("sealDamageBreaksSealThroughContract(C16)", helper,
						"physical source", "present", source));
		CursedObjectItem.trySeal(source.getItem(), 3);
		CursedObjectItem.damageSeal(source.getItem(), Integer.MAX_VALUE);
		IncidentControl.InspectView view = IncidentControl.inspect(record.id);
		CursedObjectState stackState = CursedObjectItem.state(source.getItem());
		helper.assertTrue(!view.sealed() && stackState != null && !stackState.sealed()
						&& stackState.sealIntegrity() == 0,
				CursedIncidentTestFixtures.diagnostic("sealDamageBreaksSealThroughContract(C16)", helper,
						"record and stack unsealed", "false/false/0",
						view.sealed() + "/" + (stackState == null ? null : stackState.sealed())
								+ "/" + (stackState == null ? null : stackState.sealIntegrity())));
		CursedIncidentTestFixtures.cleanup(record);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void sealedObjectInContainerDecaysFromDurableAnchor(GameTestHelper helper) {
		// C8/C9: a sealed object in a chest decays by its durable anchor — no zone tick and
		// no fresh observation timestamp forgives the elapsed interval.
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		UUID id = UUID.randomUUID();
		CursedObjectState state = CursedObjectState.fresh(id, "cursed_nail", 3, now)
				.withSeal(true, 1, 100)
				.withLastDecayGameTime(now - 2 * ObjectDwellTracker.DEFAULT_DWELL_TICKS);
		BlockPos chestPos = helper.absolutePos(new BlockPos(9, 4, 8));
		helper.setBlock(new BlockPos(9, 4, 8), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		chest.setItem(0, CursedObjectItem.stack(state));
		new ObjectDwellTracker().noteContainer(level, chestPos, chest.getItem(0));
		CursedObjectState after = CursedObjectItem.state(chest.getItem(0));
		// Tier-1 decay is 8 integrity per day; two elapsed days leave 84 of 100.
		helper.assertTrue(after != null && after.sealed() && after.sealIntegrity() == 84
						&& after.lastDecayGameTime() == now,
				CursedIncidentTestFixtures.diagnostic("sealedObjectInContainerDecaysFromDurableAnchor(C8,C9)", helper,
						"integrity 84 anchor now", "84/" + now,
						after == null ? null : after.sealIntegrity() + "/" + after.lastDecayGameTime()));
		ObjectDwellTracker.forget(id);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void forgetEverywhereRemovesInventoryStackAndVoids(GameTestHelper helper) {
		// C15: cleanup removes the physical source from a player inventory and marks the id
		// voided so a leftover stack can never resurrect the incident.
		ServerPlayer player = CursedSpiritTestFixtures.setupVictim(helper, "forgetEverywhere(C15)", CENTER);
		UUID id = UUID.randomUUID();
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3,
				helper.getLevel().getGameTime()));
		player.getInventory().add(stack);
		// Inventory.add stores a copy and empties the passed stack — observe the stored one,
		// the same reference inventoryTick sees in production.
		ItemStack carried = null;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (CursedObjectItem.state(player.getInventory().getItem(slot)) != null) {
				carried = player.getInventory().getItem(slot);
				break;
			}
		}
		ObjectDwellTracker.noteCarried(carried, player);
		ObjectDwellTracker.forgetEverywhere(id);
		boolean stillCarried = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			CursedObjectState slotState = CursedObjectItem.state(player.getInventory().getItem(slot));
			if (slotState != null && id.equals(slotState.instanceId())) {
				stillCarried = true;
			}
		}
		helper.assertTrue(!stillCarried && IncidentControl.isVoided(id),
				CursedIncidentTestFixtures.diagnostic("forgetEverywhere(C15)", helper,
						"stack gone and id voided", "false/true",
						stillCarried + "/" + IncidentControl.isVoided(id)));
		CursedSpiritTestFixtures.cleanupVictim(helper, player);
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 100)
	public void voidedStackIsDestroyedOnObservation(GameTestHelper helper) {
		// C15: a stack whose id was voided is destroyed on sight instead of re-registering.
		ServerLevel level = helper.getLevel();
		UUID id = UUID.randomUUID();
		IncidentControl.voidObject(id);
		ItemEntity dropped = new ItemEntity(level, helper.absolutePos(CENTER).getX() + 0.5,
				helper.absolutePos(CENTER).getY(), helper.absolutePos(CENTER).getZ() + 0.5,
				CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime())));
		level.addFreshEntity(dropped);
		ObjectDwellTracker.noteWorldItem(dropped);
		helper.assertTrue(dropped.isRemoved(),
				CursedIncidentTestFixtures.diagnostic("voidedStackIsDestroyedOnObservation(C15)", helper,
						"voided entity discarded", true, dropped.isRemoved()));
		helper.succeed();
	}

	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 140)
	public void distantChestRelocatesIncident(GameTestHelper helper) {
		// C6: an object carried beyond the zone and stored in a chest keeps being observed;
		// once its dwell completes the incident relocates to the new position.
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(CENTER);
		IncidentRecord record = CursedIncidentTestFixtures.created(IncidentControl.spawn(
				new IncidentControl.SpawnRequest(center, level.dimension(), "blight",
						3, 1182L, IncidentStage.INITIAL, "cursed_nail",
						jujutsu.mod.cursedincident.SourceKind.OBJECT, 3.0, 40L)));
		UUID id = record.objectInstanceId;
		ItemEntity source = CursedIncidentTestFixtures.findCursedObject(helper, CENTER, id);
		helper.assertTrue(source != null,
				CursedIncidentTestFixtures.diagnostic("distantChestRelocatesIncident(C6)", helper,
						"physical source", "present", source));
		ItemStack stack = source.getItem().copy();
		source.discard();
		BlockPos chestPos = helper.absolutePos(new BlockPos(15, 4, 15));
		helper.setBlock(new BlockPos(15, 4, 15), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		chest.setItem(0, stack);
		ObjectDwellTracker tracker = new ObjectDwellTracker();
		tracker.noteContainer(level, chestPos, chest.getItem(0));
		helper.runAtTickTime(50, () -> {
			tracker.noteContainer(level, chestPos, chest.getItem(0));
			IncidentControl.advance(record.id, 50L);
			helper.assertTrue(chestPos.equals(record.center),
					CursedIncidentTestFixtures.diagnostic("distantChestRelocatesIncident(C6)", helper,
							"incident center relocated to chest", chestPos, record.center));
			CursedIncidentTestFixtures.cleanup(record);
			helper.succeed();
		});
	}

	/**
	 * Q-drop regression (spec §14): a player-thrown cursed object must keep its vanilla
	 * pickup delay — the tracker must never zero it, or the item bounces straight back
	 * into the thrower's inventory. Tracker-spawned entities still get instant pickup.
	 */
	@GameTest(structure = "jujutsumod:large_empty", maxTicks = 120)
	public void playerDropKeepsPickupDelay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID id = UUID.randomUUID();
		ItemStack stack = CursedObjectItem.stack(CursedObjectState.fresh(id, "cursed_nail", 3, level.getGameTime()));
		// Simulate a Q-drop: vanilla sets a throw delay on the entity.
		ItemEntity dropped = new ItemEntity(level, helper.absolutePos(CENTER).getX() + 0.5,
				helper.absolutePos(CENTER).getY(), helper.absolutePos(CENTER).getZ() + 0.5, stack);
		dropped.setDefaultPickUpDelay();
		level.addFreshEntity(dropped);
		ObjectDwellTracker.noteWorldItem(dropped);
		helper.assertTrue(dropped.hasPickUpDelay(),
				CursedIncidentTestFixtures.diagnostic("playerDropKeepsPickupDelay(Q-drop)", helper,
						"pickup delay survives tracker observation", true, dropped.hasPickUpDelay()));
		// The periodic re-observation path must not clear it either.
		ObjectDwellTracker.noteWorldItem(dropped);
		helper.assertTrue(dropped.hasPickUpDelay(),
				CursedIncidentTestFixtures.diagnostic("playerDropKeepsPickupDelay(Q-drop)", helper,
						"pickup delay survives re-observation", true, dropped.hasPickUpDelay()));
		// Tracker-spawned entities still get instant pickup — spawn path sets it directly.
		UUID spawnedId = IncidentControl.spawnObject(level, helper.absolutePos(new BlockPos(10, 4, 8)),
				"cursed_coin", 3, 9917L);
		helper.assertTrue(spawnedId != null,
				CursedIncidentTestFixtures.diagnostic("playerDropKeepsPickupDelay(Q-drop)", helper,
						"tracker spawn accepted", "non-null", spawnedId));
		ItemEntity spawned = CursedIncidentTestFixtures.findCursedObject(helper, new BlockPos(10, 4, 8), spawnedId);
		helper.assertTrue(spawned != null && !spawned.hasPickUpDelay(),
				CursedIncidentTestFixtures.diagnostic("playerDropKeepsPickupDelay(Q-drop)", helper,
						"tracker-spawned item has no pickup delay", false,
						spawned == null ? null : spawned.hasPickUpDelay()));
		helper.succeed();
	}
}
