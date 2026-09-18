package jujutsu.mod.gametest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.MegumiPartialRuntime;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;

/**
 * Toad's partial tongue, server-state half (issue #108, block B4): the anchor surface rules of R29,
 * the two per-tick break conditions of R31/R32, and the R30a tripwire that the server never moves
 * the holder. Driven through the production press/release hops {@code
 * MegumiPartialRuntime.tryPartial} / {@code tryPartialRelease}, the same ones the PARTIAL /
 * PARTIAL_RELEASE slots reach.
 *
 * <p><b>What is deliberately not tested here.</b> The pull itself is client-authoritative physics:
 * the ramp, the cap and the steering are pure and covered by {@code TonguePullPolicyTest}, while
 * the in-game feel (R30b velocity, R35 inertia transfer, R36 movement tech) needs a real client and
 * is an MCP-run oracle, never a GameTest one. R33 (no impact damage) has no oracle by construction
 * — the tongue applies none — and is pinned instead by the source-absence tripwire in that same
 * JUnit class.
 *
 * <p><b>Geometry.</b> The scenarios use the 16x8x16 {@code large_empty} template because the tongue
 * reaches ten blocks and the default arena is eight: the caster stands at rel (2,1,2) with the eye
 * at ~2.62, the near wall face sits 9.5 blocks away and the far one 10.5, which is the closest grid
 * position past the ten-block limit (a block face cannot land exactly at 10.01, so the pair pins the
 * accept/refuse boundary on either side of it). All cells are structure-relative; the run's random
 * world offset is never assumed away.
 *
 * <p><b>Break polls.</b> The tongue is broken by the production per-tick upkeep, so a mutation made
 * inside tick T is first visible to the upkeep of T+1 (or of T itself, depending on where in the
 * tick the harness runs its callbacks). The polls therefore start at T+1 and succeed on the FIRST
 * tick that reads the tongue inactive, failing at T+2 — one tick of headroom for that intra-tick
 * ordering and nothing more.
 */
