package jujutsu.mod.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy.Phase;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.TeardownReason;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;

/**
 * Piercing Ox server scenarios — the roster-common skeleton (summon, materialize, recall,
 * death, no-room, cooldown rejection, sic, owner death, dimension teardown, coexistence,
 * autonomous pick, retaliation, friendly-fire) plus the charge signature matrix:
 * distance-scaled impact, frozen-line no-steer, wall abort, hit-once, allied spares, the
 * leash commit refusal and the needs-an-order oracle.
 *
 * <p>Traps respected: owner-UUID body scans (the world offset is random per run), literal
 * cooldown and impact-band pins, {@link MegumiShikigamiTestFixtures#runGuarded} on
 * intermediate steps, {@code cleanupCaster} on every terminal path, poll-until-deadline
 * cadences, and victims parked NoAI so nothing wanders off the measured line.
 */
public final class MegumiOxGameTests {

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int DOGS_TICK = 4;
	private static final int OX_TICK = 6;
	private static final int VICTIM_TICK = 8;
	private static final int STAGE_TICK = 26;   // OX_MATERIALIZE_TICKS is 20 — ACTIVE by ~23
	private static final int SIC_TICK = 30;

	/** Pins the literal rows, NOT {@code MegumiShikigamiProfile.OX_RECALL_COOLDOWN_TICKS}. */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 520;

	/** Staged charge origin (west end of the +X corridor) and the mark anchors. */
	private static final BlockPos OX_SPOT = new BlockPos(1, 1, 1);
	private static final BlockPos VICTIM_SHORT = new BlockPos(4, 1, 1);   // ~3 block corridor
	// rel 6, not 7: the wall-clamped corridor reach from the ox spot is ~5.5, so a victim
	// parked at 7 is unreachable and the commit gate honestly refuses — the tests that need
	// a charge to run stage their mark inside reach and let the charge die on the wall anyway.
	private static final BlockPos VICTIM_FAR = new BlockPos(6, 1, 1);     // ~5 block corridor
	private static final BlockPos VICTIM_DIAG = new BlockPos(6, 1, 6);    // ~7 block diagonal
	private static final BlockPos WALL_OX_SPOT = new BlockPos(1, 1, 4);   // centre lane: drift room on both sides
	private static final BlockPos WALL_VICTIM = new BlockPos(6, 1, 4);
	private static final BlockPos OWNER_OUTSIDE = new BlockPos(-16, 1, 1); // teleports ignore walls

	/**
	 * Impact band literals for a zero-armour cow. Damage = clamp(2.0 + 0.35·realTravel, 2, 12);
	 * the swept box meets the victim ~1.3 blocks early (ox half-width 0.75 + inflate 0.3 + cow
	 * half-width ~0.45), so a 3-block corridor lands ≈ 2.0+0.35·1.7 ≈ 2.6 and the ~7-block
	 * diagonal ≈ 2.0+0.35·5.8 ≈ 4.0.
	 */
	private static final double SHORT_IMPACT_MIN = 2.2;
	private static final double SHORT_IMPACT_MAX = 3.0;
	private static final double LONG_IMPACT_MIN = 3.4;
	private static final double LONG_IMPACT_MAX = 4.8;

	// ----------------------------------------------------------------------------------------------
	// Common skeleton
	// ----------------------------------------------------------------------------------------------

	/** S1 — selecting OX and pressing the technique key summons exactly one live body. */
	@GameTest(maxTicks = 60)
	public void oxSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "oxSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.OX);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.OX.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.OX.id(), view.get().type()));

				List<MegumiOxEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiOxEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned ox bodies", "1", bodies.size()));
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

