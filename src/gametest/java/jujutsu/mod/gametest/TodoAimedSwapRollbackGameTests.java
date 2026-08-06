package jujutsu.mod.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.todo.TodoBoogieWoogieRuntime;

/**
 * Scenarios 3 and 4 of the aimed Boogie Woogie slice (issue #21): the blocked-destination atomic
 * refusal and the forced partial-commit rollback.
 *
 * <p>{@link #blockedDestinationRefusesAtomically} builds a real alcove whose every STRICT
 * placement candidate for the target fails {@code noBlockCollision}, so the destination preflight
 * cancels the whole cast: nobody moves, nothing charges. {@link #forcedSecondPlacementFailureRollsBackBothBodies}
 * makes the second commit placement report failure through the block-1 seam
 * ({@link TodoBoogieWoogieRuntime#overrideCommitTeleport}) and observes the production rollback
 * restore both bodies to their snapshots.
 *
 * <p><b>Why the seam is the only deterministic route into the rollback branch.</b> The commit
 * teleport is the 8-arg {@code Entity#teleportTo}; bytecode inspection of 1.21.8 shows it returns
 * {@code false} only when the entity is removed or the level is not a {@code ServerLevel}, and
 * the runtime re-checks liveness, aliveness and level identity synchronously immediately before
 * the two commit calls. Removal happens on the tick loop, so nothing can interleave between the
 * two back-to-back synchronous placements. No deterministic world condition can make the second
 * placement fail; the seam is the only way in.
 *
 * <p><b>Rollback never routes through the seam.</b> The seam wraps ONLY the two aimed-swap commit
 * call sites; {@code place}, {@code restore} and {@code rollback} keep the production teleport.
 * The rollback observed by scenario 4 is therefore production code — the seam only manufactures
 * the second commit's failure, and the first commit delegates to
 * {@link TodoBoogieWoogieRuntime#PRODUCTION_COMMIT_TELEPORT}, so the caster's body really moves
 * before the rollback puts it back. The seam is process-global static state, so every override is
 * paired with {@link TodoBoogieWoogieRuntime#restoreProductionCommitTeleport()} in a
 * {@code finally} block; a leak would poison sibling tests.
 *
 * <p><b>Candidate-kill table</b> for the scenario 3 alcove — the STRICT scan
 * ({@code SafeBodyPlacement.find}, ring {@code +-0.5 / +-1.0 / +-0.7*diag} at up-steps 0..3)
 * places the Iron Golem (box 1.4 x 2.9, half-width 0.7) requested at the caster's feet
 * {@code (2.5, 1.0, 2.5)}:
 *
 * <pre>
 *   candidate class                          | why noBlockCollision fails
 *   -----------------------------------------|-----------------------------------------------
 *   requested point, up=0                    | walls in x AND z: box x 1.8..3.2 overlaps the
 *                                            | wall at x=1.0..2.0, box z 1.8..3.2 the wall at
 *                                            | z=1.0..2.0 (and the ceiling starts at y=3.0,
 *                                            | box top 3.9, so the ceiling alone would too)
 *   ring +-0.5 / +-1.0 in x, up=0            | box z 1.8..3.2 always overlaps a tunnel side
 *                                            | wall (z=1.0..2.0 or z=3.0..4.0): the 1-wide
 *                                            | tunnel leaves no 1.4-wide column clear
 *   ring +-0.5 / +-1.0 in z, up=0            | box x 1.8..3.2 always overlaps an alcove wall
 *                                            | (x=1.0..2.0 or x=3.0..4.0) and/or a corner block
 *   ring +-0.7 diagonals, up=0               | corner wall blocks (1|3, 1, 1|3) overlap the box
 *   up-steps 1..3 (feet y = 2..4)            | ceiling slab y=3..5 over x=1..4, z=1..4: box
 *                                            | tops 4.9 / 5.9 / 6.9 always intersect the slab
 *   every candidate                          | findSafeDestination(STRICT) returns null =>
 *                                            | TodoSwapPlan.preflight empty => atomic refuse
 * </pre>
 *
 * <p>Both tests invoke the exact server-side production path the real C2S handler uses —
 * {@code CharacterAbilityExecutor.tryCast(player, PRIMARY, true)} via
 * {@link TodoSwapTestFixtures#castPrimary(ServerPlayer)} — on a vanilla mock server player.
 */