public final class MegumiTongueGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final BlockPos CASTER_FEET = new BlockPos(2, 1, 2);
	/** Press edge: the caster aims at the target surface and the tongue attaches. */
	private static final int ATTACH_TICK = 2;
	/** A second press-edge step (the release, or the out-of-range refusal) sits two ticks later. */
	private static final int SECOND_STEP_TICK = 4;
	/** The block is changed on this tick; the break polls start on the tick after. */
	private static final int MUTATE_TICK = 4;
	/** R31/R32: the tongue must be gone by this tick, one tick after the mutation. */
	private static final long BREAK_DEADLINE_TICK = MUTATE_TICK + 2;
	/** R30a: how long the holder is watched after the attach. */
	private static final int STILLNESS_WINDOW_TICKS = 20;
	/** R30a: a holder moved by the server would blow past both of these in a single tick. */
	private static final double STILLNESS_PER_TICK = 0.05;
	private static final double STILLNESS_TOTAL = 0.1;

	/**
	 * R29 — the wall case: a flat wall 9.5 blocks from the eye takes the tongue, the release edge
	 * takes it back the same tick, and a wall whose face is 10.5 blocks away is refused outright.
	 */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void tongueHooksAWallFaceWithinTenBlocksAndRefusesPastTen(GameTestHelper helper) {
		String fixture = "tongueHooksAWallFaceWithinTenBlocksAndRefusesPastTen";
		paveFloor(helper);
		layWall(helper, new BlockPos(12, 1, 2));
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(ATTACH_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			attachTongue(helper, caster, fixture, new BlockPos(12, 2, 2));
			helper.assertTrue(MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attach", helper.getTick(), ownerId,
							"tongue active", "true", "false"));
		}));

		helper.runAtTickTime(SECOND_STEP_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(MegumiPartialRuntime.tryPartialRelease(caster),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "release", helper.getTick(), ownerId,
								"tryPartialRelease result", "true", "false"));
				helper.assertTrue(!MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "release", helper.getTick(), ownerId,
								"tongue active after the release edge (R30: same tick)", "false", "true"));

				// The first wall must come down before the refuse step: left standing, its face at
				// 9.5 blocks is still in range and the ray hits it instead of reaching the far wall.
				for (int offset = -1; offset <= 1; offset++) {
					helper.setBlock(new BlockPos(12, 1, 2 + offset), Blocks.AIR);
					helper.setBlock(new BlockPos(12, 2, 2 + offset), Blocks.AIR);
				}

				// Same geometry, one cell further out: the face is now 10.5 blocks away, past the limit.
				layWall(helper, new BlockPos(13, 1, 2));
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
				TodoSwapTestFixtures.aimAt(caster, blockCenter(helper, new BlockPos(13, 2, 2)));
				helper.assertTrue(!MegumiPartialRuntime.tryPartial(caster, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(), ownerId,
								"tryPartial on a 10.5-block wall face", "false", "true"));
				helper.assertTrue(!MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(), ownerId,
								"tongue active after the out-of-range refusal", "false", "true"));
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** R29 — the ceiling case: the ray goes up into a block overhead, four and a half blocks away. */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void tongueHooksACeilingWithinTenBlocks(GameTestHelper helper) {
		String fixture = "tongueHooksACeilingWithinTenBlocks";
		paveFloor(helper);
		helper.setBlock(new BlockPos(2, 7, 2), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(ATTACH_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				attachTongue(helper, caster, fixture, new BlockPos(2, 7, 2));
				helper.assertTrue(MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "attach", helper.getTick(), ownerId,
								"tongue active", "true", "false"));
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** R29 — the side case: the same ten-block rule on a face along the other horizontal axis. */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void tongueHooksASideFaceWithinTenBlocks(GameTestHelper helper) {
		String fixture = "tongueHooksASideFaceWithinTenBlocks";
		paveFloor(helper);
		layWall(helper, new BlockPos(2, 1, 13));
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(ATTACH_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				attachTongue(helper, caster, fixture, new BlockPos(2, 2, 13));
				helper.assertTrue(MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "attach", helper.getTick(), ownerId,
								"tongue active", "true", "false"));
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * R31 — an opaque solid placed on the line breaks the tongue: the holder keeps the wall it was
	 * attached to, but a block now stands between eye and anchor, and the per-tick line re-clip ends
	 * the hold.
	 */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void aWallPlacedOnTheLineBreaksTheTongue(GameTestHelper helper) {
		String fixture = "aWallPlacedOnTheLineBreaksTheTongue";
		paveFloor(helper);
		layWall(helper, new BlockPos(12, 1, 2));
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);
		AtomicBoolean broken = new AtomicBoolean();

		helper.runAtTickTime(ATTACH_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
				attachTongue(helper, caster, fixture, new BlockPos(12, 2, 2))));

		helper.runAtTickTime(MUTATE_TICK, () -> {
			// The eye-to-anchor ray sits at y ~= 2.56 across x = 3..11, so this cell is on the line.
			helper.setBlock(new BlockPos(7, 2, 2), Blocks.STONE);
		});

		pollUntilBroken(helper, caster, fixture, broken);
	}

	/** R32 — destroying the anchor block drops the tongue the same way: air where the hold was. */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void anAnchorTurnedToAirBreaksTheTongue(GameTestHelper helper) {
		String fixture = "anAnchorTurnedToAirBreaksTheTongue";
		paveFloor(helper);
		layWall(helper, new BlockPos(12, 1, 2));
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);
		AtomicBoolean broken = new AtomicBoolean();

		helper.runAtTickTime(ATTACH_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
				attachTongue(helper, caster, fixture, new BlockPos(12, 2, 2))));

		helper.runAtTickTime(MUTATE_TICK, () -> {
			helper.setBlock(new BlockPos(12, 2, 2), Blocks.AIR);
			helper.setBlock(new BlockPos(12, 1, 2), Blocks.AIR);
		});

		pollUntilBroken(helper, caster, fixture, broken);
	}

	/**
	 * R30a — the server never moves the holder: the tongue's pull is client-authoritative, so a
	 * server-side yank (a teleport to the anchor, a velocity written straight onto the player) is a
	 * bug this watches for. A mock player is not physics-simulated server-side, which is why the
	 * bound can be this tight: any movement at all is the server's own doing.
	 */
	@GameTest(maxTicks = 60, structure = "jujutsumod:large_empty")
	public void theServerNeverMovesTheHolderWhileTheTongueIsAttached(GameTestHelper helper) {
		String fixture = "theServerNeverMovesTheHolderWhileTheTongueIsAttached";
		paveFloor(helper);
		layWall(helper, new BlockPos(12, 1, 2));
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, CASTER_FEET, 0.0f, 0.0f);
		AtomicReference<Vec3> start = new AtomicReference<>();
		AtomicReference<Vec3> lastSeen = new AtomicReference<>();
		AtomicBoolean settled = new AtomicBoolean();

		helper.runAtTickTime(ATTACH_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			attachTongue(helper, caster, fixture, new BlockPos(12, 2, 2));
			start.set(caster.position());
			lastSeen.set(caster.position());
		}));

		for (int step = 1; step <= STILLNESS_WINDOW_TICKS; step++) {
			final long pollTick = ATTACH_TICK + step;
			helper.runAtTickTime(pollTick, () -> {
				if (settled.get()) {
					return;
				}
				try {
					Vec3 previous = lastSeen.getAndSet(caster.position());
					double stepMoved = previous.distanceTo(caster.position());
					double total = start.get().distanceTo(caster.position());
					helper.assertTrue(stepMoved <= STILLNESS_PER_TICK,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "still", pollTick, caster.getUUID(),
									"holder movement in one tick", "<= " + STILLNESS_PER_TICK, stepMoved));
					helper.assertTrue(total <= STILLNESS_TOTAL,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "still", pollTick, caster.getUUID(),
									"holder drift since attach", "<= " + STILLNESS_TOTAL, total));
				} catch (RuntimeException | AssertionError failure) {
					settled.set(true);
					cleanup(helper, caster);
					throw failure;
				}
			});
		}

		helper.runAtTickTime(ATTACH_TICK + STILLNESS_WINDOW_TICKS + 1, () -> {
			settled.set(true);
			cleanup(helper, caster);
			helper.succeed();
		});
	}

	/**
	 * The production press edge at one aimed surface cell: select the shikigami the partial key
	 * belongs to, aim, press, and assert the attach both ways. The selection is set here rather than
	 * in setup because the key is selection-driven (R21): TOAD is what makes this the tongue.
	 */
	private static void attachTongue(GameTestHelper helper, ServerPlayer caster, String fixture, BlockPos aimCell) {
		UUID ownerId = caster.getUUID();
		MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
		TodoSwapTestFixtures.aimAt(caster, blockCenter(helper, aimCell));
		helper.assertTrue(MegumiPartialRuntime.tryPartial(caster, false),
				MegumiShikigamiTestFixtures.diagnostic(fixture, "attach", helper.getTick(), ownerId,
						"tryPartial at " + aimCell, "true", "false"));
	}

	/**
	 * The R31/R32 oracle: from the tick after the mutation, the first poll that reads the tongue
	 * inactive wins; a tongue still attached one tick later fails here with the whole state in the
	 * diagnostic (the toad-lane flake protocol: restart the lane, never widen the assert).
	 */
	private static void pollUntilBroken(GameTestHelper helper, ServerPlayer caster, String fixture,
			AtomicBoolean broken) {
		for (long tick = MUTATE_TICK + 1; tick <= BREAK_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (broken.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				if (MegumiPartialRuntime.isActiveForType(ownerId, MegumiShikigami.TOAD)) {
					if (pollTick == BREAK_DEADLINE_TICK) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "break",
									pollTick, ownerId, "tongue inactive by tick " + BREAK_DEADLINE_TICK,
									"false", "true"));
						} finally {
							cleanup(helper, caster);
						}
					}
					return;
				}
				try {
					broken.set(true);
					helper.assertTrue(!MegumiPartialRuntime.isAnyActive(ownerId),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "break", pollTick, ownerId,
									"no partial left active", "false", "true"));
					helper.assertTrue(caster.isAlive(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "break", pollTick, ownerId,
									"caster alive after the break", "true", caster.isAlive()));
				} finally {
					cleanup(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	private static Vec3 blockCenter(GameTestHelper helper, BlockPos relative) {
		BlockPos absolute = helper.absolutePos(relative);
		return new Vec3(absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5);
	}

	/**
	 * A three-cell-wide, two-high wall, one cell thick: narrow enough to stay inside the template,
	 * wide enough that the aimed ray cannot miss it for anything but the range being tested. The
	 * three cells step along Z, so the flat face the caster's ray meets is the one at their X.
	 */
	private static void layWall(GameTestHelper helper, BlockPos foot) {
		for (int offset = -1; offset <= 1; offset++) {
			helper.setBlock(foot.offset(0, 0, offset), Blocks.STONE);
			helper.setBlock(foot.offset(0, 1, offset), Blocks.STONE);
		}
	}

	/** The template is empty air, so every scenario lays its own floor first. */
	private static void paveFloor(GameTestHelper helper) {
		for (int x = 0; x <= 15; x++) {
			for (int z = 0; z <= 15; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Both halves of the state: the partial runtime's own record and the shared caster fixtures. */
	private static void cleanup(GameTestHelper helper, ServerPlayer caster) {
		try {
			MegumiPartialRuntime.teardown(helper.getLevel().getServer(), caster.getUUID());
		} finally {
			MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
		}
	}
}
