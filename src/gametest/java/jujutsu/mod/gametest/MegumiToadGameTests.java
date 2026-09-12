package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.combat.CombatStagger;

/**
 * Toad (Ten Shadows selection layer) server scenarios, block 1 — summon (S1), the tongue grab
 * (S2), recall (S3), death (S4) — exercised through the production runtime calls
 * {@code MegumiShikigamiRuntime.tryPrimary} / {@code trySic}, the same hop the vessel router
 * reaches for the PRIMARY / PRIMARY_SNEAK slots.
 *
 * <p><b>Pinned literals.</b> S3/S4 assert the literal 240/400 ticks rather than the profile
 * constants ON PURPOSE: the red-proof mutates the profile row (240-&gt;241, 400-&gt;401) and the
 * assert must follow the balance contract, not the constant. Every other number (recall window,
 * windup, pull-up window) references {@link MegumiShikigamiProfile} directly.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * bodies are found by owner-UUID scan ({@link #toadOwnedBy}), never by bounds. The
 * summon/recall/kill steps sit on different ticks — the runtime drops same-tick duplicate
 * technique presses. {@code hurtServer} on the body is gated on the ACTIVE phase, so S4 kills
 * only after the 16-tick materialization (with an explicit ACTIVE premise assert). The tongue
 * target is an AI zombie with zeroed speed (Slowness 100): full AI keeps physics, so external
 * velocity moves it, while self-motion is removed — only the grab can displace it. (A NoAI body
 * is fully frozen — position never changes — so a NoAI distance assert would be vacuous.) The
 * strike is detected through the tongue's stagger (the body's melee swing never staggers, so a
 * stray bite cannot fake the strike). The zombie gets a stone roof one block above the scan
 * instant, so no flight corridor is needed. Static state (selection map, both pack maps, both
 * cooldown slots) is cleared in setup and on every success/failure path.
 */
