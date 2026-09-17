package jujutsu.mod.gametest;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiPartialRuntime;
import jujutsu.mod.character.megumi.MegumiPartialRuntime.PartialView;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Partial manifestation (#108) server scenarios — the selection table (R21), the same-type exclusion
 * both ways (R23), the selection lock while one is out (R24), the landing fold (R27), the free price
 * (R34), the no-combat-function tripwires (R38) and the teardown matrix (R40) — exercised through the
 * production entry points: {@code MegumiPartialRuntime.tryPartial} / {@code tryPartialRelease} (the
 * two arms the vessel router sends the partial key's edges to), {@code MegumiShikigamiRuntime
 * .tryPrimary} for the summon side of the exclusion, and the real lifecycle events for the teardown
 * hooks.
 *
 * <p><b>Timing.</b> The runtime's upkeep runs on END_SERVER_TICK, i.e. after the test callbacks of
 * the same server tick (the GameTest runner ticks inside the server tick, the upkeep at its tail), and
 * a mock player's own physics runs before both. A landing signal delivered in the tick-T callback is
 * therefore folded by the upkeep of tick T, and the wings are gone by the tick-T+1 callback; the
 * asserts below sit on that boundary so "≤1 tick" is measured, not guessed. The airborne half is
 * driven explicitly ({@code setOnGroundWithMovement}) instead of waiting for the mock player to fall,
 * for the same reason.
 *
 * <p><b>Teardown matrix.</b> Death, respawn and the dimension change are driven through the very
 * events the hooks are registered on, so a hook that lost its registration fails here. The disconnect
 * and server-stop triggers are called through {@link MegumiPartialRuntime#teardown} directly: a mock
 * player has no connection to hand the disconnect event, and invoking the server-stopping event would
 * stop the harness under the remaining tests. The vessel deselect and the dev fixture step run their
 * real production bodies.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions: the
 * engine is read through the runtime's views and the caster's own effect list. The tongue is anchored
 * by aiming straight down at the laid pad (pitch 90) rather than at a wall, because this class tests
 * state, not hook geometry — that belongs with the tongue's own scenarios. Everything the static
 * runtime maps hold is cleared in setup and on every success/failure path (the shared fixtures predate
 * #107/#108 and must not learn about them).
 */
public final class MegumiPartialGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int PRESS_TICK = 2;
	private static final int ACT_TICK = 4;

	/**
	 * R21 — the partial key answers to the selection and only Nue and Toad bring anything out of it.
	 * The dogs (the setup default), Rabbit Escape and Max Elephant all refuse, and the refusal starts
	 * nothing: no state record, no marker effect.
	 */
	@GameTest(maxTicks = 60)
	public void partialKeyRefusesSelectionsWithNoPartial(GameTestHelper helper) {
		String fixture = "partialKeyRefusesSelectionsWithNoPartial";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(PRESS_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean dogs = MegumiPartialRuntime.tryPartial(caster, false);
				helper.assertTrue(!dogs, MegumiShikigamiTestFixtures.diagnostic(fixture, "dogs", helper.getTick(),
						ownerId, "tryPartial on the default DOGS selection", "false", dogs));
				assertNoPartial(helper, fixture, "dogs", caster);

				for (MegumiShikigami bare : new MegumiShikigami[] {MegumiShikigami.RABBITS, MegumiShikigami.ELEPHANT}) {
					MegumiShikigamiSelection.set(ownerId, bare);
					boolean pressed = MegumiPartialRuntime.tryPartial(caster, false);
					helper.assertTrue(!pressed, MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse",
							helper.getTick(), ownerId, "tryPartial on " + bare, "false", pressed));
					assertNoPartial(helper, fixture, bare.id(), caster);
				}
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * R34 — bringing a partial out costs nothing and costs it twice: pressing the key on Nue toggles
	 * the wings on and off, both edges leave every cooldown slot at zero (and neither summon cooldown
	 * touched), and the wings stay out across grounded ticks — a player who manifests on the ground
	 * and never leaves it keeps them, which is what makes the landing fold a landing fold rather than a
	 * ground check. The release edge has no wings semantics: it is refused and the wings stay out.
	 */
	@GameTest(maxTicks = 60)
	public void wingsToggleIsFreeAndSurvivesGroundedTicks(GameTestHelper helper) {
		String fixture = "wingsToggleIsFreeAndSurvivesGroundedTicks";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			boolean on = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(on, MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(),
					caster.getUUID(), "first tryPartial result", "true", on));
			assertPartial(helper, fixture, "press", caster, MegumiShikigami.NUE);
			assertFreeAndFreeOfSummonCooldowns(helper, fixture, "press", caster);
		}));

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			// Three grounded ticks later the wings are still out: nothing folds them but a landing.
			assertPartial(helper, fixture, "grounded", caster, MegumiShikigami.NUE);
			boolean release = MegumiPartialRuntime.tryPartialRelease(caster);
			helper.assertTrue(!release, MegumiShikigamiTestFixtures.diagnostic(fixture, "grounded",
					helper.getTick(), caster.getUUID(), "release edge on the wings", "false", release));
			assertPartial(helper, fixture, "grounded", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> {
			try {
				boolean off = MegumiPartialRuntime.tryPartial(caster, false);
				helper.assertTrue(off, MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(),
						caster.getUUID(), "second tryPartial result (toggle off)", "true", off));
				assertNoPartial(helper, fixture, "off", caster);
				assertFreeAndFreeOfSummonCooldowns(helper, fixture, "off", caster);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * R24 — while a partial is out the selection cannot move (the cycle gate lives in the shikigami
	 * runtime, this pins the rule from the outside), and it moves again the moment the partial ends:
	 * the lock is the partial, not a broken cycle.
	 */
	@GameTest(maxTicks = 60)
	public void selectionCycleIsLockedWhileAPartialIsOut(GameTestHelper helper) {
		String fixture = "selectionCycleIsLockedWhileAPartialIsOut";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			MegumiPartialRuntime.tryPartial(caster, false);
			assertPartial(helper, fixture, "press", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiRuntime.tryCycle(caster, false);
			MegumiShikigami afterBlocked = MegumiShikigamiSelection.selected(caster.getUUID());
			helper.assertTrue(afterBlocked == MegumiShikigami.NUE,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "locked", helper.getTick(), caster.getUUID(),
							"selection after cycling while the wings are out", MegumiShikigami.NUE, afterBlocked));
			assertPartial(helper, fixture, "locked", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> {
			try {
				MegumiShikigamiRuntime.tryCycle(caster, false);
				MegumiShikigami stillLocked = MegumiShikigamiSelection.selected(caster.getUUID());
				helper.assertTrue(stillLocked == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "locked", helper.getTick(), caster.getUUID(),
								"selection after a second cycling attempt", MegumiShikigami.NUE, stillLocked));

				boolean off = MegumiPartialRuntime.tryPartial(caster, false);
				helper.assertTrue(off, MegumiShikigamiTestFixtures.diagnostic(fixture, "unlock", helper.getTick(),
						caster.getUUID(), "toggle the wings off", "true", off));
				assertNoPartial(helper, fixture, "unlock", caster);

				MegumiShikigamiRuntime.tryCycle(caster, false);
				MegumiShikigami unlocked = MegumiShikigamiSelection.selected(caster.getUUID());
				helper.assertTrue(unlocked == MegumiShikigami.TOAD,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "unlock", helper.getTick(), caster.getUUID(),
								"selection after the partial ends", MegumiShikigami.TOAD, unlocked));
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * R23, the Nue direction — both halves of §19's same-type exclusion. First: with the full Nue out
	 * the partial is refused (and starts nothing). Then: with the wings out the full Nue's summon is
	 * refused and the refusal does not cost the partial that is already there. The summon cooldown is
	 * cleared before the second half on purpose, so the only reason left for the refusal to answer
	 * "false" is the exclusion itself.
	 */
	@GameTest(maxTicks = 80)
	public void wingsAndFullNueExcludeEachOther(GameTestHelper helper) {
		String fixture = "wingsAndFullNueExcludeEachOther";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
					caster.getUUID(), "nue tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			boolean partial = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(!partial, MegumiShikigamiTestFixtures.diagnostic(fixture, "full out",
					helper.getTick(), ownerId, "tryPartial while the full Nue is out", "false", partial));
			assertNoPartial(helper, fixture, "full out", caster);

			MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.RECALL);
			MegumiSummonCooldowns.clear(ownerId);
			MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recalled", caster);

			boolean wings = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(wings, MegumiShikigamiTestFixtures.diagnostic(fixture, "partial out",
					helper.getTick(), ownerId, "tryPartial after the recall", "true", wings));
			assertPartial(helper, fixture, "partial out", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> {
			try {
				UUID ownerId = caster.getUUID();
				long gameTime = caster.level().getGameTime();
				// Premise: nothing but the live partial can refuse the summon.
				long summonCooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime);
				helper.assertTrue(summonCooldown == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "premise", helper.getTick(), ownerId,
								"nue summon cooldown before the refused summon", 0, summonCooldown));

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon",
						helper.getTick(), ownerId, "tryPrimary while the wings are out", "false", summoned));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "refused summon", caster);
				// The refusal must not have swept the partial on its way out.
				assertPartial(helper, fixture, "refused summon", caster, MegumiShikigami.NUE);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * R23, the Toad direction — the same two halves with the tongue in the partial role, aimed
	 * straight down at the pad so the anchor is not this class's problem (hook geometry has its own
	 * scenarios).
	 */
	@GameTest(maxTicks = 80)
	public void tongueAndFullToadExcludeEachOther(GameTestHelper helper) {
		String fixture = "tongueAndFullToadExcludeEachOther";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 90.0f);

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
					caster.getUUID(), "toad tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			boolean partial = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(!partial, MegumiShikigamiTestFixtures.diagnostic(fixture, "full out",
					helper.getTick(), ownerId, "tryPartial while the full Toad is out", "false", partial));
			assertNoPartial(helper, fixture, "full out", caster);

			MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.RECALL);
			MegumiSummonCooldowns.clear(ownerId);
			MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recalled", caster);

			boolean tongue = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(tongue, MegumiShikigamiTestFixtures.diagnostic(fixture, "partial out",
					helper.getTick(), ownerId, "tryPartial after the recall", "true", tongue));
			assertPartial(helper, fixture, "partial out", caster, MegumiShikigami.TOAD);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> {
			try {
				UUID ownerId = caster.getUUID();
				long gameTime = caster.level().getGameTime();
				long summonCooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.TOAD, gameTime);
				helper.assertTrue(summonCooldown == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "premise", helper.getTick(), ownerId,
								"toad summon cooldown before the refused summon", 0, summonCooldown));

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon",
						helper.getTick(), ownerId, "tryPrimary while the tongue is out", "false", summoned));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "refused summon", caster);
				assertPartial(helper, fixture, "refused summon", caster, MegumiShikigami.TOAD);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * R27 — the wings fold themselves on landing, within the landing tick: the owner is taken airborne
	 * with the wings out (the upkeep's glide request is what makes that airborne state a glide), and the
	 * landing claim in the tick-T callback is folded by the upkeep of that same tick, so the effect is
	 * gone by the tick-T+1 callback.
	 */
	@GameTest(maxTicks = 80, skyAccess = true)
	public void wingsFoldOnLanding(GameTestHelper helper) {
		String fixture = "wingsFoldOnLanding";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			boolean on = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(on, MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(),
					caster.getUUID(), "tryPartial result", "true", on));
			assertPartial(helper, fixture, "press", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			BlockPos up = helper.absolutePos(casterFeet.above(4));
			caster.teleportTo(level, up.getX() + 0.5, up.getY(), up.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
			caster.setOnGroundWithMovement(false, Vec3.ZERO);
			// The upkeep of this tick latches the airborne flag and re-asserts the glide request.
			assertPartial(helper, fixture, "airborne", caster, MegumiShikigami.NUE);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> guarded(helper, caster, () -> {
			// Two airborne ticks in: no premature fold while the owner is off the ground.
			assertPartial(helper, fixture, "airborne", caster, MegumiShikigami.NUE);
			caster.setOnGroundWithMovement(true, Vec3.ZERO);
		}));

		helper.runAtTickTime(ACT_TICK + 3, () -> {
			try {
				// One upkeep after the landing claim.
				assertNoPartial(helper, fixture, "landed", caster);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * R38, the wings half — a partial manifestation adds no combat function. The owner lands on a
	 * golem's head with the wings out and stays there; the golem's health never moves and nothing on
	 * either side of the encounter ever carries the hold marker the grab systems use. A future impact
	 * damage rider would fail the health assert here.
	 */
	@GameTest(maxTicks = 90)
	public void wingsDealNoDamage(GameTestHelper helper) {
		String fixture = "wingsDealNoDamage";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(2, 1, 3);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			boolean on = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(on, MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(),
					caster.getUUID(), "tryPartial result", "true", on));
		}));

		// Spawned after the activation so the golem is never inside the partial's own setup path, and
		// no-AI so the determinism switch owns its behaviour rather than the golem's own brain.
		IronGolem golem = GameTestFixtures.spawnMob(helper, fixture, EntityType.IRON_GOLEM, victimFeet);
		float healthBefore = golem.getHealth();

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			BlockPos up = helper.absolutePos(victimFeet.above(4));
			caster.teleportTo(level, up.getX() + 0.5, up.getY(), up.getZ() + 0.5, Set.of(), 0.0f, 0.0f, false);
			caster.setOnGroundWithMovement(false, Vec3.ZERO);
		}));

		helper.runAtTickTime(ACT_TICK + 10, () -> guarded(helper, caster, () -> {
			helper.assertTrue(golem.getHealth() == healthBefore,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "impact", helper.getTick(), caster.getUUID(),
							"golem health after the owner fell onto it with the wings out", healthBefore,
							golem.getHealth()));
			helper.assertTrue(!golem.hasEffect(JujutsuEffects.GRIPPED),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "impact", helper.getTick(), caster.getUUID(),
							"GRIPPED on the golem", "false", true));
		}));

		helper.runAtTickTime(ACT_TICK + 20, () -> {
			try {
				helper.assertTrue(golem.getHealth() == healthBefore,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "settled", helper.getTick(),
								caster.getUUID(), "golem health over the whole encounter", healthBefore,
								golem.getHealth()));
				helper.assertTrue(!golem.hasEffect(JujutsuEffects.GRIPPED)
								&& !caster.hasEffect(JujutsuEffects.GRIPPED),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "settled", helper.getTick(),
								caster.getUUID(), "GRIPPED on either side of the encounter", "false", true));
			} finally {
				golem.discard();
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * R38, the tongue half — the grapple pulls the owner and nobody else. A golem stands beside the
	 * anchored tongue for a hold's worth of ticks: it is never gripped, never mounted and never hurt,
	 * which is the shape the requirement takes now that the tongue's own visuals are client state.
	 */
	@GameTest(maxTicks = 80)
	public void tongueGrabsNothing(GameTestHelper helper) {
		String fixture = "tongueGrabsNothing";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos bystanderFeet = new BlockPos(2, 1, 3);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 90.0f);
		IronGolem bystander = GameTestFixtures.spawnMob(helper, fixture, EntityType.IRON_GOLEM, bystanderFeet);
		float healthBefore = bystander.getHealth();

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			boolean tongue = MegumiPartialRuntime.tryPartial(caster, false);
			helper.assertTrue(tongue, MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(),
					caster.getUUID(), "tryPartial result", "true", tongue));
			assertPartial(helper, fixture, "press", caster, MegumiShikigami.TOAD);
			helper.assertTrue(!bystander.hasEffect(JujutsuEffects.GRIPPED) && bystander.getVehicle() == null,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(), caster.getUUID(),
							"bystander gripped or mounted by the anchored tongue", "false", true));
		}));

		helper.runAtTickTime(ACT_TICK + 6, () -> guarded(helper, caster, () -> {
			// The hold survives its own upkeep (the anchor below the eye is on the line), and it still
			// touches nothing but the owner.
			assertPartial(helper, fixture, "hold", caster, MegumiShikigami.TOAD);
			helper.assertTrue(bystander.getHealth() == healthBefore,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", helper.getTick(), caster.getUUID(),
							"bystander health during the hold", healthBefore, bystander.getHealth()));
			helper.assertTrue(!bystander.hasEffect(JujutsuEffects.GRIPPED) && bystander.getVehicle() == null,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", helper.getTick(), caster.getUUID(),
							"bystander gripped or mounted during the hold", "false", true));
			helper.assertTrue(!caster.hasEffect(JujutsuEffects.GRIPPED),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", helper.getTick(), caster.getUUID(),
							"GRIPPED on the tongue's owner", "false", true));
		}));

		helper.runAtTickTime(ACT_TICK + 8, () -> {
			try {
				boolean release = MegumiPartialRuntime.tryPartialRelease(caster);
				helper.assertTrue(release, MegumiShikigamiTestFixtures.diagnostic(fixture, "release",
						helper.getTick(), caster.getUUID(), "release edge on the anchored tongue", "true", release));
				assertNoPartial(helper, fixture, "release", caster);
			} finally {
				bystander.discard();
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * R40, the lifecycle half — every trigger ends an active partial and takes its marker with it, with
	 * both partial kinds taking turns so neither path is the only one exercised. Death, respawn and the
	 * dimension change go through the events the hooks are registered on (a lost registration fails
	 * here); disconnect and server stop call the same teardown their hooks call, because a mock player
	 * has no connection to hand the disconnect event and firing the server-stopping event would stop
	 * the harness under the remaining tests.
	 */
	@GameTest(maxTicks = 120)
	public void lifecycleTeardownsEndAnActivePartial(GameTestHelper helper) {
		String fixture = "lifecycleTeardownsEndAnActivePartial";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 90.0f);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();

		helper.runAtTickTime(ACT_TICK, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.NUE);
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(caster, caster.damageSources().generic());
			assertNoPartial(helper, fixture, "death", caster);
		}));

		helper.runAtTickTime(ACT_TICK + 2, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.TOAD);
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(caster, caster, true);
			assertNoPartial(helper, fixture, "respawn", caster);
		}));

		helper.runAtTickTime(ACT_TICK + 4, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.NUE);
			ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.invoker()
					.afterChangeWorld(caster, level, level);
			assertNoPartial(helper, fixture, "dimension", caster);
		}));

		helper.runAtTickTime(ACT_TICK + 6, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.TOAD);
			MegumiPartialRuntime.teardown(server, caster.getUUID());
			assertNoPartial(helper, fixture, "disconnect", caster);
		}));

		helper.runAtTickTime(ACT_TICK + 8, () -> {
			try {
				activate(helper, fixture, caster, MegumiShikigami.NUE);
				MegumiPartialRuntime.teardown(server, caster.getUUID());
				assertNoPartial(helper, fixture, "server stop", caster);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * R40, the vessel half — switching the vessel away runs the production deselect path, and the
	 * partial goes with it while the player keeps their shikigami selection (the same split the pack
	 * teardown has). Returning to Megumi greets a clean, still-free key.
	 */
	@GameTest(maxTicks = 80)
	public void deselectEndsAnActivePartial(GameTestHelper helper) {
		String fixture = "deselectEndsAnActivePartial";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 90.0f);

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.TOAD);
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				CharacterSelectionManager.select(caster, JujutsuCharacter.NONE);
				assertNoPartial(helper, fixture, "deselect", caster);
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.TOAD,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "deselect", helper.getTick(), ownerId,
								"selection after the deselect", MegumiShikigami.TOAD, selected));

				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				assertNoPartial(helper, fixture, "return", caster);
				assertFreeAndFreeOfSummonCooldowns(helper, fixture, "return", caster);
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * R40, the dev-fixture half — the two steps the reset tool appends (#108's teardown and #107's
	 * per-type cooldown clear), run in the tool's order. The teardown is the tool's own body: no
	 * cooldown, and the shikigami selection survives (only the tool's separate selection step resets
	 * it), which is the same split responsibility the pack teardown has.
	 */
	@GameTest(maxTicks = 80)
	public void fixtureResetStepEndsAnActivePartial(GameTestHelper helper) {
		String fixture = "fixtureResetStepEndsAnActivePartial";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		MinecraftServer server = helper.getLevel().getServer();

		helper.runAtTickTime(PRESS_TICK, () -> guarded(helper, caster, () -> {
			activate(helper, fixture, caster, MegumiShikigami.NUE);
			long gameTime = caster.level().getGameTime();
			MegumiSummonCooldowns.start(caster.getUUID(), MegumiShikigami.NUE, gameTime + 240);
			long armed = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.NUE, gameTime);
			helper.assertTrue(armed > 0, MegumiShikigamiTestFixtures.diagnostic(fixture, "arm", helper.getTick(),
					caster.getUUID(), "nue summon cooldown armed for the fixture step", "> 0", armed));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// The two appended tool steps, in tool order.
				MegumiPartialRuntime.teardown(server, ownerId);
				MegumiSummonCooldowns.clear(ownerId);

				assertNoPartial(helper, fixture, "reset", caster);
				for (MegumiShikigami type : MegumiShikigami.values()) {
					long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, type,
							caster.level().getGameTime());
					helper.assertTrue(remaining == 0,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "reset", helper.getTick(), ownerId,
									"summon cooldown after the clear: " + type, 0, remaining));
				}
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "reset", helper.getTick(), ownerId,
								"selection after the partial teardown alone", MegumiShikigami.NUE, selected));
				int partial = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PARTIAL);
				helper.assertTrue(partial == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "reset", helper.getTick(), ownerId,
								"PARTIAL cooldown (a teardown is free)", 0, partial));
			} finally {
				cleanup(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/** Puts the selection on {@code type} and presses the partial key, asserting both halves. */
	private static void activate(GameTestHelper helper, String fixture, ServerPlayer caster, MegumiShikigami type) {
		MegumiShikigamiSelection.set(caster.getUUID(), type);
		boolean pressed = MegumiPartialRuntime.tryPartial(caster, false);
		helper.assertTrue(pressed, MegumiShikigamiTestFixtures.diagnostic(fixture, "activate", helper.getTick(),
				caster.getUUID(), "tryPartial on " + type, "true", pressed));
		assertPartial(helper, fixture, "activate", caster, type);
	}

	private static void assertPartial(GameTestHelper helper, String fixture, String phase,
			ServerPlayer caster, MegumiShikigami expected) {
		long tick = helper.getTick();
		UUID ownerId = caster.getUUID();
		Optional<PartialView> view = MegumiPartialRuntime.partialView(ownerId);
		helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
				"partial view present", "present", "absent"));
		helper.assertTrue(expected.id().equals(view.get().kind()),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"partial kind", expected.id(), view.get().kind()));
		helper.assertTrue(MegumiPartialRuntime.isAnyActive(ownerId)
						&& MegumiPartialRuntime.isActiveForType(ownerId, expected),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"query accessors agree with the " + expected + " partial", "true", false));
		helper.assertTrue(caster.hasEffect(markerFor(expected)),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"marker effect carried for " + expected, "true", false));
	}

	private static void assertNoPartial(GameTestHelper helper, String fixture, String phase, ServerPlayer caster) {
		long tick = helper.getTick();
		UUID ownerId = caster.getUUID();
		helper.assertTrue(MegumiPartialRuntime.partialView(ownerId).isEmpty(),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"partial view present", "absent", "present"));
		helper.assertTrue(!MegumiPartialRuntime.isAnyActive(ownerId),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"isAnyActive", "false", true));
		// No partial, no markers: a lingering marker would keep the elytra grant or the client's pull
		// alive with nothing behind it, which is the failure mode the teardown exists to prevent.
		helper.assertTrue(!caster.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS)
						&& !caster.hasEffect(JujutsuEffects.MEGUMI_TOAD_TONGUE),
				MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
						"partial markers on the caster", "neither", "one or both"));
	}

	/** R34's price check: both partial slots read zero, and the partial touched no summon cooldown. */
	private static void assertFreeAndFreeOfSummonCooldowns(GameTestHelper helper, String fixture, String phase,
			ServerPlayer caster) {
		long tick = helper.getTick();
		UUID ownerId = caster.getUUID();
		for (CharacterAbility slot : new CharacterAbility[] {
				CharacterAbility.PARTIAL, CharacterAbility.PARTIAL_RELEASE}) {
			int remaining = CharacterAbilityCooldowns.remainingTicks(caster, slot);
			helper.assertTrue(remaining == 0, MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
					"cooldown remaining on " + slot, 0, remaining));
		}
		for (MegumiShikigami type : new MegumiShikigami[] {MegumiShikigami.NUE, MegumiShikigami.TOAD}) {
			long summonCooldown = MegumiSummonCooldowns.remainingTicks(ownerId, type,
					caster.level().getGameTime());
			helper.assertTrue(summonCooldown == 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, phase, tick, ownerId,
							"summon cooldown touched by the partial: " + type, 0, summonCooldown));
		}
	}

	private static Holder<MobEffect> markerFor(MegumiShikigami type) {
		return type == MegumiShikigami.NUE ? JujutsuEffects.MEGUMI_NUE_WINGS : JujutsuEffects.MEGUMI_TOAD_TONGUE;
	}

	/**
	 * Cleanup for success AND failure paths. The partial teardown runs first and by name: the shared
	 * fixtures predate #108 and are used by every Megumi scenario, so they must not learn about it, and
	 * a state record whose player was already removed would have to be reaped by the upkeep instead.
	 */
	private static void cleanup(GameTestHelper helper, ServerPlayer caster) {
		MinecraftServer server = helper.getLevel().getServer();
		safe(() -> MegumiPartialRuntime.teardown(server, caster.getUUID()));
		MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
	}

	/**
	 * Intermediate-callback guard: runs {@code step} and on failure cleans up (partials included)
	 * before rethrowing. Later callbacks never run once the test has failed, so the final callback owns
	 * the unconditional cleanup instead.
	 */
	private static void guarded(GameTestHelper helper, ServerPlayer caster, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException | AssertionError failure) {
			cleanup(helper, caster);
			throw failure;
		}
	}

	private static void safe(Runnable step) {
		try {
			step.run();
		} catch (Throwable ignored) {
			// Cleanup is best-effort on every path; the test's own assertions are what must be loud.
		}
	}

	/** Floor-supported 3x3 stone pad: the caster's ground and a stable tongue anchor below it. */
	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}
}
