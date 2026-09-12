package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritProfile;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Natural-spawn gate oracles (Block 4, Step 5): the real in-world check behind
 * {@code CursedSpiritEntity.checkSpawnRules} instead of vacuous placement-type asserts.
 *
 * <p>One sealed dark room (opaque shell, zero light at any sky level) vs one glowstone-lit cell
 * (block light ~14 at any sky level): the vanilla light half of the gate reads false in the lit
 * cell and true in the dark room. A {@code PEACEFUL} phase pins Main's routed difficulty
 * requirement on the entity override. A crowd of exactly {@code MAX_SPIRITS_NEARBY} then flips
 * the dark-room check to false (the cap half).
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so every position here is
 * fixture-relative and the crowd count is queried by radius around the structure, never by
 * absolute coordinates. Light settles a few ticks after the blocks go down, so construction
 * happens at tick 1 while every oracle runs at tick 15. All spawns use {@code helper.spawn}
 * (full AI — a spawn-gate probe is not an attacker, and every oracle reads state synchronously
 * in the same callback, so nothing can wander between placement and the check). Every spawned
 * body is discarded on all paths (try/finally plus the tick-30 sweep) so the shared level stays
 * clean for sibling classes.
 *
 * <p><b>Gates.</b> This class is registered in {@code fabric.mod.json} by Block 4's serialized
 * edit (after {@code block-2 report filed}); the cap/peaceful phases additionally need
 * ServerCore's routed {@code checkSpawnRules} override in {@code CursedSpiritEntity}.
 */
public final class CursedSpiritSpawnGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required.

	/** Feet block of the probe inside the sealed dark room. */
	private static final BlockPos DARK_FEET = new BlockPos(2, 1, 2);
	/** Feet block of the probe in the lit cell. */
	private static final BlockPos LIT_FEET = new BlockPos(6, 1, 2);
	/** Glowstone next to the lit feet: block light 14 at the feet whatever the sky does. */
	private static final BlockPos LIT_LAMP = new BlockPos(6, 1, 3);

	private static final int SETUP_TICK = 1;
	private static final int ORACLE_TICK = 15;
	private static final int SWEEP_TICK = 30;

	/**
	 * Lit refuses, dark allows, peaceful refuses, a full crowd refuses — then the arena is swept
	 * clean and the test succeeds.
	 */
	@GameTest(maxTicks = 60)
	public void spawnGateRefusesLightPeacefulAndCrowdButAllowsDark(GameTestHelper helper) {
		String fixture = "spawnGateRefusesLightPeacefulAndCrowdButAllowsDark";
		buildDarkRoom(helper);
		helper.setBlock(LIT_FEET.below(), Blocks.STONE);
		helper.setBlock(LIT_LAMP, Blocks.GLOWSTONE);

		helper.runAtTickTime(SETUP_TICK, () -> clearNearbySpirits(helper));

		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			List<CursedSpiritEntity> live = new ArrayList<>();
			try {
				BlockPos center = helper.absolutePos(DARK_FEET);
				helper.assertTrue(countNearby(level, center) == 0,
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"premise: no stray spirits near the arena", "0", countNearby(level, center)));

				// PEACEFUL refuses even in the dark room (explicit gate on the entity override).
				Difficulty before = level.getDifficulty();
				level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
				try {
					CursedSpiritEntity peacefulProbe = spawnProbe(helper, live, DARK_FEET);
					helper.assertFalse(peacefulProbe.checkSpawnRules(level, EntitySpawnReason.NATURAL),
							GameTestFixtures.diagnostic(fixture, helper.getTick(),
									"peaceful check in the dark room", "false", "see report"));
				} finally {
					level.getServer().setDifficulty(before, true);
				}

				// Light refuses in the lit cell.
				CursedSpiritEntity litProbe = spawnProbe(helper, live, LIT_FEET);
				helper.assertFalse(litProbe.checkSpawnRules(level, EntitySpawnReason.NATURAL),
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"natural check in the lit cell", "false", "see report"));

				// Darkness allows in the sealed room (stray probes still far below the cap).
				CursedSpiritEntity darkProbe = spawnProbe(helper, live, DARK_FEET);
				helper.assertTrue(darkProbe.checkSpawnRules(level, EntitySpawnReason.NATURAL),
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"natural check in the dark room", "true", "see report"));

				// A full crowd refuses: top the arena up to exactly MAX_SPIRITS_NEARBY bodies.
				while (live.size() < CursedSpiritProfile.MAX_SPIRITS_NEARBY) {
					spawnProbe(helper, live, crowdSpot(live.size()));
				}
				// Assert on the in-room probe: its super (light) half is pinned true inside the
				// sealed room at any sky level, so this observes ONLY the cap half. (The last
				// crowd body stands under open sky, where daylight alone would refuse — asserting
				// on it would let the light half mask a broken cap.)
				helper.assertFalse(darkProbe.checkSpawnRules(level, EntitySpawnReason.NATURAL),
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"natural check on the in-room probe at cap (" + live.size() + " nearby)",
								"false", "see report"));
			} finally {
				for (CursedSpiritEntity spirit : live) {
					spirit.discard();
				}
			}
		});

		helper.runAtTickTime(SWEEP_TICK, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos center = helper.absolutePos(DARK_FEET);
			helper.assertTrue(countNearby(level, center) == 0,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"sweep: arena clean after the oracles", "0", countNearby(level, center)));
			helper.succeed();
		});
	}

	private static void buildDarkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
			}
		}
		for (int y = 1; y <= 2; y++) {
			for (int x = 1; x <= 3; x++) {
				helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
				helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
			helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
		}
	}

	private static BlockPos crowdSpot(int index) {
		return new BlockPos(3 + (index % 5), 1, 5 + (index / 5));
	}

	private static CursedSpiritEntity spawnProbe(GameTestHelper helper, List<CursedSpiritEntity> live,
			BlockPos relativePos) {
		CursedSpiritEntity spirit = helper.spawn(JujutsuEntities.LESSER_CURSED_SPIRIT, relativePos);
		live.add(spirit);
		return spirit;
	}

	private static int countNearby(ServerLevel level, BlockPos absoluteCenter) {
		return level.getEntitiesOfClass(CursedSpiritEntity.class,
				new AABB(absoluteCenter).inflate(CursedSpiritProfile.CROWD_RADIUS + 8.0)).size();
	}

	private static void clearNearbySpirits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(DARK_FEET);
		for (CursedSpiritEntity spirit : level.getEntitiesOfClass(CursedSpiritEntity.class,
				new AABB(center).inflate(CursedSpiritProfile.CROWD_RADIUS + 8.0))) {
			spirit.discard();
		}
	}
}
