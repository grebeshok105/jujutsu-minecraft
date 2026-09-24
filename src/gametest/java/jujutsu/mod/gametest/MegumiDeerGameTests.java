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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiDeerEntity;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Round Deer server scenarios — the roster-common skeleton plus the support signature matrix
 * (spec §7, plan §I): heal priority, cleanse allowlist, defensive antler shove, threat-aware
 * interpose. Every row follows the suite traps: owner-UUID body scans (random world offset),
 * literal pins instead of profile constants (the red-proof mutates the profile row), intermediate
 * steps inside {@link MegumiShikigamiTestFixtures#runGuarded}, {@code cleanupCaster} on every
 * terminal path, and poll-until-deadline loops instead of exact ticks for anything cadence-driven.
 */
public final class MegumiDeerGameTests {

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int ACT_TICK = 30;

	// Literal pins — deliberately NOT the profile constants, so a profile edit turns this suite red.
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 180;
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 320;
	private static final int EXPECTED_MATERIALIZE_TICKS = 14;
	private static final double EXPECTED_HEAL_MAX = 6.0;
	private static final double EXPECTED_SELF_HEAL_CEILING = 3.0;
	private static final double EXPECTED_INTERPOSE_RADIUS = 3.0;
	private static final double INTERPOSE_ARRIVE_TOLERANCE = 1.75;
	private static final double FOLLOW_BOUNDS = 8.0;

	// — Fixtures —————————————————————————————————————————————————————————

	/** Stone floor plus a sun roof: zombies under a flat lid never cook mid-scenario. */
	private static void layPad(GameTestHelper helper) {
		for (int dx = -1; dx <= 9; dx++) {
			for (int dz = -1; dz <= 9; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
				helper.setBlock(new BlockPos(dx, 6, dz), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void summonDeer(GameTestHelper helper, String fixture, ServerPlayer caster,
			AtomicBoolean summoned) {
		MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DEER);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(caster, false);
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), caster.getUUID(), "deer tryPrimary result", "true", ok));
		});
	}

	private static MegumiDeerEntity theDeer(GameTestHelper helper, String fixture,
			ServerLevel level, UUID ownerId, String phase) {
		List<MegumiDeerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
				level, ownerId, MegumiDeerEntity.class);
		helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture, phase,
				helper.getTick(), ownerId, "owned deer bodies", "1", bodies.size()));
		MegumiDeerEntity deer = bodies.get(0);
		helper.assertTrue(deer.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture, phase,
				helper.getTick(), ownerId, "deer ACTIVE (materialization over)", "true",
				deer.phase()));
		return deer;
	}

	/** World position → fixture-relative block (the arena's world offset is random per run). */
	private static BlockPos relativeOf(GameTestHelper helper, Vec3 worldPos) {
		return BlockPos.containing(worldPos).subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** The policy's interpose geometry, recomputed: radius out from owner toward threat. */
	private static Vec3 interposePoint(Vec3 ownerPos, Vec3 threatPos) {
		double dx = threatPos.x - ownerPos.x;
		double dz = threatPos.z - ownerPos.z;
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0E-6) {
			return ownerPos;
		}
		double step = Math.min(EXPECTED_INTERPOSE_RADIUS, distance);
		return new Vec3(ownerPos.x + dx / distance * step, ownerPos.y, ownerPos.z + dz / distance * step);
	}

	// — Roster-common skeleton ———————————————————————————————————————————

	/** S1 — selecting DEER and pressing the technique key summons exactly one live body. */
	@GameTest(maxTicks = 60)
	public void deerSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "deerSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DEER);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.DEER.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.DEER.id(), view.get().type()));

				List<MegumiDeerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiDeerEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned deer bodies", "1", bodies.size()));
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

	/** S2 — a manual recall removes the body and costs exactly the deer recall cooldown. */
	@GameTest(maxTicks = 80)
	public void deerRecallRemovesBodyAndChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "deerRecallRemovesBodyAndChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DEER);
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
						ownerId, MegumiShikigami.DEER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"deer recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
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

	/**
	 * S3 — the deer emerges as a shadow first: combat stays OFF through the 14-tick
	 * materialization and flips on exactly when the body goes ACTIVE.
	 */
	@GameTest(maxTicks = 80)
	public void deerSummonMaterializesThenActivates(GameTestHelper helper) {
		String fixture = "deerSummonMaterializesThenActivates";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiDeerEntity> bodyRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DEER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
			List<MegumiDeerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
					level, caster.getUUID(), MegumiDeerEntity.class);
			helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), caster.getUUID(), "deer bodies", "1", bodies.size()));
			bodyRef.set(bodies.get(0));
		}));

		helper.runAtTickTime(SUMMON_TICK + 4, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiDeerEntity deer = bodyRef.get();
			helper.assertTrue(deer != null && !deer.combatEnabled(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "materialize", helper.getTick(),
							caster.getUUID(), "still materializing (combat off)",
							"true", deer == null ? "missing" : deer.phase()));
			helper.assertTrue(deer.phase() == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "materialize", helper.getTick(),
							caster.getUUID(), "presentation phase",
							MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, deer.phase()));
		}));

		helper.runAtTickTime(SUMMON_TICK + EXPECTED_MATERIALIZE_TICKS + 6, () -> {
			try {
				MegumiDeerEntity deer = bodyRef.get();
				helper.assertTrue(deer != null && deer.combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "activate", helper.getTick(),
								caster.getUUID(), "body combat-enabled after 14 ticks",
								"true", deer == null ? "missing" : deer.phase()));
				helper.assertTrue(deer.phase() == MegumiShikigamiPresentationPolicy.Phase.ACTIVE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "activate", helper.getTick(),
								caster.getUUID(), "presentation phase",
								MegumiShikigamiPresentationPolicy.Phase.ACTIVE, deer.phase()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(50, () -> helper.succeed());
	}

	/**
	 * S4 — killing the body routes through the death reconciliation: the deer death cooldown
	 * reads exactly 320 ticks and the pack record is gone. The kill waits past materialization
	 * with an explicit ACTIVE premise because {@code hurtServer} is gated on combat.
	 */
	@GameTest(maxTicks = 100)
	public void deerDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "deerDeathChargesDeathCooldownAndClearsPack";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiDeerEntity deer = theDeer(helper, fixture, level, ownerId, "kill");
				boolean damaged = deer.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture, "kill",
						helper.getTick(), ownerId, "lethal damage applied", "true", damaged));
				// AFTER_DEATH reconciles inside hurtServer, so the same-tick read is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.DEER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"deer death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0, MegumiShikigamiTestFixtures.diagnostic(fixture, "kill",
						helper.getTick(), ownerId, "PRIMARY slot (per-type map)", "0", primary));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * S5 — a summon with no safe ground spot refuses: {@code tryPrimary} returns false, no pack
	 * is staged, and neither cooldown is armed. The arena is coffined in stone so every placement
	 * candidate (right/left/forward/self at every vertical offset) collides.
	 */
	@GameTest(maxTicks = 60)
	public void deerNoRoomSummonRefusesAndKeepsCooldownFree(GameTestHelper helper) {
		String fixture = "deerNoRoomSummonRefusesAndKeepsCooldownFree";
		// Coffin: the four ground candidates sit within ±1.8 of the caster, and the placement scan
		// probes ±3 vertically — a solid 5x5x11 column centered on the caster denies all of them.
		for (int dx = 1; dx <= 5; dx++) {
			for (int dz = 1; dz <= 5; dz++) {
				for (int dy = -3; dy <= 7; dy++) {
					helper.setBlock(new BlockPos(dx, dy, dz), Blocks.STONE);
				}
			}
		}
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DEER);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"no-room", helper.getTick(), ownerId, "tryPrimary refuses", "false", summoned));
				helper.assertTrue(MegumiShikigamiRuntime.packView(level.getServer(), ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "no-room", helper.getTick(),
								ownerId, "no pack staged", "empty", "present"));
				helper.assertTrue(MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiDeerEntity.class).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "no-room", helper.getTick(),
								ownerId, "no body staged", "0", "spawned"));
				long summonCooldown = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.DEER, level.getGameTime());
				helper.assertTrue(summonCooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"no-room", helper.getTick(), ownerId, "deer cooldown stays free", "0", summonCooldown));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"no-room", helper.getTick(), ownerId, "PRIMARY stays free", "0", primary));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * S6 — a sic order on a zombie lands on the deer as a mark (the deer reads marks as threat
	 * awareness, never as an attack order): the zombie takes no damage all window.
	 */
	@GameTest(maxTicks = 140)
	public void deerSicCommandMarksTheTarget(GameTestHelper helper) {
		String fixture = "deerSicCommandMarksTheTarget";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(6, 1, 3));
			zombie.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(zombie);
			zombieRef.set(zombie);
			TodoSwapTestFixtures.aimAt(caster, zombie.getEyePosition());
			boolean ordered = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(ordered, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic",
					helper.getTick(), ownerId, "sic accepted", "true", ordered));
			MegumiDeerEntity deer = theDeer(helper, fixture, level, ownerId, "sic");
			// The mark lands as awareness only — the deer's attack target stays empty (spec §7).
			helper.assertTrue(deer.getTarget() == null, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), ownerId, "the sic mark is not an attack order",
					"null", String.valueOf(deer.getTarget())));
		}));

		helper.runAtTickTime(ACT_TICK + 60, () -> {
			try {
				Zombie zombie = zombieRef.get();
				MegumiDeerEntity deer = theDeer(helper, fixture, level, caster.getUUID(), "sic");
				Vec3 point = interposePoint(caster.position(), zombie.position());
				helper.assertTrue(
						horizontalDistance(deer.position(), point) <= INTERPOSE_ARRIVE_TOLERANCE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
								caster.getUUID(), "the mark reads as awareness (deer interposed)",
								"distance <= " + INTERPOSE_ARRIVE_TOLERANCE,
								horizontalDistance(deer.position(), point)));
				helper.assertTrue(deer.getTarget() == null, MegumiShikigamiTestFixtures.diagnostic(
						fixture, "sic", helper.getTick(), caster.getUUID(), "never an attack target",
						"null", String.valueOf(deer.getTarget())));
				helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
								caster.getUUID(), "marked but never attacked",
								zombie.getMaxHealth(), zombie.getHealth()));
			} finally {
				zombieRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(120, () -> helper.succeed());
	}

	/**
	 * S7a — the owner dying sweeps the deer: the pack record is gone and the death cooldown
	 * reads exactly 320. A survival-bodied owner is required — die() fires AFTER_DEATH
	 * synchronously where kill() is a no-op on a mock.
	 */
	@GameTest(maxTicks = 80)
	public void deerOwnerDeathTeardownClearsPack(GameTestHelper helper) {
		String fixture = "deerOwnerDeathTeardownClearsPack";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, owner, summoned));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = owner.getUUID();
				owner.die(level.damageSources().genericKill());
				helper.assertTrue(owner.isDeadOrDying(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"owner-death", helper.getTick(), ownerId, "owner died", "true",
						owner.isDeadOrDying()));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.DEER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "owner-death", helper.getTick(),
								ownerId, "deer death cooldown on owner death",
								EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "owner-death", owner);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				CursedSpiritTestFixtures.cleanupVictim(helper, owner);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * S7b — a dimension change is a recall-family teardown: the pack record is gone and the deer
	 * cooldown reads exactly 180.
	 */
	@GameTest(maxTicks = 80)
	public void deerDimensionChangeTeardownChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "deerDimensionChangeTeardownChargesRecallCooldown";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "dimension", caster);
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.DEER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "dimension", helper.getTick(),
								ownerId, "deer recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/** S8 — the deer coexists with the dog pack: two live packs on one owner, no interference. */
	@GameTest(maxTicks = 100)
	public void deerCoexistsWithAnotherPack(GameTestHelper helper) {
		String fixture = "deerCoexistsWithAnotherPack";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(4, 1, 4), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean dogsOut = new AtomicBoolean();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			boolean out = MegumiSummonRuntime.tryToggle(caster, false);
			dogsOut.set(out);
			helper.assertTrue(out, MegumiShikigamiTestFixtures.diagnostic(fixture, "dogs",
					helper.getTick(), caster.getUUID(), "dog tryToggle result", "true", out));
		}));

		helper.runAtTickTime(SUMMON_TICK + 2, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				helper.assertTrue(dogsOut.get() && summoned.get(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
								ownerId, "both summons succeeded", "true,true",
								dogsOut.get() + "," + summoned.get()));
				helper.assertTrue(MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
								ownerId, "dog pack kept", "present", "absent"));
				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent()
								&& MegumiShikigami.DEER.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
								ownerId, "deer pack kept", MegumiShikigami.DEER.id(),
								view.map(PackView::type).orElse("absent")));
				List<MegumiDeerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiDeerEntity.class);
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "deer bodies", "1", bodies.size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(70, () -> helper.succeed());
	}

	// — Signature rows ————————————————————————————————————————————————————

	/**
	 * Spec §7 (healing): a wounded owner is the deer's first lane. The owner is a real damageable
	 * victim; the wound is set directly and its hunger is dropped so natural regen cannot fake a
	 * pulse — any health rise is the deer's heal. Poll-until-deadline: the first scan needs up to
	 * a full cadence after activation plus the ten-tick channel.
	 */
	@GameTest(maxTicks = 260)
	public void deerHealsWoundedOwner(GameTestHelper helper) {
		String fixture = "deerHealsWoundedOwner";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicBoolean healed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, owner, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			theDeer(helper, fixture, level, owner.getUUID(), "wound");
			owner.setHealth(10.0f);
			owner.getFoodData().setFoodLevel(5);
			helper.assertTrue(owner.getHealth() == 10.0f, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"wound", helper.getTick(), owner.getUUID(), "owner wounded to half", "10.0",
					owner.getHealth()));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 170; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (healed.get()) {
					return;
				}
				if (owner.getHealth() > 10.0f) {
					healed.set(true);
					try {
						helper.assertTrue(owner.getHealth() <= 10.0f + EXPECTED_HEAL_MAX + 0.01f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "heal", helper.getTick(),
										owner.getUUID(), "one pulse stays inside the 2.0..6.0 band",
										"<= " + (10.0f + EXPECTED_HEAL_MAX), owner.getHealth()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 170) {
					MegumiDeerEntity deer = MegumiShikigamiTestFixtures.ownedBy(
							level, owner.getUUID(), MegumiDeerEntity.class).stream().findFirst().orElse(null);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "heal",
							pollTick, owner.getUUID(),
							"the wounded owner is pulsed within two scan windows",
							"health > 10.0", owner.getHealth() + " deer="
									+ (deer == null ? "gone" : deer.position())));
				}
			});
		}
	}

	/**
	 * Spec §7 (healing) + §16 cross: an own-side shikigami body is the second lane. A wounded
	 * toad pack-mate gets pulsed; the heal lands within the 2.0..6.0 band.
	 */
	@GameTest(maxTicks = 280)
	public void deerHealsWoundedShikigamiBody(GameTestHelper helper) {
		String fixture = "deerHealsWoundedShikigamiBody";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(4, 1, 4), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiToadEntity> toadRef = new AtomicReference<>();
		AtomicBoolean healed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "toad tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> {
			MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
				MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DEER);
				helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
								caster.getUUID(), "deer tryPrimary result", "true", "false"));
			});
		});

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			List<MegumiToadEntity> toads = MegumiShikigamiTestFixtures.ownedBy(
					level, caster.getUUID(), MegumiToadEntity.class);
			helper.assertTrue(toads.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"wound", helper.getTick(), caster.getUUID(), "toad bodies", "1", toads.size()));
			MegumiToadEntity toad = toads.get(0);
			helper.assertTrue(toad.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"wound", helper.getTick(), caster.getUUID(), "toad ACTIVE", "true", toad.phase()));
			toadRef.set(toad);
			toad.setHealth(6.0f);
			// The toad's FollowOwnerGoal wanders it — pin it inside the pulse ring so the
			// recipient stays in DEER_HEAL_RANGE between the scan pick and the commit. NoAI
			// (not Slowness): the deer's cleanse scan would strip a freeze effect off it.
			toad.setNoAi(true);
			theDeer(helper, fixture, level, caster.getUUID(), "wound");
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				MegumiToadEntity toad = toadRef.get();
				if (healed.get() || toad == null) {
					return;
				}
				if (toad.getHealth() > 6.0f) {
					healed.set(true);
					try {
						helper.assertTrue(toad.getHealth() <= 6.0f + EXPECTED_HEAL_MAX + 0.01f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "heal", helper.getTick(),
										caster.getUUID(), "one pulse inside the 2.0..6.0 band",
										"<= " + (6.0f + EXPECTED_HEAL_MAX), toad.getHealth()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 200) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "heal",
							pollTick, caster.getUUID(), "the wounded own-body is pulsed",
							"health > 6.0", toad.getHealth()));
				}
			});
		}
	}

	/**
	 * Spec §7 (healing refusal): an enemy is never eligible. A wounded hostile parked next to the
	 * deer holds exactly its seeded health through two full scan windows.
	 */
	@GameTest(maxTicks = 240)
	public void deerNeverHealsEnemies(GameTestHelper helper) {
		String fixture = "deerNeverHealsEnemies";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			theDeer(helper, fixture, level, caster.getUUID(), "refusal");
			Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(5, 1, 3));
			zombie.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(zombie);
			zombie.setHealth(5.0f);
			zombieRef.set(zombie);
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 170; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				if (zombie == null) {
					return;
				}
				helper.assertTrue(zombie.isAlive() && zombie.getHealth() == 5.0f,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refusal", pollTick,
								caster.getUUID(), "enemy health never rises across the scan window",
								"5.0", zombie.getHealth()));
			});
		}
		helper.runAtTickTime(ACT_TICK + 180, () -> {
			try {
				zombieRef.get().discard();
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/**
	 * Spec §7 (healing refusal): a neutral mob is never eligible either — narrowed to enemy+neutral
	 * per plan §4.6 (no second mock caster exists for a foreign-owner row). A wounded untamed cow
	 * beside the deer stays at its seeded health.
	 */
	@GameTest(maxTicks = 240)
	public void deerNeverHealsForeignOrNeutral(GameTestHelper helper) {
		String fixture = "deerNeverHealsForeignOrNeutral";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Cow> cowRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			theDeer(helper, fixture, level, caster.getUUID(), "refusal");
			Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(5, 1, 4));
			cow.setPersistenceRequired();
			cow.setHealth(5.0f);
			cowRef.set(cow);
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 170; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Cow cow = cowRef.get();
				if (cow == null) {
					return;
				}
				helper.assertTrue(cow.isAlive() && cow.getHealth() == 5.0f,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refusal", pollTick,
								caster.getUUID(), "neutral health never rises across the scan window",
								"5.0", cow.getHealth()));
			});
		}
		helper.runAtTickTime(ACT_TICK + 180, () -> {
			try {
				cowRef.get().discard();
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/**
	 * Spec §7 (cleanse) — the RED-PROOF anchor row: the cleanse strips hostile debuffs but never
	 * touches the mod's internal authority markers. {@code CURSED_FEAR} must be removed;
	 * {@code GRIPPED} (hold channel), {@code MEGUMI_SHADOW_GRIP} (trap refresh) and
	 * {@code MEGUMI_NUE_WINGS} (beneficial gate) must all survive the cleanse scan — the
	 * reclassification names which lines are allowlist, not category.
	 */
	@GameTest(maxTicks = 280)
	public void deerCleanseStripsOnlyAllowedEffects(GameTestHelper helper) {
		String fixture = "deerCleanseStripsOnlyAllowedEffects";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicBoolean cleansed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			theDeer(helper, fixture, level, caster.getUUID(), "cleanse");
			caster.addEffect(new MobEffectInstance(JujutsuEffects.CURSED_FEAR, 6000));
			caster.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED, 6000));
			caster.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_NUE_WINGS, 6000));
			caster.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SHADOW_GRIP, 6000));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 220; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (cleansed.get()) {
					return;
				}
				if (!caster.hasEffect(JujutsuEffects.CURSED_FEAR)) {
					cleansed.set(true);
					try {
						helper.assertTrue(caster.hasEffect(JujutsuEffects.GRIPPED)
										&& caster.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS)
										&& caster.hasEffect(JujutsuEffects.MEGUMI_SHADOW_GRIP),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "cleanse", pollTick,
										caster.getUUID(),
										"allowlist reclassification: internal markers survive"
												+ " (GRIPPED hold channel, SHADOW_GRIP futile trap,"
												+ " NUE_WINGS beneficial)",
										"all present",
										"GRIPPED=" + caster.hasEffect(JujutsuEffects.GRIPPED)
												+ " NUE_WINGS=" + caster.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS)
												+ " SHADOW_GRIP=" + caster.hasEffect(JujutsuEffects.MEGUMI_SHADOW_GRIP)));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 220) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "cleanse",
							pollTick, caster.getUUID(), "CURSED_FEAR stripped by the cleanse scan",
							"absent", "present"));
				}
			});
		}
	}

	/**
	 * Spec §7 (self-heal cap): the deer may heal itself but only inside the same limited mechanic —
	 * the self pulse runs at half strength, so the ceiling lands at 3.0, not 6.0.
	 */
	@GameTest(maxTicks = 260)
	public void deerSelfHealsOnlyWithinLimits(GameTestHelper helper) {
		String fixture = "deerSelfHealsOnlyWithinLimits";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicBoolean healed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiDeerEntity deer = theDeer(helper, fixture, level, caster.getUUID(), "wound");
			deer.setHealth(20.0f);
			deerRef.set(deer);
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				MegumiDeerEntity deer = deerRef.get();
				if (healed.get() || deer == null) {
					return;
				}
				if (deer.getHealth() > 20.0f) {
					healed.set(true);
					try {
						helper.assertTrue(deer.getHealth() <= 20.0f + EXPECTED_SELF_HEAL_CEILING + 0.01f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "self-heal", pollTick,
										caster.getUUID(), "the self pulse is halved (6.0 * 0.5)",
										"<= " + (20.0f + EXPECTED_SELF_HEAL_CEILING), deer.getHealth()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 200) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "self-heal",
							pollTick, caster.getUUID(), "the wounded deer heals itself",
							"health > 20.0", deer.getHealth()));
				}
			});
		}
	}

	/**
	 * Spec §7 (combat): the antler shove is a cornered reflex — only the deer's fresh aggressor,
	 * only within 2.0, and it never turns into a chase. The seeded hit is attributed through the
	 * real damage pipeline; the attacker is NoAI so its velocity FIELD reads the knockback
	 * directly (NoAI bodies never integrate velocity into position).
	 */
	@GameTest(maxTicks = 240)
	public void deerShovesAttackerButNeverChases(GameTestHelper helper) {
		String fixture = "deerShovesAttackerButNeverChases";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean shoved = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, owner, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiDeerEntity deer = theDeer(helper, fixture, level, owner.getUUID(), "attack");
			deerRef.set(deer);
			Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE,
					relativeOf(helper, deer.position().add(0.0, 0.0, 1.2)));
			zombie.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(zombie);
			zombieRef.set(zombie);
			boolean damaged = deer.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0f);
			helper.assertTrue(damaged && deer.getLastHurtByMob() == zombie,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the hit is attributed to the zombie",
							zombie.getUUID(), String.valueOf(deer.getLastHurtByMob())));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 80; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				MegumiDeerEntity deer = deerRef.get();
				if (zombie == null || deer == null) {
					return;
				}
				// The deer never becomes a damage dealer and never leaves its owner's side.
				helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "chase", pollTick,
								owner.getUUID(), "the attacker is never damaged",
								zombie.getMaxHealth(), zombie.getHealth()));
				helper.assertTrue(deer.distanceTo(owner) <= FOLLOW_BOUNDS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "chase", pollTick,
								owner.getUUID(), "the deer stays inside follow bounds",
								"<= " + FOLLOW_BOUNDS, deer.distanceTo(owner)));
				if (!shoved.get() && Math.hypot(zombie.getDeltaMovement().x, zombie.getDeltaMovement().z) > 0.05) {
					double away = (zombie.getX() - deer.getX()) * zombie.getDeltaMovement().x
							+ (zombie.getZ() - deer.getZ()) * zombie.getDeltaMovement().z;
					helper.assertTrue(away > 0.0, MegumiShikigamiTestFixtures.diagnostic(fixture, "shove",
							pollTick, owner.getUUID(), "the shove pushes the attacker AWAY",
							"> 0", away));
					shoved.set(true);
				}
				if (pollTick == ACT_TICK + 80) {
					boolean ok = shoved.get();
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "shove",
							pollTick, owner.getUUID(), "the fresh aggressor inside 2.0 is shoved",
							"velocity > 0", "never"));
					if (ok) {
						helper.succeed();
					}
				}
			});
		}
	}

	// — §I additions —————————————————————————————————————————————————————

	/**
	 * Autonomous pick: a threat the coordinator flags (the deer's own fresh-aggressor entry in the
	 * shared ally-threat map) drives the deer's positioning — WITHOUT a mark ever landing on the
	 * deer. The deer drifts onto the owner→threat line at the interpose radius and still never
	 * attacks.
	 */
	@GameTest(maxTicks = 280)
	public void deerAutonomousPickFollowsCoordinatorFlag(GameTestHelper helper) {
		String fixture = "deerAutonomousPickFollowsCoordinatorFlag";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean interposed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, owner, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiDeerEntity deer = theDeer(helper, fixture, level, owner.getUUID(), "threat");
			deerRef.set(deer);
			Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(6, 1, 4));
			zombie.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(zombie);
			zombieRef.set(zombie);
			// The deer's own record feeds the coordinator's allyThreats map — a flag, not an order.
			boolean damaged = deer.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0f);
			helper.assertTrue(damaged && deer.getLastHurtByMob() == zombie,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "threat", helper.getTick(),
							owner.getUUID(), "the hit is attributed to the zombie",
							zombie.getUUID(), String.valueOf(deer.getLastHurtByMob())));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				MegumiDeerEntity deer = deerRef.get();
				if (zombie == null || deer == null || interposed.get()) {
					return;
				}
				helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomous", pollTick,
								owner.getUUID(), "the threat is never attacked",
								zombie.getMaxHealth(), zombie.getHealth()));
				Vec3 point = interposePoint(owner.position(), zombie.position());
				if (horizontalDistance(deer.position(), point) <= INTERPOSE_ARRIVE_TOLERANCE) {
					interposed.set(true);
					helper.assertTrue(deer.getTarget() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "autonomous", pollTick,
									owner.getUUID(), "no mark lands on a coordinator-flagged threat",
									"null", String.valueOf(deer.getTarget())));
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 200) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"autonomous", pollTick, owner.getUUID(),
							"the deer drifts to the owner→threat interpose point",
							"distance <= " + INTERPOSE_ARRIVE_TOLERANCE,
							horizontalDistance(deer.position(), point) + " deer=" + deer.position()
									+ " point=" + point));
				}
			});
		}
	}

	/**
	 * Retaliation: a hit on the owner marks the deer (the pack answer it CAN take is positioning —
	 * the mark lands as threat awareness) and the deer drifts to the interpose point, still
	 * dealing no damage to the aggressor.
	 */
	@GameTest(maxTicks = 280)
	public void deerRetaliationMarkEngages(GameTestHelper helper) {
		String fixture = "deerRetaliationMarkEngages";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean interposed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, owner, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			deerRef.set(theDeer(helper, fixture, level, owner.getUUID(), "attack"));
			Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(6, 1, 4));
			zombie.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(zombie);
			zombieRef.set(zombie);
			boolean damaged = owner.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0f);
			helper.assertTrue(damaged && owner.getLastHurtByMob() == zombie,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the hit is attributed to the zombie",
							zombie.getUUID(), String.valueOf(owner.getLastHurtByMob())));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				MegumiDeerEntity deer = deerRef.get();
				if (zombie == null || deer == null || interposed.get()) {
					return;
				}
				helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation", pollTick,
								owner.getUUID(), "the aggressor is never attacked",
								zombie.getMaxHealth(), zombie.getHealth()));
				Vec3 point = interposePoint(owner.position(), zombie.position());
				if (horizontalDistance(deer.position(), point) <= INTERPOSE_ARRIVE_TOLERANCE) {
					interposed.set(true);
					try {
						// The retaliation mark reads as awareness — the deer interposes while
						// never holding the aggressor as an attack target (spec §7).
						helper.assertTrue(deer.getTarget() == null,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation",
										pollTick, owner.getUUID(),
										"the retaliation mark is not an attack order",
										"null", String.valueOf(deer.getTarget())));
					} finally {
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 200) {
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"retaliation", pollTick, owner.getUUID(),
							"the deer interposes on the owner's aggressor",
							"distance <= " + INTERPOSE_ARRIVE_TOLERANCE,
							horizontalDistance(deer.position(), point) + " deer=" + deer.position()
									+ " point=" + point));
				}
			});
		}
	}

	/**
	 * Friendly fire: an ALLIED body is never answered — a teamed cow that hits the deer stays
	 * unshoved (velocity field reads zero the whole window: NoAI bodies keep whatever the field
	 * holds) and never damaged.
	 *
	 * <p>The teamed cow is asserted <em>conditionally</em>: every mock player in the whole gametest
	 * suite shares the profile name {@code "test-mock-player"}, and scoreboard teams key on the
	 * name — a neighbouring test teaming its own caster rips the shared name out of this row's
	 * team, so the alliance can legitimately flicker off mid-run for reasons this test cannot
	 * control. Shove/damage asserts fail only while the alliance stayed continuously live; the
	 * both-bodies-alive anchor is checked unconditionally — it never touches the scoreboard.
	 */
	@GameTest(maxTicks = 240)
	public void deerNeverAttacksAlliedBodies(GameTestHelper helper) {
		String fixture = "deerNeverAttacksAlliedBodies";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(3, 1, 3), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicBoolean allianceBroken = new AtomicBoolean();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicReference<Cow> cowRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDeer(helper, fixture, caster, summoned));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiDeerEntity deer = theDeer(helper, fixture, level, caster.getUUID(), "ally");
			deerRef.set(deer);
			Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW,
					relativeOf(helper, deer.position().add(0.0, 0.0, 1.2)));
			cow.setPersistenceRequired();
			PlayerTeam team = level.getScoreboard().addPlayerTeam(fixture + "_team");
			level.getScoreboard().addPlayerToTeam(caster.getScoreboardName(), team);
			level.getScoreboard().addPlayerToTeam(cow.getScoreboardName(), team);
			cowRef.set(cow);
			boolean damaged = deer.hurtServer(level, level.damageSources().mobAttack(cow), 1.0f);
			helper.assertTrue(damaged && deer.getLastHurtByMob() == cow,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", helper.getTick(),
							caster.getUUID(), "the hit is attributed to the allied cow",
							cow.getUUID(), String.valueOf(deer.getLastHurtByMob())));
		}));

		for (long tick = ACT_TICK + 1; tick <= ACT_TICK + 90; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Cow cow = cowRef.get();
				MegumiDeerEntity deer = deerRef.get();
				if (cow == null || deer == null) {
					return;
				}
				if (!caster.isAlliedTo(cow)) {
					allianceBroken.set(true);
				}
				helper.assertTrue(deer.isAlive() && cow.isAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
								caster.getUUID(), "both bodies stay live", "alive,alive",
								deer.isAlive() + "," + cow.isAlive()));
				boolean shoved = Math.hypot(cow.getDeltaMovement().x, cow.getDeltaMovement().z) > 0.05;
				helper.assertTrue(!(shoved && !allianceBroken.get()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
								caster.getUUID(), "the allied body is never shoved",
								"velocity ~0", cow.getDeltaMovement()));
				boolean hurt = cow.getHealth() < cow.getMaxHealth();
				helper.assertTrue(!(hurt && !allianceBroken.get()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
								caster.getUUID(), "the allied body is never damaged",
								cow.getMaxHealth(), cow.getHealth()));
			});
		}
		helper.runAtTickTime(ACT_TICK + 100, () -> {
			try {
				cowRef.get().discard();
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/**
	 * Cross row (spec §16): the deer's heal eligibility spans BOTH runtimes — a wounded divine dog
	 * and a wounded toad are pulsed across successive scans. Three packs coexist: dogs live on
	 * their own runtime, toad and deer on the shikigami one.
	 */
	@GameTest(maxTicks = 320)
	public void deerHealsDogsAndToad(GameTestHelper helper) {
		String fixture = "deerHealsDogsAndToad";
		layPad(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(4, 1, 4), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiDivineDogEntity> dogRef = new AtomicReference<>();
		AtomicReference<MegumiToadEntity> toadRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			helper.assertTrue(MegumiSummonRuntime.tryToggle(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "dogs tryToggle result", "true", "false"));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "toad tryPrimary result", "true", "false"));
		}));
		helper.runAtTickTime(SUMMON_TICK + 4, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DEER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "deer tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(ACT_TICK + 6, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			List<MegumiDivineDogEntity> dogs = MegumiSummonRuntime.livingDogs(
					level.getServer(), caster.getUUID());
			helper.assertTrue(!dogs.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture, "wound",
					helper.getTick(), caster.getUUID(), "dog pack present", ">=1", dogs.size()));
			List<MegumiToadEntity> toads = MegumiShikigamiTestFixtures.ownedBy(
					level, caster.getUUID(), MegumiToadEntity.class);
			helper.assertTrue(toads.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture, "wound",
					helper.getTick(), caster.getUUID(), "toad bodies", "1", toads.size()));
			dogRef.set(dogs.get(0));
			toadRef.set(toads.get(0));
			dogs.get(0).setHealth(6.0f);
			toads.get(0).setHealth(6.0f);
			// Same wander guard as the single-body row (NoAI, not a cleansable freeze):
			// pack AI follows the owner and can carry a body out of the pulse ring mid-channel.
			dogs.get(0).setNoAi(true);
			toads.get(0).setNoAi(true);
			theDeer(helper, fixture, level, caster.getUUID(), "wound");
		}));

		for (long tick = ACT_TICK + 7; tick <= ACT_TICK + 240; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				MegumiDivineDogEntity dog = dogRef.get();
				MegumiToadEntity toad = toadRef.get();
				if (done.get() || dog == null || toad == null) {
					return;
				}
				if (dog.getHealth() > 6.0f && toad.getHealth() > 6.0f) {
					done.set(true);
					try {
						helper.assertTrue(dog.getHealth() <= 6.0f + EXPECTED_HEAL_MAX + 0.01f
										&& toad.getHealth() <= 6.0f + EXPECTED_HEAL_MAX + 0.01f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "heal", pollTick,
										caster.getUUID(), "each pulse stays inside 2.0..6.0",
										"both <= " + (6.0f + EXPECTED_HEAL_MAX),
										"dog=" + dog.getHealth() + " toad=" + toad.getHealth()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == ACT_TICK + 240) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "heal",
							pollTick, caster.getUUID(), "both own-side bodies are healed",
							"dog > 6.0 && toad > 6.0",
							"dog=" + dog.getHealth() + " toad=" + toad.getHealth()));
				}
			});
		}
	}
}
