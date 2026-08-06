package jujutsu.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;

/**
 * Stage A server canaries (issue #42): they prove the GameTest harness itself — a dedicated-server
 * boot with the production mod loaded, and a working world/entity surface — not gameplay.
 *
 * <p>Scenarios that actually exercise abilities and world behaviour belong to #21; these two tests
 * exist so the merge gate's {@code runGametest} step has a real signal. Nothing here touches
 * repository-owned static state (cooldown maps and the like): canaries stay on the vanilla surface.
 */
public final class ServerGameTests {

	/** Production mod id; its presence is what the whole test mod exists to prove. */
	private static final String PRODUCTION_MOD_ID = "jujutsumod";
	/** A real entity id registered by the production mod (see {@code JujutsuEntities.register()}). */
	private static final ResourceLocation PRODUCTION_ENTITY_ID =
			ResourceLocation.fromNamespaceAndPath(PRODUCTION_MOD_ID, "todo_stone");

	/**
	 * Canary 1: the GameTest server boots as a real dedicated server with the production mod loaded
	 * and its registry content present.
	 */
	@GameTest
	public void serverLoadsProductionMod(GameTestHelper helper) {
		boolean modLoaded = FabricLoader.getInstance().isModLoaded(PRODUCTION_MOD_ID);
		helper.assertTrue(modLoaded, GameTestFixtures.diagnostic("serverLoadsProductionMod", helper.getTick(),
				"production mod loaded", "mod '" + PRODUCTION_MOD_ID + "' present", modLoaded ? "present" : "absent"));

		boolean entityRegistered = BuiltInRegistries.ENTITY_TYPE.containsKey(PRODUCTION_ENTITY_ID);
		helper.assertTrue(entityRegistered, GameTestFixtures.diagnostic("serverLoadsProductionMod", helper.getTick(),
				"production entity registered", "id '" + PRODUCTION_ENTITY_ID + "' in BuiltInRegistries.ENTITY_TYPE",
				entityRegistered ? "registered" : "missing"));

		ServerLevel level = helper.getLevel();
		boolean dedicatedServer = !level.isClientSide();
		helper.assertTrue(dedicatedServer, GameTestFixtures.diagnostic("serverLoadsProductionMod", helper.getTick(),
				"test runs on a dedicated server", "ServerLevel with isClientSide=false", "isClientSide=" + level.isClientSide()));

		helper.succeed();
	}

	/**
	 * Canary 2: a neutral vanilla mob spawns, stays put, and is cleanly removed again — the
	 * world/entity surface of the harness, nothing gameplay-related.
	 *
	 * <p>Determinism: a solid floor under the spawn point stops gravity from moving the pig between
	 * assertions, and {@code spawnWithNoFreeWill} spawns it AI-less so it never wanders. All
	 * positions are fixture-local (structure-relative) — every {@code GameTestHelper} method
	 * converts internally; only the direct {@code pig.blockPosition()} comparison needs
	 * {@code absolutePos}. The pig is then discarded and verified gone from the level. Entity
	 * handles are held as objects; no network ids are stored as durable identity.
	 */
	@GameTest(maxTicks = 100)
	public void neutralEntityLifecycle(GameTestHelper helper) {
		BlockPos relativeSpawn = new BlockPos(3, 1, 3);
		helper.setBlock(relativeSpawn.below(), Blocks.STONE);

		Pig pig = GameTestFixtures.spawnMob(helper, "neutralEntityLifecycle", EntityType.PIG, relativeSpawn);
		helper.assertEntityPresent(EntityType.PIG, relativeSpawn);

		helper.runAtTickTime(20, () -> {
			boolean alive = pig.isAlive();
			BlockPos actualPos = pig.blockPosition();
			BlockPos expectedPos = helper.absolutePos(relativeSpawn);
			helper.assertTrue(alive && actualPos.equals(expectedPos),
					GameTestFixtures.diagnostic("neutralEntityLifecycle", helper.getTick(),
							"pig state 20 ticks after spawn", "alive and at " + expectedPos,
							"alive=" + alive + ", pos=" + actualPos));
		});

		GameTestFixtures.removeAndVerifyGone(helper, "neutralEntityLifecycle", pig, EntityType.PIG, relativeSpawn, 40);
		helper.runAtTickTime(51, () -> helper.succeed());
	}
}
