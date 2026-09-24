package jujutsu.mod.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiTigerEntity;
import jujutsu.mod.character.megumi.MegumiToadEntity;

/**
 * Tiger Funeral server scenarios — the roster-common skeleton plus the committed-combo
 * signature matrix (plan §C/§I). The combo's beats resolve against a locked target identity
 * ({@code comboTargetUuid}) and a facing frozen at windup end: the tests pin the beat walk on
 * the synced {@code DATA_COMBO_BEAT} (0 = none, 1 = windup, 2/3/4 = the strikes, 5 = recover),
 * the literal 5/7/12 damage curve, the no-retarget lock, and the mid-combo sic deferral.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so bodies are found by owner-UUID
 * scan, never by bounds; mock players are creative so the retaliation/owner-death rows run on a
 * survival victim player instead; the cue packet has no test-side oracle, so the refusal rows
 * assert the structural proxies a cue can never precede (no pack, no body, cooldown free).
 */
public final class MegumiTigerGameTests {

	private static final int SUMMON_TICK = 2;
	private static final int SECOND_SUMMON_TICK = 4;
	private static final int RECALL_TICK = 4;
	private static final int RESUMMON_TICK = 6;
	private static final int SIC_TICK = 32;
	private static final int KILL_TICK = 36;
	private static final int ACT_TICK = 40;

	/** Literal pins, NOT the profile rows: the red-proof mutates the row, the assert must follow. */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 250;
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 560;
	private static final int TOUGH_ZOMBIE_HEALTH = 60;

	/** S1 — selecting TIGER and pressing the technique key summons exactly one live body. */
	@GameTest(maxTicks = 60)
	public void tigerSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "tigerSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TIGER);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.TIGER.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.TIGER.id(), view.get().type()));

				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiTigerEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned tiger bodies", "1", bodies.size()));
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"PRIMARY cooldown (summon is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** S2 — a manual recall removes the body and costs exactly the tiger recall cooldown. */
	@GameTest(maxTicks = 80)
	public void tigerRecallRemovesBodyAndChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "tigerRecallRemovesBodyAndChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "second tryPrimary result", "true", recalled));

