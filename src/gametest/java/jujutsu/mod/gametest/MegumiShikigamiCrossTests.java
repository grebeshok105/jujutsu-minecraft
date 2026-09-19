package jujutsu.mod.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;

/**
 * Cross-type shikigami guarantees, block 4 — coexistence of two live types with a free second
 * summon (C1), the fixture-reset clean slate: pack gone and the selection back to DOGS (C2), the
 * FIXTURE_RESET reason alone: pack gone, selection untouched, no cooldown (C3), vessel deselect
 * tearing the pack down while keeping the selection and leaving no summon deadline behind (C4),
 * the cooldown-free lifecycle teardowns (C5 disconnect, C6 respawn), the recall-priced dimension
 * change on the per-type map (C7), the same-tick duplicate press keeping the pack (C8), a refused
 * summon leaving the packs already out (C9), and one type's recall playing its sink out while the
 * other pack keeps fighting (C10) — exercised through the production runtime calls
 *
 * <p><b>Pinned literals.</b> C4/C7 assert the literal 240 ticks rather than the profile constant
 * ON PURPOSE: the red-proof mutates the profile row and the assert must follow the balance
 * contract, not the constant. Since issue #107 the deadline is armed on the per-type summon map
 * ({@code MegumiSummonCooldowns}) rather than the shared PRIMARY slot, so every cooldown read here
 * names the type and the slot is asserted to stay free.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * only the pack view and the selection map are read, never bounds. The summon/act steps sit on
 * different ticks — the runtime drops same-tick duplicate technique presses. C2 replicates the
 * two MCP fixture-reset tool steps ({@code megumi_shikigami_teardown} +
 * {@code megumi_shikigami_selection_clear}) because the dev tool itself is not reachable from a
 * GameTest. Static state (selection map, both pack maps, both cooldown slots) is cleared in setup
 * and on every success/failure path.
 */
public final class MegumiShikigamiCrossTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int ACT_TICK = 4;


	/**
	 * C7 pins this row: a dimension change dismisses the pack at exactly the Nue recall price
	 * (DIMENSION_CHANGE is a recall-family reason). Deliberately NOT the profile constant — the
	 * red-proof mutates it.
	 */
	private static final int EXPECTED_DIMENSION_CHANGE_COOLDOWN_TICKS = 240;

	/**
	 * C1 — coexistence between two shikigami types (issue #107 D1): with Nue out, selecting TOAD and
	 * pressing the technique key summons the Toad *beside* it. Both pack records live, the Toad pack
	 * reads one anchored body, and PRIMARY stays 0 — nothing was recalled for the arrival.
	 */
	@GameTest(maxTicks = 60)
	public void summoningToadBesideNueIsFreeAndKeepsBothPacks(GameTestHelper helper) {
		String fixture = "summoningToadBesideNueIsFreeAndKeepsBothPacks";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "nue tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean cycled = MegumiShikigamiRuntime.tryCycle(caster, false);
				helper.assertTrue(cycled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cycle", helper.getTick(), ownerId, "tryCycle result", "true", cycled));
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.TOAD,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cycle", helper.getTick(), ownerId,
								"selection after one cycle from NUE", MegumiShikigami.TOAD, selected));

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				// Both types are out: the per-type map is what makes this possible at all.
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.contains(MegumiShikigami.NUE.id()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"nue pack kept", MegumiShikigami.NUE.id(), types));
				helper.assertTrue(types.contains(MegumiShikigami.TOAD.id()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"toad pack present", MegumiShikigami.TOAD.id(), types));
				helper.assertTrue(types.size() == 2,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"live pack count", "2", types.size()));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(view.get().aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"alive bodies of the first pack", "1", view.get().aliveBodies()));

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
	 * C2 — the fixture-reset clean slate: after the teardown plus the selection clear (the exact
	 * two steps the MCP {@code fixture_reset} tool runs) the pack record is gone AND the selection
	 * is back to DOGS. Pins the adjudicated rule: the reset does not keep the selection.
	 */
	@GameTest(maxTicks = 60)
	public void fixtureResetClearsPackAndRestoresDogsDefault(GameTestHelper helper) {
		String fixture = "fixtureResetClearsPackAndRestoresDogsDefault";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// The two MCP fixture_reset steps, in tool order.
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.FIXTURE_RESET);
				MegumiShikigamiSelection.clear(ownerId);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "reset", caster);
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.DOGS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "reset", helper.getTick(), ownerId,
								"selection after fixture reset", MegumiShikigami.DOGS, selected));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C3 — the FIXTURE_RESET reason alone (without the tool's selection-clear step): the pack
	 * record is gone, the selection is untouched (still NUE), and PRIMARY stays 0. Pins the split
	 * responsibility: the reason is cooldown-free and selection-blind; only the tool step resets
	 * the selection.
	 */
	@GameTest(maxTicks = 60)
	public void fixtureTeardownAloneKeepsSelectionAndChargesNothing(GameTestHelper helper) {
		String fixture = "fixtureTeardownAloneKeepsSelectionAndChargesNothing";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.FIXTURE_RESET);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "teardown", caster);
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "teardown", helper.getTick(), ownerId,
								"selection after FIXTURE_RESET teardown", MegumiShikigami.NUE, selected));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "teardown", helper.getTick(), ownerId,
								"PRIMARY cooldown (fixture reset is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C4 — switching the vessel to NONE (production deselect path) tears the live pack down with
	 * the recall-family price while the player keeps their selection: pack record gone, selection
	 * still NUE, PRIMARY reads exactly 240 in the deselect tick.
	 */
	@GameTest(maxTicks = 60)
	public void deselectTearsPackDownAndClearsItsCooldowns(GameTestHelper helper) {
		String fixture = "deselectTearsPackDownAndClearsItsCooldowns";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				CharacterSelectionManager.select(caster, JujutsuCharacter.NONE);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "deselect", caster);
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "deselect", helper.getTick(), ownerId,
								"selection after deselect", MegumiShikigami.NUE, selected));

				// Issue #84: the deselect arms the recall cooldown and the switch then clears it — a
				// vessel change is a clean slate, so re-selecting MEGUMI must not come back to a
				// half-spent deadline. The teardown itself (pack gone, selection kept) is asserted above.
				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "deselect", helper.getTick(), ownerId,
								"PRIMARY cooldown after re-selecting MEGUMI", 0, remaining));
				// Issue #107: the summon deadline lives on the per-type map now, so the clean slate has
				// to hold there too — the returning Megumi must be able to summon at once.
				long summonCooldown = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.NUE, helper.getLevel().getGameTime());
				helper.assertTrue(summonCooldown == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "deselect", helper.getTick(), ownerId,
								"Nue summon cooldown after re-selecting MEGUMI", 0, summonCooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C5 — the disconnect teardown (the runtime half of the DISCONNECT hook; the selection-clear
	 * half lives in {@code onPlayerDisconnect}): the pack record is gone, PRIMARY stays 0, and
	 * the selection is untouched — only the hook's clear step resets it.
	 */
	@GameTest(maxTicks = 60)
	public void disconnectTeardownClearsPackWithoutCooldown(GameTestHelper helper) {
		String fixture = "disconnectTeardownClearsPackWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.DISCONNECT);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "disconnect", caster);
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "disconnect", helper.getTick(), ownerId,
								"selection after DISCONNECT teardown", MegumiShikigami.NUE, selected));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "disconnect", helper.getTick(), ownerId,
								"PRIMARY cooldown (disconnect is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C6 — the respawn teardown: the pack record is gone and PRIMARY stays 0. A respawned player
	 * re-summons from a clean slate instead of paying for bodies that died with them.
	 */
	@GameTest(maxTicks = 60)
	public void respawnTeardownClearsPackWithoutCooldown(GameTestHelper helper) {
		String fixture = "respawnTeardownClearsPackWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.RESPAWN);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "respawn", caster);

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "respawn", helper.getTick(), ownerId,
								"PRIMARY cooldown (respawn is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C7 — the dimension-change teardown: the pack record is gone and PRIMARY reads exactly 240
	 * (DIMENSION_CHANGE is a recall-family reason — crossing a portal costs the recall price,
	 * exactly like a manual recall).
	 */
	@GameTest(maxTicks = 60)
	public void dimensionChangeTeardownChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "dimensionChangeTeardownChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "dimension", caster);

				// Same-tick read: the cooldown was just armed, so the remaining time is exact. Issue
				// #107 moved the storage per type, so the PRIMARY slot stays untouched.
				long gameTime = helper.getLevel().getGameTime();
				long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime);
				helper.assertTrue(remaining == EXPECTED_DIMENSION_CHANGE_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "dimension", helper.getTick(), ownerId,
								"Nue recall cooldown", EXPECTED_DIMENSION_CHANGE_COOLDOWN_TICKS, remaining));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "dimension", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline now)", "0", primary));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C8 — pressing the technique key twice in the same game tick (key repeat, doubled packet)
	 * must not summon-then-recall: both presses return true, the pack view still reads one
	 * anchored Nue body, and PRIMARY stays 0. Without the same-tick guard the second press
	 * would resolve as RECALL_SELF and tear the just-summoned pack down.
	 */
	@GameTest(maxTicks = 60)
	public void sameTickDuplicatePressKeepsPack(GameTestHelper helper) {
		String fixture = "sameTickDuplicatePressKeepsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);

				boolean first = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(first, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"press", helper.getTick(), ownerId, "first tryPrimary result", "true", first));
				boolean second = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(second, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"press", helper.getTick(), ownerId, "second tryPrimary result", "true", second));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"press", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(view.get().aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(), ownerId,
								"alive bodies", "1", view.get().aliveBodies()));
				helper.assertTrue(view.get().anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(), ownerId,
								"anchor alive (the javadoc's anchored body)", "true",
								view.get().anchorAlive()));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "press", helper.getTick(), ownerId,
								"PRIMARY cooldown (duplicate press is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C9 — a summon whose arrival has nowhere to stand is refused, and the refusal must not cost the
	 * other packs. The caster's air column is walled in (the Toad stands forward of it and is
	 * untouched), so the Nue's three hover candidates all sit inside blocks: the technique key
	 * answers false while the toad pack still reads one live body and PRIMARY stays 0. Run against
	 * the pre-fix order this fails at the pack view — the sweep used to run before placement was
	 * ever attempted.
	 */
	@GameTest(maxTicks = 80)
	public void refusedSummonKeepsTheOtherPackOut(GameTestHelper helper) {
		String fixture = "refusedSummonKeepsTheOtherPackOut";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "toad tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				sealTheAirColumn(helper, casterFeet);
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"refused summon", helper.getTick(), ownerId, "tryPrimary result", "false", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"refused summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.TOAD.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon", helper.getTick(), ownerId,
								"pack type after the refusal", MegumiShikigami.TOAD.id(), view.get().type()));
				helper.assertTrue(view.get().aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon", helper.getTick(), ownerId,
								"alive bodies after the refusal", "1", view.get().aliveBodies()));
				helper.assertTrue(
						MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId).size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon", helper.getTick(), ownerId,
								"live pack count after the refusal", "1",
								MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId)));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refused summon", helper.getTick(), ownerId,
								"PRIMARY cooldown after the refusal", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * C10 — a recall sweeps exactly its own type (issue #107 D1). Nue and Toad are both out; recalling
	 * Nue keeps the Toad pack and its body alive, while the outgoing Nue plays out its twelve-tick
	 * sink instead of vanishing on the spot — a body that cannot finish would already be gone three
	 * ticks in, which is exactly the regression this pins.
	 */
	@GameTest(maxTicks = 80)
	public void theRecalledTypeFinishesItsSinkWhileTheOtherPackStays(GameTestHelper helper) {
		String fixture = "theRecalledTypeFinishesItsSinkWhileTheOtherPackStays";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "nue tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"coexist", helper.getTick(), ownerId, "toad tryPrimary result", "true", summoned));
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"recall", helper.getTick(), ownerId, "nue recall result", "true", recalled));
		}));

		// Three ticks in: past the outgoing body's next tick, well inside its twelve-tick sink.
		helper.runAtTickTime(ACT_TICK + 3, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			int midSink = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId).size();
			helper.assertTrue(midSink == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sink", helper.getTick(), ownerId, "nue bodies still sinking", "1", midSink));
			List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
			helper.assertTrue(types.size() == 1 && types.contains(MegumiShikigami.TOAD.id()),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "sink", helper.getTick(), ownerId,
							"packs left while the sink plays", List.of(MegumiShikigami.TOAD.id()), types));
		}));

		helper.runAtTickTime(ACT_TICK + 16, () -> {
			try {
				UUID ownerId = caster.getUUID();
				int left = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId).size();
				helper.assertTrue(left == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sink", helper.getTick(), ownerId, "nue bodies after the sink", "0", left));
				boolean toadKept = MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.TOAD);
				helper.assertTrue(toadKept, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sink", helper.getTick(), ownerId, "toad pack after the nue recall", "kept", "gone"));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * Walls the caster's own column from two to six blocks up: all three Nue hover candidates
	 * (2.5 / 3.5 / 1.5 above the feet) and their body boxes end up inside a block, so the flyer
	 * placement finds nothing. The Toad stands forward of the column and is untouched.
	 */
	private static void sealTheAirColumn(GameTestHelper helper, BlockPos casterFeet) {
		for (int dy = 2; dy <= 6; dy++) {
			helper.setBlock(casterFeet.offset(0, dy, 0), Blocks.STONE);
		}
	}

	/** Floor-supported 3x3 stone pad so the Toad ground placement always finds a safe body spot. */
	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}
	/**
	 * Issue #84 — a vessel switch is a clean slate. Both directions are covered: MEGUMI -> NONE ->
	 * MEGUMI (the reported case) and MEGUMI -> TODO -> MEGUMI (cross-vessel), each asserting the slot
	 * reads zero immediately after the switch and after switching back.
	 *
	 * <p>Re-confirming the SAME vessel is asserted too, because that is the one path the clearing must
	 * NOT cover: the menu would otherwise be a free cooldown reset.
	 */
	@GameTest(maxTicks = 120, skyAccess = true)
	public void vesselSwitchClearsAbilityCooldowns(GameTestHelper helper) {
		String fixture = "vesselSwitchClearsAbilityCooldowns";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(ACT_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// Summoning itself costs nothing (the recall price is paid on recall/death), so the
				// cooldown under test is armed explicitly — this scenario is about the switch, not
				// about what arms the deadline.
				CharacterAbilityCooldowns.start(caster, CharacterAbility.PRIMARY, 240);
				int armed = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(armed > 0, MegumiShikigamiTestFixtures.diagnostic(fixture, "arm",
						helper.getTick(), ownerId, "cooldown armed before the switch", "> 0", armed));

				CharacterSelectionManager.select(caster, JujutsuCharacter.NONE);
				int afterDeselect = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(afterDeselect == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"deselect", helper.getTick(), ownerId, "cooldown cleared by the switch to NONE",
						0, afterDeselect));

				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				int afterReturn = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(afterReturn == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"return", helper.getTick(), ownerId, "cooldown still clear after re-selecting MEGUMI",
						0, afterReturn));

				// Cross-vessel: arm MEGUMI again, leave for TODO, and return to a clean slot.
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
				CharacterAbilityCooldowns.start(caster, CharacterAbility.PRIMARY, 240);
				CharacterSelectionManager.select(caster, JujutsuCharacter.TODO);
				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				int afterTodo = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(afterTodo == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cross-vessel", helper.getTick(), ownerId,
						"cooldown cleared by the MEGUMI -> TODO -> MEGUMI round trip", 0, afterTodo));

				// Re-confirming the same vessel must NOT clear: the menu is not a reset button.
				CharacterAbilityCooldowns.start(caster, CharacterAbility.PRIMARY, 240);
				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				int afterReconfirm = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(afterReconfirm > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"reconfirm", helper.getTick(), ownerId,
						"re-confirming the same vessel keeps the cooldown", "> 0", afterReconfirm));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}
}
