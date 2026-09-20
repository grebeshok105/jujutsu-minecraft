package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritShelterGoal;
import jujutsu.mod.cursedspirit.CursedSpiritShelterPolicy;
import jujutsu.mod.cursedspirit.CursedSpiritSpawnSchedule;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Shelter-goal oracles (Block 4, Step 4): by day and out of combat the body settles in a
 * {@code score >= 0.9} cell; combat and night keep the goal silent.
 *
 * <p>Clock discipline: only this class writes long-window time, and the night scenario pins and
 * asserts strictly between the day scenario's 20-tick polls (setter-free windows), so the shared
 * level clock cannot flake either side.
 */
public final class CursedSpiritShelterGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final BlockPos SPIRIT_START = new BlockPos(2, 1, 2);
	private static final long NOON = 6000L;
	private static final long MIDNIGHT = 18000L;

	private final List<CursedSpiritEntity> owned = new ArrayList<>();
	private final boolean[] settled = new boolean[1];

	/** Day, no combat: open-sky start reaches a settled cell within 200 ticks (budget 240). */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void daySeeksShelterWithoutCombat(GameTestHelper helper) {
		String fixture = "daySeeksShelterWithoutCombat";
		layArena(helper);
		helper.runAtTickTime(5, () -> {
			owned.clear();
			settled[0] = false;
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			helper.getLevel().setDayTime(NOON);
			CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
					JujutsuEntities.LESSER_CURSED_SPIRIT, SPIRIT_START);
			owned.add(spirit);
		});
		helper.runAtTickTime(10, () -> {
			ServerLevel level = helper.getLevel();
			level.setDayTime(NOON);
			helper.assertTrue(CursedSpiritShelterPolicy.shelterScoreAt(level,
					helper.absolutePos(SPIRIT_START)) < 0.05, GameTestFixtures.diagnostic(fixture,
					helper.getTick(), "premise: start is open sky", "< 0.05", "see report"));
			helper.assertTrue(CursedSpiritShelterPolicy.shelterScoreAt(level,
					helper.absolutePos(new BlockPos(5, 1, 1))) >= 0.9, GameTestFixtures.diagnostic(
					fixture, helper.getTick(), "premise: roof cell is settled", ">= 0.9", "see report"));
			CursedSpiritShelterGoal goal = owned.get(0).shelterGoal();
			helper.assertTrue(goal != null, GameTestFixtures.diagnostic(fixture, helper.getTick(),
					"premise: shelter goal registered", "non-null", "null"));
			helper.assertTrue(goal.getFlags().equals(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP)),
					GameTestFixtures.diagnostic(fixture, helper.getTick(), "goal flags are MOVE+JUMP",
							"[MOVE, JUMP]", goal.getFlags().toString()));
		});
		for (int tick = 20; tick <= 220; tick += 20) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				ServerLevel level = helper.getLevel();
				level.setDayTime(NOON);
				CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
				// Liveness repair: a concurrent global PEACEFUL flip (the peaceful oracles
				// in this lane) discards Monster bodies via Mob.checkDespawn regardless of
				// persistence — a discarded body freezes at the open-sky start and would
				// report 0.0 forever. Replace it and keep walking; the settle oracle is
				// untouched.
				CursedSpiritEntity spirit = owned.get(0);
				if (spirit.isRemoved() || !spirit.isAlive()) {
					owned.set(0, CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
							JujutsuEntities.LESSER_CURSED_SPIRIT, SPIRIT_START));
					return;
				}
				if (CursedSpiritShelterPolicy.shelterScoreAt(level, spirit.blockPosition())
						>= CursedSpiritShelterPolicy.SETTLED_AT) {
					settled[0] = true;
					spirit.discard();
					helper.succeed();
				}
			});
		}
		helper.runAtTickTime(236, () -> {
			if (!settled[0]) {
				CursedSpiritEntity spirit = owned.isEmpty() ? null : owned.get(0);
				double score = spirit == null ? -1.0 : CursedSpiritShelterPolicy.shelterScoreAt(
						helper.getLevel(), spirit.blockPosition());
				for (CursedSpiritEntity ownedSpirit : owned) {
					ownedSpirit.discard();
				}
				helper.fail(GameTestFixtures.diagnostic(fixture, helper.getTick(),
						"day walk never settled", "score >= 0.9 by tick 220", score));
			}
		});
	}

	/** Combat: the goal holds no target and the body keeps pursuing the victim instead. */
	@GameTest(maxTicks = 180, skyAccess = true)
	public void combatSilencesShelter(GameTestHelper helper) {
		String fixture = "combatSilencesShelter";
		layArena(helper);
		boolean[] combatObserved = {false};
		helper.runAtTickTime(5, () -> {
			owned.clear();
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			helper.getLevel().setDayTime(NOON);
			net.minecraft.server.level.ServerPlayer victim =
					CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(2, 1, 5));
			// Perceiver by Block 1's flag (precedent: CursedSpiritPerceptionGameTests): without a
			// vessel the victim is NONE and untargetable once the aggro gate lands, which would
			// red the premise instead of observing combat-silence. The R43 scenario covers NONE.
			jujutsu.mod.character.CharacterSelectionManager.select(victim,
					jujutsu.mod.character.JujutsuCharacter.MEGUMI);
			victims.add(victim);
			owned.add(CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
					JujutsuEntities.LESSER_CURSED_SPIRIT, SPIRIT_START));
			approachFrom[0] = owned.get(0).position().distanceToSqr(victim.position());
			// Deterministic combat seed: vanilla NearestAttackableTargetGoal.canUse passes a
			// random gate (nextInt(~10) == 0 per tick), so an unlucky roll can skip acquisition
			// for the whole 90-tick poll window (~1/13 000 tail — seen as a CI flake where
			// shelter stayed legally active with target=null). This scenario asserts the
			// combat-silences-shelter arbitration (R-oracle), not acquisition latency, so the
			// target is seeded directly; acquisition itself is covered by the perception
			// scenarios and lesser_acquires_victim_and_deals_melee_damage.
			owned.get(0).setTarget(victim);
		});
		// History: tick-100 red showed 9.25 == 9.25 with a live target, and tick-150 forensics
		// showed dist=2.52 (body HAD approached) — the old block-quantized metric
		// (blockPosition().distToCenterSqr) cannot see sub-block movement, hence the
		// entity-position metric. The 22:47 red flipped modes (target null @150): with the
		// body in reach and striking, the victim can die between sparse top-ups, and a dead
		// victim voids the target premise — so the victim is topped up every 10 ticks, which
		// is deterministic (one strike cycle is ~25 ticks and cannot one-shot 20 HP).
		for (int tick = 60; tick <= 140; tick += 10) {
			final int upkeepTick = tick;
			helper.runAtTickTime(upkeepTick, () -> {
				helper.getLevel().setDayTime(NOON);
				CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
				CursedSpiritEntity spirit = owned.get(0);
				if (spirit.isRemoved() || !spirit.isAlive()) {
					CursedSpiritEntity fresh = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
							JujutsuEntities.LESSER_CURSED_SPIRIT, SPIRIT_START);
					owned.set(0, fresh);
					approachFrom[0] = fresh.position().distanceToSqr(victims.get(0).position());
					fresh.setTarget(victims.get(0));
					spirit = fresh;
				}
				topUpVictim();
				net.minecraft.server.level.ServerPlayer victim = victims.get(0);
				double now = spirit.position().distanceToSqr(victim.position());
				if (spirit.getTarget() == victim
						&& spirit.shelterGoal().currentShelterTarget() == null
						&& now < approachFrom[0]) {
					combatObserved[0] = true;
				}
			});
		}
		helper.runAtTickTime(150, () -> {
			CursedSpiritEntity spirit = owned.get(0);
			net.minecraft.server.level.ServerPlayer victim = victims.get(0);
			helper.assertTrue(combatObserved[0], GameTestFixtures.diagnostic(fixture,
					helper.getTick(), "combat pursuit observed while shelter stayed silent",
					"target=victim, shelter=null, distance decreased",
					"target=" + spirit.getTarget()
							+ " shelter=" + spirit.shelterGoal().currentShelterTarget()
							+ " distance=" + spirit.position().distanceToSqr(victim.position())
							+ " start=" + approachFrom[0]
							+ " victimAlive=" + victim.isAlive()
							+ " moveOwned=" + spirit.abilityBrain().movementOwned(
									helper.getLevel().getGameTime())
							+ " retreat=" + spirit.abilityBrain().shouldRetreat(
									helper.getLevel().getGameTime(), true)
							+ " navDone=" + spirit.getNavigation().isDone()
							+ " navPath=" + spirit.getNavigation().getPath()
							+ " active=" + spirit.abilityBrain()));
		});
		helper.runAtTickTime(170, () -> {
			for (CursedSpiritEntity spirit : owned) {
				spirit.discard();
			}
			for (net.minecraft.server.level.ServerPlayer victim : victims) {
				CursedSpiritTestFixtures.cleanupVictim(helper, victim);
			}
			victims.clear();
			helper.succeed();
		});
	}

	// Premise hygiene for the long combat window: strikes may land once the body arrives, so
	// keep the victim whole — an early death would void the pursuit oracle. Targeting and
	// movement are untouched.
	private void topUpVictim() {
		if (victims.isEmpty()) {
			return;
		}
		net.minecraft.server.level.ServerPlayer victim = victims.get(0);
		if (!victim.isRemoved() && victim.isAlive()) {
			victim.setHealth(victim.getMaxHealth());
		}
	}

	/** Night: the goal never engages (pins/asserts avoid the day scenario's poll ticks). */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void nightSilencesShelter(GameTestHelper helper) {
		String fixture = "nightSilencesShelter";
		layArena(helper);
		helper.runAtTickTime(5, () -> {
			owned.clear();
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			helper.getLevel().setDayTime(MIDNIGHT);
			owned.add(CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
					JujutsuEntities.LESSER_CURSED_SPIRIT, SPIRIT_START));
		});
		for (int tick : new int[] {91, 101, 111}) {
			helper.runAtTickTime(tick, () -> {
				helper.getLevel().setDayTime(MIDNIGHT);
				if (owned.get(0).shelterGoal().currentShelterTarget() == null
						&& !CursedSpiritSpawnSchedule.isDaytime(helper.getLevel().getDayTime())) {
					for (CursedSpiritEntity spirit : owned) {
						spirit.discard();
					}
					helper.succeed();
				}
			});
		}
		helper.runAtTickTime(140, () -> {
			CursedSpiritShelterGoal goal = owned.isEmpty() ? null : owned.get(0).shelterGoal();
			for (CursedSpiritEntity spirit : owned) {
				spirit.discard();
			}
			helper.fail(GameTestFixtures.diagnostic(fixture, helper.getTick(), "goal engaged at night",
					"null target", goal == null ? "goal missing" : String.valueOf(goal.currentShelterTarget())));
		});
	}

	private final List<net.minecraft.server.level.ServerPlayer> victims = new ArrayList<>();
	private final double[] approachFrom = new double[1];

	private static void layArena(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		for (int x = 5; x <= 6; x++) {
			for (int z = 1; z <= 2; z++) {
				helper.setBlock(new BlockPos(x, 4, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
	}
}
