package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.todo.SwapCommit;
import jujutsu.mod.character.todo.SwapKind;
import jujutsu.mod.character.todo.SwapOutcome;
import jujutsu.mod.character.todo.TodoCooldownPolicy;
import jujutsu.mod.character.todo.TodoRhythmRuntime;
import jujutsu.mod.character.todo.TodoRhythmState;
import jujutsu.mod.character.todo.TodoStateLifecycle;
import jujutsu.mod.character.todo.TodoSwapHooks;
import jujutsu.mod.character.todo.TodoTransientState;
import jujutsu.mod.character.todo.PendingAutoSwap;
import jujutsu.mod.registry.JujutsuEffects;

/** Live server contracts for Boogie Rhythm, Revised scheduling, and lifecycle cleanup. */
public final class TodoRhythmGameTests {
	private static final SwapOutcome SUCCESS = new SwapOutcome(true, List.of());

	/**
	 * Run: ./gradlew.bat test --tests "*TodoRhythm*" runGameTest
	 * Expected: all TodoRhythmTest and TodoRhythmGameTests checks green
	 * Red: before rhythm registration/state cleanup, this entrypoint is absent and the class cannot compile.
	 */
	public TodoRhythmGameTests() {}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void variedSwapsProgressToPeak(GameTestHelper helper) {
		String fixture = "variedSwapsProgressToPeak";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		try {
			primePeak(caster);
			TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
			helper.assertTrue(state.points() == 10, diagnostic(fixture, helper, "points", 10, state.points()));
			helper.assertTrue(state.beat() == 4, diagnostic(fixture, helper, "beat", 4, state.beat()));
			helper.assertTrue(state.peakArmed(), diagnostic(fixture, helper, "peak armed", true, state.peakArmed()));
			helper.succeed();
		} finally {
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
		}
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void spamStaysAtBeatZero(GameTestHelper helper) {
		String fixture = "spamStaysAtBeatZero";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		try {
			TodoRhythmRuntime.register();
			for (int i = 0; i < 4; i++) {
				TodoRhythmRuntime.recordSwap(caster, SwapKind.AIMED, caster.level().getGameTime() + i);
			}
			TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
			helper.assertTrue(state.points() == 2, diagnostic(fixture, helper, "points", 2, state.points()));
			helper.assertTrue(state.beat() == 0, diagnostic(fixture, helper, "beat", 0, state.beat()));
			helper.succeed();
		} finally {
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
		}
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void nextSuccessfulSwapOpensRevised(GameTestHelper helper) {
		String fixture = "nextSuccessfulSwapOpensRevised";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		try {
			openRevised(caster);
			TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
			helper.assertTrue(TodoRhythmRuntime.isRevised(caster), diagnostic(fixture, helper, "revised", true,
				TodoRhythmRuntime.isRevised(caster)));
			helper.assertTrue(TodoRhythmRuntime.revisedRemainingTicks(caster) == 120,
				diagnostic(fixture, helper, "revised duration", 120, TodoRhythmRuntime.revisedRemainingTicks(caster)));
			helper.assertTrue(state.autoSwapsUsed() <= 2,
				diagnostic(fixture, helper, "auto quota", "<=2", state.autoSwapsUsed()));
			helper.assertTrue(TodoCooldownPolicy.effectiveTicks(true, SwapKind.AIMED.baseCooldownTicks())
					<= SwapKind.AIMED.baseCooldownTicks(), diagnostic(fixture, helper, "scaled cooldown", true, true));
			helper.succeed();
		} finally {
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
		}
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void revisedAutoSwapQuotaIsAtMostTwo(GameTestHelper helper) {
		String fixture = "revisedAutoSwapQuotaIsAtMostTwo";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		try {
			openRevised(caster);
			TodoRhythmRuntime.register();
			TodoSwapHooks.fireAfterCommit(caster, SwapKind.AIMED, SUCCESS, false);
			TodoSwapHooks.fireAfterCommit(caster, SwapKind.AIMED, SUCCESS, false);
			TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
			helper.assertTrue(state.autoSwapsUsed() <= 2,
				diagnostic(fixture, helper, "reserved auto swaps", "<=2", state.autoSwapsUsed()));
			helper.assertTrue(state.pending().size() <= 2,
				diagnostic(fixture, helper, "pending auto swaps", "<=2", state.pending().size()));
			helper.succeed();
		} finally {
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
		}
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void failedAutoSwapRollsBackAndDoesNotCharge(GameTestHelper helper) {
		String fixture = "failedAutoSwapRollsBackAndDoesNotCharge";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		ServerPlayer target = null;
		AtomicInteger calls = new AtomicInteger();
		try {
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			target = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(5, 1, 2));
			TodoSwapTestFixtures.aimAt(caster, target.position());
			ServerPlayer scopedTarget = target;
			helper.runAtTickTime(2, () -> {
				try {
					// The commit seam is process-global static state, so it is held for ZERO real
					// server ticks: the override, the Revised window, the pending re-due, the manual
					// serverTick and every assert all live inside this one synchronous callback.
					// Holding it across ticks (the old tick-1 → tick-12 window) let a sibling test's
					// own override/restore pair silently replace ours — the pending then committed
					// under the production teleport and the caster really moved. The override is
					// still scoped to this fixture's bodies: anything else gets the production
					// teleport. The first commit delegates to it so the moved body proves the
					// rollback restores it; the second commit reports the authoritative failure.
					SwapCommit.overrideCommitTeleport((body, level, destination, yaw, pitch) -> {
						if (body != caster && body != scopedTarget) {
							return SwapCommit.PRODUCTION_COMMIT_TELEPORT.teleport(body, level,
									destination, yaw, pitch);
						}
						if (calls.incrementAndGet() == 1) {
							return SwapCommit.PRODUCTION_COMMIT_TELEPORT.teleport(body, level,
									destination, yaw, pitch);
						}
						return false;
					});
					openRevised(caster);
					// The production schedule reserves the auto-swap for now + 10 ticks; re-due it
					// to NOW so the manual serverTick below executes it inside this callback, while
					// the seam is still ours.
					long now = caster.level().getGameTime();
					TodoRhythmState scheduled = TodoRhythmRuntime.stateOf(caster.getUUID());
					List<PendingAutoSwap> due = new ArrayList<>(scheduled.pending().size());
					for (PendingAutoSwap pending : scheduled.pending()) {
						due.add(new PendingAutoSwap(pending.targetUuid(), now));
					}
					TodoTransientState.setRhythm(caster.getUUID(),
							scheduled.withAutoSwaps(scheduled.autoSwapsUsed(), due));
					Vec3 casterBefore = caster.position();
					Vec3 targetBefore = scopedTarget.position();
					TodoRhythmRuntime.serverTick(helper.getLevel().getServer());
					helper.assertTrue(calls.get() == 2,
							diagnostic(fixture, helper, "commit teleports attempted", 2, calls.get()));
					helper.assertTrue(caster.position().distanceToSqr(casterBefore) < 1.0E-8,
							diagnostic(fixture, helper, "caster rollback", casterBefore, caster.position()));
					helper.assertTrue(scopedTarget.position().distanceToSqr(targetBefore) < 1.0E-8,
							diagnostic(fixture, helper, "target rollback", targetBefore, scopedTarget.position()));
					helper.assertTrue(CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0,
							diagnostic(fixture, helper, "failed auto cooldown", 0,
								CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY)));
					helper.assertTrue(!caster.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM),
							diagnostic(fixture, helper, "failed auto momentum", "false",
								caster.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM)));
					helper.assertTrue(TodoRhythmRuntime.stateOf(caster.getUUID()).pending().isEmpty(),
							diagnostic(fixture, helper, "pending consumed", "empty",
								TodoRhythmRuntime.stateOf(caster.getUUID()).pending()));
					helper.succeed();
				} finally {
					SwapCommit.restoreProductionCommitTeleport();
					CursedSpiritTestFixtures.cleanupVictim(helper, scopedTarget);
					TodoSwapTestFixtures.cleanupCaster(helper, caster);
				}
			});
		} catch (RuntimeException | AssertionError failure) {
			if (target != null) {
				CursedSpiritTestFixtures.cleanupVictim(helper, target);
			}
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
			throw failure;
		}
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void noGroundAutoSwapIsSkipped(GameTestHelper helper) {
		String fixture = "noGroundAutoSwapIsSkipped";
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 8, 2), 0, 0);
		ServerPlayer target = null;
		try {
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			target = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(5, 8, 2));
			TodoSwapTestFixtures.aimAt(caster, target.position());
			openRevised(caster);
			Vec3 casterBefore = caster.position();
			ServerPlayer finalTarget = target;
			helper.runAtTickTime(12, () -> {
				try {
					TodoRhythmRuntime.serverTick(helper.getLevel().getServer());
					helper.assertTrue(caster.position().distanceToSqr(casterBefore) < 1.0E-8,
							diagnostic(fixture, helper, "no-ground skip", casterBefore, caster.position()));
					TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
					helper.assertTrue(state.pending().isEmpty(),
							diagnostic(fixture, helper, "pending consumed", 0, state.pending().size()));
					helper.succeed();
				} finally {
					CursedSpiritTestFixtures.cleanupVictim(helper, finalTarget);
					TodoSwapTestFixtures.cleanupCaster(helper, caster);
				}
			});
		} catch (RuntimeException | AssertionError failure) {
			if (target != null) {
				CursedSpiritTestFixtures.cleanupVictim(helper, target);
			}
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
			throw failure;
		}
	}


	@GameTest(maxTicks = 20, skyAccess = true)
	public void disconnectClearsPendingRhythm(GameTestHelper helper) {
		String fixture = "disconnectClearsPendingRhythm";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(2, 1, 2), 0, 0);
		try {
			openRevised(caster);
			TodoStateLifecycle.dropEverything(caster);
			TodoRhythmState state = TodoRhythmRuntime.stateOf(caster.getUUID());
			helper.assertTrue(state.points() == 0 && state.pending().isEmpty() && !TodoRhythmRuntime.isRevised(caster),
				diagnostic(fixture, helper, "rhythm after disconnect", "zero", state));
			helper.assertTrue(TodoTransientState.rhythm(caster.getUUID()).isEmpty(),
				diagnostic(fixture, helper, "transient rhythm entry", "absent",
						TodoTransientState.rhythm(caster.getUUID()).isPresent()));
			helper.succeed();
		} finally {
			TodoSwapTestFixtures.cleanupCaster(helper, caster);
		}
	}

	private static void primePeak(ServerPlayer caster) {
		TodoRhythmRuntime.register();
		SwapKind[] kinds = {SwapKind.AIMED, SwapKind.STONE_SELF, SwapKind.PAIR, SwapKind.TRIPLE, SwapKind.AIMED};
		long now = caster.level().getGameTime();
		for (int index = 0; index < kinds.length; index++) {
			TodoRhythmRuntime.recordSwap(caster, kinds[index], now + index);
		}
	}

	private static void openRevised(ServerPlayer caster) {
		primePeak(caster);
		TodoSwapHooks.fireAfterCommit(caster, SwapKind.AIMED, SUCCESS, false);
	}

	private static net.minecraft.network.chat.Component diagnostic(String fixture, GameTestHelper helper,
			String what, Object expected, Object actual) {
		return TodoSwapTestFixtures.diagnostic(fixture, "rhythm", helper.getTick(), null, null, what, expected, actual);
	}
}