	/** S2 — the body emerges MATERIALIZING (combat off) and only then lands ACTIVE. */
	@GameTest(maxTicks = 80)
	public void oxSummonMaterializesThenActivates(GameTestHelper helper) {
		String fixture = "oxSummonMaterializesThenActivates";
		paveFloor(helper, 0, 5, 0, 5);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		// Mid-materialization: the body is present but the combat gate is still shut.
		helper.runAtTickTime(10, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "emerge", level, caster.getUUID());
			helper.assertTrue(!ox.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"emerge", helper.getTick(), caster.getUUID(), "combatEnabled mid-materialize",
					"false", ox.combatEnabled()));
			helper.assertTrue(ox.phase() == Phase.MATERIALIZING, MegumiShikigamiTestFixtures.diagnostic(
					fixture, "emerge", helper.getTick(), caster.getUUID(), "presentation phase",
					"MATERIALIZING", String.valueOf(ox.phase())));
		}));

		for (long tick = 20; tick <= 45; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (bodies.isEmpty()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				if (ox.phase() != Phase.ACTIVE) {
					return;
				}
				try {
					helper.assertTrue(ox.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"activate", helper.getTick(), caster.getUUID(), "combatEnabled at ACTIVE",
							"true", ox.combatEnabled()));
				} finally {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(46, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "activate",
						helper.getTick(), caster.getUUID(), "ox reached ACTIVE", "before tick 46",
						"never"))));
	}

	/** S3 — a manual recall removes the body and costs exactly the ox recall cooldown. */
	@GameTest(maxTicks = 80)
	public void oxManualRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "oxManualRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "second tryPrimary result", "true", recalled));

				// Same-tick read: the cooldown was just armed, so the remaining time is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.OX, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"ox recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
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

	/** S4 — a re-summon inside the recall window is refused and leaves the deadline untouched. */
	@GameTest(maxTicks = 80)
	public void oxCooldownRejectsResummonAndKeepsCooldownFree(GameTestHelper helper) {
		String fixture = "oxCooldownRejectsResummonAndKeepsCooldownFree";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(RECALL_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"recall", helper.getTick(), ownerId, "recall result", "true", recalled));
		}));

		helper.runAtTickTime(8, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean resummoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!resummoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"reject", helper.getTick(), ownerId, "re-summon on cooldown", "refused", "accepted"));
				// The recalled body still sinks out visually — the honest record is the pack row.
				helper.assertTrue(!MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.OX),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "reject", helper.getTick(), ownerId,
								"ox pack record after refusal", "dropped", "kept"));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.OX, level.getGameTime());
				helper.assertTrue(remaining > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"reject", helper.getTick(), ownerId, "ox cooldown still armed", ">0", remaining));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "reject", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/** S5 — every candidate spot blocked: the summon refuses and the cooldown stays free. */
	@GameTest(maxTicks = 60)
	public void oxNoRoomSummonRefusesAndKeepsCooldownFree(GameTestHelper helper) {
		String fixture = "oxNoRoomSummonRefusesAndKeepsCooldownFree";
		// Seal the whole candidate ring (right±1.5, forward 1.8, own feet) inside stone — the
		// vertical scan only reaches ±3, so a 5-deep fill denies every placement.
		paveFloor(helper, 0, 5, 0, 5);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 5; z++) {
				for (int y = 1; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.OX);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"noroom", helper.getTick(), ownerId, "tryPrimary in a sealed cell",
						"refused", "accepted"));
				helper.assertTrue(oxenOwnedBy(level, ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "noroom", helper.getTick(), ownerId,
								"ox bodies after refusal", "0", oxenOwnedBy(level, ownerId).size()));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.OX, level.getGameTime());
				helper.assertTrue(remaining == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"noroom", helper.getTick(), ownerId, "ox cooldown after refusal", "0", remaining));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "noroom", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** S6 — the dying body reconciles through DEATH: the pack drops and the death cooldown is literal 520. */
	@GameTest(maxTicks = 90)
	public void oxDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "oxDeathChargesDeathCooldownAndClearsPack";
		paveFloor(helper, 0, 5, 0, 5);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(STAGE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiOxEntity ox = onlyOx(helper, fixture, "kill", level, ownerId);
				helper.assertTrue(ox.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true",
						ox.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = ox.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"summon death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(45, () -> helper.succeed());
	}

	/** S7 — the sic key marks the aimed target: the ox takes the order as its mark. */
	@GameTest(maxTicks = 90)
	public void oxSicCommandMarksTheTarget(GameTestHelper helper) {
		String fixture = "oxSicCommandMarksTheTarget";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 5)))));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			Zombie zombie = zombieRef.get();
			helper.assertTrue(zombie != null && zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(
					fixture, "sic", helper.getTick(), caster.getUUID(), "zombie alive", "true", "gone"));
			TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), caster.getUUID(), "trySic result", "true", sicced));
		}));

		long deadline = SIC_TICK + 20;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				boolean marked = ox.getTarget() == zombie
						|| zombie.getUUID().equals(ox.chargeTargetUuid());
				if (!marked) {
					return;
				}
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
						caster.getUUID(), "ox marked the sic'd zombie", "within 20 ticks", "never"));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
	}

	/** S8 — the owner dying sweeps the ox and drops the pack record (state-injection proxy). */
	@GameTest(maxTicks = 80)
	public void oxOwnerDeathSweepsThePack(GameTestHelper helper) {
		String fixture = "oxOwnerDeathSweepsThePack";
		paveFloor(helper, 0, 5, 0, 5);
		ServerPlayer caster = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiOxEntity ox = onlyOx(helper, fixture, "death", level, ownerId);
				helper.assertTrue(ox.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"death", helper.getTick(), ownerId, "ox alive before owner death", "true",
						ox.isAlive()));
				// kill() no-ops on mock players — die() fires AFTER_DEATH synchronously.
				caster.die(level.damageSources().genericKill());
				helper.assertTrue(oxenOwnedBy(level, ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "death", helper.getTick(), ownerId,
								"ox bodies after owner death", "0", oxenOwnedBy(level, ownerId).size()));
				helper.assertTrue(!MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.OX),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "death", helper.getTick(), ownerId,
								"ox pack record", "dropped", "kept"));
			} finally {
				cleanupOwner(helper, caster);
			}
		});
		helper.runAtTickTime(50, () -> helper.succeed());
	}

	/** S9 — the dimension-change teardown sweeps the ox and arms the recall cooldown. */
	@GameTest(maxTicks = 80)
	public void oxDimensionTeardownArmsRecallCooldown(GameTestHelper helper) {
		String fixture = "oxDimensionTeardownArmsRecallCooldown";
		paveFloor(helper, 0, 5, 0, 5);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiOxEntity ox = onlyOx(helper, fixture, "teardown", level, ownerId);
				helper.assertTrue(ox.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"teardown", helper.getTick(), ownerId, "ox alive before teardown", "true",
						ox.isAlive()));
				// A real dimension hop can't be staged on the fixture box — the teardown seam is
				// the representative exit the AFTER_PLAYER_CHANGE_WORLD hook feeds.
				MegumiShikigamiRuntime.teardown(level.getServer(), ownerId, TeardownReason.DIMENSION_CHANGE);
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.OX, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "teardown", helper.getTick(), ownerId,
								"ox recall cooldown after dimension teardown",
								EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
				helper.assertTrue(!MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.OX),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "teardown", helper.getTick(), ownerId,
								"ox pack record", "dropped", "kept"));
				// DIMENSION_CHANGE recalls visually — the body sinks out over OX_RECALL_TICKS.
				helper.assertTrue(ox.isRemoved() || ox.phase() == Phase.RECALLING,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "teardown", helper.getTick(), ownerId,
								"ox body after teardown", "recalling or gone",
								ox.isRemoved() ? "removed" : String.valueOf(ox.phase())));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(50, () -> helper.succeed());
	}

	/** S10 — issue #107 coexistence: the ox and the dogs ride side by side, both packs live. */
	@GameTest(maxTicks = 80)
	public void oxCoexistsWithAnotherPack(GameTestHelper helper) {
		String fixture = "oxCoexistsWithAnotherPack";
		paveFloor(helper, 0, 6, 0, 6);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(DOGS_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DOGS);
			boolean summoned = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), caster.getUUID(), "tryToggle result", "true", summoned));
		}));

		helper.runAtTickTime(OX_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(STAGE_TICK + 10, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.OX),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"ox pack record", "present", "absent"));
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, ownerId);
				helper.assertTrue(dogs.size() >= 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "owned dogs alive", ">=1", dogs.size()));
				List<MegumiOxEntity> oxen = oxenOwnedBy(level, ownerId);
				helper.assertTrue(oxen.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "owned ox bodies", "1", oxen.size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(45, () -> helper.succeed());
	}

	/**
	 * S11 — the coordinator's autonomous pick lands on a hostile inside the autonomy bubble:
	 * no order was ever given, yet the ox takes the mark as its target.
	 */
	@GameTest(maxTicks = 120)
	public void oxAutonomousPickFollowsCoordinatorFlag(GameTestHelper helper) {
		String fixture = "oxAutonomousPickFollowsCoordinatorFlag";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 5)))));

		long deadline = SIC_TICK + 40;
		for (long tick = STAGE_TICK; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty()) {
					return;
				}
				if (bodies.get(0).getTarget() != zombie) {
					return; // The coordinator scans on its own cadence — not picked yet.
				}
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomy", helper.getTick(),
						caster.getUUID(), "coordinator autonomous pick on the ox", "within 40 ticks",
						"never"));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
	}

	/** S12 — the owner-hurt mark drives the pick: the attacker takes the charge. */
	@GameTest(maxTicks = 160)
	public void oxRetaliationMarkEngages(GameTestHelper helper) {
		String fixture = "oxRetaliationMarkEngages";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean damaged = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> attackerRef.set(spawnToughZombie(helper, fixture, new BlockPos(5, 1, 5), 100.0))));

		// The hit lands through the real damage pipeline — a zombie swing, not die().
		helper.runAtTickTime(STAGE_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			Zombie attacker = attackerRef.get();
			helper.assertTrue(attacker != null && attacker.isAlive(), MegumiShikigamiTestFixtures.diagnostic(
					fixture, "hurt", helper.getTick(), caster.getUUID(), "attacker alive", "true", "gone"));
			caster.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
		}));

		long deadline = STAGE_TICK + 90;
		for (long tick = STAGE_TICK + 3; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie attacker = attackerRef.get();
				if (attacker == null || attacker.isRemoved() || damaged.get()) {
					return;
				}
				if (attacker.getHealth() < attacker.getMaxHealth()) {
					damaged.set(true);
					attacker.discard();
					cleanupOwner(helper, caster);
					helper.succeed();
				}
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation", helper.getTick(),
						caster.getUUID(), "attacker damaged by the ox", "before deadline", "never"));
			} finally {
				Zombie attacker = attackerRef.get();
				if (attacker != null) {
					attacker.discard();
				}
				cleanupOwner(helper, caster);
			}
		});
	}

	/** S13 — allied bodies are never marks: the dogs stand at full health and the ox never fires. */
	@GameTest(maxTicks = 130)
	public void oxNeverAttacksAlliedBodies(GameTestHelper helper) {
		String fixture = "oxNeverAttacksAlliedBodies";
		paveFloor(helper, 0, 6, 0, 6);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean oxCharged = new AtomicBoolean();

		helper.runAtTickTime(DOGS_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DOGS);
			boolean summoned = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), caster.getUUID(), "tryToggle result", "true", summoned));
		}));

		helper.runAtTickTime(OX_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		long deadline = STAGE_TICK + 50;
		for (long tick = STAGE_TICK; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (!bodies.isEmpty() && bodies.get(0).charging()) {
					oxCharged.set(true);
				}
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(!oxCharged.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"allies", helper.getTick(), ownerId, "ox charged with only allies around",
						"never", "yes"));
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, ownerId);
				helper.assertTrue(dogs.size() >= 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"allies", helper.getTick(), ownerId, "owned dogs alive", ">=1", dogs.size()));
				for (MegumiDivineDogEntity dog : dogs) {
					helper.assertTrue(dog.getHealth() >= dog.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "allies", helper.getTick(),
									ownerId, "dog health after ox window", "full",
									String.valueOf(dog.getHealth())));
				}
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	// ----------------------------------------------------------------------------------------------
	// Charge signature matrix
	// ----------------------------------------------------------------------------------------------

	/**
	 * OX-1 — the impact scales with REAL distance travelled. Two committed runs on a zero-armour
	 * cow: the short ~3-block corridor lands inside {@link #SHORT_IMPACT_MIN}..{@link #SHORT_IMPACT_MAX}
	 * and the ~7-block diagonal inside {@link #LONG_IMPACT_MIN}..{@link #LONG_IMPACT_MAX} — the
	 * bands are disjoint, so the multiplier is what separates them, not luck.
	 */
	@GameTest(maxTicks = 460)
	public void oxChargeDamageScalesWithRealDistance(GameTestHelper helper) {
		String fixture = "oxChargeDamageScalesWithRealDistance";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Cow> cowRef = new AtomicReference<>();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<Double> shortDrop = new AtomicReference<>();
		AtomicReference<Double> longDrop = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, VICTIM_SHORT);
			cow.setPersistenceRequired();
			cow.setNoAi(true);
			CursedSpiritTestFixtures.freezeGround(cow);
			cowRef.set(cow);
		}));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, caster.getUUID());
			teleportTo(helper, ox, OX_SPOT);
			Cow cow = cowRef.get();
			helper.assertTrue(cow != null && cow.isAlive(), MegumiShikigamiTestFixtures.diagnostic(
					fixture, "stage", helper.getTick(), caster.getUUID(), "cow alive", "true", "gone"));
			healthBefore.set((double) cow.getHealth());
			sicAt(helper, fixture, caster, cow);
		}));

		// Run 1 — the short corridor. The mark persists across recovery, so the moment the
		// first drop lands the whole stage moves: ox back to the west end and the cow out on
		// the diagonal BEFORE the 240-tick cooldown can let the ox commit on its own.
		AtomicBoolean restaged = new AtomicBoolean();
		for (long tick = STAGE_TICK + 1; tick <= STAGE_TICK + 90; tick++) {
			helper.runAtTickTime(tick, () -> {
				Cow cow = cowRef.get();
				if (cow == null || cow.isRemoved() || shortDrop.get() != null) {
					return;
				}
				double before = healthBefore.get();
				if (cow.getHealth() < before) {
					shortDrop.set(before - cow.getHealth());
				}
			});
		}
		for (long tick = STAGE_TICK + 1; tick <= STAGE_TICK + 280; tick++) {
			helper.runAtTickTime(tick, () -> {
				Cow cow = cowRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (cow == null || cow.isRemoved() || restaged.get() || shortDrop.get() == null
						|| bodies.isEmpty() || bodies.get(0).charging()) {
					// Restage only once the first charge fully settled — a mid-flight teleport
					// would just send the frozen line into the wall and burn another cooldown.
					return;
				}
				try {
					MegumiOxEntity ox = bodies.get(0);
					teleportTo(helper, ox, OX_SPOT);
					teleportTo(helper, cow, VICTIM_DIAG);
					healthBefore.set((double) cow.getHealth());
					restaged.set(true);
				} catch (RuntimeException | AssertionError failure) {
					cow.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}

		// The still-standing mark re-commits as soon as the cooldown expires — the long run
		// drives itself. Poll the second drop the same way.
		for (long tick = STAGE_TICK + 91; tick <= STAGE_TICK + 400; tick++) {
			helper.runAtTickTime(tick, () -> {
				Cow cow = cowRef.get();
				if (cow == null || cow.isRemoved() || longDrop.get() != null
						|| !restaged.get()) {
					return;
				}
				double before = healthBefore.get();
				if (cow.getHealth() < before) {
					longDrop.set(before - cow.getHealth());
				}
			});
		}

		helper.runAtTickTime(STAGE_TICK + 402, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(shortDrop.get() != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"charge", helper.getTick(), ownerId, "short corridor hit landed", "yes", "never"));
				helper.assertTrue(longDrop.get() != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"charge", helper.getTick(), ownerId, "long corridor hit landed", "yes", "never"));
				double shortHit = shortDrop.get();
				double longHit = longDrop.get();
				helper.assertTrue(shortHit >= SHORT_IMPACT_MIN && shortHit <= SHORT_IMPACT_MAX,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "charge", helper.getTick(), ownerId,
								"short-corridor damage band", "[" + SHORT_IMPACT_MIN + "," + SHORT_IMPACT_MAX + "]",
								shortHit));
				helper.assertTrue(longHit >= LONG_IMPACT_MIN && longHit <= LONG_IMPACT_MAX,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "charge", helper.getTick(), ownerId,
								"long-corridor damage band", "[" + LONG_IMPACT_MIN + "," + LONG_IMPACT_MAX + "]",
								longHit));
				helper.assertTrue(longHit > shortHit, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"charge", helper.getTick(), ownerId, "damage scales with real distance",
						"long > short", longHit + " vs " + shortHit));
			} finally {
				Cow cow = cowRef.get();
				if (cow != null) {
					cow.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/**
	 * OX-2 — no homing: the victim sidesteps mid-charge and the ox still runs the frozen line.
	 * The direction is locked at windup start, so the teleport lands on an already-bought line.
	 */
	@GameTest(maxTicks = 200)
	public void oxChargeDoesNotSteer(GameTestHelper helper) {
		String fixture = "oxChargeDoesNotSteer";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicReference<Double> oxStartX = new AtomicReference<>();
		AtomicBoolean sidestepped = new AtomicBoolean();
		AtomicBoolean resolved = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnFrozenZombie(helper, fixture, VICTIM_FAR))));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, caster.getUUID());
			teleportTo(helper, ox, OX_SPOT);
			oxStartX.set(ox.getX());
			Zombie zombie = zombieRef.get();
			sicAt(helper, fixture, caster, zombie);
		}));

		long deadline = STAGE_TICK + 130;
		for (long tick = STAGE_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty() || resolved.get()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				// Mid-CHARGE (the action index the client rides) — the victim steps aside now.
				if (!sidestepped.get() && ox.presentationAction() == MegumiOxEntity.ACTION_CHARGE) {
					zombie.teleportTo(zombie.getX(), zombie.getY(), zombie.getZ() + 5.0);
					sidestepped.set(true);
					return;
				}
				if (!sidestepped.get() || ox.charging()) {
					return;
				}
				// The charge resolved: the sidestepped target is untouched and the ox still ran east.
				try {
					resolved.set(true);
					UUID ownerId = caster.getUUID();
					helper.assertTrue(zombie.getHealth() >= zombie.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "steer", helper.getTick(),
									ownerId, "sidestepped victim health", "full",
									String.valueOf(zombie.getHealth())));
					helper.assertTrue(ox.getX() - oxStartX.get() >= 3.0,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "steer", helper.getTick(),
									ownerId, "ox travel along the frozen line", ">=3.0 blocks",
									String.valueOf(ox.getX() - oxStartX.get())));
				} finally {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.assertTrue(sidestepped.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"steer", helper.getTick(), caster.getUUID(), "charge ever committed", "yes",
						"never"));
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "steer", helper.getTick(),
						caster.getUUID(), "charge resolved after the sidestep", "before deadline", "never"));
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
	 * OX-3 — the charge dies on the barrier wall. The mark is on the corridor to buy the
	 * windup, then sidesteps mid-flight: the frozen line carries the ox past the empty mark
	 * spot and the charge aborts on the wall face without ever crossing the arena boundary.
	 * The lane runs through the arena's centre: the lock line is measured from the ox's real
	 * position at windup, and a centre lane gives any drift enough room to still reach the
	 * east wall before a side wall.
	 */
	@GameTest(maxTicks = 200)
	public void oxStopsAtWalls(GameTestHelper helper) {
		String fixture = "oxStopsAtWalls";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicReference<Double> oxStartX = new AtomicReference<>();
		AtomicBoolean sidestepped = new AtomicBoolean();
		AtomicBoolean resolved = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnToughZombie(helper, fixture, WALL_VICTIM, 200.0))));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, caster.getUUID());
			teleportTo(helper, ox, WALL_OX_SPOT);
			oxStartX.set(ox.getX());
			sicAt(helper, fixture, caster, zombieRef.get());
		}));

		long deadline = STAGE_TICK + 130;
		for (long tick = STAGE_TICK + 1; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty() || resolved.get()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				// Once the line is frozen and the ox is running, the mark steps aside — the
				// corridor ahead is empty and the wall face is the only thing that stops it.
				if (!sidestepped.get() && ox.presentationAction() == MegumiOxEntity.ACTION_CHARGE) {
					zombie.teleportTo(zombie.getX(), zombie.getY(), zombie.getZ() - 4.0);
					sidestepped.set(true);
					return;
				}
				if (!sidestepped.get() || ox.charging()) {
					return;
				}
				try {
					resolved.set(true);
					UUID ownerId = caster.getUUID();
					helper.assertTrue(zombie.getHealth() >= zombie.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "wall", helper.getTick(), ownerId,
									"sidestepped victim health", "full",
									String.valueOf(zombie.getHealth())));
					double travelled = ox.getX() - oxStartX.get();
					helper.assertTrue(travelled >= 5.0, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"wall", helper.getTick(), ownerId, "ox travel down the corridor",
							">=5.0 blocks", String.valueOf(travelled)));
					double wallX = helper.absolutePos(new BlockPos(8, 1, 1)).getX();
					helper.assertTrue(ox.getX() < wallX, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"wall", helper.getTick(), ownerId, "ox stayed inside the barrier",
							"west of wall", String.valueOf(ox.getX() - wallX)));
				} finally {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.assertTrue(sidestepped.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"wall", helper.getTick(), caster.getUUID(), "charge ever committed", "yes",
						"never"));
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "wall", helper.getTick(),
						caster.getUUID(), "charge resolved into the wall", "before deadline", "never"));
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
	 * OX-4 — pass-through still lands once: the 400hp victim reads exactly one hit's worth of
	 * damage even though the charge keeps running past it into the wall.
	 */
	@GameTest(maxTicks = 200)
	public void oxHitOncePerEntityPerCharge(GameTestHelper helper) {
		String fixture = "oxHitOncePerEntityPerCharge";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicBoolean resolved = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			Zombie zombie = spawnToughZombie(helper, fixture, VICTIM_FAR, 400.0);
			zombieRef.set(zombie);
			healthBefore.set((double) zombie.getHealth());
		}));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, caster.getUUID());
			teleportTo(helper, ox, OX_SPOT);
			sicAt(helper, fixture, caster, zombieRef.get());
		}));

		long deadline = STAGE_TICK + 130;
		for (long tick = STAGE_TICK + 1; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty() || resolved.get()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				// Settle only after the hit landed AND the charge ended (no second pass possible).
				if (ox.charging() || zombie.getHealth() >= healthBefore.get()) {
					return;
				}
				try {
					resolved.set(true);
					double drop = healthBefore.get() - zombie.getHealth();
					// One hit on a 2-armour zombie at ~6 travel: ~4.1·0.92 ≈ 3.8. Two hits ≈ 7.5+.
					helper.assertTrue(drop >= 3.0 && drop <= 4.6,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "once", helper.getTick(),
									caster.getUUID(), "total damage (exactly one hit)", "[3.0,4.6]",
									String.valueOf(drop)));
				} finally {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "once", helper.getTick(),
						caster.getUUID(), "charge hit and settled", "before deadline", "never"));
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
	 * OX-5 — the corridor runs THROUGH an own dog parked on the line: the friendly filter drops
	 * it before damage, so the ally takes zero and the real mark still eats the hit.
	 */
	@GameTest(maxTicks = 200)
	public void oxSparesAlliedSummons(GameTestHelper helper) {
		String fixture = "oxSparesAlliedSummons";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicReference<Double> dogHealthBefore = new AtomicReference<>();
		AtomicBoolean resolved = new AtomicBoolean();

		helper.runAtTickTime(DOGS_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DOGS);
			boolean summoned = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), caster.getUUID(), "tryToggle result", "true", summoned));
		}));

		helper.runAtTickTime(OX_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnToughZombie(helper, fixture, VICTIM_FAR, 200.0))));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, ownerId);
			teleportTo(helper, ox, OX_SPOT);
			List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, ownerId);
			helper.assertTrue(dogs.size() >= 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"stage", helper.getTick(), ownerId, "own dogs alive", ">=1", dogs.size()));
			for (MegumiDivineDogEntity dog : dogs) {
				dog.setNoAi(true); // Freeze the pack — a wanderer drifting off the line weakens the read.
			}
			MegumiDivineDogEntity blocker = dogs.get(0);
			teleportTo(helper, blocker, new BlockPos(4, 1, 1));
			dogHealthBefore.set((double) blocker.getHealth());
			sicAt(helper, fixture, caster, zombieRef.get());
		}));

		long deadline = STAGE_TICK + 130;
		for (long tick = STAGE_TICK + 1; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (zombie == null || zombie.isRemoved() || bodies.isEmpty() || resolved.get()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				if (ox.charging() || zombie.getHealth() >= zombie.getMaxHealth()) {
					return; // Wait for the hit and the charge's end.
				}
				try {
					resolved.set(true);
					UUID ownerId = caster.getUUID();
					List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, ownerId);
					helper.assertTrue(!dogs.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"allies", helper.getTick(), ownerId, "dog survived the sweep", "alive", "gone"));
					double dogDrop = dogHealthBefore.get() - dogs.get(0).getHealth();
					helper.assertTrue(dogDrop <= 0.0, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"allies", helper.getTick(), ownerId, "own dog damage through the corridor",
							"0", String.valueOf(dogDrop)));
					helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "allies", helper.getTick(),
									ownerId, "real mark still hit", "yes",
									String.valueOf(zombie.getHealth())));
				} finally {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "allies", helper.getTick(),
						caster.getUUID(), "charge resolved through the ally", "before deadline", "never"));
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
	 * OX-6 — the leash gate: with the owner teleported past the wall-clamped projected stop
	 * (~rel 7 vs owner at rel -16 → ~22 > RETURN_RADIUS 20), every commit is refused and the
	 * charge never starts, even though the mark stands. The mark is spawned INTO the sic
	 * callback — parked early it would earn the coordinator's autonomous pick and commit the
	 * moment the ox goes ACTIVE, before the owner ever leaves.
	 */
	@GameTest(maxTicks = 160)
	public void oxRefusesCommitBeyondLeash(GameTestHelper helper) {
		String fixture = "oxRefusesCommitBeyondLeash";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean oxCharged = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(STAGE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiOxEntity ox = onlyOx(helper, fixture, "stage", level, caster.getUUID());
			teleportTo(helper, ox, OX_SPOT);
			// Mark first, then pull the owner out in the SAME tick — the body's next commit read
			// already sees the far owner, and the ox-to-owner gap (17.5) stays inside leash (25)
			// so the mark itself survives.
			Zombie zombie = spawnFrozenZombie(helper, fixture, VICTIM_FAR);
			zombieRef.set(zombie);
			sicAt(helper, fixture, caster, zombie);
			Vec3 outside = Vec3.atBottomCenterOf(helper.absolutePos(OWNER_OUTSIDE));
			caster.teleportTo(outside.x, outside.y, outside.z);
		}));

		long deadline = STAGE_TICK + 80;
		for (long tick = STAGE_TICK + 1; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (!bodies.isEmpty() && bodies.get(0).charging()) {
					oxCharged.set(true);
				}
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(!oxCharged.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"leash", helper.getTick(), ownerId, "charge committed beyond leash",
						"never", "yes"));
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1 && bodies.get(0).chargeTargetUuid() == null,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "leash", helper.getTick(), ownerId,
								"charge target armed while refused", "null",
								bodies.isEmpty() ? "no body" : String.valueOf(bodies.get(0).chargeTargetUuid())));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/**
	 * OX-7 — the honest no-order oracle: CHARGE must never run while {@code chargeTargetUuid}
	 * is null. A zombie in the bubble earns the autonomous mark mid-window — a legal charge
	 * still carries the armed uuid, so only a true self-start fails.
	 */
	@GameTest(maxTicks = 160)
	public void oxChargeNeedsAnOrder(GameTestHelper helper) {
		String fixture = "oxChargeNeedsAnOrder";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean violation = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 5)))));

		long deadline = STAGE_TICK + 80;
		for (long tick = STAGE_TICK; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (bodies.isEmpty()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				if (ox.charging() && ox.chargeTargetUuid() == null) {
					violation.set(true);
				}
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.assertTrue(!violation.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"order", helper.getTick(), caster.getUUID(),
						"charge began with no armed target", "never", "yes"));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** OX-8 — positive autonomy: the coordinator's own mark drives the ox into the charge. */
	@GameTest(maxTicks = 160)
	public void coordinatorAutonomousMarkTriggersOx(GameTestHelper helper) {
		String fixture = "coordinatorAutonomousMarkTriggersOx";
		paveFloor(helper, 0, 7, 0, 7);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean charged = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, fixture, caster)));

		helper.runAtTickTime(VICTIM_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> zombieRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 5)))));

		long deadline = STAGE_TICK + 90;
		for (long tick = STAGE_TICK; tick <= deadline; tick++) {
			helper.runAtTickTime(tick, () -> {
				List<MegumiOxEntity> bodies = oxenOwnedBy(level, caster.getUUID());
				if (bodies.isEmpty() || charged.get()) {
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				if (ox.charging() && ox.chargeTargetUuid() != null
						&& ox.chargeTargetUuid().equals(zombieRef.get() == null ? null : zombieRef.get().getUUID())) {
					charged.set(true);
					Zombie zombie = zombieRef.get();
					if (zombie != null) {
						zombie.discard();
					}
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				}
			});
		}
		helper.runAtTickTime(deadline + 1, () -> {
			try {
				helper.fail(MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomy", helper.getTick(),
						caster.getUUID(), "autonomous mark triggered the charge", "within window", "never"));
			} finally {
				Zombie zombie = zombieRef.get();
				if (zombie != null) {
					zombie.discard();
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
	}

	// ----------------------------------------------------------------------------------------------
	// Private helpers
	// ----------------------------------------------------------------------------------------------

	private static void paveFloor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void summonOx(GameTestHelper helper, String fixture, ServerPlayer caster) {
		MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.OX);
		boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
		helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"summon", helper.getTick(), caster.getUUID(), "tryPrimary result", "true", summoned));
	}

	/** Aim + sic at the victim through the real order path. */
	private static void sicAt(GameTestHelper helper, String fixture, ServerPlayer caster,
			LivingEntity target) {
		helper.assertTrue(target != null && target.isAlive(), MegumiShikigamiTestFixtures.diagnostic(
				fixture, "sic", helper.getTick(), caster.getUUID(), "victim alive", "true", "gone"));
		TodoSwapTestFixtures.aimAt(caster,
				target.position().add(0.0, target.getBbHeight() / 2.0, 0.0));
		boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
		helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"sic", helper.getTick(), caster.getUUID(), "trySic result", "true", sicced));
	}

	private static MegumiOxEntity onlyOx(GameTestHelper helper, String fixture, String phase,
			ServerLevel level, UUID ownerId) {
		List<MegumiOxEntity> bodies = oxenOwnedBy(level, ownerId);
		helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
				phase, helper.getTick(), ownerId, "owned ox bodies", "1", bodies.size()));
		return bodies.get(0);
	}

	private static List<MegumiOxEntity> oxenOwnedBy(ServerLevel level, UUID ownerId) {
		return MegumiShikigamiTestFixtures.ownedBy(level, ownerId, MegumiOxEntity.class);
	}

	private static List<MegumiDivineDogEntity> dogsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiDivineDogEntity> owned = new java.util.ArrayList<>();
		for (MegumiDivineDogEntity dog : level.getEntities(
				EntityTypeTest.forClass(MegumiDivineDogEntity.class), candidate -> true)) {
			if (ownerId.equals(dog.ownerUuid())) {
				owned.add(dog);
			}
		}
		return owned;
	}

	/**
	 * Husks, not zombies: plain zombies ignite in the open-air arena's daylight and the burn
	 * ticks pollute every damage-read (~1.0 per tick — the phantom "hit" the real harness saw).
	 * A husk shares the zombie combat surface but never burns.
	 */
	private static Zombie spawnFrozenZombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.HUSK, feet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);
		return zombie;
	}

	private static Zombie spawnToughZombie(GameTestHelper helper, String fixture, BlockPos feet,
			double maxHealth) {
		Zombie zombie = spawnFrozenZombie(helper, fixture, feet);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zombie.setHealth((float) maxHealth);
		return zombie;
	}

	/** Relative arena position → absolute feet teleport (the world offset is random per run). */
	private static void teleportTo(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
			BlockPos relativeFeet) {
		Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(relativeFeet));
		entity.teleportTo(spot.x, spot.y, spot.z);
	}

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void cleanupOwner(GameTestHelper helper, ServerPlayer owner) {
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
		CursedSpiritTestFixtures.cleanupVictim(helper, owner);
	}
}
