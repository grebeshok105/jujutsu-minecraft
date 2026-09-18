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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Nue (Ten Shadows selection layer) server scenarios, block B5 — summon (S1), recall (S2),
 * coexistence beside the dogs (S3), death (S4), dive on a plain target (S5) and the soaked-target
 * escalation (S6) — exercised through the production runtime calls
 * {@code MegumiShikigamiRuntime.tryPrimary} / {@code trySic}, the same hop the vessel router
 * reaches for the PRIMARY / PRIMARY_SNEAK slots.
 *
 * <p><b>Pinned literals.</b> S2/S4 assert the literal 240/400 ticks rather than the profile
 * constants ON PURPOSE: the red-proof mutates the profile row (240-&gt;241, 400-&gt;401) and the
 * assert must follow the balance contract, not the constant. Every other number (recall window,
 * dive timeout, slow durations) references {@link MegumiShikigamiProfile} directly. Since issue
 * #107 the price is armed on the per-type summon map, so the same reads also prove the shared
 * PRIMARY slot was left alone.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * bodies are found by owner-UUID scan ({@link MegumiShikigamiTestFixtures#nueOwnedBy}), never by
 * bounds. The summon/recall/kill steps sit on different ticks — the runtime drops same-tick
 * duplicate technique presses. {@code hurtServer} on the body is gated on the ACTIVE phase, so S4
 * kills only after the 16-tick materialization (with an explicit ACTIVE premise assert). The dive
 * target is a NoAI zombie with a stone roof one block above its head: the roof kills sky-burn
 * flakiness while the shallow dive arrives from the side below it, and the enclosed default
 * template keeps the flyer inside the structure. The impact is captured by per-tick polling and
 * the test succeeds on the FIRST impact, so Nue's 60-tick re-dive can never contaminate the
 * damage bands. Static state (selection map, both pack maps, both cooldown slots) is cleared in
 * setup and on every success/failure path.
 */
public final class MegumiShikigamiGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int SWAP_TICK = 4;
	private static final int KILL_TICK = 25;
	private static final int SIC_TICK = 22;
	private static final long DIVE_DEADLINE_TICK =
			SIC_TICK + MegumiShikigamiProfile.NUE_DIVE_TIMEOUT_TICKS + 60;

	/**
	 * S2 pins this row: a manual recall costs exactly the Nue recall cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.NUE_RECALL_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	/**
	 * S4 pins this row: losing the body costs exactly the Nue death cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.NUE_DEATH_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 400;

	/**
	 * S1 — selecting NUE and pressing the technique key summons exactly one live body with no
	 * cooldown: the pack view reads type "nue" with one anchored body, one
	 * {@link MegumiNueEntity} owned by the caster sits in the level, and PRIMARY stays at 0.
	 */
	@GameTest(maxTicks = 60)
	public void nueSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "nueSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.NUE.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.NUE.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));
				helper.assertTrue(pack.anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"anchor alive", "true", pack.anchorAlive()));

				List<MegumiNueEntity> bodies = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned Nue bodies in level", "1", bodies.size()));

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
	 * S2 — pressing the key again while Nue is out recalls it: PRIMARY reads exactly 240 ticks in
	 * the recall tick, the pack record is gone, and once the 12-tick recall sink finishes no owned
	 * body remains in the level.
	 */
	@GameTest(maxTicks = 60)
	public void nueRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "nueRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
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
				long gameTime = level.getGameTime();
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime);
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"Nue summon cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
				// Issue #107: the recall price is per type now, so the shared PRIMARY slot stays free.
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline now)", "0", primary));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});

		// The recall sink (NUE_RECALL_TICKS) plays out after the record is gone; past it +2 the
		// body must have left the level lookup.
		helper.runAtTickTime(RECALL_TICK + MegumiShikigamiProfile.NUE_RECALL_TICKS + 2, () -> {
			List<MegumiNueEntity> bodies =
					MegumiShikigamiTestFixtures.nueOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"gone", helper.getTick(), caster.getUUID(), "owned Nue bodies in level", "0", bodies.size()));
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * S3 — coexistence (issue #107 D1): with the Divine Dogs already out, selecting NUE and pressing
	 * the technique key summons Nue *beside* them — both pack records live, one anchored Nue body
	 * sits in the level, and PRIMARY stays 0 (a summon is free, and nothing was torn down for it).
	 */
	@GameTest(maxTicks = 60)
	public void summoningNueBesideDogsKeepsBothPacks(GameTestHelper helper) {
		String fixture = "summoningNueBesideDogsKeepsBothPacks";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		// A 6x6 pad, not the caster's own block: the dog pair lands on the two spots the placement
		// search tries first (owner ± right * 1.5), so both of them need a floor to be chosen.
		for (int dx = 1; dx <= 6; dx++) {
			for (int dz = 1; dz <= 6; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			boolean dogsOut = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogsOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogsOut));
			boolean dogsPresent = MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent();
			helper.assertTrue(dogsPresent, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog pack present", "present", "absent"));
		}));

		helper.runAtTickTime(SWAP_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				// The dogs keep their own record: the same key used to swap them off the field.
				boolean dogsStillOut = MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent();
				helper.assertTrue(dogsStillOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "dog pack record kept", "present", "absent"));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "nue pack present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.NUE.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.NUE.id(), view.get().type()));
				helper.assertTrue(view.get().aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"alive bodies", "1", view.get().aliveBodies()));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"PRIMARY cooldown (nothing was recalled)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * S4 — killing the Nue body routes through the death reconciliation: PRIMARY reads exactly
	 * 400 ticks and the pack record is gone. The kill waits past the 16-tick materialization with
	 * an explicit ACTIVE premise, because {@code hurtServer} on the body is gated on combat being
	 * enabled.
	 */
	@GameTest(maxTicks = 80)
	public void nueDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "nueDeathChargesDeathCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiNueEntity> bodies = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"owned Nue bodies in level", "1", bodies.size()));
				MegumiNueEntity body = bodies.get(0);
				helper.assertTrue(body.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true", body.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				long gameTime = level.getGameTime();
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime);
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"Nue death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				// Issue #107: the death price is per type now, so the shared PRIMARY slot stays free.
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline now)", "0", primary));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * S5 — sic on a plain zombie ends in a dive impact: the zombie loses about
	 * {@code NUE_DIVE_DAMAGE} health (banded for the zombie's 2 armour points), carries SLOWNESS
	 * I on the unsoaked row, and is staggered. No dive internals are asserted — only the three
	 * contract effects on the target.
	 */
	@GameTest(maxTicks = 200)
	public void nueDiveDamagesAndSlowsPlainTarget(GameTestHelper helper) {
		runDiveScenario(helper, "nueDiveDamagesAndSlowsPlainTarget", false);
	}

	/**
	 * S6 — same flight against a {@code MEGUMI_SOAKED} zombie: the damage lands in the escalated
	 * band (disjoint from S5's, so escalation is proven without cross-test reads) and the
	 * SLOWNESS duration reads the soaked row. The still-present soak is asserted as the premise.
	 */
	@GameTest(maxTicks = 200)
	public void nueDiveEscalatesAgainstSoakedTarget(GameTestHelper helper) {
		runDiveScenario(helper, "nueDiveEscalatesAgainstSoakedTarget", true);
	}

	private void runDiveScenario(GameTestHelper helper, String fixture, boolean soaked) {
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 4);
		helper.setBlock(casterFeet.below(), Blocks.STONE);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		// One opaque block above the zombie's head: kills sky-burn flakiness, and the shallow
		// dive (hover ~+3 descending to eyes ~+1.6 over ~4.5 blocks) passes well below it, as do
		// both sight lines.
		helper.setBlock(new BlockPos(6, 4, 4), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();

		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicBoolean impacted = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiNueEntity> bodies = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned Nue bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));

				if (soaked) {
					zombie.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SOAKED, 400, 0), caster);
					helper.assertTrue(zombie.hasEffect(JujutsuEffects.MEGUMI_SOAKED),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
									"soak applied", "present", "absent"));
				}
				double before = zombie.getHealth();
				helper.assertTrue(before == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"zombie at full health", zombie.getMaxHealth(), before));
				healthBefore.set(before);

				// Same aim math as the Todo scenarios: eye-to-chest ray, corridor is open air.
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

		for (long tick = SIC_TICK + 1; tick <= DIVE_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (impacted.get() || zombie.isRemoved()) {
					return;
				}
				if (!zombie.hasEffect(MobEffects.SLOWNESS)) {
					if (pollTick == DIVE_DEADLINE_TICK) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"impact", helper.getTick(), caster.getUUID(), "dive impacted within window",
									"slowness present by tick " + DIVE_DEADLINE_TICK, "absent"));
						} finally {
							zombie.discard();
							MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						}
					}
					return;
				}
				try {
					assertDiveImpact(helper, fixture, caster, zombie, healthBefore.get(), soaked);
					impacted.set(true);
					zombie.discard();
				} finally {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	/**
	 * The contract surface on the target only: health drop inside the row's band (5.0 plain /
	 * 7.5 soaked, each banded for armour and float rounding; the bands are disjoint so S6 proves
	 * escalation against S5's row), SLOWNESS I with the row's duration (40 / 60, banded for the
	 * tick the impact is first observed), and an active stagger.
	 */
	private static void assertDiveImpact(GameTestHelper helper, String fixture, ServerPlayer caster,
			Zombie zombie, Double healthBefore, boolean soaked) {
		UUID ownerId = caster.getUUID();
		long tick = helper.getTick();
		double low = soaked ? 6.0 : 4.0;
		double high = soaked ? 8.0 : 5.5;
		double dropped = healthBefore - zombie.getHealth();
		helper.assertTrue(dropped >= low && dropped <= high,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "impact", tick, ownerId,
						(soaked ? "soaked" : "plain") + " dive damage", "[" + low + ", " + high + "]", dropped));

		MobEffectInstance slow = zombie.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slow != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"impact", tick, ownerId, "slowness applied", "present", "absent"));
		int durationLow = soaked ? 50 : 30;
		int durationHigh = soaked ? 60 : 40;
		helper.assertTrue(slow.getDuration() >= durationLow && slow.getDuration() <= durationHigh,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "impact", tick, ownerId,
						(soaked ? "soaked" : "plain") + " slowness duration",
						"[" + durationLow + ", " + durationHigh + "]", slow.getDuration()));
		helper.assertTrue(slow.getAmplifier() == 0,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "impact", tick, ownerId,
						"slowness amplifier", "0", slow.getAmplifier()));

		boolean staggered =
				CombatStagger.GLOBAL.isStaggered(zombie.getUUID(), helper.getLevel().getGameTime());
		helper.assertTrue(staggered, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"impact", tick, ownerId, "target staggered", "true", staggered));
	}
}
