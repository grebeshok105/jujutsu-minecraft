package jujutsu.mod.gametest;

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

/**
 * Cross-type shikigami guarantees, block 4 — one-active-across-types with a free swap (C1), the
 * fixture-reset clean slate: pack gone and the selection back to DOGS (C2), the FIXTURE_RESET
 * reason alone: pack gone, selection untouched, no cooldown (C3), and vessel deselect tearing the
 * pack down while keeping the selection (C4) — exercised through the production runtime calls
 * {@code MegumiShikigamiRuntime.tryPrimary} / {@code tryCycle} / {@code teardown} and the
 * production {@code CharacterSelectionManager.select}, the same hops the vessel router and the
 * deselect hook reach.
 *
 * <p><b>Pinned literals.</b> C4 asserts the literal 240 ticks rather than the profile constant
 * ON PURPOSE: the red-proof mutates the profile row and the assert must follow the balance
 * contract, not the constant.
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
	 * C4 pins this row: vessel deselect costs exactly the Nue recall cooldown (DESELECTED is a
	 * recall-family reason). Deliberately NOT the profile constant — the red-proof mutates it.
	 */
	private static final int EXPECTED_DESELECT_COOLDOWN_TICKS = 240;

	/**
	 * C1 — with Nue out, cycling to TOAD and pressing the technique key swaps for free: the pack
	 * view reads type "toad" with one anchored body and PRIMARY stays 0 (the swap-out teardown
	 * uses the cooldown-free SWAPPED reason and the summon starts none).
	 */
	@GameTest(maxTicks = 60)
	public void swappingNueForToadIsFreeAndLeavesOnePack(GameTestHelper helper) {
		String fixture = "swappingNueForToadIsFreeAndLeavesOnePack";
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

				boolean swapped = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(swapped, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"swap", helper.getTick(), ownerId, "tryPrimary result", "true", swapped));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"swap", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.TOAD.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "swap", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.TOAD.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "swap", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "swap", helper.getTick(), ownerId,
								"PRIMARY cooldown (swap is free)", "0", remaining));
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
	public void deselectTearsPackDownButKeepsSelection(GameTestHelper helper) {
		String fixture = "deselectTearsPackDownButKeepsSelection";
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

				// The cooldown store keys on (player, vessel, slot) and resolves the vessel from the
				// live selection, so the deselect only changed the key being read: what the teardown
				// armed lives under MEGUMI. Switching back must find it exactly where it was left —
				// that is the player-visible promise (the recall price survives the vessel switch).
				CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_DESELECT_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "deselect", helper.getTick(), ownerId,
								"PRIMARY recall cooldown after re-selecting MEGUMI",
								EXPECTED_DESELECT_COOLDOWN_TICKS, remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** Floor-supported 3x3 stone pad so the Toad ground placement always finds a safe body spot. */
	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}
}