				// Same-tick read: the cooldown was just armed, so the remaining time is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"tiger recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
				// Issue #107: the recall price is per type, so the shared PRIMARY slot stays free.
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline)", "0", primary));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/** Materialization gate: the body exists before it fights — {@code combatEnabled} flips once. */
	@GameTest(maxTicks = 80)
	public void tigerSummonMaterializesThenActivates(GameTestHelper helper) {
		String fixture = "tigerSummonMaterializesThenActivates";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean sawMaterializing = new AtomicBoolean();
		AtomicBoolean sawActive = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));

		for (long tick = SUMMON_TICK + 1; tick <= 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				if (!bodies.get(0).combatEnabled()) {
					sawMaterializing.set(true);
				} else {
					sawActive.set(true);
				}
			});
		}
		helper.runAtTickTime(62, () -> {
			try {
				helper.assertTrue(sawMaterializing.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"materialize", 62, caster.getUUID(), "body materializing before it fights",
						"observed", "never observed"));
				helper.assertTrue(sawActive.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"materialize", 62, caster.getUUID(), "body ACTIVE after materialization",
						"observed", "never observed"));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(70, () -> helper.succeed());
	}

	/** S4 — killing the body charges the literal death row and clears the pack the same tick. */
	@GameTest(maxTicks = 90)
	public void tigerDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "tigerDeathChargesDeathCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiTigerEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"owned tiger bodies in level", "1", bodies.size()));
				MegumiTigerEntity body = bodies.get(0);
				helper.assertTrue(body.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true",
						body.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				long gameTime = level.getGameTime();
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.TIGER, gameTime);
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"tiger death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline)", "0", primary));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(45, () -> helper.succeed());
	}

	/**
	 * A summon pressed inside solid stone refuses clean: no pack, no body, cooldown free — and the
	 * refusal never reaches a body anchor for the summon cue to ride on (the §10 negative pin is
	 * structural: {@code TIGER_SUMMON} cannot emit from a commit that never happened).
	 */
	@GameTest(maxTicks = 60)
	public void tigerNoRoomSummonRefusesAndKeepsCooldownFree(GameTestHelper helper) {
		String fixture = "tigerNoRoomSummonRefusesAndKeepsCooldownFree";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		// Solid box around the caster: every ground-placement candidate sits inside stone, and the
		// vertical scan window (±3) stays inside the lid.
		for (int dx = 0; dx <= 4; dx++) {
			for (int dy = 0; dy <= 4; dy++) {
				for (int dz = 0; dz <= 4; dz++) {
					helper.setBlock(new BlockPos(dx, dy, dz), Blocks.STONE);
				}
			}
		}

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TIGER);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result in solid stone",
						"false", summoned));

				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiTigerEntity.class);
				helper.assertTrue(bodies.isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned tiger bodies after refusal", "0", bodies.size()));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "summon", caster);

				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"tiger cooldown after refusal (never armed)", "0", remaining));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"PRIMARY slot", "0", primary));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** Cooldown refusal: a resummon inside the recall deadline refuses and arms nothing new. */
	@GameTest(maxTicks = 60)
	public void tigerResummonRefusedWhileRecallCooldownArmed(GameTestHelper helper) {
		String fixture = "tigerResummonRefusedWhileRecallCooldownArmed";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(RECALL_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(),
							caster.getUUID(), "recall tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(RESUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"resummon", helper.getTick(), ownerId, "tryPrimary result on cooldown",
						"false", summoned));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining > 0 && remaining <= EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "resummon", helper.getTick(), ownerId,
								"tiger cooldown still armed", "(0, " + EXPECTED_RECALL_COOLDOWN_TICKS + "]",
								remaining));
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiTigerEntity.class);
				helper.assertTrue(bodies.isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "resummon", helper.getTick(), ownerId,
								"owned tiger bodies after refusal", "0", bodies.size()));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "resummon", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** The manual sic marks the zombie on the body — the mark every stalk read consumes. */
	@GameTest(maxTicks = 100)
	public void tigerSicCommandMarksTheTarget(GameTestHelper helper) {
		String fixture = "tigerSicCommandMarksTheTarget";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		parkVictim(zombie, caster);
		AtomicBoolean marked = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		for (long tick = SIC_TICK + 1; tick <= SIC_TICK + 40; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty() || marked.get()) {
					return;
				}
				if (bodies.get(0).getTarget() == zombie) {
					marked.set(true);
				}
			});
		}
		helper.runAtTickTime(SIC_TICK + 45, () -> {
			try {
				helper.assertTrue(marked.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", SIC_TICK + 45, caster.getUUID(), "tiger body targets the sic'd zombie",
						"marked", "never marked"));
			} finally {
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 50, () -> helper.succeed());
	}

	/**
	 * Signature row — the combo lands all three beats: the synced action index walks
	 * 1 → 2 → 3 → 4 → 5 (windup, strike_1, strike_2, finisher, recover) and the victim's
	 * health drops on a strictly increasing curve — the literal 5/7/12 table, read through
	 * the zombie's uniform 2 armour as three ordered positive deltas.
	 */
	@GameTest(maxTicks = 400)
	public void tigerComboLandsThreeBeats(GameTestHelper helper) {
		String fixture = "tigerComboLandsThreeBeats";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = spawnToughZombie(helper, fixture, zombieFeet, caster);

		AtomicInteger lastBeat = new AtomicInteger(0);
		AtomicInteger walkPosition = new AtomicInteger(1);
		AtomicReference<Double> healthAtBeat = new AtomicReference<>((double) zombie.getHealth());
		AtomicReference<Double> delta1 = new AtomicReference<>();
		AtomicReference<Double> delta2 = new AtomicReference<>();
		AtomicReference<Double> delta3 = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				int beat = bodies.get(0).comboBeat();
				int last = lastBeat.get();
				if (beat != last) {
					// The index just flipped: the closing beat's resolve ran in the same tick, so
					// this poll reads the victim's health after it.
					double spent = healthAtBeat.get() - zombie.getHealth();
					if (last == 2) {
						delta1.set(spent);
					} else if (last == 3) {
						delta2.set(spent);
					} else if (last == 4) {
						delta3.set(spent);
					}
					healthAtBeat.set((double) zombie.getHealth());
					lastBeat.set(beat);
					// Strict-order walk: the index may only climb 1→2→3→4→5 inside a combo.
					if (beat == walkPosition.get() && walkPosition.get() <= 5) {
						walkPosition.incrementAndGet();
					}
				}
				if (beat == 0 && walkPosition.get() == 6) {
					done.set(true);
					try {
						assertBeatDelta(helper, fixture, pollTick, caster, delta1.get(), "strike_1 (5.0)");
						assertBeatDelta(helper, fixture, pollTick, caster, delta2.get(), "strike_2 (7.0)");
						assertBeatDelta(helper, fixture, pollTick, caster, delta3.get(), "finisher (12.0)");
						double d1 = delta1.get();
						double d2 = delta2.get();
						double d3 = delta3.get();
						helper.assertTrue(d1 < d2 && d2 < d3,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "combo", pollTick,
										caster.getUUID(), "beat deltas ordered by the 5/7/12 curve",
										"d1 < d2 < d3", d1 + " / " + d2 + " / " + d3));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "combo",
							pollTick, caster.getUUID(), "combo walk reached recovery",
							"beat indices 1→5 all observed", "stuck at expected " + walkPosition.get()
									+ " (last beat " + beat + ", victim hp " + zombie.getHealth() + ")"));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * The frozen facing is honest: once STRIKE_1 is running, teleporting the victim behind the
	 * body leaves it untouched — the beats whiff on the air where it was, and the tiger never
	 * teleports to chase.
	 */
	@GameTest(maxTicks = 400)
	public void tigerComboMissesWhenTargetLeavesArc(GameTestHelper helper) {
		String fixture = "tigerComboMissesWhenTargetLeavesArc";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = spawnToughZombie(helper, fixture, zombieFeet, caster);

		AtomicBoolean moved = new AtomicBoolean();
		AtomicReference<Vec3> tigerAtWindup = new AtomicReference<>();
		AtomicBoolean sawRecover = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				int beat = tiger.comboBeat();
				if (beat == 1 && tigerAtWindup.get() == null) {
					tigerAtWindup.set(tiger.position());
				}
				if (!moved.get() && beat == 2) {
					// STRIKE_1 is running — the facing froze. Move the victim to the body's rear:
					// 180 degrees outside every arc the combo will ever test.
					double yaw = Math.toRadians(tiger.getYRot());
					Vec3 rear = tiger.position().add(Math.sin(yaw) * 2.5, 0.0, -Math.cos(yaw) * 2.5);
					zombie.teleportTo(rear.x, zombie.getY(), rear.z);
					moved.set(true);
				}
				if (beat == 5) {
					sawRecover.set(true);
				}
				if (sawRecover.get() && beat == 0) {
					done.set(true);
					try {
						helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "miss", pollTick,
										caster.getUUID(), "victim outside the frozen arc takes nothing",
										"full health", zombie.getHealth() + "/" + zombie.getMaxHealth()));
						Vec3 home = tigerAtWindup.get();
						double drift = home == null ? 0.0 : tiger.position().distanceTo(home);
						helper.assertTrue(home != null && drift <= 0.6,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "miss", pollTick,
										caster.getUUID(), "tiger never teleports to chase",
										"planted at windup spot", "drifted " + drift));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "miss",
							pollTick, caster.getUUID(), "combo completed through recovery",
							"beat 5 then 0", "last beat " + beat + " moved=" + moved.get()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * RED-PROOF anchor — a second hostile inside the arc's reach cannot steal the combo: the
	 * locked {@code comboTargetUuid} carries the whole sequence and never retargets.
	 */
	@GameTest(maxTicks = 400)
	public void tigerComboNeverRetargets(GameTestHelper helper) {
		String fixture = "tigerComboNeverRetargets";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombieA = spawnToughZombie(helper, fixture, zombieFeet, caster);
		AtomicReference<Zombie> secondRef = new AtomicReference<>();
		AtomicBoolean lockSeen = new AtomicBoolean();
		AtomicBoolean retargeted = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombieA);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				UUID lock = tiger.comboTargetUuid();
				if (lock != null && !zombieA.getUUID().equals(lock)) {
					retargeted.set(true);
				}
				if (!lockSeen.get() && zombieA.getUUID().equals(lock)) {
					lockSeen.set(true);
					// A second hostile steps into reach while the lock already holds.
					Zombie zombieB = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE,
							new BlockPos(3, 1, 5));
					parkVictim(zombieB, caster);
					secondRef.set(zombieB);
				}
				if (lockSeen.get() && beatDone(tiger)) {
					done.set(true);
					try {
						helper.assertTrue(!retargeted.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
								"retarget", pollTick, caster.getUUID(),
								"locked identity stayed on zombie A for the whole combo",
								"never moved", "moved to " + lock));
						Zombie zombieB = secondRef.get();
						helper.assertTrue(zombieB != null && zombieB.getHealth() == zombieB.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "retarget", pollTick,
										caster.getUUID(), "the decoy inside reach is untouched",
										"full health", zombieB == null ? "never spawned" : zombieB.getHealth()));
					} finally {
						zombieA.discard();
						Zombie zombieB = secondRef.get();
						if (zombieB != null) {
							zombieB.discard();
						}
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombieA.discard();
					Zombie zombieB = secondRef.get();
					if (zombieB != null) {
						zombieB.discard();
					}
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "retarget",
							pollTick, caster.getUUID(), "combo ran to its end on the lock",
							"lock released after recovery", "lock seen=" + lockSeen.get()
									+ " last=" + lock));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * A manual sic landing mid-combo is a pending order, not a live retarget: the runtime records
	 * the mark, the lock still carries the running sequence, and only once recovery ends does the
	 * body turn to the new mark.
	 */
	@GameTest(maxTicks = 400)
	public void tigerManualSicMidComboAppliesAfterRecovery(GameTestHelper helper) {
		String fixture = "tigerManualSicMidComboAppliesAfterRecovery";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombieA = spawnToughZombie(helper, fixture, zombieFeet, caster);
		AtomicReference<Zombie> secondRef = new AtomicReference<>();
		AtomicBoolean siccedB = new AtomicBoolean();
		AtomicBoolean sawRecover = new AtomicBoolean();
		AtomicBoolean lockBroke = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombieA);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				int beat = tiger.comboBeat();
				UUID lock = tiger.comboTargetUuid();
				if (beat >= 1 && lock != null && !zombieA.getUUID().equals(lock)) {
					lockBroke.set(true);
				}
				if (!siccedB.get() && beat == 2) {
					// Mid-combo: the order lands while strike_1 is swinging.
					Zombie zombieB = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE,
							new BlockPos(3, 1, 5));
					parkVictim(zombieB, caster);
					secondRef.set(zombieB);
					TodoSwapTestFixtures.aimAt(caster,
							zombieB.position().add(0.0, zombieB.getBbHeight() / 2.0, 0.0));
					boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
					helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic-B", pollTick, caster.getUUID(), "mid-combo sic still resolves",
							"true", sicced));
					siccedB.set(true);
				}
				if (beat == 5) {
					sawRecover.set(true);
				}
				if (siccedB.get() && sawRecover.get() && beat == 0) {
					done.set(true);
					try {
						Zombie zombieB = secondRef.get();
						helper.assertTrue(!lockBroke.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
								"sic-B", pollTick, caster.getUUID(),
								"comboTargetUuid stayed on zombie A through the sequence",
								"never moved", "moved off"));
						helper.assertTrue(zombieB != null && tiger.getTarget() == zombieB,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "sic-B", pollTick,
										caster.getUUID(), "the deferred mark applies after recovery",
										"target = zombie B", tiger.getTarget()));
					} finally {
						zombieA.discard();
						if (secondRef.get() != null) {
							secondRef.get().discard();
						}
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombieA.discard();
					if (secondRef.get() != null) {
						secondRef.get().discard();
					}
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic-B",
							pollTick, caster.getUUID(), "combo ended on the original lock",
							"recovery then stalk", "beat " + beat + " siccedB=" + siccedB.get()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * Friendly fire: an allied body standing inside the strike arc takes nothing — the connect
	 * test names exactly the locked identity, so the toad in the swing's path is just scenery.
	 */
	@GameTest(maxTicks = 400)
	public void tigerSparesAlliedSummons(GameTestHelper helper) {
		String fixture = "tigerSparesAlliedSummons";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = spawnToughZombie(helper, fixture, zombieFeet, caster);
		AtomicBoolean sawAction = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "toad tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK + 4, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 5; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> tigers = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				List<MegumiToadEntity> toads = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiToadEntity.class);
				if (tigers.isEmpty() || toads.isEmpty()) {
					return;
				}
				int beat = tigers.get(0).comboBeat();
				if (beat >= 2 && beat <= 4) {
					sawAction.set(true);
				}
				if (sawAction.get() && beat == 0) {
					done.set(true);
					try {
						MegumiToadEntity toad = toads.get(0);
						helper.assertTrue(toad.getHealth() == toad.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "allied", pollTick,
										caster.getUUID(), "allied toad inside the arc took nothing",
										"full health", toad.getHealth() + "/" + toad.getMaxHealth()));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "allied",
							pollTick, caster.getUUID(), "combo ran while the ally stood in the arc",
							"beats seen then recovery", "sawAction=" + sawAction.get()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/** The finisher's extra lift: the victim's rise peaks measurably higher than the quick hits'. */
	@GameTest(maxTicks = 400)
	public void tigerFinisherLaunches(GameTestHelper helper) {
		String fixture = "tigerFinisherLaunches";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = spawnToughZombie(helper, fixture, zombieFeet, caster);

		double startY = zombie.getY();
		AtomicReference<Double> strikePeakY = new AtomicReference<>(startY);
		AtomicReference<Double> finisherPeakY = new AtomicReference<>(startY);
		AtomicBoolean sawRecover = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				int beat = bodies.get(0).comboBeat();
				if (beat == 2 || beat == 3) {
					strikePeakY.set(Math.max(strikePeakY.get(), zombie.getY()));
				}
				if (beat == 4 || beat == 5) {
					finisherPeakY.set(Math.max(finisherPeakY.get(), zombie.getY()));
				}
				if (beat == 5) {
					sawRecover.set(true);
				}
				if (sawRecover.get() && beat == 0) {
					done.set(true);
					try {
						helper.assertTrue(finisherPeakY.get() > startY + 0.4,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "finisher", pollTick,
										caster.getUUID(), "the finisher launched the victim",
										"> " + (startY + 0.4), finisherPeakY.get()));
						helper.assertTrue(finisherPeakY.get() > strikePeakY.get() + 0.15,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "finisher", pollTick,
										caster.getUUID(), "finisher lift exceeds the quick hits' pop",
										"peak > strike peak + 0.15",
										"finisher " + finisherPeakY.get() + " vs strikes " + strikePeakY.get()));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "finisher",
							pollTick, caster.getUUID(), "combo completed", "recovery observed",
							"beat " + beat + " hp " + zombie.getHealth()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * Retaliation: an attacker who hurts the owner becomes the mark without any sic. Mock players
	 * are creative and refuse {@code hurtServer}, so the owner here is a survival victim player —
	 * same fixture the shared retaliation suite uses.
	 */
	@GameTest(maxTicks = 400, skyAccess = true)
	public void tigerRetaliationMarkEngages(GameTestHelper helper) {
		String fixture = "tigerRetaliationMarkEngages";
		layArenaFloor(helper);
		laySkyCover(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean marked = new AtomicBoolean();
		AtomicBoolean locked = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							owner.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE,
					new BlockPos(5, 1, 4));
			attacker.setPersistenceRequired();
			attacker.setNoAi(true);
			CursedSpiritTestFixtures.freezeGround(attacker);
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			helper.assertTrue(owner.getHealth() < owner.getMaxHealth(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "scripted hit landed", "< " + owner.getMaxHealth(),
							owner.getHealth()));
		}));

		long deadline = ACT_TICK + 200;
		for (long tick = ACT_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, owner.getUUID(), MegumiTigerEntity.class);
				if (attacker == null || bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				if (!marked.get() && tiger.getTarget() == attacker) {
					marked.set(true);
				}
				if (marked.get() && attacker.getUUID().equals(tiger.comboTargetUuid())) {
					locked.set(true);
				}
				if (locked.get() && attacker.getHealth() < attacker.getMaxHealth()) {
					done.set(true);
					try {
						helper.assertTrue(owner.getHealth() >= owner.getMaxHealth() - 1.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate", pollTick,
										owner.getUUID(), "owner untouched beyond the scripted hit",
										owner.getMaxHealth() - 1.0f, owner.getHealth()));
					} finally {
						attacker.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					}
					return;
				}
				if (pollTick == deadline) {
					if (attacker != null) {
						attacker.discard();
					}
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate",
							pollTick, owner.getUUID(),
							marked.get() ? "locked the retaliating attacker" : "marked the attacker",
							"marked then locked", "marked=" + marked.get() + " target=" + tiger.getTarget()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * Autonomy: a hostile standing inside the owner's read is flagged by the coordinator without
	 * any command — the tiger accepts the autonomous mark and walks the combo on it.
	 */
	@GameTest(maxTicks = 400)
	public void coordinatorAutonomousMarkTriggersTiger(GameTestHelper helper) {
		String fixture = "coordinatorAutonomousMarkTriggersTiger";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);

		AtomicBoolean marked = new AtomicBoolean();
		AtomicBoolean engaged = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));

		long deadline = SIC_TICK + 200;
		for (long tick = SUMMON_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				if (!marked.get() && tiger.getTarget() == zombie) {
					marked.set(true);
				}
				if (marked.get() && zombie.getUUID().equals(tiger.comboTargetUuid())) {
					engaged.set(true);
				}
				if (engaged.get() && zombie.getHealth() < zombie.getMaxHealth()) {
					done.set(true);
					try {
						helper.assertTrue(zombie.getUUID().equals(tiger.comboTargetUuid()),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomy", pollTick,
										caster.getUUID(), "the autonomous mark drove the combo to contact",
										zombie.getUUID(), tiger.comboTargetUuid()));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomy",
							pollTick, caster.getUUID(),
							marked.get() ? "combo engaged the autonomous mark" : "coordinator marked",
							"marked then engaged", "marked=" + marked.get() + " target=" + tiger.getTarget()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/**
	 * An allied body can never be the lock: sic'ing at the toad resolves to nothing (the cancel
	 * order), and the tiger simply idles — no mark, no swing, no damage.
	 */
	@GameTest(maxTicks = 160)
	public void tigerNeverAttacksAlliedBodies(GameTestHelper helper) {
		String fixture = "tigerNeverAttacksAlliedBodies";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean sawMark = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "toad tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> summonTiger(helper, fixture, caster));

		helper.runAtTickTime(SIC_TICK + 8, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			List<MegumiToadEntity> toads = MegumiShikigamiTestFixtures.ownedBy(
					level, caster.getUUID(), MegumiToadEntity.class);
			helper.assertTrue(toads.size() == 1,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
							caster.getUUID(), "allied toad out", "1", toads.size()));
			MegumiToadEntity toad = toads.get(0);
			TodoSwapTestFixtures.aimAt(caster, toad.position().add(0.0, toad.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic",
					helper.getTick(), caster.getUUID(), "sic aimed at an ally resolves as cancel",
					"true", sicced));
		}));

		for (long tick = SIC_TICK + 9; tick <= SIC_TICK + 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiTigerEntity> tigers = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (tigers.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = tigers.get(0);
				if (tiger.getTarget() != null || tiger.comboTargetUuid() != null) {
					sawMark.set(true);
				}
			});
		}
		helper.runAtTickTime(SIC_TICK + 65, () -> {
			try {
				List<MegumiToadEntity> toads = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiToadEntity.class);
				helper.assertTrue(!sawMark.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"ally", SIC_TICK + 65, caster.getUUID(), "tiger never marks an allied body",
						"no target, no lock", "target or lock observed"));
				helper.assertTrue(toads.size() == 1
								&& toads.get(0).getHealth() == toads.get(0).getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", SIC_TICK + 65,
								caster.getUUID(), "allied toad unharmed", "full health",
								toads.isEmpty() ? "gone" : toads.get(0).getHealth()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 70, () -> helper.succeed());
	}

	/**
	 * The lock outlives the corpse: the victim dies mid-combo, the locked identity stays pinned
	 * while the beats whiff on the grave, and recovery still walks out clean — DATA returns to 0
	 * and the lock clears.
	 */
	@GameTest(maxTicks = 400)
	public void tigerDeadTargetMidComboRecoversCleanly(GameTestHelper helper) {
		String fixture = "tigerDeadTargetMidComboRecoversCleanly";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 5);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		parkVictim(zombie, caster);

		AtomicBoolean killed = new AtomicBoolean();
		AtomicBoolean sawRecover = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonTiger(helper, fixture, caster));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = SIC_TICK + 220;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, caster.getUUID(), MegumiTigerEntity.class);
				if (bodies.isEmpty()) {
					return;
				}
				MegumiTigerEntity tiger = bodies.get(0);
				int beat = tiger.comboBeat();
				if (!killed.get() && beat == 2) {
					// The corpse lands mid-sequence — the lock must not corrupt the state machine.
					zombie.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
					killed.set(true);
				}
				if (beat == 5) {
					sawRecover.set(true);
				}
				if (killed.get() && sawRecover.get() && beat == 0) {
					done.set(true);
					try {
						helper.assertTrue(tiger.comboTargetUuid() == null,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "dead-target", pollTick,
										caster.getUUID(), "lock cleared on recovery exit",
										"null", tiger.comboTargetUuid()));
						helper.assertTrue(tiger.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
								"dead-target", pollTick, caster.getUUID(), "tiger alive after the dead lock",
								"alive", "dead"));
						helper.assertTrue(tiger.getTarget() == null,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "dead-target", pollTick,
										caster.getUUID(), "no phantom mark re-arms",
										"null", tiger.getTarget()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "dead-target",
							pollTick, caster.getUUID(), "recovery walked out after the lock died",
							"beat 0 and lock null", "beat " + beat + " killed=" + killed.get()));
				}
			});
		}
		helper.runAtTickTime(deadline + 10, () -> helper.succeed());
	}

	/** Owner death tears down the pack through AFTER_DEATH — the body's gone and the row prices 560. */
	@GameTest(maxTicks = 90, skyAccess = true)
	public void tigerOwnerDeathTeardownClearsPack(GameTestHelper helper) {
		String fixture = "tigerOwnerDeathTeardownClearsPack";
		layArenaFloor(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							owner.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = owner.getUUID();
				owner.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(owner.isDeadOrDying(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"owner-death", helper.getTick(), ownerId, "owner died", "true", owner.isDeadOrDying()));

				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "owner-death", helper.getTick(), ownerId,
								"tiger death cooldown on owner death", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "owner-death", owner);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				CursedSpiritTestFixtures.cleanupVictim(helper, owner);
			}
		});
		helper.runAtTickTime(80, () -> helper.succeed());
	}

	/** The dimension-change teardown prices the recall row — the same shape as the shared C7 row. */
	@GameTest(maxTicks = 60)
	public void tigerDimensionChangeTeardownChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "tigerDimensionChangeTeardownChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "dimension", caster);
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "dimension", helper.getTick(), ownerId,
								"tiger recall cooldown on dimension change", EXPECTED_RECALL_COOLDOWN_TICKS,
								remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** Two types out at once — the tiger shares the owner's row with the toad, additively. */
	@GameTest(maxTicks = 90)
	public void tigerCoexistsWithAnotherPack(GameTestHelper helper) {
		String fixture = "tigerCoexistsWithAnotherPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layArenaFloor(helper);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "toad tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> summonTiger(helper, fixture, caster));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.size() == 2
								&& types.contains(MegumiShikigami.TIGER.id())
								&& types.contains(MegumiShikigami.TOAD.id()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"pack types out together", "{tiger, toad}", types));
				helper.assertTrue(MegumiShikigamiTestFixtures.ownedBy(level, ownerId, MegumiTigerEntity.class).size() == 1
								&& MegumiShikigamiTestFixtures.ownedBy(level, ownerId, MegumiToadEntity.class).size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"one body per pack", "{tiger:1, toad:1}", "mismatch"));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(80, () -> helper.succeed());
	}

	// --- helpers ---

	private static void summonTiger(GameTestHelper helper, String fixture, ServerPlayer caster) {
		MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tiger tryPrimary result", "true", "false"));
		});
	}

	/** The production sic call: eye-to-chest aim, then the runtime resolves the mark. */
	private static void sicAt(GameTestHelper helper, String fixture, ServerPlayer caster, Zombie victim) {
		UUID ownerId = caster.getUUID();
		helper.assertTrue(victim.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
				"sic", helper.getTick(), ownerId, "victim alive before the sic", "true", victim.isAlive()));
		TodoSwapTestFixtures.aimAt(caster, victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
		boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
		helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
	}

	/**
	 * The §I victim: a zombie the whole 24-point curve cannot kill — AI body (full physics, real
	 * knockback) but self-motion zeroed by Slowness 100, so every displacement belongs to a beat.
	 */
	private static Zombie spawnToughZombie(GameTestHelper helper, String fixture, BlockPos feet,
			ServerPlayer caster) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, feet);
		parkVictim(zombie, caster);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TOUGH_ZOMBIE_HEALTH);
		zombie.setHealth(TOUGH_ZOMBIE_HEALTH);
		return zombie;
	}

	private static void parkVictim(Zombie zombie, ServerPlayer caster) {
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
	}

	/** A survival-bodied owner for the rows that need a damageable player. */
	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void assertBeatDelta(GameTestHelper helper, String fixture, long tick,
			ServerPlayer caster, Double delta, String label) {
		helper.assertTrue(delta != null && delta > 0.0,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "combo", tick, caster.getUUID(),
						label + " landed on the victim", "> 0", delta));
	}

	private static boolean beatDone(MegumiTigerEntity tiger) {
		return tiger.comboBeat() == 0 && tiger.comboTargetUuid() == null;
	}

	/** Solid arena floor: the tiger's straight-line approach must never fall off the map. */
	private static void layArenaFloor(GameTestHelper helper) {
		for (int dx = 0; dx <= 7; dx++) {
			for (int dz = 0; dz <= 7; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}

	/** Stone ceiling over the arena: zombie victims would otherwise burn in daylight mid-combo. */
	private static void laySkyCover(GameTestHelper helper) {
		for (int dx = 0; dx <= 7; dx++) {
			for (int dz = 0; dz <= 7; dz++) {
				helper.setBlock(new BlockPos(dx, 5, dz), Blocks.STONE);
			}
		}
	}
}
