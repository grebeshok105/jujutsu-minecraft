package jujutsu.mod.gametest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritProfile;
import jujutsu.mod.cursedspirit.CursedSpiritTier;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Block 2 in-world scenarios (Step 10): tier AI and strikes (R3/R4/R5), the damage path (R11),
 * stagger (R12), knockback ordering (R13), Boogie Woogie allow/deny (R14/R15), hitbox reach
 * (R24), lethality (R25), and the entity-type tags.
 *
 * <p>Every scenario asserts its premises BEFORE its behaviour (difficulty hostile, victim body
 * valid, line of sight, target acquired) so a red run names the broken assumption. Per-scenario
 * red-proofs live in {@code block-2-report.md}.
 */
public final class CursedSpiritGameTests {
	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final TagKey<EntityType<?>> CURSE_TAG =
			TagKey.create(Registries.ENTITY_TYPE, JujutsuMod.id("resonance_remnant_curse"));
	private static final TagKey<EntityType<?>> IMMUNE_TAG =
			TagKey.create(Registries.ENTITY_TYPE, JujutsuMod.id("boogie_woogie_immune"));

	/**
	 * R3 — a lesser spirit acquires the survival victim ({@code getTarget() == victim} poll) and
	 * its melee strike lands (victim HP drops). First-strike detection: the assert fires on the
	 * first damaged tick, so later knockback drift and regen cannot flake it.
	 */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void lesserAcquiresVictimAndDealsMeleeDamage(GameTestHelper helper) {
		String fixture = "lesserAcquiresVictimAndDealsMeleeDamage";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(4, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		// Issue #80: curses acquire/damage perceivers only — a NONE victim is correctly ignored.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.LESSER_CURSED_SPIRIT, spiritFeet);
		double victimMax = victim.getMaxHealth();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"difficulty hostile", "!= PEACEFUL", level.getDifficulty()));
			helper.assertTrue(victim.getHealth() == victimMax,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"victim starts at full health", victimMax, victim.getHealth()));
			helper.assertTrue(spirit.hasLineOfSight(victim),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"line of sight to victim", "true", spirit.hasLineOfSight(victim)));
		});
		for (long tick = 3; tick <= 120; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (victim.getHealth() < victimMax) {
						helper.assertTrue(spirit.getTarget() == victim,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"lesser acquired the victim", victim.getUUID(),
										spirit.getTarget() == null ? "null" : spirit.getTarget().getUUID()));
						done.set(true);
						spirit.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, victim);
						helper.succeed();
						return;
					}
					if (pollTick == 120) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), null, "lesser damaged the victim by tick 120",
								"hp < " + victimMax, victim.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					throw failure;
				}
			});
		}
	}

	/**
	 * R4 — the common strike carries the profile {@code strikeStep} burst: peak horizontal speed
	 * over the strike window reads at/above 0.10, with victim damage as the strike premise. The
	 * body spawns in reach so it stands (no walk motion); the 0.35 impulse is sampled
	 * post-friction at ~0.19, against a 0.0 standing baseline.
	 */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void commonStrikeCarriesStepBurst(GameTestHelper helper) {
		String fixture = "commonStrikeCarriesStepBurst";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(4, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		// Issue #80: curses acquire/damage perceivers only — a NONE victim is correctly ignored.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.CURSED_SPIRIT, spiritFeet);
		double victimMax = victim.getMaxHealth();
		AtomicReference<Double> maxHorizSpeed = new AtomicReference<>(0.0);
		// The burst must stand out from the body's OWN motion: a flat floor below the walk-speed
		// row (0.24 for COMMON) would pass on walking alone. Baseline = the peak reached before
		// the strike landed; the burst has to add at least 0.10 on top of it.
		AtomicReference<Double> preStrikePeak = new AtomicReference<>(-1.0);
		AtomicBoolean done = new AtomicBoolean();

		for (long tick = 2; tick <= 120; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (spirit.isAlive() && !spirit.isRemoved()) {
						Vec3 velocity = spirit.getDeltaMovement();
						double horiz = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
						maxHorizSpeed.accumulateAndGet(horiz, Math::max);
						if (preStrikePeak.get() < 0.0 && victim.getHealth() < victimMax) {
							// First damage tick: freeze the pre-strike peak as the baseline.
							preStrikePeak.compareAndSet(-1.0, maxHorizSpeed.get());
						}
					}
					if (pollTick == 120) {
						helper.assertTrue(victim.getHealth() < victimMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"strike premise: victim damaged", "hp < " + victimMax, victim.getHealth()));
						double baseline = Math.max(0.0, preStrikePeak.get());
						helper.assertTrue(maxHorizSpeed.get() >= baseline + 0.10,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"the strikeStep burst adds speed above the body's own baseline",
										">= baseline " + baseline + " + 0.10", maxHorizSpeed.get()));
						done.set(true);
						spirit.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, victim);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					throw failure;
				}
			});
		}
	}

	/**
	 * R5 — the greater slam splashes: once the player victim takes the direct strike, the pig 3.0
	 * blocks from the body (inside the 3.5 profile radius) has lost health while the pig 5.0
	 * blocks out has not. The body is Slowness-frozen so the geometry holds.
	 */
	@GameTest(maxTicks = 220, skyAccess = true)
	public void greaterSlamSplashesBodiesInsideRadius(GameTestHelper helper) {
		String fixture = "greaterSlamSplashesBodiesInsideRadius";
		BlockPos spiritFeet = new BlockPos(1, 1, 2);
		BlockPos victimFeet = new BlockPos(2, 1, 2);
		BlockPos pigInFeet = new BlockPos(4, 1, 2);
		BlockPos pigOutFeet = new BlockPos(6, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		// Issue #80: curses acquire/damage perceivers only — a NONE victim is correctly ignored.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, spiritFeet);
		CursedSpiritTestFixtures.freezeGround(spirit);
		Pig pigIn = GameTestFixtures.spawnMob(helper, fixture, EntityType.PIG, pigInFeet);
		Pig pigOut = GameTestFixtures.spawnMob(helper, fixture, EntityType.PIG, pigOutFeet);
		double victimMax = victim.getMaxHealth();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			helper.assertTrue(spirit.hasLineOfSight(victim),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"line of sight to victim", "true", spirit.hasLineOfSight(victim)));
			helper.assertTrue(pigIn.getHealth() == 10.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"inside pig starts whole", "10.0", pigIn.getHealth()));
			helper.assertTrue(pigOut.getHealth() == 10.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"outside pig starts whole", "10.0", pigOut.getHealth()));
			helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"difficulty hostile", "!= PEACEFUL", level.getDifficulty()));
		});
		for (long tick = 3; tick <= 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (victim.getHealth() < victimMax) {
						helper.assertTrue(spirit.getTarget() == victim,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"greater acquired the victim", victim.getUUID(),
										spirit.getTarget() == null ? "null" : spirit.getTarget().getUUID()));
						helper.assertTrue(pigIn.getHealth() < 10.0,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"pig inside 3.5 splashed", "hp < 10.0", pigIn.getHealth()));
						helper.assertTrue(pigOut.getHealth() == 10.0,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"pig outside 3.5 untouched", "10.0", pigOut.getHealth()));
						done.set(true);
						spirit.discard();
						pigIn.discard();
						pigOut.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, victim);
						helper.succeed();
						return;
					}
					if (pollTick == 200) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), null, "greater struck the victim by tick 200",
								"hp < " + victimMax, victim.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					pigIn.discard();
					pigOut.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					throw failure;
				}
			});
		}
	}

	/**
	 * R11 — the normal damage path: two different sources both reduce HP by their full face
	 * amount (second lands after the 10-tick invulnerability window), with an explicit
	 * no-god-mode premise.
	 */
	@GameTest(maxTicks = 50, skyAccess = true)
	public void twoDamageSourcesBothReduceHealth(GameTestHelper helper) {
		String fixture = "twoDamageSourcesBothReduceHealth";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.CURSED_SPIRIT, spiritFeet);

		AtomicReference<Double> maxAtFirstHit = new AtomicReference<>(0.0);
		helper.runAtTickTime(2, () -> {
			// Absolute HP lives on the grade axis (Block 2 bands), not in this scenario: the R11
			// contract is RELATIVE — both sources reduce by their full face amount.
			double max = spirit.getMaxHealth();
			maxAtFirstHit.set(max);
			helper.assertTrue(max > 0.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"spirit starts at positive rolled health", "> 0", spirit.getHealth()));
			helper.assertTrue(spirit.getHealth() == max,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"spirit starts whole", max, spirit.getHealth()));
			helper.assertTrue(!spirit.isInvulnerable(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"no god-mode flag", "false", spirit.isInvulnerable()));
			boolean accepted = spirit.hurtServer(level, level.damageSources().mobAttack(spirit), 5.0f);
			helper.assertTrue(accepted, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					"first source accepted", "true", accepted));
		});
		helper.runAtTickTime(24, () -> {
			boolean accepted = spirit.hurtServer(level, level.damageSources().magic(), 7.0f);
			helper.assertTrue(accepted, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					"second source accepted past invuln window", "true", accepted));
			double expected = maxAtFirstHit.get() - 5.0 - 7.0;
			helper.assertTrue(Math.abs(spirit.getHealth() - expected) < 0.01,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"both sources reduced HP (max - 5 - 7)", expected, spirit.getHealth()));
			spirit.discard();
			helper.succeed();
		});
	}

	/**
	 * R12 — stagger via the production {@link CombatStagger} LivingEntity path (which routes the
	 * profile multiplier): present right after the hit, cleared after the 20-tick window.
	 */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void staggerAppliesThenClearsAfterWindow(GameTestHelper helper) {
		String fixture = "staggerAppliesThenClearsAfterWindow";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.CURSED_SPIRIT, spiritFeet);

		helper.runAtTickTime(2, () -> {
			helper.assertTrue(!CombatStagger.GLOBAL.isStaggered(spirit.getUUID(), level.getGameTime()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"no stagger before the hit", "false", true));
			CombatStagger.GLOBAL.apply(spirit, level.getGameTime(), 20);
			helper.assertTrue(CombatStagger.GLOBAL.isStaggered(spirit.getUUID(), level.getGameTime()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"stagger present after the hit", "true", true));
		});
		helper.runAtTickTime(26, () -> {
			helper.assertTrue(!CombatStagger.GLOBAL.isStaggered(spirit.getUUID(), level.getGameTime()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"stagger cleared after the 20-tick window", "false", true));
			spirit.discard();
			helper.succeed();
		});
	}

	/**
	 * R13 — one identical {@code knockback} impulse per the frozen resistance rows
	 * (0.0 / 0.0 / 0.85): the formula-level response is asserted on the immediate post-impulse
	 * velocity (lesser == common within noise, greater at ~15%), and the integrated
	 * displacement keeps common strictly above greater. Bodies are AI-plus-Slowness (a NoAI
	 * body never integrates velocity, which would make the oracle vacuous).
	 */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void knockbackDisplacementOrdersByTier(GameTestHelper helper) {
		String fixture = "knockbackDisplacementOrdersByTier";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		CursedSpiritEntity lesser = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(1, 1, 1));
		CursedSpiritEntity common = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(1, 1, 3));
		CursedSpiritEntity greater = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, new BlockPos(1, 1, 5));
		CursedSpiritTestFixtures.freezeGround(lesser);
		CursedSpiritTestFixtures.freezeGround(common);
		CursedSpiritTestFixtures.freezeGround(greater);
		AtomicReference<Vec3> lesserStart = new AtomicReference<>();
		AtomicReference<Vec3> commonStart = new AtomicReference<>();
		AtomicReference<Vec3> greaterStart = new AtomicReference<>();

		AtomicReference<Double> lesserKick = new AtomicReference<>(0.0);
		AtomicReference<Double> commonKick = new AtomicReference<>(0.0);
		AtomicReference<Double> greaterKick = new AtomicReference<>(0.0);
		helper.runAtTickTime(2, () -> {
			lesserStart.set(lesser.position());
			commonStart.set(common.position());
			greaterStart.set(greater.position());
			lesser.knockback(1.0, 1.0, 0.0);
			common.knockback(1.0, 1.0, 0.0);
			greater.knockback(1.0, 1.0, 0.0);
			lesserKick.set(horizontalSpeed(lesser));
			commonKick.set(horizontalSpeed(common));
			greaterKick.set(horizontalSpeed(greater));
			helper.assertTrue(Math.abs(lesserKick.get() - commonKick.get()) < 0.05,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null,
							"equal frozen resistance -> equal impulse response",
							"|<" + lesserKick.get() + " - " + commonKick.get() + ">| < 0.05",
							Math.abs(lesserKick.get() - commonKick.get())));
			helper.assertTrue(greaterKick.get() < commonKick.get() * 0.5,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null,
							"0.85 resistance damps the greater kick",
							"< " + commonKick.get() * 0.5, greaterKick.get()));
		});
		helper.runAtTickTime(10, () -> {
			// Vanilla knockback integrates away from the impulse point; the sign is
			// convention, the magnitude is the oracle — compare absolute displacements.
			double lesserDisp = Math.abs(lesser.position().x - lesserStart.get().x);
			double commonDisp = Math.abs(common.position().x - commonStart.get().x);
			double greaterDisp = Math.abs(greater.position().x - greaterStart.get().x);
			helper.assertTrue(lesserDisp > 0, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), null, "lesser moved", "> 0", lesserDisp));
			// Integration noise separates the equal pair by a few percent (observed 1.075 vs
			// 1.125), so the displacement half pins closeness, not order, for that pair.
			helper.assertTrue(Math.abs(lesserDisp - commonDisp) <= 0.15,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null,
							"lesser displacement close to common (equal frozen resistance)",
							"within 0.15 of " + commonDisp, lesserDisp));
			helper.assertTrue(commonDisp > greaterDisp,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null,
							"common displacement > greater (0.85 resistance)",
							"> " + greaterDisp, commonDisp));
			lesser.discard();
			common.discard();
			greater.discard();
			helper.succeed();
		});
	}

	/**
	 * R14 — Boogie Woogie ALLOW: the Todo caster exchanges exact positions with a lesser spirit
	 * and then with a common one through the production executor route (same-tick
	 * capture/cast/assert, so AI drift cannot flake the exchange).
	 */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void lesserAndCommonSwapPositions(GameTestHelper helper) {
		String fixture = "lesserAndCommonSwapPositions";
		BlockPos casterFeet = new BlockPos(1, 1, 1);
		BlockPos lesserFeet = new BlockPos(5, 1, 5);
		BlockPos commonFeet = new BlockPos(5, 1, 1);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, casterFeet, -45.0f, 0.0f);
		CursedSpiritEntity lesser = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, lesserFeet);
		CursedSpiritEntity common = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, commonFeet);
		// Slowness-frozen: the exchange oracles capture pre-cast positions same-tick, but the
		// bodies must not wander between setup and the cast callbacks (aim/LOS/range flake).
		CursedSpiritTestFixtures.freezeGround(lesser);
		CursedSpiritTestFixtures.freezeGround(common);
		AtomicBoolean asserted = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			try {
				TodoSwapTestFixtures.BodyState lesserBefore = TodoSwapTestFixtures.BodyState.capture(lesser);
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				helper.assertTrue(caster.hasLineOfSight(lesser),
						TodoSwapTestFixtures.diagnostic(fixture, "resolve", helper.getTick(),
								caster.getUUID(), lesser.getUUID(), "line of sight to lesser", "true",
								caster.hasLineOfSight(lesser)));
				TodoSwapTestFixtures.aimAt(caster, lesser.position().add(0.0, lesser.getBbHeight() / 2.0, 0.0));
				boolean swapped = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(swapped, TodoSwapTestFixtures.diagnostic(fixture, "commit",
						helper.getTick(), caster.getUUID(), lesser.getUUID(),
						"lesser swap cast result", "true", swapped));
				helper.assertTrue(caster.position().distanceToSqr(lesserBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), lesser.getUUID(), "caster at lesser's pre-cast position",
								lesserBefore.position(), caster.position()));
				helper.assertTrue(lesser.position().distanceToSqr(casterBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), lesser.getUUID(), "lesser at caster's pre-cast position",
								casterBefore.position(), lesser.position()));
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining > 0 && remaining <= TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), lesser.getUUID(), "PRIMARY cooldown started",
								"in (0, " + TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS + "]", remaining));
				asserted.set(true);
			} catch (RuntimeException | AssertionError failure) {
				lesser.discard();
				common.discard();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		helper.runAtTickTime(4, () -> {
			if (!asserted.get()) {
				return;
			}
			try {
				CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
				TodoSwapTestFixtures.BodyState commonBefore = TodoSwapTestFixtures.BodyState.capture(common);
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				helper.assertTrue(caster.hasLineOfSight(common),
						TodoSwapTestFixtures.diagnostic(fixture, "resolve2", helper.getTick(),
								caster.getUUID(), common.getUUID(), "line of sight to common", "true",
								caster.hasLineOfSight(common)));
				helper.assertTrue(caster.distanceTo(common) <= TodoProfile.BOOGIE_WOOGIE_RANGE,
						TodoSwapTestFixtures.diagnostic(fixture, "resolve2", helper.getTick(),
								caster.getUUID(), common.getUUID(), "common in swap range",
								"<= " + TodoProfile.BOOGIE_WOOGIE_RANGE, caster.distanceTo(common)));
				TodoSwapTestFixtures.aimAt(caster, common.position().add(0.0, common.getBbHeight() / 2.0, 0.0));
				boolean swapped = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(swapped, TodoSwapTestFixtures.diagnostic(fixture, "commit2",
						helper.getTick(), caster.getUUID(), common.getUUID(),
						"common swap cast result", "true", swapped));
				helper.assertTrue(caster.position().distanceToSqr(commonBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit2", helper.getTick(),
								caster.getUUID(), common.getUUID(), "caster at common's pre-cast position",
								commonBefore.position(), caster.position()));
				helper.assertTrue(common.position().distanceToSqr(casterBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit2", helper.getTick(),
								caster.getUUID(), common.getUUID(), "common at caster's pre-cast position",
								casterBefore.position(), common.position()));
				lesser.discard();
				common.discard();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				lesser.discard();
				common.discard();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
	}

	/**
	 * R15 — Boogie Woogie DENY: the greater spirit refuses atomically — {@code routed:false},
	 * nobody moved field-for-field, no cooldown.
	 */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void greaterRefusesSwapAtomically(GameTestHelper helper) {
		String fixture = "greaterRefusesSwapAtomically";
		BlockPos casterFeet = new BlockPos(1, 1, 1);
		BlockPos greaterFeet = new BlockPos(5, 1, 5);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, casterFeet, -45.0f, 0.0f);
		CursedSpiritEntity greater = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, greaterFeet);

		helper.runAtTickTime(2, () -> {
			try {
				TodoSwapTestFixtures.BodyState greaterBefore = TodoSwapTestFixtures.BodyState.capture(greater);
				TodoSwapTestFixtures.aimAt(caster, greater.position().add(0.0, greater.getBbHeight() / 2.0, 0.0));
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				boolean cast = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(!cast, TodoSwapTestFixtures.diagnostic(fixture, "deny",
						helper.getTick(), caster.getUUID(), greater.getUUID(),
						"greater swap cast result", "false", cast));
				TodoSwapTestFixtures.assertBodyState(helper, fixture, "deny", "caster", caster, casterBefore);
				TodoSwapTestFixtures.assertBodyState(helper, fixture, "deny", "greater", greater, greaterBefore);
				TodoSwapTestFixtures.assertNoPrimaryCharge(helper, fixture, "deny", caster);
				greater.discard();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				greater.discard();
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
	}

	/**
	 * R24 — melee reach respects the hitbox edge: with a Slowness-frozen common body (reach 2.5 +
	 * halves = 3.325 centre boundary), the player 3.0 out takes the strike while the player 3.7
	 * out is untouched. First-strike detection keeps later knockback drift out of the oracle.
	 */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void meleeReachRespectsHitboxEdge(GameTestHelper helper) {
		String fixture = "meleeReachRespectsHitboxEdge";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		BlockPos nearFeet = new BlockPos(5, 1, 2);
		BlockPos farFeet = new BlockPos(5, 1, 5);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerPlayer near = CursedSpiritTestFixtures.setupVictim(helper, fixture, nearFeet);
		ServerPlayer far = CursedSpiritTestFixtures.setupVictim(helper, fixture, farFeet);
		// Issue #80: the far oracle must prove REACH, not perception — a NONE far victim would
		// stay untouched trivially. Both victims perceive; only geometry spares the far one.
		CharacterSelectionManager.select(near, JujutsuCharacter.MEGUMI);
		CharacterSelectionManager.select(far, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.CURSED_SPIRIT, spiritFeet);
		CursedSpiritTestFixtures.freezeGround(spirit);
		double nearMax = near.getMaxHealth();
		double farMax = far.getMaxHealth();
		AtomicBoolean done = new AtomicBoolean();

		// Acquisition premise: the target-goal scan interval makes any single early tick racy,
		// so poll to tick 30 (the strike oracle below is unchanged). Cleanup on failure: two
		// victims must not linger for the neighbour arenas. The message names removal state
		// (both victims plus the spirit) so a null target self-explains (unscanned vs body gone).
		AtomicBoolean acquired = new AtomicBoolean();
		java.util.concurrent.atomic.AtomicReference<String> firstSeen = new java.util.concurrent.atomic.AtomicReference<>("none");
		java.util.concurrent.atomic.AtomicLong firstSeenTick = new java.util.concurrent.atomic.AtomicLong(-1);
		for (long tick = 8; tick <= 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (spirit.getTarget() == near) {
					acquired.set(true);
				}
				if (spirit.getTarget() != null && "none".equals(firstSeen.get())) {
					firstSeen.set(spirit.getTarget().getUUID().toString());
					firstSeenTick.set(pollTick);
				}
				if (pollTick == 60) {
					try {
						helper.assertTrue(acquired.get(),
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"spirit acquired the nearer victim by tick 60 [nearRemoved="
												+ near.isRemoved() + " farRemoved=" + far.isRemoved()
												+ " spiritRemoved=" + spirit.isRemoved()
												+ " firstSeen=" + firstSeen.get() + "@" + firstSeenTick.get()
												+ " dist=" + spirit.distanceTo(near)
												+ " los=" + spirit.hasLineOfSight(near)
												+ " alive=" + near.isAlive() + "/" + far.isAlive()
												+ " canAttack=" + spirit.canAttack(near)
												+ " noAi=" + spirit.isNoAi()
												+ " follow=" + spirit.getAttributeValue(
														net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
												+ " players=" + helper.getLevel().players().size() + "]",
										near.getUUID(),
										spirit.getTarget() == null ? "null" : spirit.getTarget().getUUID()));
					} catch (RuntimeException | AssertionError failure) {
						spirit.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, near);
						CursedSpiritTestFixtures.cleanupVictim(helper, far);
						throw failure;
					}
				}
			});
		}
		for (long tick = 9; tick <= 120; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (near.getHealth() < nearMax) {
						helper.assertTrue(far.getHealth() == farMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"victim past the hitbox edge untouched", farMax, far.getHealth()));
						done.set(true);
						spirit.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, near);
						CursedSpiritTestFixtures.cleanupVictim(helper, far);
						helper.succeed();
						return;
					}
					if (pollTick == 120) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), null, "near victim struck by tick 120",
								"hp < " + nearMax, near.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, near);
					CursedSpiritTestFixtures.cleanupVictim(helper, far);
					throw failure;
				}
			});
		}
	}

	/**
	 * R25 — lethal {@code hurtServer} kills and the removal flushes the lookup (vanilla death
	 * removal lands at deathTime 20, so the absence oracles sit past it).
	 */
	@GameTest(maxTicks = 50, skyAccess = true)
	public void lethalDamageRemovesBody(GameTestHelper helper) {
		String fixture = "lethalDamageRemovesBody";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit =
				CursedSpiritTestFixtures.spawnSpirit(helper, fixture, JujutsuEntities.CURSED_SPIRIT, spiritFeet);

		helper.runAtTickTime(2, () -> {
			spirit.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertTrue(!spirit.isAlive(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), null, "lethal hit kills", "alive=false", spirit.isAlive()));
		});
		helper.runAtTickTime(26, () -> helper.assertTrue(spirit.isRemoved(),
				CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null,
						"dead body removed", "isRemoved=true", spirit.isRemoved())));
		helper.runAtTickTime(36, () -> {
			helper.assertEntityNotPresent(JujutsuEntities.CURSED_SPIRIT, spiritFeet);
			helper.succeed();
		});
	}

	/**
	 * Tags in-world plus the variant sync: every tier's type is in the curse-remnant tag, only
	 * the greater type is Boogie Woogie immune, and {@code setVariant} sticks (with tier-valid
	 * defaults straight from the spawn).
	 */
	@GameTest(maxTicks = 30, skyAccess = true)
	public void entityTypeTagsAndVariantSync(GameTestHelper helper) {
		String fixture = "entityTypeTagsAndVariantSync";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);

		CursedSpiritEntity lesser = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(1, 1, 1));
		CursedSpiritEntity common = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(1, 1, 3));
		CursedSpiritEntity greater = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, new BlockPos(1, 1, 5));

		helper.runAtTickTime(2, () -> {
			try {
				helper.assertTrue(lesser.getType().is(CURSE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"lesser in curse tag", "true", lesser.getType().is(CURSE_TAG)));
				helper.assertTrue(common.getType().is(CURSE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"common in curse tag", "true", common.getType().is(CURSE_TAG)));
				helper.assertTrue(greater.getType().is(CURSE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"greater in curse tag", "true", greater.getType().is(CURSE_TAG)));
				helper.assertTrue(!lesser.getType().is(IMMUNE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"lesser swappable", "false", lesser.getType().is(IMMUNE_TAG)));
				helper.assertTrue(!common.getType().is(IMMUNE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"common swappable", "false", common.getType().is(IMMUNE_TAG)));
				helper.assertTrue(greater.getType().is(IMMUNE_TAG),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"greater immune", "true", greater.getType().is(IMMUNE_TAG)));
				helper.assertTrue(lesser.variant().tier() == CursedSpiritTier.LESSER,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"lesser default variant in tier", CursedSpiritTier.LESSER, lesser.variant()));
				helper.assertTrue(common.variant().tier() == CursedSpiritTier.COMMON,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"common default variant in tier", CursedSpiritTier.COMMON, common.variant()));
				helper.assertTrue(greater.variant().tier() == CursedSpiritTier.GREATER,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"greater default variant in tier", CursedSpiritTier.GREATER, greater.variant()));
				greater.setVariant(CursedSpiritVariant.WISTIVER);
				helper.assertTrue(greater.variant() == CursedSpiritVariant.WISTIVER,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"setVariant sticks", CursedSpiritVariant.WISTIVER, greater.variant()));
				helper.assertTrue(greater.variant().deathSound() != null,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"variant death voice resolves", "non-null", null));
				lesser.discard();
				common.discard();
				greater.discard();
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				lesser.discard();
				common.discard();
				greater.discard();
				throw failure;
			}
		});
	}

	/** Profile sanity visible from the world side: the registered follow ranges are tier-ordered. */
	@GameTest(maxTicks = 20, skyAccess = true)
	public void profileFollowRangesOrderByTier(GameTestHelper helper) {
		String fixture = "profileFollowRangesOrderByTier";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		helper.runAtTickTime(2, () -> {
			double lesser = CursedSpiritProfile.of(CursedSpiritTier.LESSER).followRange();
			double common = CursedSpiritProfile.of(CursedSpiritTier.COMMON).followRange();
			double greater = CursedSpiritProfile.of(CursedSpiritTier.GREATER).followRange();
			helper.assertTrue(lesser < common && common < greater,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"follow ranges tier-ordered", "16 < 24 < 32",
							lesser + " < " + common + " < " + greater));
			helper.assertTrue(lesser == 16.0 && common == 24.0 && greater == 32.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"follow ranges pin the profile rows", "16/24/32",
							lesser + "/" + common + "/" + greater));
			helper.succeed();
		});
	}

	/** Horizontal speed of a body, for the knockback impulse-response oracle. */
	private static double horizontalSpeed(LivingEntity body) {
		Vec3 velocity = body.getDeltaMovement();
		return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
	}
	/**
	 * Issue #85 — a whiffed swing must not cancel the slam. The tracked victim is yanked out of
	 * reach the moment the windup opens, and a pig parked inside the 3.5 profile radius must still
	 * take the shockwave. Before the fix the strike-time reach re-check returned before the AoE
	 * block, so the whole swing dealt nothing: the "empty attacks" Walking Bed report. Since #99
	 * the out-of-reach victim no longer walks free inside the crater: it takes exactly the AoE
	 * shockwave hit, never the direct strike — the oracle below pins the delta to the aoeDamage
	 * row, which a direct hit (primaryDamage, unscaled) would overshoot.
	 */
	@GameTest(maxTicks = 220, skyAccess = true)
	public void greaterSlamStillLandsWhenTheTrackedVictimLeavesReach(GameTestHelper helper) {
		String fixture = "greaterSlamStillLandsWhenTheTrackedVictimLeavesReach";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(2, 1, 3);
		BlockPos pigFeet = new BlockPos(4, 1, 2);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		// Issue #80: the windup pull triggers on the TRACKED victim — a NONE body is never
		// acquired, so the slam oracle would wait forever. The victim perceives; the pig is a
		// mob and always does.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, spiritFeet);
		spirit.setVariant(CursedSpiritVariant.WALKING_BED);
		Pig bystander = GameTestFixtures.spawnMob(helper, fixture, EntityType.PIG, pigFeet);
		CursedSpiritTestFixtures.freezeGround(bystander);
		double victimMax = victim.getMaxHealth();
		AtomicBoolean pulled = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			helper.assertTrue(spirit.hasLineOfSight(victim),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"line of sight to victim", "true", spirit.hasLineOfSight(victim)));
			helper.assertTrue(bystander.getHealth() == 10.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"bystander starts whole", "10.0", bystander.getHealth()));
		});
		for (long tick = 3; tick <= 210; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (!pulled.get() && spirit.attackAnimationState.isStarted()
							&& spirit.getTarget() == victim) {
						// The windup just opened: take the victim out of reach so the strike-time
						// re-check sees a whiff. The clip runs on to the slam either way. The pull
						// stays INSIDE the 6x6 pad — off the pad the victim drops out of the arena
						// and the swing would stop instead of whiffing.
						Vec3 away = helper.absolutePos(new BlockPos(6, 1, 6)).getCenter();
						victim.teleportTo(away.x, away.y, away.z);
						pulled.set(true);
						return;
					}
					if (pulled.get() && bystander.getHealth() < 10.0) {
						// #99: inside the crater but past direct reach the victim takes the AoE
						// hit exactly once — the same damage the bystander took — and never the
						// full direct strike.
						float expectedAoe = CursedSpiritAttackPolicy.aoeDamage(
								spirit.gradeStats(), CursedSpiritProfile.of(spirit.tier()));
						helper.assertTrue(Math.abs(victim.getHealth() - (victimMax - expectedAoe)) < 0.001,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"out-of-reach victim takes only the AoE hit, not the direct strike",
										String.valueOf(victimMax - expectedAoe),
										victim.getHealth()));
						done.set(true);
						spirit.discard();
						bystander.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, victim);
						helper.succeed();
						return;
					}
					if (pollTick == 210) {
						Vec3 spiritPos = spirit.position();
						Vec3 victimPos = victim.position();
						Vec3 pigPos = bystander.position();
						helper.assertTrue(false, net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT,
								"whiff slam forensics: pulled=%s anim=%s target=%s spirit=(%.2f,%.2f,%.2f) "
										+ "victim=(%.2f,%.2f,%.2f) dist=%.2f victimHp=%.1f/%.1f pig=(%.2f,%.2f,%.2f) "
										+ "pigDist=%.2f pigHp=%.1f spiritAlive=%s",
								pulled.get(), spirit.attackAnimationState.isStarted(),
								spirit.getTarget() == victim ? "victim" : String.valueOf(spirit.getTarget()),
								spiritPos.x, spiritPos.y, spiritPos.z,
								victimPos.x, victimPos.y, victimPos.z, spirit.distanceTo(victim),
								victim.getHealth(), victimMax,
								pigPos.x, pigPos.y, pigPos.z,
								Math.sqrt(pigPos.distanceToSqr(spiritPos)), bystander.getHealth(),
								spirit.isAlive())));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					bystander.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					throw failure;
				}
			});
		}
	}

	/**
	 * Issue #99 — the slam dead zone. The tracked victim is yanked mid-windup into the ring
	 * PAST the hitbox-edge reach boundary (3.0 + halves 0.675 + 0.3 = 3.975 centre) but still
	 * inside the crater the splash collects (mob box inflated by 3.5, edge reach ~4.175 from
	 * centre). Pull spot (5,1,5) sits at ~4.24 centre distance: before the fix the victim took
	 * zero damage there — out of direct reach AND excluded from the AoE by strikeTargets.
	 * After the fix the shockwave hit lands. The in-reach double-dip is impossible by
	 * construction: the directHit flag gates the fallback AND vanilla's 10-tick invulnerability
	 * window would swallow a same-tick second hurt regardless.
	 */
	@GameTest(maxTicks = 220, skyAccess = true)
	public void greaterSlamHitsVictimInCraterRingPastReach(GameTestHelper helper) {
		String fixture = "greaterSlamHitsVictimInCraterRingPastReach";
		BlockPos spiritFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(2, 1, 3);
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		// Perceiving victim (issue #80): a NONE body is never acquired, so the slam never runs.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, spiritFeet);
		double victimMax = victim.getMaxHealth();
		AtomicBoolean pulled = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			helper.assertTrue(spirit.hasLineOfSight(victim),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"line of sight to victim", "true", spirit.hasLineOfSight(victim)));
		});
		for (long tick = 3; tick <= 210; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (!pulled.get() && spirit.attackAnimationState.isStarted()
							&& spirit.getTarget() == victim) {
						// The windup just opened: drop the victim into the dead-zone ring —
						// outside direct reach, inside the crater the splash collects. The pull
						// stays inside the pad so the victim remains a valid target.
						Vec3 ring = helper.absolutePos(new BlockPos(5, 1, 5)).getCenter();
						victim.teleportTo(ring.x, ring.y, ring.z);
						pulled.set(true);
						return;
					}
					if (pulled.get() && victim.getHealth() < victimMax) {
						done.set(true);
						spirit.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, victim);
						helper.succeed();
						return;
					}
					if (pollTick == 210) {
						Vec3 spiritPos = spirit.position();
						Vec3 victimPos = victim.position();
						helper.assertTrue(false, net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT,
								"dead-zone forensics: pulled=%s anim=%s target=%s spirit=(%.2f,%.2f,%.2f) "
										+ "victim=(%.2f,%.2f,%.2f) dist=%.2f victimHp=%.1f/%.1f spiritAlive=%s",
								pulled.get(), spirit.attackAnimationState.isStarted(),
								spirit.getTarget() == victim ? "victim" : String.valueOf(spirit.getTarget()),
								spiritPos.x, spiritPos.y, spiritPos.z,
								victimPos.x, victimPos.y, victimPos.z, spirit.distanceTo(victim),
								victim.getHealth(), victimMax, spirit.isAlive())));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					spirit.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					throw failure;
				}
			});
		}
	}
}