public final class TodoAimedSwapRollbackGameTests {

	private static final String FIXTURE_BLOCKED_DESTINATION = "blockedDestinationRefusesAtomically";
	private static final String FIXTURE_FORCED_ROLLBACK = "forcedSecondPlacementFailureRollsBackBothBodies";

	/**
	 * Scenario 3 — a destination that cannot hold the target cancels the whole cast atomically.
	 *
	 * <p>The caster stands in a 1x1x2 alcove; every STRICT candidate around his feet is inside
	 * collision geometry for an Iron Golem (1.4 x 2.9): alcove walls kill the laterals, the
	 * 1-wide 2-tall aim tunnel leaves no 1.4-wide column clear, and the ceiling slab kills the
	 * up-steps. The golem's own destination is irrelevant — SOFT belongs to the caster's arrival
	 * and this cast never commits. The cast must return {@code false} at the destination
	 * preflight with both bodies bit-for-bit where they were, no cooldown, no momentum, no
	 * transient state. See the class javadoc for the full candidate-kill table.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void blockedDestinationRefusesAtomically(GameTestHelper helper) {
		// Caster at (2,1,2) feet-center (2.5, 1.0, 2.5); golem station at (5,1,2). Solid floors.
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 0, 2), Blocks.STONE);
		// Alcove walls, y=1..2, around the 1x1 column; the +x side is the tunnel mouth (air).
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 2, 3), Blocks.STONE);
		// 1-wide, 2-tall aim tunnel x=3..4 at z=2 (air), walled at z=1 and z=3, y=1..2.
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
		// Ceiling slab >=3 blocks thick over the alcove and tunnel mouth: y=3..5, x=1..4, z=1..4.
		for (int y = 3; y <= 5; y++) {
			for (int x = 1; x <= 4; x++) {
				for (int z = 1; z <= 4; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}

		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, FIXTURE_BLOCKED_DESTINATION,
				new BlockPos(2, 1, 2), 0.0f, 0.0f);
		IronGolem golem = GameTestFixtures.spawnMob(helper, FIXTURE_BLOCKED_DESTINATION,
				EntityType.IRON_GOLEM, new BlockPos(5, 1, 2));

		helper.runAtTickTime(2, () -> {
			try {
				// Aim through the tunnel at the golem's bounding-box centre: the resolver must
				// reach the ENTITY (gates + LOS + range all pass) so the refusal point is the
				// destination preflight and nothing else.
				TodoSwapTestFixtures.aimAt(caster, golem.position().add(0.0, golem.getBbHeight() / 2.0, 0.0));
				// Capture AFTER aiming: production snapshots rotations at cast time, and the
				// refused cast restores nothing — the captured state is the exact compare target.
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				TodoSwapTestFixtures.BodyState golemBefore = TodoSwapTestFixtures.BodyState.capture(golem);
				boolean castResult = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(!castResult,
						TodoSwapTestFixtures.diagnostic(FIXTURE_BLOCKED_DESTINATION, "preflight",
								helper.getTick(), caster.getUUID(), golem.getUUID(),
								"cast refused on blocked destination", true, !castResult));
				// Atomic: NOBODY moved — positions exact, rotations, velocity, fall distance,
				// dimension, alive, not removed, for both bodies.
				TodoSwapTestFixtures.assertBodyState(helper, FIXTURE_BLOCKED_DESTINATION, "preflight",
						"caster", caster, casterBefore);
				TodoSwapTestFixtures.assertBodyState(helper, FIXTURE_BLOCKED_DESTINATION, "preflight",
						"golem", golem, golemBefore);
				TodoSwapTestFixtures.assertNoPrimaryCharge(helper, FIXTURE_BLOCKED_DESTINATION, "preflight", caster);
			} finally {
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
			}
		});

		GameTestFixtures.removeAndVerifyGone(helper, FIXTURE_BLOCKED_DESTINATION, golem,
				EntityType.IRON_GOLEM, new BlockPos(5, 1, 2), 6);
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * Scenario 4 — a failed second placement rolls the partial commit back to both snapshots.
	 *
	 * <p>Open arena like scenario 1; the block-1 seam is overridden BEFORE the cast with a
	 * counting wrapper whose first call (the caster's commit) delegates to the production
	 * teleport — the caster's body REALLY moves — and whose second call (the target's commit)
	 * reports failure, the state no deterministic world condition can produce. The production
	 * rollback must then restore both bodies exactly: position (within epsilon), velocity, yaw,
	 * pitch, head yaw, fall distance 0, same dimension, alive, not removed — and the refusal
	 * must charge nothing. The seam is static process-global state: the override and everything
	 * through the asserts live in one try whose finally restores the production teleport and
	 * cleans up the caster, so an assert failure cannot leak either.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void forcedSecondPlacementFailureRollsBackBothBodies(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 0, 5), Blocks.STONE);

		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, FIXTURE_FORCED_ROLLBACK,
				new BlockPos(1, 1, 1), 0.0f, 0.0f);
		Pig pig = GameTestFixtures.spawnMob(helper, FIXTURE_FORCED_ROLLBACK, EntityType.PIG,
				new BlockPos(5, 1, 5));
		AtomicInteger commits = new AtomicInteger();

		helper.runAtTickTime(2, () -> {
			try {
				TodoBoogieWoogieRuntime.overrideCommitTeleport((body, level, dest, yaw, pitch) -> {
					if (commits.incrementAndGet() == 1) {
						return TodoBoogieWoogieRuntime.PRODUCTION_COMMIT_TELEPORT.teleport(body, level, dest, yaw, pitch); // caster commit REAL
					}
					return false; // second (target) commit reports authoritative failure
				});
				TodoSwapTestFixtures.aimAt(caster, pig.position().add(0.0, pig.getBbHeight() / 2.0, 0.0));
				// Capture AFTER aiming: production snapshots rotations at cast time and the
				// rollback restores exactly those, so the captured state is the compare target.
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				TodoSwapTestFixtures.BodyState pigBefore = TodoSwapTestFixtures.BodyState.capture(pig);
				boolean castResult = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(!castResult,
						TodoSwapTestFixtures.diagnostic(FIXTURE_FORCED_ROLLBACK, "commit2",
								helper.getTick(), caster.getUUID(), pig.getUUID(),
								"cast refused after second commit failure", true, !castResult));
				// Proves the first commit really ran (real teleport) and the second was the failure point.
				helper.assertTrue(commits.get() == 2,
						TodoSwapTestFixtures.diagnostic(FIXTURE_FORCED_ROLLBACK, "commit2",
								helper.getTick(), caster.getUUID(), pig.getUUID(),
								"commit teleports attempted", 2, commits.get()));
				// Production rollback contract on BOTH bodies: positions are the ORIGINALS (no
				// second swap), motion/rotations exact, fall distance 0, same dimension, alive.
				TodoSwapTestFixtures.assertBodyState(helper, FIXTURE_FORCED_ROLLBACK, "rollback",
						"caster", caster, casterBefore);
				TodoSwapTestFixtures.assertBodyState(helper, FIXTURE_FORCED_ROLLBACK, "rollback",
						"pig", pig, pigBefore);
				TodoSwapTestFixtures.assertNoPrimaryCharge(helper, FIXTURE_FORCED_ROLLBACK, "rollback", caster);
			} finally {
				TodoBoogieWoogieRuntime.restoreProductionCommitTeleport();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
			}
		});

		GameTestFixtures.removeAndVerifyGone(helper, FIXTURE_FORCED_ROLLBACK, pig, EntityType.PIG,
				new BlockPos(5, 1, 5), 6);
		helper.runAtTickTime(20, () -> helper.succeed());
	}
}
