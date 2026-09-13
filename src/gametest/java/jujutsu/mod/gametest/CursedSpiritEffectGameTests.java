package jujutsu.mod.gametest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.ability.effects.AcidZoneRuntime;
import jujutsu.mod.cursedspirit.ability.effects.BerserkEffect;
import jujutsu.mod.cursedspirit.ability.effects.RegenRetreatEffect;
import jujutsu.mod.cursedspirit.ability.effects.RunnerEffect;
import jujutsu.mod.cursedspirit.ability.effects.SlamEffect;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Block 4 effect-coverage scenarios (Phase 4 QA gaps): the five ability effects with no
 * in-world proof (R51 slam/acid/regen/armor/berserk), acid-zone stacking and immunity
 * (R53), regen/armor/berserk behaviour (R56/R57/R58) and runner routing plus the
 * exactly-three action denies (R54).
 *
 * <p>Every scenario pins its pool through {@code forcePoolForTest} and asserts the effect
 * actually started: without the id in the pool the start call refuses, and an unasserted
 * refuse would pass vacuously. Companion to {@code CursedSpiritAbilityGameTests} (dash,
 * pool shape, runner carry) — that class is owned by a sibling fixer and stays untouched.
 */
public final class CursedSpiritEffectGameTests {
	/** R51 slam — the landing detonates: the crater takes it, outside keeps full HP, a
	 * NONE body in the crater takes nothing, a second spirit burns. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void slamDetonationHitsInsideSparesOutside(GameTestHelper helper) {
		String fixture = "slamDetonationHitsInsideSparesOutside";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		// Start outside melee reach; move into the crater in the same callback that
		// starts the slam, so pre-slam AI cannot damage the premise victim.
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(6, 1, 2));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(2, 1, 4));
		// The outsider is a perceiving mage on purpose: a NONE outsider would be spared by
		// the perception gate, and only a mage proves the radius did the sparing.
		ServerPlayer outside = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(6, 1, 6));
		CharacterSelectionManager.select(outside, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritEntity second = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(3, 1, 3));
		CursedSpiritTestFixtures.freezeGround(spirit);
		// A passive damage sponge: NoAI removes its own strikes so the crater oracle cannot
		// be fed by its melee, while hurtServer still lands (AI is not part of that path).
		second.setNoAi(true);
		double mageMax = mage.getMaxHealth();
		double noneMax = none.getMaxHealth();
		double outsideMax = outside.getMaxHealth();
		double secondMax = second.getHealth();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			BlockPos crater = helper.absolutePos(new BlockPos(4, 1, 2));
			mage.teleportTo(level, crater.getX() + 0.5, crater.getY(), crater.getZ() + 0.5,
					Set.of(), 0.0f, 0.0f, false);
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GROUND_SLAM,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GROUND_SLAM, spirit.grade());
			helper.assertTrue(SlamEffect.start(spirit, mage, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "slam started", "true", "false"));
		});
		for (long tick = 2; tick <= 60; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (mage.getHealth() < mageMax) {
						done.set(true);
						// Detonation, not timeout and not melee: the blast force-ends the
						// window, and the attack clip it held all along barred any melee
						// windup before it.
						helper.assertTrue(!spirit.abilityBrain().isActive(
								CursedSpiritAbilityId.GROUND_SLAM, level.getGameTime()),
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"window ended by detonation", "inactive", "active"));
						helper.assertTrue(none.getHealth() == noneMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"NONE in crater unhurt", noneMax, none.getHealth()));
						helper.assertTrue(outside.getHealth() == outsideMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"mage outside radius unhurt", outsideMax,
										outside.getHealth()));
						helper.assertTrue(second.getHealth() < secondMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"second spirit in crater hurt", "<" + secondMax,
										second.getHealth()));
						cleanupBodies(helper, spirit, second, mage, none, outside);
						helper.succeed();
					} else if (poll == 60) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "crater body damaged by tick 60", "<" + mageMax,
								mage.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanupBodies(helper, spirit, second, mage, none, outside);
					throw failure;
				}
			});
		}
	}

	/** R53 — two zones of one owner, one pulse per body per window (stacking does not
	 * happen: see the oracle comment); the creator standing in both keeps full HP, a
	 * foreign spirit burns, a NONE body in both takes nothing. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void acidZonesStackAndSpareCreatorAndNone(GameTestHelper helper) {
		String fixture = "acidZonesStackAndSpareCreatorAndNone";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer overlap = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(3, 1, 2));
		CharacterSelectionManager.select(overlap, JujutsuCharacter.MEGUMI);
		ServerPlayer single = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(1, 1, 2));
		CharacterSelectionManager.select(single, JujutsuCharacter.MEGUMI);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(3, 1, 3));
		CursedSpiritEntity owner = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritEntity foreign = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 1));
		owner.setNoAi(true);
		foreign.setNoAi(true);
		owner.setHealth(owner.getMaxHealth());
		foreign.setHealth(foreign.getMaxHealth());
		double overlapMax = overlap.getMaxHealth();
		double singleMax = single.getMaxHealth();
		double noneMax = none.getMaxHealth();
		double ownerMax = owner.getMaxHealth();
		double foreignMax = foreign.getHealth();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			AcidZoneRuntime.place(level, owner.getUUID(), centerOf(helper, new BlockPos(2, 1, 2)),
					2.0, 400, 2.0f);
			AcidZoneRuntime.place(level, owner.getUUID(), centerOf(helper, new BlockPos(4, 1, 2)),
					2.0, 400, 2.0f);
			helper.assertTrue(AcidZoneRuntime.zoneCountOf(owner.getUUID()) == 2,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"two live zones of one owner", 2,
							AcidZoneRuntime.zoneCountOf(owner.getUUID())));
		});
		for (long tick = 2; tick <= 70; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (single.getHealth() < singleMax) {
						done.set(true);
						// R53: overlapping zones STACK. Each pool pulses its own damage, and the
						// pulse clears the hurt window first — a zone tick is an area effect,
						// not a hit, so vanilla's 20-tick mercy must not swallow the sibling
						// pool (same precedent as the elephant's presence pulse). Compared
						// against the single-zone body at the same moment, so an offset between
						// the two zone clocks cannot make the oracle pass or fail by timing.
						double singleTaken = singleMax - single.getHealth();
						double overlapTaken = overlapMax - overlap.getHealth();
						helper.assertTrue(singleTaken >= 2.0,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"single-zone pulse works", ">= 2.0", singleTaken));
						helper.assertTrue(overlapTaken >= singleTaken + 2.0,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"overlap body takes both pulses (stacking)",
										">= single + 2.0", overlapTaken));
						helper.assertTrue(none.getHealth() == noneMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"NONE in both zones unhurt", noneMax, none.getHealth()));
						helper.assertTrue(owner.getHealth() == ownerMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"creator immune in own zones", ownerMax,
										owner.getHealth()));
						helper.assertTrue(foreign.getHealth() < foreignMax,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"foreign spirit in zone hurt", "<" + foreignMax,
										foreign.getHealth()));
						cleanupAcid(helper, owner, foreign, overlap, single, none);
						helper.succeed();
					} else if (poll == 70) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "zone pulse landed by tick 70", "<" + singleMax,
								single.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanupAcid(helper, owner, foreign, overlap, single, none);
					throw failure;
				}
			});
		}
	}

	/** R56 — the HoT window survives incoming damage: pulses keep landing after a
	 * mid-window hit, and the window itself stays open. */
	@GameTest(maxTicks = 90, skyAccess = true)
	public void regenPulseContinuesUnderIncomingDamage(GameTestHelper helper) {
		String fixture = "regenPulseContinuesUnderIncomingDamage";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		AtomicReference<Double> postDamage = new AtomicReference<>();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.REGEN,
					CursedSpiritAbilityId.DASH, CursedSpiritAbilityId.FEAR));
			spirit.setHealth(spirit.getMaxHealth() - 6.0f);
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.REGEN, spirit.grade());
			helper.assertTrue(RegenRetreatEffect.start(spirit, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "regen started", "true", "false"));
		});
		helper.runAtTickTime(10, () -> {
			boolean accepted = spirit.hurtServer(level, level.damageSources().magic(), 3.0f);
			helper.assertTrue(accepted, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "mid-window hit accepted", "true", "false"));
			postDamage.set((double) spirit.getHealth());
		});
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(spirit.abilityBrain().isActive(CursedSpiritAbilityId.REGEN,
					level.getGameTime()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "window alive after damage", "active", "inactive"));
		});
		helper.runAtTickTime(50, () -> {
			helper.assertTrue(postDamage.get() != null, CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), "damage premise recorded", "non-null", null));
			// Two pulses (every 20 ticks) land between the hit and this tick at every
			// grade, so health must read strictly above the post-damage mark.
			helper.assertTrue(spirit.getHealth() > postDamage.get(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"pulses healed past post-damage hp", ">" + postDamage.get(),
							spirit.getHealth()));
			spirit.discard();
			helper.succeed();
		});
	}

	/** R57 — flat absorption floors at zero: a weak hit is fully eaten (and refused), a
	 * heavy hit passes through partially. */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void armorAbsorbsWeakHitLetsHeavyThrough(GameTestHelper helper) {
		String fixture = "armorAbsorbsWeakHitLetsHeavyThrough";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.ARMOR,
					CursedSpiritAbilityId.DASH, CursedSpiritAbilityId.REGEN));
			spirit.setHealth(spirit.getMaxHealth());
			float full = spirit.getHealth();
			// 1.0 sits below every grade's absorption (2.0/3.0/4.0); 10.0 above all of them.
			boolean weakAccepted = spirit.hurtServer(level, level.damageSources().magic(), 1.0f);
			helper.assertTrue(!weakAccepted, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "weak hit fully absorbed", "false", weakAccepted));
			helper.assertTrue(spirit.getHealth() == full,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"weak hit leaves full hp", full, spirit.getHealth()));
			boolean heavyAccepted = spirit.hurtServer(level, level.damageSources().magic(), 10.0f);
			helper.assertTrue(heavyAccepted, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "heavy hit passes", "true", "false"));
			float drop = full - spirit.getHealth();
			helper.assertTrue(drop > 0.0f && drop < 10.0f,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"heavy drop partial (floored, not negated)", "(0, 10)", drop));
			spirit.discard();
			helper.succeed();
		});
	}

	/** R58 — the latch never opens (a full heal keeps the buffs) and a reload re-arms it
	 * from the persisted flag: both oracles read attributes, never the flag alone. */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void berserkLatchSurvivesHealAndReload(GameTestHelper helper) {
		String fixture = "berserkLatchSurvivesHealAndReload";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.BERSERK,
					CursedSpiritAbilityId.DASH, CursedSpiritAbilityId.REGEN));
			AttributeInstance attack = spirit.getAttribute(Attributes.ATTACK_DAMAGE);
			AttributeInstance speed = spirit.getAttribute(Attributes.MOVEMENT_SPEED);
			helper.assertTrue(attack != null && speed != null,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"combat attributes present", "non-null", "null"));
			double baseAttack = attack.getValue();
			double baseSpeed = speed.getValue();
			spirit.setHealth(spirit.getMaxHealth() * 0.30f);
			helper.assertTrue(spirit.hurtServer(level, level.damageSources().magic(), 1.0f),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"latch wound accepted", "true", "false"));
			helper.assertTrue(spirit.berserkLatched(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"latch closed under threshold", "true", "false"));
			helper.assertTrue(attack.getValue() > baseAttack,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"attack buffed by latch", ">" + baseAttack, attack.getValue()));
			helper.assertTrue(speed.getValue() > baseSpeed,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"speed buffed by latch", ">" + baseSpeed, speed.getValue()));
			// A full heal must not open the latch: the buffs stay above base.
			spirit.setHealth(spirit.getMaxHealth());
			helper.assertTrue(spirit.berserkLatched(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"latch survives heal", "true", "false"));
			helper.assertTrue(attack.getValue() > baseAttack && speed.getValue() > baseSpeed,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"buffs survive heal", "above base", "at base"));
			// A reload drops transient modifiers while the flag persists; re-arming from
			// the flag must bring the attributes back above base.
			attack.removeModifier(BerserkEffect.damageModifierId());
			speed.removeModifier(BerserkEffect.speedModifierId());
			helper.assertTrue(attack.getValue() == baseAttack && speed.getValue() == baseSpeed,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"reload transient loss simulated", "at base", "buffed"));
			BerserkEffect.restoreIfLatched(spirit, spirit.grade());
			helper.assertTrue(attack.getValue() > baseAttack && speed.getValue() > baseSpeed,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"reload restores buffs by attributes", "above base", "at base"));
			spirit.discard();
			helper.succeed();
		});
	}

	/** R54 lava — a lava column on the course turns the run: the yaw kicks the same tick
	 * the probe sees lava. */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void runnerTurnsAwayFromLava(GameTestHelper helper) {
		String fixture = "runnerTurnsAwayFromLava";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		// Yaw 0 faces +Z, so the 3-block probe lands on rel (2, 1, 5).
		BlockPos lavaRel = new BlockPos(2, 1, 5);
		helper.setBlock(lavaRel, Blocks.LAVA);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(5, 1, 2));
		// Perceiving vessel: the grab is control, so a NONE victim refuses the start
		// (same gate as the passing runnerCarriesWithUnbrokenMarker).
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GRAB_RUNNER,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GRAB_RUNNER, spirit.grade());
			helper.assertTrue(RunnerEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "run started", "true", "false"));
			spirit.setYRot(0.0f);
		});
		for (long tick = 2; tick <= 12; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				try {
					// No wander inside this window (steer period 15), so any large kick is
					// the lava turn (120 degrees per unsafe tick).
					double delta = Math.abs(wrapDegrees(spirit.getYRot()));
					if (delta > 60.0) {
						done.set(true);
						helper.assertTrue(RunnerEffect.isRunnerVictim(victim),
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"still carried at the turn", "true", "false"));
						helper.assertTrue(level.getFluidState(helper.absolutePos(lavaRel))
								.is(FluidTags.LAVA), CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "lava premise still holds", "lava", "gone"));
						cleanupRunner(helper, spirit, victim);
						helper.succeed();
					} else if (poll == 12) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "course turned off lava by tick 12", ">60 deg",
								delta));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanupRunner(helper, spirit, victim);
					throw failure;
				}
			});
		}
	}

	/** R54 cliff — a drop deeper than 10 blocks on the course turns the run. */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void runnerTurnsAwayFromCliffDrop(GameTestHelper helper) {
		String fixture = "runnerTurnsAwayFromCliffDrop";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		// A 3x3 ledge at rel y=11; the probe column (2, *, 5) falls to the arena floor,
		// 11 blocks below the feet, while the ledge keeps clear of that column.
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 11, dz), Blocks.STONE);
			}
		}
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(5, 1, 2));
		// Perceiving vessel: the grab is control, so a NONE victim refuses the start.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			BlockPos topAbs = helper.absolutePos(new BlockPos(2, 12, 2));
			spirit.teleportTo(level, topAbs.getX() + 0.5, topAbs.getY(), topAbs.getZ() + 0.5,
					Set.of(), 0.0f, 0.0f, false);
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GRAB_RUNNER,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GRAB_RUNNER, spirit.grade());
			helper.assertTrue(RunnerEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "run started", "true", "false"));
			spirit.setYRot(0.0f);
		});
		for (long tick = 2; tick <= 12; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				try {
					double delta = Math.abs(wrapDegrees(spirit.getYRot()));
					if (delta > 60.0) {
						done.set(true);
						double floorY = helper.absolutePos(new BlockPos(2, 1, 2)).getY();
						helper.assertTrue(spirit.position().y - floorY > 10.0,
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										"still above the drop at the turn", ">10",
										spirit.position().y - floorY));
						cleanupRunner(helper, spirit, victim);
						helper.succeed();
					} else if (poll == 12) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "course turned off the drop by tick 12",
								">60 deg", delta));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanupRunner(helper, spirit, victim);
					throw failure;
				}
			});
		}
	}

	/** R54 holds — a carried victim keeps door/chest/item use: exactly attack, block
	 * breaking and block placement are denied (the "ban was not widened" pin). */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void runnerCarriedVictimKeepsDoorAndChestUse(GameTestHelper helper) {
		String fixture = "runnerCarriedVictimKeepsDoorAndChestUse";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		BlockPos doorRel = new BlockPos(3, 1, 3);
		BlockPos chestRel = new BlockPos(1, 1, 3);
		helper.setBlock(doorRel, Blocks.OAK_DOOR);
		helper.setBlock(chestRel, Blocks.CHEST);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		// Perceiving vessel: the grab is control, so a NONE victim refuses the start.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GRAB_RUNNER,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GRAB_RUNNER, spirit.grade());
			helper.assertTrue(RunnerEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "run started", "true", "false"));
		});
		helper.runAtTickTime(5, () -> {
			helper.assertTrue(RunnerEffect.isRunnerVictim(victim),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"victim carried", "true", "false"));
			helper.assertTrue(victim.hasEffect(JujutsuEffects.GRIPPED),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"GRIPPED held", "true", "false"));
			BlockPos doorAbs = helper.absolutePos(doorRel);
			BlockHitResult doorHit = new BlockHitResult(
					new Vec3(doorAbs.getX() + 0.5, doorAbs.getY() + 0.5, doorAbs.getZ() + 0.5),
					Direction.NORTH, doorAbs, false);
			// Doors open with an empty hand: UseBlock must pass, not fail.
			InteractionResult doorUse = UseBlockCallback.EVENT.invoker().interact(victim, level,
					InteractionHand.MAIN_HAND, doorHit);
			helper.assertTrue(doorUse != InteractionResult.FAIL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"door use allowed while carried", "!=FAIL", doorUse));
			// Chests open while holding a non-block item.
			victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
			helper.assertTrue(!RunnerEffect.isBlockPlacement(victim.getMainHandItem()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"apple is not a placement", "false", "true"));
			BlockPos chestAbs = helper.absolutePos(chestRel);
			BlockHitResult chestHit = new BlockHitResult(
					new Vec3(chestAbs.getX() + 0.5, chestAbs.getY() + 0.5, chestAbs.getZ() + 0.5),
					Direction.NORTH, chestAbs, false);
			InteractionResult chestUse = UseBlockCallback.EVENT.invoker().interact(victim, level,
					InteractionHand.MAIN_HAND, chestHit);
			helper.assertTrue(chestUse != InteractionResult.FAIL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"chest use allowed while carried", "!=FAIL", chestUse));
			// The three denies: placement with a block in hand, attacks, breaking.
			victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
			helper.assertTrue(RunnerEffect.isBlockPlacement(victim.getMainHandItem()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"cobble is a placement", "true", "false"));
			InteractionResult placement = UseBlockCallback.EVENT.invoker().interact(victim, level,
					InteractionHand.MAIN_HAND, doorHit);
			helper.assertTrue(placement == InteractionResult.FAIL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"block placement denied", InteractionResult.FAIL, placement));
			InteractionResult attack = AttackEntityCallback.EVENT.invoker().interact(victim, level,
					InteractionHand.MAIN_HAND, spirit, new EntityHitResult(spirit));
			helper.assertTrue(attack == InteractionResult.FAIL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"entity attack denied", InteractionResult.FAIL, attack));
			InteractionResult breaking = AttackBlockCallback.EVENT.invoker().interact(victim,
					level, InteractionHand.MAIN_HAND, doorAbs, Direction.NORTH);
			helper.assertTrue(breaking == InteractionResult.FAIL,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"block breaking denied", InteractionResult.FAIL, breaking));
			cleanupRunner(helper, spirit, victim);
			helper.succeed();
		});
	}

	private static Vec3 centerOf(GameTestHelper helper, BlockPos relative) {
		BlockPos absolute = helper.absolutePos(relative);
		return new Vec3(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
	}

	private static double wrapDegrees(float yaw) {
		double delta = yaw % 360.0;
		if (delta > 180.0) {
			delta -= 360.0;
		}
		if (delta < -180.0) {
			delta += 360.0;
		}
		return delta;
	}

	private static void cleanupBodies(GameTestHelper helper, CursedSpiritEntity spirit,
			CursedSpiritEntity second, ServerPlayer... victims) {
		spirit.discard();
		second.discard();
		for (ServerPlayer victim : victims) {
			CursedSpiritTestFixtures.cleanupVictim(helper, victim);
		}
	}

	private static void cleanupAcid(GameTestHelper helper, CursedSpiritEntity owner,
			CursedSpiritEntity foreign, ServerPlayer... victims) {
		AcidZoneRuntime.removeZonesOfForTest(owner.getUUID());
		owner.discard();
		foreign.discard();
		for (ServerPlayer victim : victims) {
			CursedSpiritTestFixtures.cleanupVictim(helper, victim);
		}
	}

	private static void cleanupRunner(GameTestHelper helper, CursedSpiritEntity spirit,
			ServerPlayer victim) {
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, victim);
	}
}