public final class MegumiToadGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int KILL_TICK = 25;
	private static final int SIC_TICK = 24;
	private static final long STRIKE_DEADLINE_TICK = SIC_TICK + 60;
	private static final int PULL_SETTLE_TICKS = 12;

	/**
	 * S3 pins this row: a manual recall costs exactly the Toad recall cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.TOAD_RECALL_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	/**
	 * S4 pins this row: losing the body costs exactly the Toad death cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.TOAD_DEATH_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 400;

	/**
	 * S1 — selecting TOAD and pressing the technique key summons exactly one live body with no
	 * cooldown: the pack view reads type "toad" with one anchored body, one
	 * {@link MegumiToadEntity} owned by the caster sits in the level, and PRIMARY stays at 0.
	 */
	@GameTest(maxTicks = 60)
	public void toadSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "toadSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.TOAD.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.TOAD.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));
				helper.assertTrue(pack.anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"anchor alive", "true", pack.anchorAlive()));

				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));

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

	/**
	 * S2 — sic on an AI zombie with zeroed speed 5 blocks away ends in a tongue strike: the
	 * strike is detected through the tongue's stagger (nothing else in the arena staggers), the
	 * zombie's health dropped by at least the armoured tongue hit, its peak toward-the-toad
	 * speed over the strike window reads at least 0.5 (binding against the 0.65 contract), and
	 * the zombie's own displacement from its strike-tick spot toward the toad reads at least 1.5
	 * blocks within 12 further ticks (secondary, non-vacuous: Slowness 100 removes self-motion
	 * while full AI keeps physics, so only the grab can move it; in-game the pull drags
	 * 6.0→3.16 blocks within 6 ticks, so both floors sit ~2× below observed).
	 */
	@GameTest(maxTicks = 150)
	public void toadTonguePullsTheTargetTowardTheBody(GameTestHelper helper) {
		String fixture = "toadTonguePullsTheTargetTowardTheBody";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		// One opaque block above the zombie: kills sky-burn flakiness. The tongue is instant, so
		// no corridor matters, and the owner-to-chest sight line passes well below the roof.
		helper.setBlock(new BlockPos(6, 4, 5), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		// AI body with zeroed speed: full AI keeps physics (external velocity moves it) while
		// Slowness 100 removes self-motion, so any displacement toward the toad is the grab.
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<Vec3> strikeZombiePos = new AtomicReference<>();
		AtomicReference<Vec3> strikeToadPos = new AtomicReference<>();
		AtomicReference<Double> maxTowardSpeed = new AtomicReference<>(0.0);
		AtomicBoolean struck = new AtomicBoolean();
		AtomicLong struckTick = new AtomicLong(-1L);
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));

				healthBefore.set((double) zombie.getHealth());

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

		for (long tick = SIC_TICK + 1; tick <= STRIKE_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				samplePull(helper, level, caster, zombie, maxTowardSpeed);
				long gameTime = level.getGameTime();
				if (!struck.get() && CombatStagger.GLOBAL.isStaggered(zombie.getUUID(), gameTime)) {
					try {
						double dropped = healthBefore.get() - zombie.getHealth();
						helper.assertTrue(dropped >= 2.5,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "strike",
										helper.getTick(), caster.getUUID(),
										"tongue damage (3.0 vs 2 armour, 2.94 observed)", ">= 2.5", dropped));
						struck.set(true);
						struckTick.set(pollTick);
						List<MegumiToadEntity> strikeBodies = toadOwnedBy(level, caster.getUUID());
						if (!strikeBodies.isEmpty()) {
							strikeZombiePos.set(zombie.position());
							strikeToadPos.set(strikeBodies.get(0).position());
						}
					} catch (RuntimeException | AssertionError failure) {
						done.set(true);
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						throw failure;
					}
				}
				if (!struck.get() && pollTick == STRIKE_DEADLINE_TICK) {
					try {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"strike", helper.getTick(), caster.getUUID(), "tongue struck within window",
								"stagger present by tick " + STRIKE_DEADLINE_TICK, "absent"));
					} finally {
						done.set(true);
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					return;
				}
				if (struck.get() && pollTick >= struckTick.get() + PULL_SETTLE_TICKS) {
					try {
						List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
						helper.assertTrue(!bodies.isEmpty(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "pull",
										helper.getTick(), caster.getUUID(), "toad still present", "present", "absent"));
						Vec3 zombieStrike = strikeZombiePos.get();
						Vec3 toadStrike = strikeToadPos.get();
						helper.assertTrue(zombieStrike != null && toadStrike != null,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "pull",
										helper.getTick(), caster.getUUID(),
										"strike positions captured", "present", "absent"));
						Vec3 toToad = new Vec3(toadStrike.x - zombieStrike.x, 0.0, toadStrike.z - zombieStrike.z);
						double reach = toToad.length();
						helper.assertTrue(reach > 1.0E-6,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "pull",
										helper.getTick(), caster.getUUID(),
										"strike reach non-degenerate", "> 0", reach));
						Vec3 displacement = zombie.position().subtract(zombieStrike);
						double towardDisplacement =
								(displacement.x * toToad.x + displacement.z * toToad.z) / reach;
						helper.assertTrue(towardDisplacement >= 1.5,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "pull",
										helper.getTick(), caster.getUUID(),
										"zombie displacement toward the toad", ">= 1.5", towardDisplacement));
						helper.assertTrue(maxTowardSpeed.get() >= 0.5,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "pull",
										helper.getTick(), caster.getUUID(),
										"peak toward-the-toad speed", ">= 0.5", maxTowardSpeed.get()));
						done.set(true);
						zombie.discard();
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
				}
			});
		}
	}

	/**
	 * S3 — pressing the key again while the Toad is out recalls it: PRIMARY reads exactly 240
	 * ticks in the recall tick, the pack record is gone, and once the 12-tick recall sink
	 * finishes no owned body remains in the level.
	 */
	@GameTest(maxTicks = 60)
	public void toadRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "toadRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
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

				// Same-tick read: the cooldown was just armed, so the remaining time is exact.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});

		// The recall sink (TOAD_RECALL_TICKS) plays out after the record is gone; past it +2 the
		// body must have left the level lookup.
		helper.runAtTickTime(RECALL_TICK + MegumiShikigamiProfile.TOAD_RECALL_TICKS + 2, () -> {
			List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"gone", helper.getTick(), caster.getUUID(), "owned Toad bodies in level", "0", bodies.size()));
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * S4 — killing the Toad body routes through the death reconciliation: PRIMARY reads exactly
	 * 400 ticks and the pack record is gone. The kill waits past the 16-tick materialization with
	 * an explicit ACTIVE premise, because {@code hurtServer} on the body is gated on combat being
	 * enabled.
	 */
	@GameTest(maxTicks = 80)
	public void toadDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "toadDeathChargesDeathCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));
				MegumiToadEntity body = bodies.get(0);
				helper.assertTrue(body.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true", body.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"PRIMARY death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * Every live Toad body owned by {@code ownerId} in {@code level}. Owner-filtered, never
	 * bounds-filtered: the world offset is random per run and bodies drift (follow), so a
	 * structure bounds scan could miss a live body or catch a sibling test's. Fresh mock UUIDs
	 * per test make the owner filter exact. Local (not in the shared fixtures) so Block 1 needs
	 * no shared-file edit.
	 */
	private static List<MegumiToadEntity> toadOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiToadEntity> owned = new ArrayList<>();
		for (MegumiToadEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiToadEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/**
	 * One tick's pull sample: the zombie's horizontal velocity projected onto the
	 * zombie-to-toad direction, folded into the running peak. Never throws: a sampling miss must
	 * not fail the scenario, the peak assert at the settle tick owns the verdict.
	 */
	private static void samplePull(GameTestHelper helper, ServerLevel level, ServerPlayer caster,
			Zombie zombie, AtomicReference<Double> maxTowardSpeed) {
		try {
			if (!zombie.isAlive() || zombie.isRemoved()) {
				return;
			}
			List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
			if (bodies.isEmpty()) {
				return;
			}
			Vec3 velocity = zombie.getDeltaMovement();
			Vec3 toToad = bodies.get(0).position().subtract(zombie.position());
			double horizontal = Math.hypot(toToad.x, toToad.z);
			if (horizontal < 1.0E-6) {
				return;
			}
			double toward = (velocity.x * toToad.x + velocity.z * toToad.z) / horizontal;
			maxTowardSpeed.accumulateAndGet(toward, Math::max);
		} catch (RuntimeException ignored) {
			// Best-effort sampling; the settle-tick asserts own the verdict.
		}
		}


	/** Floor-supported 3x3 stone pad so the ground placement always finds a safe body spot. */
	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}
}
