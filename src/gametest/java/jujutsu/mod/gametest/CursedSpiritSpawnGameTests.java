package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritProfile;
import jujutsu.mod.cursedspirit.CursedSpiritSpawnRules;
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
	 * fixture-relative and the crowd count is queried by radius around the structure, never by
	 * absolute coordinates. Light settles a few ticks after the blocks go down, so construction
	 * happens at tick 1 while every oracle runs at tick 15. All spawns use {@code helper.spawn}
	 * (full AI) and every oracle reads state synchronously in the same callback, so nothing can
	 * wander between placement and the check). Cleanup is owner-scoped: every spawned body is
	 * tracked in a list and discarded on all paths (try/finally over owned references
	 * only — never a radius sweep, so sibling scenarios sharing the level are untouched).
	 * The premise and the final sweep are owner-scoped the same way: they count only the bodies
	 * this scenario spawned (a neighbour arena's legitimate bodies must not red them), and the
	 * radius they use is exactly the production {@code CursedSpiritProfile.CROWD_RADIUS}, so the
	 * test observes what production enforces.
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

	private static final int ORACLE_TICK = 15;
	private static final int SWEEP_TICK = 30;
	/**
	 * Every body this scenario spawns. A field (not a callback-local) so the sweep tick can
	 * verify the oracle tick's cleanup: the premise and the sweep count only these owned
	 * references, never a by-type radius query, so a sibling arena's legitimate bodies sharing
	 * the level cannot red them. Cleared at the start of the oracle tick, so a repeat run on
	 * the same instance cannot inherit stale references.
	 */
	private final List<CursedSpiritEntity> owned = new ArrayList<>();

	/**
	 * Lit refuses, dark allows, peaceful refuses, a full crowd refuses — then the owned bodies
	 * are asserted discarded and the test succeeds.
	 */
	@GameTest(maxTicks = 60)
	public void spawnGateRefusesLightPeacefulAndCrowdButAllowsDark(GameTestHelper helper) {
		String fixture = "spawnGateRefusesLightPeacefulAndCrowdButAllowsDark";
		buildDarkRoom(helper);
		helper.setBlock(LIT_FEET.below(), Blocks.STONE);
		helper.setBlock(LIT_LAMP, Blocks.GLOWSTONE);

		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			List<CursedSpiritEntity> live = owned;
			try {
				BlockPos center = helper.absolutePos(DARK_FEET);
				helper.assertTrue(countOwned(live, center) == 0,
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"premise: this scenario starts with no owned bodies near the arena", "0",
								countOwned(live, center)));

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

				// Light refuses in the lit cell. SPAWNER reason keeps the crowd cap out of this
				// oracle: sibling arenas share the level, so a NATURAL call here could refuse
				// for crowding and mask a broken light half.
				CursedSpiritEntity litProbe = spawnProbe(helper, live, LIT_FEET);
				helper.assertFalse(litProbe.checkSpawnRules(level, EntitySpawnReason.SPAWNER),
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"light check in the lit cell " + gateDiagnostic(level, helper.absolutePos(LIT_FEET)),
								"false", "see report"));

				// Darkness allows in the sealed room (stray probes still far below the cap).
				CursedSpiritEntity darkProbe = spawnProbe(helper, live, DARK_FEET);
				helper.assertTrue(darkProbe.checkSpawnRules(level, EntitySpawnReason.SPAWNER),
						GameTestFixtures.diagnostic(fixture, helper.getTick(),
								"light check in the dark room " + gateDiagnostic(level, helper.absolutePos(DARK_FEET)),
								"true", "see report"));

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
			BlockPos center = helper.absolutePos(DARK_FEET);
			helper.assertTrue(countOwned(owned, center) == 0,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"sweep: owned bodies discarded after the oracles", "0",
							countOwned(owned, center)));
			helper.succeed();
		});
	}

	/**
	 * Compact world-state probe for the spawn-gate assertions: how many cursed spirits the
	 * production cap counts around {@code absoluteCenter}, the block light there (the light
	 * half of {@code super.checkSpawnRules}) and whether the cheap pure cap predicate agrees.
	 * Purely informational — the assertions still read the production gate itself.
	 */
	private static String gateDiagnostic(ServerLevel level, BlockPos absoluteCenter) {
		int nearby = level.getEntitiesOfClass(CursedSpiritEntity.class,
				new AABB(absoluteCenter).inflate(CursedSpiritProfile.CROWD_RADIUS)).size();
		int blockLight = level.getBrightness(LightLayer.BLOCK, absoluteCenter);
		int skyLight = level.getBrightness(LightLayer.SKY, absoluteCenter);
		return "[worldNearby=" + nearby + "/" + CursedSpiritProfile.MAX_SPIRITS_NEARBY
				+ " blockLight=" + blockLight + " skyLight=" + skyLight
				+ " belowCap=" + CursedSpiritSpawnRules.belowLocalCap(level, absoluteCenter) + "]";
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

	/**
	 * Owned bodies (non-removed) inside the production crowd radius of the arena centre. Scoped
	 * to the {@code live} references this scenario created — never a by-type level query — and
	 * measured with exactly {@code CursedSpiritProfile.CROWD_RADIUS}, the radius production
	 * enforces in {@code CursedSpiritSpawnRules}.
	 */
	private static int countOwned(List<CursedSpiritEntity> live, BlockPos absoluteCenter) {
		AABB box = new AABB(absoluteCenter).inflate(CursedSpiritProfile.CROWD_RADIUS);
		int nearby = 0;
		for (CursedSpiritEntity spirit : live) {
			if (!spirit.isRemoved() && box.contains(spirit.position())) {
				nearby++;
			}
		}
		return nearby;
	}
}
