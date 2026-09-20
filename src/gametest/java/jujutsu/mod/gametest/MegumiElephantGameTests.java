package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiElephantPolicy;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiFriendlyFire;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Max Elephant (Ten Shadows selection layer) server scenarios — summon (S1), the trunk jet on a
 * plain target (S2), the jet sparing its owner (S3), recall (S4), death (S5), the jet planting the
 * body (S6), the presence shoving a neutral without damage (S7), the presence wounding a hostile
 * every period (S8), the presence sparing the owner (S9) and owned bodies (S10), the footprint
 * breaking only its allowlist (S11), the footprint standing still / budgeted (S12), and the
 * out-of-combat owner leash (R17) —
 * exercised through the production runtime calls {@code MegumiShikigamiRuntime.tryPrimary} /
 * {@code trySic}.
 *
 * <p><b>Pinned literals.</b> S4/S5 assert the literals 260/600 ticks rather than the profile
 * constants ON PURPOSE: the red-proof mutates the profile rows (260-&gt;261, 600-&gt;601) and the
 * assert must follow the balance contract, not the constant. S2/S3 assert the literal [0.9, 1.1] drop band for the same
 * reason (the {@code ELEPHANT_JET_DAMAGE} row): one pulse at face value, shaved ~6% by the
 * zombie's armour, detected on the first soaked tick (exactly one pulse by construction), while
 * every timing number references {@link MegumiShikigamiProfile} directly. S8 reuses the same
 * [0.9, 1.1] band for the same 1.0-vs-2-armour reason (the {@code ELEPHANT_PRESENCE_DAMAGE} row),
 * doubled to [1.8, 2.2] for the two-pulse repeat check.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so bodies are found by owner-UUID scan,
 * never by bounds, and every victim/strip geometry is derived from a live body position minus the
 * structure origin — never absolute. The summon/sic steps sit on different ticks — the runtime drops same-tick
 * duplicate presses. The jet brain only runs in the ACTIVE phase, so the sic waits past the
 * 30-tick materialization with an explicit premise assert (S7–S12 reuse the same premise: the
 * presence and the footprint only run while ACTIVE). The jet target is a NoAI zombie with a
 * stone roof one block above its head (kills sky-burn flakiness; the near-horizontal jet passes
 * well below it). S7/S8 keep their victims NoAI ON PURPOSE beyond stillness: a zombie with AI
 * would acquire the mock owner as its target, and the retaliation pass would hand the elephant a
 * melee mark — the 6.0 melee hits would contaminate the presence-damage oracle. S7 therefore
 * reads the velocity FIELD, never displacement (a NoAI body never integrates it — the rabbits S5
 * oracle note), which also keeps the cow off the ground-track: no launch, no fall damage in the
 * health oracle. S11/S12 drive the body with per-tick {@code moveTo} (never AI luck) while the
 * owner waits off-path inside the 8-block follow-start radius, so the follow goal never fires and
 * never fights the drive. Static state is cleared in setup and on every success/failure path.
 */
