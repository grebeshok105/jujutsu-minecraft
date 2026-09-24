package jujutsu.mod.gametest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.ability.effects.DashEffect;
import jujutsu.mod.cursedspirit.ability.effects.RunnerEffect;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Block 3 in-world ability scenarios (short form): pool shape (R44), dash hit/miss
 * (R52), runner carry with an unbroken GRIPPED marker (R54).
 *
 * <p>Every behaviour scenario pins its pool through
 * {@code forcePoolForTest} and asserts the effect actually started: without the id in
 * the pool the start call refuses, and an unasserted refuse would pass vacuously.
 */
public final class CursedSpiritAbilityGameTests {
	/** R44 — a newborn pool is exactly three distinct ids. */
	@GameTest(maxTicks = 20, skyAccess = true)
	public void poolHasThreeDistinctAbilities(GameTestHelper helper) {
		String fixture = "poolHasThreeDistinctAbilities";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			List<CursedSpiritAbilityId> pool = spirit.abilityBrain().pool();
			helper.assertTrue(pool.size() == 3,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"pool size 3", 3, pool.size()));
			helper.assertTrue(Set.copyOf(pool).size() == 3,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"pool distinct", "3 distinct", pool));
			spirit.discard();
			helper.succeed();
		});
	}

	/** R52 hit — a stationary victim in the dash line takes damage. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void dashHitsStationaryTarget(GameTestHelper helper) {
		String fixture = "dashHitsStationaryTarget";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		// Perceiving vessel: without it mayTouch gates the dash off and the hit premise dies.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		double victimMax = victim.getMaxHealth();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.DASH,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.DASH, spirit.grade());
			helper.assertTrue(DashEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "dash started", "true", "false"));
		});
		for (long tick = 2; tick <= 60; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (victim.getHealth() < victimMax) {
						done.set(true);
						cleanup(helper, spirit, victim);
						helper.succeed();
					} else if (poll == 60) {
						helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
								helper.getTick(), "stationary victim damaged by tick 60",
								"hp < " + victimMax, victim.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanup(helper, spirit, victim);
					throw failure;
				}
			});
		}
	}

	/** R52 miss — a victim that leaves the frozen line takes nothing. Red-proof: re-aiming
	 * the dash mid-flight would hit and turn this red. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void dashMissesDodgedTarget(GameTestHelper helper) {
		String fixture = "dashMissesDodgedTarget";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		// Perceiving vessel: without it the gate (not the dodge) voids the damage.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		double victimMax = victim.getMaxHealth();
		AtomicReference<Vec3> dashFrom = new AtomicReference<>();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.DASH,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.DASH, spirit.grade());
			helper.assertTrue(DashEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "dash started", "true", "false"));
			dashFrom.set(spirit.position());
			// Same-tick sidestep: the dash commits to the victim's old line while the victim
			// is already gone — a homing dash would still connect, a fixed course misses.
			// (A tick-2 dodge arrives after the 0.9-b/tick dash already covers the 2-block
			// gap, so the old oracle timed out instead of dodging.)
			BlockPos escape = helper.absolutePos(new BlockPos(4, 1, 5));
			victim.teleportTo(level, escape.getX() + 0.5, escape.getY(), escape.getZ() + 0.5,
					Set.of(), 0.0f, 0.0f, false);
		});
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(victim.getHealth() == victimMax,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"dodged victim undamaged", victimMax, victim.getHealth()));
			helper.assertTrue(spirit.position().distanceTo(dashFrom.get()) > 3.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"dash travelled (miss is not a refused start)", "> 3.0",
							spirit.position().distanceTo(dashFrom.get())));
			cleanup(helper, spirit, victim);
			helper.succeed();
		});
	}

	/** R54 — the run approaches before it commits, then holds GRIPPED every carry tick,
	 * and ends on its window. */
	@GameTest(maxTicks = 130, skyAccess = true)
	public void runnerCarriesWithUnbrokenMarker(GameTestHelper helper) {
		String fixture = "runnerCarriesWithUnbrokenMarker";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		// Perceiving vessel: the grab is control, so a NONE victim refuses the start.
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		AtomicBoolean gap = new AtomicBoolean();
		AtomicBoolean carrying = new AtomicBoolean();
		AtomicReference<Vec3> startPos = new AtomicReference<>();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GRAB_RUNNER,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			startPos.set(victim.position());
			victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GRAB_RUNNER, spirit.grade());
			helper.assertTrue(RunnerEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "run started", "true", "false"));
		});
		for (long tick = 2; tick <= 85; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (RunnerEffect.phaseOf(spirit) == RunnerEffect.Phase.CARRY) {
					carrying.set(true);
					if (!victim.hasEffect(JujutsuEffects.GRIPPED)) {
						gap.set(true);
					}
					// Issue #119: the carry is a passenger seat, not a pin — the victim must
					// ride the spirit for the whole CARRY phase.
					if (victim.getVehicle() != spirit) {
						gap.set(true);
					}
				}
			});
		}
		helper.runAtTickTime(90, () -> {
			helper.assertTrue(carrying.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "runner reached CARRY", "true", "false"));
			helper.assertTrue(!gap.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "GRIPPED every carry tick", "no gap", "gap"));
			helper.assertTrue(victim.getMainHandItem().isEmpty() && victim.getOffhandItem().isEmpty(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"both hands dropped", "empty", "held"));
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
					new AABB(startPos.get(), startPos.get()).inflate(8.0));
			helper.assertTrue(drops.size() == 2, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "two drops near run start", 2, drops.size()));
			helper.assertTrue(!spirit.abilityBrain().isActive(CursedSpiritAbilityId.GRAB_RUNNER,
					level.getGameTime()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "run ended by tick 90", "inactive", "active"));
			// P0: window expiry routes through end(), so the carry set is empty again —
			// a stuck UUID would deny the victim attacks/breaks/placements until restart.
			helper.assertTrue(RunnerEffect.carriedCount() == 0, CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), "carry set empty after window", 0,
					RunnerEffect.carriedCount()));
			helper.assertTrue(!RunnerEffect.isRunnerVictim(victim), CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), "victim released after window", "false",
					RunnerEffect.isRunnerVictim(victim)));
			// Issue #119: release must leave no passenger relation behind.
			helper.assertTrue(victim.getVehicle() == null, CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), "victim dismounted after window", "no vehicle",
					victim.getVehicle()));
			cleanup(helper, spirit, victim);
			helper.succeed();
		});
	}

	/** Issue #119 — the carry is a passenger attachment: the victim rides the spirit at the
	 * hand anchor, a sneak press cannot dismount them mid-carry, and release clears the seat. */
	@GameTest(maxTicks = 130, skyAccess = true)
	public void runnerCarryMountsVictimAndBlocksDismount(GameTestHelper helper) {
		String fixture = "runnerCarryMountsVictimAndBlocksDismount";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		AtomicBoolean carrying = new AtomicBoolean();
		AtomicBoolean seated = new AtomicBoolean();
		AtomicBoolean nearAnchor = new AtomicBoolean();
		AtomicBoolean sneakHeld = new AtomicBoolean();
		AtomicBoolean escaped = new AtomicBoolean();
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
		for (long tick = 2; tick <= 85; tick++) {
			helper.runAtTickTime(tick, () -> {
				// Sneak must not free the victim: Player.rideTick honours wantsToStopRiding,
				// and the mixin keeps it false while the carry owns the seat. The check runs
				// outside the phase gate and keys on isRunnerVictim — a dismount clears the
				// phase before the next callback, so a phase-gated check can miss it.
				if (sneakHeld.get() && RunnerEffect.isRunnerVictim(victim)
						&& victim.getVehicle() != spirit) {
					escaped.set(true);
				}
				if (RunnerEffect.phaseOf(spirit) != RunnerEffect.Phase.CARRY) {
					return;
				}
				carrying.set(true);
				if (victim.getVehicle() == spirit) {
					seated.set(true);
				}
				// Once the pull-in window has passed the seat must sit on the hand anchor —
				// the clamped position, not a teleport trail.
				if (victim.getVehicle() == spirit
						&& victim.position().distanceTo(RunnerEffect.carryAnchorFor(spirit)) < 1.0) {
					nearAnchor.set(true);
				}
				victim.setShiftKeyDown(true);
				sneakHeld.set(true);
			});
		}
		helper.runAtTickTime(90, () -> {
			helper.assertTrue(carrying.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "runner reached CARRY", "true", "false"));
			helper.assertTrue(seated.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "victim rode the spirit during CARRY", "mounted",
					"never mounted"));
			helper.assertTrue(nearAnchor.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "victim reached the hand anchor", "< 1.0", "never close"));
			helper.assertTrue(sneakHeld.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "sneak was held during CARRY", "true", "false"));
			helper.assertTrue(!escaped.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "sneak never dismounted a carried victim", "false",
					"escaped the seat"));
			helper.assertTrue(victim.getVehicle() == null, CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), "seat cleared after release", "no vehicle",
					victim.getVehicle()));
			helper.assertTrue(!victim.hasEffect(JujutsuEffects.GRIPPED),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"GRIPPED lifted after release", "false", "still gripped"));
			cleanup(helper, spirit, victim);
			helper.succeed();
		});
	}

	/** R12 — an eight-block target is an approach intent, never a distant grab. Moving the victim
	 * away on CONTACT must abort without CARRIED, GRIPPED, or a teleport. */
	@GameTest(maxTicks = 140, skyAccess = true)
	public void runnerMustApproachBeforeGrab(GameTestHelper helper) {
		String fixture = "runnerMustApproachBeforeGrab";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		// The approach corridor runs z=2→14, past layStoneFloor's 1..6 pad: a shifted world
		// offset can drop the spirit one block below the victim's ledge, where MoveControl
		// stalls without a collision to jump against (observed: spirit parked at y−1, dist 3.04).
		for (int dx = 0; dx <= 8; dx++) {
			for (int dz = 0; dz <= 15; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
				for (int dy = 1; dy <= 3; dy++) {
					helper.setBlock(new BlockPos(dx, dy, dz), Blocks.AIR);
				}
			}
		}
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(2, 1, 10));
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		AtomicBoolean movedAtContact = new AtomicBoolean();
		AtomicBoolean observedApproach = new AtomicBoolean();
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.GRAB_RUNNER,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(
					CursedSpiritAbilityId.GRAB_RUNNER, spirit.grade());
			helper.assertTrue(RunnerEffect.start(spirit, victim, level.getGameTime(), params,
					spirit.abilityBrain()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "approach intent started", "true", "false"));
			helper.assertTrue(!RunnerEffect.isRunnerVictim(victim)
					&& !victim.hasEffect(JujutsuEffects.GRIPPED),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"far target is not carried at start", "false/false",
							RunnerEffect.isRunnerVictim(victim) + "/" + victim.hasEffect(JujutsuEffects.GRIPPED)));
		});
		for (long tick = 2; tick <= 130; tick++) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (RunnerEffect.phaseOf(spirit) == RunnerEffect.Phase.APPROACH) {
					observedApproach.set(true);
					helper.assertTrue(!RunnerEffect.isRunnerVictim(victim)
							&& !victim.hasEffect(JujutsuEffects.GRIPPED),
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
									"approach has no held marker", "false/false",
									RunnerEffect.isRunnerVictim(victim) + "/"
											+ victim.hasEffect(JujutsuEffects.GRIPPED)));
				} else if ((RunnerEffect.phaseOf(spirit) == RunnerEffect.Phase.WINDUP
						|| RunnerEffect.phaseOf(spirit) == RunnerEffect.Phase.CONTACT)
						&& movedAtContact.compareAndSet(false, true)) {
					// CONTACT lasts one tick and can be consumed between polls; WINDUP is the
					// two-tick telegraph, so pulling the victim there still lands the miss on
					// the CONTACT reach+LOS re-check.
					BlockPos missPosition = helper.absolutePos(new BlockPos(2, 1, 14));
					victim.teleportTo(level, missPosition.getX() + 0.5, missPosition.getY(),
							missPosition.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
				} else if (movedAtContact.get()
						&& !spirit.abilityBrain().isActive(CursedSpiritAbilityId.GRAB_RUNNER,
								level.getGameTime())) {
					helper.assertTrue(observedApproach.get(),
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
									"runner observed approach phase", "true", "false"));
					helper.assertTrue(!RunnerEffect.isRunnerVictim(victim)
							&& !victim.hasEffect(JujutsuEffects.GRIPPED),
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
									"contact miss aborts cleanly", "false/false",
									RunnerEffect.isRunnerVictim(victim) + "/"
											+ victim.hasEffect(JujutsuEffects.GRIPPED)));
					cleanup(helper, spirit, victim);
					helper.succeed();
				} else if (poll == 130) {
					helper.assertTrue(false, CursedSpiritTestFixtures.diagnostic(fixture,
							helper.getTick(), "contact miss observed by tick 130",
									"abort", "phase=" + RunnerEffect.phaseOf(spirit)
											+ " active=" + spirit.abilityBrain().isActive(
													CursedSpiritAbilityId.GRAB_RUNNER, level.getGameTime())
											+ " dist=" + spirit.distanceTo(victim)
											+ " spiritPos=" + spirit.position()
											+ " victimPos=" + victim.position()
											+ " moved=" + movedAtContact.get()
											+ " approachSeen=" + observedApproach.get()
											+ " navDone=" + spirit.getNavigation().isDone()
											+ " navPath=" + spirit.getNavigation().getPath()
											+ " target=" + spirit.getTarget()
											+ " noAi=" + spirit.isNoAi()
											+ " hColl=" + spirit.horizontalCollision
											+ " victimAlive=" + victim.isAlive()
											+ " victimDisc=" + victim.hasDisconnected()
											+ " mayTouch=" + jujutsu.mod.cursedspirit.perception.CursePerception.mayTouch(spirit, victim)
											+ " inLevel=" + (level.getEntity(victim.getUUID()) != null)
											+ " moveCtrlOp=" + spirit.getMoveControl()));
				}
			});
		}
	}

	/** Fear cast range — a victim beyond the profile radius never catches
	 * {@code CURSED_FEAR}, while the same pair in range does (the gate discriminates,
	 * so the negative assert cannot pass vacuously). */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void fearBeyondCastRangeCatchesNothing(GameTestHelper helper) {
		String fixture = "fearBeyondCastRangeCatchesNothing";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(4, 1, 2));
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		helper.runAtTickTime(1, () -> {
			spirit.gradeStats();
			spirit.rollAbilityPool();
			spirit.abilityBrain().forcePoolForTest(List.of(CursedSpiritAbilityId.FEAR,
					CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR));
			double castRange = CursedSpiritAbilityProfile
					.of(CursedSpiritAbilityId.FEAR, spirit.grade()).radius();
			Vec3 hidden = spirit.position().add(castRange + 3.0, 0.0, 0.0);
			victim.teleportTo(level, hidden.x, hidden.y, hidden.z, Set.of(), 0.0f, 0.0f, false);
			helper.assertTrue(spirit.distanceTo(victim) > castRange,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
							"victim beyond cast range", ">" + castRange,
							spirit.distanceTo(victim)));
			spirit.abilityBrain().decideInCombat(spirit, spirit.grade(), victim,
					level.getGameTime());
			helper.assertTrue(!spirit.abilityBrain().isActive(CursedSpiritAbilityId.FEAR,
					level.getGameTime()), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), "no fear window beyond range", "inactive", "active"));
		});
		helper.runAtTickTime(5, () -> {
			try {
				helper.assertTrue(!victim.hasEffect(JujutsuEffects.CURSED_FEAR),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"no fear debuff beyond range", "absent", "present"));
				Vec3 close = spirit.position().add(3.0, 0.0, 0.0);
				victim.teleportTo(level, close.x, close.y, close.z, Set.of(), 0.0f, 0.0f,
						false);
				spirit.abilityBrain().decideInCombat(spirit, spirit.grade(), victim,
						level.getGameTime());
				helper.assertTrue(victim.hasEffect(JujutsuEffects.CURSED_FEAR),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								"fear lands in range", "present", "absent"));
			} finally {
				cleanup(helper, spirit, victim);
			}
			helper.succeed();
		});
	}

	private static void cleanup(GameTestHelper helper, CursedSpiritEntity spirit,
			ServerPlayer victim) {
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, victim);
	}
}