public final class MegumiElephantGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	/** Past the 30-tick materialization with margin: {@code hurtServer} is gated on ACTIVE. */
	private static final int KILL_TICK = 40;
	/** Past the 30-tick materialization with margin: the jet brain needs the ACTIVE phase. */
	private static final int SIC_TICK = 36;
	/** S7–S12 premise tick: past the 30-tick materialization, presence/footprint need ACTIVE. */
	private static final int ACTIVE_TICK = 40;
	private static final int JET_WINDOW_TICKS = MegumiShikigamiProfile.ELEPHANT_JET_WINDUP_TICKS
			+ MegumiShikigamiProfile.ELEPHANT_JET_DURATION_TICKS + 5;

	/**
	 * S4 pins this row: a manual recall costs exactly the elephant recall cooldown. Deliberately
	 * NOT {@code MegumiShikigamiProfile.ELEPHANT_RECALL_COOLDOWN_TICKS}.
	 */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 260;
	/**
	 * S5 pins this row: losing the body costs exactly the elephant death cooldown. Deliberately
	 * NOT {@code MegumiShikigamiProfile.ELEPHANT_DEATH_COOLDOWN_TICKS} — the red-proof mutates
	 * that row.
	 */
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 600;
	/**
	 * S2/S3 pin this band, not the profile row: one jet pulse deals
	 * {@code ELEPHANT_JET_DAMAGE} face value, but the zombie's 2 armour points shave ~6% off
	 * (observed 0.94 in the gate), so the floor sits just below face value. Deliberately NOT
	 * {@code MegumiShikigamiProfile.ELEPHANT_JET_DAMAGE} — the red-proof mutates that row.
	 * The poll succeeds on the FIRST soaked tick, which always carries exactly one pulse (pulses
	 * fire every 2 ticks, polls run every tick), so the ceiling pins single-pulse detection: two
	 * pulses would read ~1.88 and trip it.
	 */
	private static final double EXPECTED_JET_DROP_LOW = 0.9;
	private static final double EXPECTED_JET_DROP_HIGH = 1.1;
	/**
	 * S1 — selecting ELEPHANT and pressing the technique key summons exactly one live body with no
	 * cooldown: the pack view reads type "elephant" with one anchored body, one
	 * {@link MegumiElephantEntity} owned by the caster sits in the level, and PRIMARY stays at 0.
	 */
	@GameTest(maxTicks = 60)
	public void elephantSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "elephantSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 4, 0, 4);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.ELEPHANT.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.ELEPHANT.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));
				helper.assertTrue(pack.anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"anchor alive", "true", pack.anchorAlive()));

				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));

				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.ELEPHANT,
						level.getGameTime());
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"summon cooldown (summon is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * S2 — sic on a zombie 5 blocks away on a diagonal ends in a trunk jet: the zombie's health
	 * drops by one armour-shaved pulse and it carries {@code MEGUMI_SOAKED} within
	 * windup+duration+5 ticks. The diagonal (never axis-aligned) keeps a mirrored aim honest: an
	 * X-flipped yaw would hose the empty lane beside the zombie instead.
	 */
	@GameTest(maxTicks = 150)
	public void elephantJetSoaksAndDamagesTarget(GameTestHelper helper) {
		runJetScenario(helper, "elephantJetSoaksAndDamagesTarget", false);
	}

	/**
	 * S3 — same jet with the owner teleported into the corridor (3 blocks down-jet, 1 block to
	 * the side): the zombie in the same corridor takes damage + soak while the owner takes NO
	 * damage and NO soak. The soak contrast is the binding oracle — the mock damage pipeline
	 * refuses health edits on players, but effect application is real, so an owner carrying soak
	 * proves the friendly-fire gate is missing.
	 */
	@GameTest(maxTicks = 150)
	public void elephantJetSparesItsOwner(GameTestHelper helper) {
		runJetScenario(helper, "elephantJetSparesItsOwner", true);
	}

	/**
	 * S4 — pressing the key again while the elephant is out recalls it: PRIMARY reads exactly
	 * 260 ticks and the pack record is gone.
	 */
	@GameTest(maxTicks = 60)
	public void elephantRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "elephantRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 4, 0, 4);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "second tryPrimary result", "true", recalled));

				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.ELEPHANT,
						level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"summon recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});

		// The recall sink plays out after the record is gone; past it +2 no owned body remains.
		helper.runAtTickTime(RECALL_TICK + MegumiShikigamiProfile.ELEPHANT_RECALL_TICKS + 2, () -> {
			List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"gone", helper.getTick(), caster.getUUID(), "owned elephant bodies in level", "0", bodies.size()));
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * S5 — killing the elephant body routes through the death reconciliation: PRIMARY reads exactly
	 * 600 ticks and the pack record is gone. The kill waits past the 30-tick materialization with
	 * an explicit ACTIVE premise, because {@code hurtServer} on the body is gated on combat being
	 * enabled.
	 */
	@GameTest(maxTicks = 80)
	public void elephantDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "elephantDeathChargesDeathCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 4, 0, 4);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));
				MegumiElephantEntity body = bodies.get(0);
				helper.assertTrue(body.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true", body.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.ELEPHANT,
						level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"summon death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * S6 — the jet plants the body: once the sic commits, the body runs NoAI with no vanilla
	 * target (melee/follow goals suspended, pending path cancelled), and when the jet's action
	 * timer runs out the goals resume with the sic target restored. The NoAI window is the binding
	 * oracle — clearing the target alone never stops the goals from walking the trunk off aim.
	 * The zombie fights at 200 health so it survives all twenty pulses and is still a valid
	 * restore target when the jet ends.
	 */
	@GameTest(maxTicks = 120)
	public void elephantJetSuspendsAiWhileFiring(GameTestHelper helper) {
		String fixture = "elephantJetSuspendsAiWhileFiring";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 6);
		paveFloor(helper, 0, 6, 0, 7);
		helper.setBlock(new BlockPos(5, 4, 6), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		// The zombie spawns inside the sic callback, not at setup: left in the arena from tick 0,
		// the elephant's autonomy pass marks it and fires the jet before the sic ever lands, and
		// the "goals live before sic" oracle reads the jet's NoAI instead of a live body.
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean planted = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
				zombie.setPersistenceRequired();
				AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
				helper.assertTrue(health != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie health attribute", "present", "absent"));
				health.setBaseValue(200.0);
				zombie.setHealth(200.0f);
				zombieRef.set(zombie);
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(!bodies.get(0).isNoAi(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"goals live before sic", "NoAI false", "NoAI true"));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));
				TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		long deadline = SIC_TICK + JET_WINDOW_TICKS + 10;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
				if (live.isEmpty() || zombie == null || zombie.isRemoved()) {
					return;
				}
				MegumiElephantEntity body = live.get(0);
				if (body.isNoAi()) {
					helper.assertTrue(body.getTarget() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "no vanilla target while planted", "null",
									String.valueOf(body.getTarget())));
					planted.set(true);
					return;
				}
				if (!planted.get()) {
					return;
				}
				try {
					helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"jet", helper.getTick(), caster.getUUID(), "zombie survived the jet", "alive", "dead"));
					helper.assertTrue(body.getTarget() == zombie,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "sic target restored after the jet", "zombie",
									String.valueOf(body.getTarget())));
					zombie.discard();
				} finally {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.assertTrue(planted.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"jet", helper.getTick(), caster.getUUID(), "planted jet observed", "true", planted.get()));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
	}

	/**
	 * S7 — presence shoves a neutral without wounding it: a NoAI cow parked 2 blocks out gains a
	 * velocity field pointing away (floor ~2x below the 0.7 push) while its health stays full. Fails
	 * if neutrals are skipped, damaged, or shoved toward the body. Reads the FIELD, never position
	 * (a NoAI body never integrates it — no launch, no fall damage in the oracle).
	 */
	@GameTest(maxTicks = 120)
	public void elephantPresencePushesNeutralWithoutDamaging(GameTestHelper helper) {
		String fixture = "elephantPresencePushesNeutralWithoutDamaging";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(5, 1, 6));
		cow.setPersistenceRequired();
		// The cow is teamed with the caster: a bare neutral is still autonomy-eligible
		// (isEligibleTarget accepts any non-allied living), and the elephant would mark it between
		// the park and the push — the jet pulse then reads as exactly 1.0 damage. Allied keeps the
		// push (isOwnSideOnly excludes only the owner's own bodies) but drops the mark.
		net.minecraft.world.scores.PlayerTeam team = level.getScoreboard()
				.addPlayerTeam(fixture + "_team");
		level.getScoreboard().addPlayerToTeam(caster.getScoreboardName(), team);
		level.getScoreboard().addPlayerToTeam(cow.getScoreboardName(), team);
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicBoolean pushed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(cow.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "cow alive", "true", cow.isAlive()));
				Vec3 epos = bodies.get(0).position();
				cow.teleportTo(epos.x + 2.0, epos.y, epos.z);
				healthBefore.set((double) cow.getHealth());
			} catch (RuntimeException | AssertionError failure) {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = ACTIVE_TICK + 50;
		for (long tick = ACTIVE_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (pushed.get() || cow.isRemoved()) {
					return;
				}
				double before = healthBefore.get();
				if (!cow.isAlive() || cow.getHealth() < before) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"push", helper.getTick(), caster.getUUID(), "neutral takes no damage", before, cow.getHealth()));
					} finally {
						cow.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				Vec3 v = cow.getDeltaMovement();
				if (Math.hypot(v.x, v.z) < 0.3) {
					if (pollTick == deadline) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"push", helper.getTick(), caster.getUUID(), "neutral shoved", "speed >= 0.3", Math.hypot(v.x, v.z)));
						} finally {
							cow.discard();
							MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						}
					}
					return;
				}
				try {
					List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
					helper.assertTrue(!live.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"push", helper.getTick(), caster.getUUID(), "body present", "present", "absent"));
					Vec3 away = cow.position().subtract(live.get(0).position());
					away = new Vec3(away.x, 0.0, away.z).normalize();
					Vec3 dir = new Vec3(v.x, 0.0, v.z).normalize();
					helper.assertTrue(away.dot(dir) > 0.5, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"push", helper.getTick(), caster.getUUID(), "shove points away", "> 0.5", away.dot(dir)));
					helper.assertTrue(cow.getHealth() == before, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"push", helper.getTick(), caster.getUUID(), "neutral health unchanged", before, cow.getHealth()));
					pushed.set(true);
					cow.discard();
				} finally {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	/**
	 * S8 — presence wounds a hostile every period: a NoAI zombie parked 2.5 blocks out drops one
	 * armour-shaved pulse ([0.9, 1.1]) on the first period and a second pulse ([1.8, 2.2]) later.
	 * Fails if hostiles are spared, or if damage bleeds every tick (overshoots the two-pulse ceiling).
	 */
	@GameTest(maxTicks = 150)
	public void elephantPresenceDamagesHostileEachPeriod(GameTestHelper helper) {
		String fixture = "elephantPresenceDamagesHostileEachPeriod";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		helper.setBlock(new BlockPos(5, 4, 6), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(5, 1, 6));
		zombie.setPersistenceRequired();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicBoolean first = new AtomicBoolean();
		AtomicBoolean second = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));
				Vec3 epos = bodies.get(0).position();
				zombie.teleportTo(epos.x + 2.5, epos.y, epos.z);
				helper.setBlock(relativeOf(helper, new Vec3(epos.x + 2.5, epos.y, epos.z)).offset(0, 3, 0), Blocks.STONE);
				zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
				healthBefore.set((double) zombie.getHealth());
				helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "zombie full health", zombie.getMaxHealth(), zombie.getHealth()));
			} catch (RuntimeException | AssertionError failure) {
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = ACTIVE_TICK + 70;
		for (long tick = ACTIVE_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (second.get() || zombie.isRemoved()) {
					return;
				}
				double dropped = healthBefore.get() - zombie.getHealth();
				if (!first.get() && dropped >= 0.9) {
					try {
						helper.assertTrue(dropped <= 1.1, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"pulse", helper.getTick(), caster.getUUID(), "first period costs one pulse", "<= 1.1", dropped));
						first.set(true);
						// Presence deliberately pushes. Re-anchor after the first observed pulse
						// so the second-period oracle tests cadence, not whether the shove ejects.
						List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, caster.getUUID());
						helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(
								fixture, "pulse", helper.getTick(), caster.getUUID(),
								"owned body still present", "1", bodies.size()));
						Vec3 epos = bodies.get(0).position();
						zombie.teleportTo(epos.x + 2.5, epos.y, epos.z);
						zombie.setDeltaMovement(Vec3.ZERO);
					} catch (RuntimeException | AssertionError failure) {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						throw failure;
					}
				}
				if (first.get() && dropped >= 1.8) {
					try {
						helper.assertTrue(dropped <= 2.2, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"pulse", helper.getTick(), caster.getUUID(), "second period costs one more pulse", "<= 2.2", dropped));
						second.set(true);
						zombie.discard();
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == deadline) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"pulse", helper.getTick(), caster.getUUID(),
								first.get() ? "second pulse landed" : "first pulse landed", "damage observed", "dropped=" + dropped));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				}
			});
		}
	}

	/**
	 * S9 — presence spares the owner: parked 2 blocks out for 3+ periods, the owner keeps full
	 * health, still position and still horizontal velocity. Position/velocity is the binding oracle
	 * (mock players refuse health edits, per the S3 note); health documents intent.
	 */
	@GameTest(maxTicks = 120)
	public void elephantPresenceSparesItsOwner(GameTestHelper helper) {
		String fixture = "elephantPresenceSparesItsOwner";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Vec3> parkPos = new AtomicReference<>();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<BlockPos> laidPad = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				Vec3 epos = bodies.get(0).position();
				double sx = epos.x + 2.0;
				double sz = epos.z;
				int floorY = (int) Math.floor(epos.y);
				BlockPos pad = new BlockPos((int) Math.floor(sx), floorY - 1, (int) Math.floor(sz));
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						level.setBlock(pad.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
					}
				}
				laidPad.set(pad);
				caster.teleportTo(level, sx, floorY, sz, Set.of(), 0.0f, 0.0f, false);
				parkPos.set(caster.position());
				healthBefore.set((double) caster.getHealth());
			} catch (RuntimeException | AssertionError failure) {
				clearLaidFloor(level, laidPad);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = ACTIVE_TICK + 35;
		for (long tick = ACTIVE_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Vec3 drift = caster.position().subtract(parkPos.get());
				Vec3 hv = caster.getDeltaMovement();
				if (caster.getHealth() < healthBefore.get() || drift.length() > 0.1 || Math.hypot(hv.x, hv.z) > 0.1) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"spare", helper.getTick(), caster.getUUID(), "owner untouched",
								"still + whole", "drift=" + drift.length() + " health=" + caster.getHealth()));
					} finally {
						clearLaidFloor(level, laidPad);
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (pollTick == deadline) {
					try {
						helper.assertTrue(caster.getHealth() == healthBefore.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
								"spare", helper.getTick(), caster.getUUID(), "owner full health", healthBefore.get(), caster.getHealth()));
						helper.assertTrue(caster.position().distanceTo(parkPos.get()) <= 0.1, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"spare", helper.getTick(), caster.getUUID(), "owner unmoved", "<= 0.1", caster.position().distanceTo(parkPos.get())));
						done.set(true);
					} finally {
						clearLaidFloor(level, laidPad);
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
				}
			});
		}
	}

	/**
	 * S10 — presence spares owned bodies, asserted through the production gate: a live second
	 * summon is impossible (a second tryPrimary recalls), so the scenario pins
	 * {@code isOwnSideOnly} — true for the owner and its elephant body, false for a foreign cow.
	 */
	@GameTest(maxTicks = 80)
	public void elephantPresenceSparesOwnedBodies(GameTestHelper helper) {
		String fixture = "elephantPresenceSparesOwnedBodies";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(5, 1, 6));
		cow.setPersistenceRequired();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK + 1, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"gate", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"gate", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(MegumiShikigamiFriendlyFire.isOwnSideOnly(caster, caster), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"gate", helper.getTick(), ownerId, "owner is own side", "true", "false"));
				helper.assertTrue(MegumiShikigamiFriendlyFire.isOwnSideOnly(caster, bodies.get(0)), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"gate", helper.getTick(), ownerId, "owned body is own side", "true", "false"));
				helper.assertTrue(!MegumiShikigamiFriendlyFire.isOwnSideOnly(caster, cow), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"gate", helper.getTick(), ownerId, "foreign body is not own side", "false", "true"));
				cow.discard();
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * S11 — footprint breaks its allowlist, not the rest: driven through a poppy strip at
	 * foot level, the walking body clears all 5 poppies while both obsidian blocks survive. The
	 * strip sits at y=1 (the foot band) over a dirt support row — the sweep never digs the floor
	 * itself, so the flowers are the breakable cells and obsidian the surviving control. Fails if
	 * the allowlist is empty (poppies stay) or wide open (obsidian breaks).
	 */
	@GameTest(maxTicks = 180)
	public void elephantFootprintBreaksAllowlistOnly(GameTestHelper helper) {
		String fixture = "elephantFootprintBreaksAllowlistOnly";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		AtomicInteger rowRef = new AtomicInteger(2);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK + 1, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"strip", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"strip", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				int row = Math.min(7, Math.max(0, relativeOf(helper, bodies.get(0).position()).getZ()));
				rowRef.set(row);
				for (int x = 0; x <= 4; x++) {
					helper.setBlock(new BlockPos(x, 0, row), Blocks.DIRT);
					helper.setBlock(new BlockPos(x, 1, row), Blocks.POPPY);
				}
				// Obsidian closes the strip on the drive row: inside the sweep band once the body
				// arrives, while the drive target stops just short of it — a solid block reached
				// mid-lane would stall the drive before the poppies are swept.
				helper.setBlock(new BlockPos(5, 1, row), Blocks.OBSIDIAN);
				helper.setBlock(new BlockPos(6, 1, row), Blocks.OBSIDIAN);
				bodies.get(0).teleportTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + row + 0.5);
				caster.teleportTo(level, origin.getX() + 3.5, origin.getY() + 1.0, origin.getZ() + 5.5,
						Set.of(), 0.0f, 0.0f, false);
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = ACTIVE_TICK + 101;
		for (long tick = ACTIVE_TICK + 2; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
				if (live.isEmpty()) {
					return;
				}
				int row = rowRef.get();
				driveElephant(live.get(0), origin, 4.0, 1.0, row + 0.5);
				int dirtAir = 0;
				int obsidianOk = 0;
				for (int x = 0; x <= 6; x++) {
					BlockState state = stateAt(level, origin, x, 1, row);
					if (state.isAir()) {
						dirtAir++;
					}
					if (stateAt(level, origin, x, 1, row).is(Blocks.OBSIDIAN)) {
						obsidianOk++;
					}
				}
				if (dirtAir == 5 && obsidianOk == 2) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
					return;
				}
				if (pollTick == deadline) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"walk", helper.getTick(), caster.getUUID(), "poppies cleared, obsidian kept",
								"5 air + 2 obsidian", dirtAir + " air + " + obsidianOk + " obsidian"));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				}
			});
		}
	}

	/**
	 * S12 — footprint is budgeted and needs movement: a standing body breaks nothing over 3 periods,
	 * then a walking sweep over a 16-dirt pad breaks at least 1 but leaves more than 16-2*budget.
	 * The counted sweep is provably the first while moving (drive starts the tick after a
	 * standing-due anchor), so an unbounded sweep cannot hide in timing slop.
	 */
	@GameTest(maxTicks = 200)
	public void elephantFootprintBudgetedAndNeedsMovement(GameTestHelper helper) {
		String fixture = "elephantFootprintBudgetedAndNeedsMovement";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 6, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		AtomicReference<List<BlockPos>> padCells = new AtomicReference<>(List.of());
		AtomicBoolean moved = new AtomicBoolean();
		AtomicReference<Vec3> lastPos = new AtomicReference<>();
		AtomicInteger colRef = new AtomicInteger(2);
		AtomicBoolean driving = new AtomicBoolean();
		AtomicLong anchorDue = new AtomicLong(-1L);
		AtomicLong sweepTick = new AtomicLong(-1L);
		AtomicBoolean budgetDone = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACTIVE_TICK + 1, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"stand", helper.getTick(), ownerId, "owned bodies", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"stand", helper.getTick(), ownerId, "body ACTIVE", "true", bodies.get(0).combatEnabled()));
				BlockPos relE = relativeOf(helper, bodies.get(0).position());
				List<BlockPos> pad = new ArrayList<>();
				for (int dx = -1; dx <= 0; dx++) {
					for (int dz = -1; dz <= 0; dz++) {
						int cx = Math.min(6, Math.max(0, relE.getX() + dx));
						int cz = Math.min(7, Math.max(0, relE.getZ() + dz));
						helper.setBlock(new BlockPos(cx, 0, cz), Blocks.DIRT);
						helper.setBlock(new BlockPos(cx, 1, cz), Blocks.POPPY);
						pad.add(new BlockPos(cx, 0, cz));
					}
				}
				padCells.set(pad);
				colRef.set(Math.min(5, Math.max(0, relE.getX())));
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		for (long tick = ACTIVE_TICK + 2; tick <= ACTIVE_TICK + 31; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
				if (live.isEmpty()) {
					return;
				}
				if (movedSinceLastPoll(live.get(0), lastPos)) {
					moved.set(true);
				}
				if (pollTick == ACTIVE_TICK + 31) {
					try {
						helper.assertTrue(!moved.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
								"stand", helper.getTick(), caster.getUUID(), "body stood still", "still", "moved"));
						int poppies = 0;
						for (BlockPos cell : padCells.get()) {
							if (stateAt(level, origin, cell.getX(), cell.getY() + 1, cell.getZ()).is(Blocks.POPPY)) {
								poppies++;
							}
						}
						helper.assertTrue(poppies == 4, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"stand", helper.getTick(), caster.getUUID(), "standing breaks nothing", "4 poppies", poppies));
						int col = colRef.get();
						for (int x = col; x <= col + 1; x++) {
							for (int z = 0; z <= 7; z++) {
								helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
								helper.setBlock(new BlockPos(x, 1, z), Blocks.POPPY);
							}
						}
						live.get(0).teleportTo(origin.getX() + col + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
						caster.teleportTo(level, origin.getX() + 3.5, origin.getY() + 1.0, origin.getZ() + 3.5,
								Set.of(), 0.0f, 0.0f, false);
					} catch (RuntimeException | AssertionError failure) {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						throw failure;
					}
				}
			});
		}

		long deadline = ACTIVE_TICK + 130;
		for (long tick = ACTIVE_TICK + 32; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (budgetDone.get()) {
					return;
				}
				List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
				if (live.isEmpty()) {
					return;
				}
				MegumiElephantEntity body = live.get(0);
				long gameTime = level.getGameTime();
				if (!driving.get()) {
					if (anchorDue.get() < 0 && gameTime % MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_PERIOD_TICKS == 0
							&& !movedSinceLastPoll(body, lastPos)) {
						anchorDue.set(gameTime);
					} else if (anchorDue.get() >= 0 && gameTime == anchorDue.get() + 1) {
						driving.set(true);
						driveElephant(body, origin, 7.0, 1.0, 0.5);
					}
					if (pollTick == deadline) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"budget", helper.getTick(), caster.getUUID(), "drive anchored",
									"anchor set", "anchor=" + anchorDue.get()));
						} finally {
							MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						}
					}
					return;
				}
				driveElephant(body, origin, 7.0, 1.0, 0.5);
				if (sweepTick.get() < 0 && gameTime == anchorDue.get() + 9) {
					if (!movedSinceLastPoll(body, lastPos)) {
						driving.set(false);
						anchorDue.set(-1L);
					} else {
						sweepTick.set(anchorDue.get() + 10);
					}
				}
				if (sweepTick.get() >= 0 && gameTime >= sweepTick.get() + 2) {
					int air = 0;
					int col = colRef.get();
					for (int x = col; x <= col + 1; x++) {
						for (int z = 0; z <= 7; z++) {
							if (stateAt(level, origin, x, 1, z).isAir()) {
								air++;
							}
						}
					}
					try {
						helper.assertTrue(air >= 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"budget", helper.getTick(), caster.getUUID(), "walking sweep breaks something", ">= 1 air", air));
						helper.assertTrue(BUDGET_PAD_CELLS - air
								> BUDGET_PAD_CELLS - 2 * MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_BUDGET,
								MegumiShikigamiTestFixtures.diagnostic(fixture,
										"budget", helper.getTick(), caster.getUUID(), "one sweep is budgeted", "> 8 remain", BUDGET_PAD_CELLS - air));
						budgetDone.set(true);
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == deadline) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"budget", helper.getTick(), caster.getUUID(), "budgeted sweep observed",
								"air counted", "anchor=" + anchorDue.get() + " sweep=" + sweepTick.get()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				}
			});
		}
	}
	/**
	 * R17 — out of combat the body stays on its owner: parked past the follow-start radius with
	 * the owner standing still and no sic ever issued, the body walks back inside the follow-stop
	 * radius by the deadline and never drifts farther than parked + slack on any intermediate
	 * tick. Distance is measured live body-to-owner every tick — never the follow-range
	 * attribute. Red-proof: delete the FollowOwnerGoal and the reunion assert fires (the body
	 * stands where it was parked); invert the per-tick bound and it fires on the walk back.
	 */
	@GameTest(maxTicks = 200, structure = "jujutsumod:large_empty")
	public void elephantStaysWithinLeashOfStandingOwner(GameTestHelper helper) {
		String fixture = "elephantStaysWithinLeashOfStandingOwner";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 15, 0, 15);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Vec3> ownerPark = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		final long reunionDeadline = ACTIVE_TICK + 130;

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		// No sic on purpose: the out-of-combat leash, never a jet mark.
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "park", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "park", helper.getTick(), ownerId,
								"body ACTIVE before parking", "true", bodies.get(0).combatEnabled()));
				// This scenario uses the 16x16 empty template, so its barrier wall is at rel 16:
				// the real >8-block horizontal leash can run without crossing the default arena.
				BlockPos ownerCell = helper.absolutePos(new BlockPos(2, 1, 2));
				BlockPos elephantCell = helper.absolutePos(new BlockPos(12, 1, 12));
				caster.teleportTo(level, ownerCell.getX() + 0.5, ownerCell.getY(),
						ownerCell.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
				bodies.get(0).teleportTo(elephantCell.getX() + 0.5, elephantCell.getY(),
						elephantCell.getZ() + 0.5);
				// Teleport does not clear the path/momentum chosen at the summon point. Remove
				// both so this oracle observes only the fresh FollowOwnerGoal decision.
				bodies.get(0).getNavigation().stop();
				bodies.get(0).setDeltaMovement(Vec3.ZERO);
				ownerPark.set(caster.position());
				double parked = bodies.get(0).position().distanceTo(caster.position());
				helper.assertTrue(parked > MegumiShikigamiProfile.ELEPHANT_FOLLOW_START_DISTANCE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "park", helper.getTick(), ownerId,
								"parked outside the follow-start radius",
								"> " + MegumiShikigamiProfile.ELEPHANT_FOLLOW_START_DISTANCE, parked));
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});


		for (long tick = ACTIVE_TICK + 1; tick <= reunionDeadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
				try {
					helper.assertTrue(live.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "leash", pollTick, caster.getUUID(),
									"owned elephant bodies in level", "1", live.size()));
					double distance = live.get(0).position().distanceTo(caster.position());
					if (pollTick == reunionDeadline) {
						helper.assertTrue(caster.position().distanceTo(ownerPark.get()) <= 0.1,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "leash", pollTick,
										caster.getUUID(), "owner stood still",
										"<= 0.1", caster.position().distanceTo(ownerPark.get())));
						MegumiElephantEntity body = live.get(0);
						// The vanilla goal measures centre-to-centre and its ground
						// navigation finishes the path a touch past the stop radius on a
						// diagonal (3.0 straight = 4.24 diagonal). A 2-block-wide elephant
						// standing at 4.24 centres is 3.24 edge-to-edge — genuinely at its
						// owner's side — so the oracle admits the body's half-width.
						double leashMargin = MegumiShikigamiProfile.ELEPHANT_FOLLOW_STOP_DISTANCE
								+ live.get(0).getBbWidth() * 0.5;
						helper.assertTrue(distance <= leashMargin,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "leash", pollTick,
										caster.getUUID(), "body reunited inside the follow-stop radius",
										"<= " + leashMargin,
										"distance=" + distance + " target=" + body.getTarget()
												+ " navDone=" + body.getNavigation().isDone()
												+ " velocity=" + body.getDeltaMovement()));
						done.set(true);
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/** Structure-relative cell of a world position: the run's random offset, subtracted back out. */
	private static BlockPos relativeOf(GameTestHelper helper, Vec3 worldPos) {
		return BlockPos.containing(worldPos).subtract(helper.absolutePos(BlockPos.ZERO));
	}

	/**
	 * Whether the body actually moved since the previous poll — deliberately not the velocity field.
	 * The brain samples the body before its own travel for the tick, so {@code getDeltaMovement()}
	 * there is a leftover fraction of the real step (production reads a sampled step for exactly this
	 * reason); a test that trusted it would never see the elephant walking. Polls run once per tick,
	 * so one tick of movement is compared against the profile's per-tick threshold.
	 */
	private static boolean movedSinceLastPoll(LivingEntity body, AtomicReference<Vec3> lastPos) {
		Vec3 previous = lastPos.getAndSet(body.position());
		return previous != null
				&& previous.distanceTo(body.position()) > MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_MIN_SPEED;
	}

	/** World block read at a structure-relative cell. */
	private static BlockState stateAt(ServerLevel level, BlockPos origin, int x, int y, int z) {
		return level.getBlockState(origin.offset(x, y, z));
	}

	/** Test-loop drive (re-issued every tick): navigation, never AI luck. */
	private static void driveElephant(MegumiElephantEntity body, BlockPos origin, double x, double y, double z) {
		body.getNavigation().moveTo(origin.getX() + x, origin.getY() + y, origin.getZ() + z, 1.0);
	}

	/** Cells in the S12 budget pad: 2 columns by the full 8-row template length. */
	private static final int BUDGET_PAD_CELLS = 16;

	private void runJetScenario(GameTestHelper helper, String fixture, boolean ownerInCorridor) {
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 6);
		paveFloor(helper, 0, 6, 0, 7);
		// One opaque block above the zombie's head: kills sky-burn flakiness. The near-horizontal
		// jet (trunk ~+1.9 descending to the zombie's chest ~+1.0) passes well below it, as do all
		// sight lines.
		helper.setBlock(new BlockPos(5, 4, 6), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<BlockPos> laidFloor = new AtomicReference<>();
		AtomicBoolean jetted = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));
				if (ownerInCorridor) {
					// Stand the owner inside the coming corridor: 3 blocks down-jet from the body,
					// 0.7 blocks to the side. The owner's chest rides ~1 below the trunk, so the true
					// 3D lateral reads ~1.2 against the 1.4 half-width — inside with margin, while a
					// missing friendly-fire gate still hoses them. Everything here is world
					// coordinates straight off live entities: structure-relative math would silently
					// misplace the owner whenever the test structure is rotated, so the floor is
					// laid with ServerLevel.setBlock (absolute) and the feet level is read off the
					// live body. The brain plants the body for the jet's duration, so this geometry
					// holds from sic tick to impact with nothing moving but the water.
					Vec3 flat = zombie.position().subtract(bodies.get(0).position());
					flat = new Vec3(flat.x, 0.0, flat.z).normalize();
					Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
					Vec3 bodyFeet = bodies.get(0).position();
					Vec3 spot = bodyFeet.add(flat.scale(3.0)).add(side.scale(0.7));
					BlockPos floor = new BlockPos((int) Math.floor(spot.x),
							(int) Math.floor(bodyFeet.y) - 1, (int) Math.floor(spot.z));
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
						}
					}
					caster.teleportTo(level, spot.x, bodyFeet.y, spot.z, Set.of(), 0.0f, 0.0f, false);
					laidFloor.set(floor);
				}

				double before = zombie.getHealth();
				helper.assertTrue(before == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"zombie at full health", zombie.getMaxHealth(), before));
				healthBefore.set(before);

				// Same aim math as the Nue scenarios: eye-to-chest ray, corridor is open air.
				TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = SIC_TICK + JET_WINDOW_TICKS;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (jetted.get() || zombie.isRemoved()) {
					return;
				}
				boolean soaked = zombie.hasEffect(JujutsuEffects.MEGUMI_SOAKED);
				double dropped = healthBefore.get() == null ? 0.0 : healthBefore.get() - zombie.getHealth();
				if (!soaked || dropped < EXPECTED_JET_DROP_LOW) {
					if (pollTick == deadline) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"jet", helper.getTick(), caster.getUUID(), "jet soaked and damaged within window",
									"soaked + drop in [" + EXPECTED_JET_DROP_LOW + ", " + EXPECTED_JET_DROP_HIGH + "]",
									"soaked=" + soaked + " dropped=" + dropped));
						} finally {
							zombie.discard();
							clearLaidFloor(level, laidFloor);
						}
					}
					return;
				}
			try {
				helper.assertTrue(dropped <= EXPECTED_JET_DROP_HIGH,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
								caster.getUUID(), "first soaked tick carries one pulse",
								"drop <= " + EXPECTED_JET_DROP_HIGH, dropped));
				if (ownerInCorridor) {
					List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
					helper.assertTrue(live.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owned elephant bodies in level", "1", live.size()));
					Vec3 look = live.get(0).getLookAngle();
					Vec3 trunk = live.get(0).getEyePosition()
							.add(look.scale(MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD));
					Vec3 chest = caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0);
					helper.assertTrue(MegumiElephantPolicy.inJetCorridor(chest, trunk, look.normalize(),
							MegumiShikigamiProfile.ELEPHANT_JET_LENGTH,
							MegumiShikigamiProfile.ELEPHANT_JET_HALF_WIDTH),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner inside the live corridor", "inside", "outside"));
					helper.assertTrue(!caster.hasEffect(JujutsuEffects.MEGUMI_SOAKED),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner carries no soak", "absent", "present"));
					helper.assertTrue(caster.getHealth() == caster.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner takes no damage",
									caster.getMaxHealth(), caster.getHealth()));
				}
					jetted.set(true);
					zombie.discard();
				} finally {
					clearLaidFloor(level, laidFloor);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	/**
	 * Every live elephant body owned by {@code ownerId} in {@code level}. Owner-filtered, never
	 * bounds-filtered: the world offset is random per run, so a structure bounds scan could miss a
	 * live body. Fresh mock UUIDs per test make the owner filter exact.
	 */
	private static List<MegumiElephantEntity> elephantsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiElephantEntity> owned = new ArrayList<>();
		for (MegumiElephantEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiElephantEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/**
	 * Removes the S3 owner's world-placed footing: absolute blocks outside the structure bounds
	 * are not cleared by the test teardown and would leak into the shared world.
	 */
	private static void clearLaidFloor(ServerLevel level, AtomicReference<BlockPos> laidFloor) {
		BlockPos floor = laidFloor.getAndSet(null);
		if (floor == null) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(floor.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}
	/** Stone floor patch so the ground-placement scan finds footing under every candidate. */
	private static void paveFloor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
