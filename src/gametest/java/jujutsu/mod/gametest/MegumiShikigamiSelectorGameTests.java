package jujutsu.mod.gametest;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.JujutsuCharacters;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;

/**
 * The quick selector's server half, block B2: a click-select of an already-summoned type is accepted
 * and costs nothing, a cooling or unknown entry is refused, the cycle steps over what it cannot use,
 * and the last click of a burst wins without despawning anything.
 *
 * <p>Every scenario goes through the production hop — {@code JujutsuCharacters.of(player)
 * .selectShikigami(...)} is exactly what the C2S receiver calls — rather than a test-only setter, so
 * a mistake between the vessel seam and the roster fails here.
 *
 * <p><b>The pack assertions are the point, not decoration.</b> "Selecting an already-summoned entry
 * is accepted" is worth nothing on an empty world: scenario one summons a Nue pack and asserts a live
 * anchored pack before <em>and</em> after the select, and the multi-click scenario leaves that pack
 * standing while selecting two other types, because a selection change that quietly swept the world
 * would pass a selection-only check. The refusal scenarios set the selection somewhere other than the
 * default first, so "unchanged" cannot be satisfied by the value it started on.
 *
 * <p><b>Traps avoided.</b> The world offset is random per run, so bodies are found through the
 * runtime's own owner-keyed pack view, never by position or structure bounds. Steps sit on different
 * ticks. The roster ledger is not part of the shared fixture, so each scenario clears its own row on
 * every success and failure path — a leftover cooldown would refuse the next scenario's click.
 */
public final class MegumiShikigamiSelectorGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int CLICK_TICK = 4;
	private static final BlockPos CASTER_FEET = new BlockPos(2, 1, 2);

	/**
	 * Selecting the type that is already out is accepted, starts no cooldown and leaves the pack
	 * standing (R14/R18). The premise assert is what keeps this from passing on an empty world.
	 */
	@GameTest(maxTicks = 60)
	public void selectingTheSummonedTypeIsAcceptedAndKeepsThePackAlive(GameTestHelper helper) {
		String fixture = "selectingTheSummonedTypeIsAcceptedAndKeepsThePackAlive";
		helper.setBlock(CASTER_FEET.below(), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
			Optional<PackView> before = MegumiShikigamiRuntime.packView(helper.getLevel().getServer(), ownerId);
			helper.assertTrue(before.isPresent() && before.get().aliveBodies() > 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "premise", helper.getTick(), ownerId,
							"live pack before the select", "present with bodies", before));
			clearLedger(caster);
		}));

		helper.runAtTickTime(CLICK_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				Optional<PackView> before = MegumiShikigamiRuntime.packView(helper.getLevel().getServer(), ownerId);
				boolean accepted = JujutsuCharacters.of(caster).selectShikigami(caster, MegumiShikigami.NUE.id());
				helper.assertTrue(accepted, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"select", helper.getTick(), ownerId, "selectShikigami on the summoned type", "true", accepted));
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "select", helper.getTick(), ownerId,
								"selection", MegumiShikigami.NUE, selected));
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "select", helper.getTick(), ownerId,
								"PRIMARY cooldown (selecting is free)", "0", remaining));
				Optional<PackView> after = MegumiShikigamiRuntime.packView(helper.getLevel().getServer(), ownerId);
				helper.assertTrue(after.isPresent() && after.get().aliveBodies() > 0
								&& after.get().aliveBodies() >= before.map(PackView::aliveBodies).orElse(0),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "select", helper.getTick(), ownerId,
								"live pack after the select", "present, no body lost", after));
				helper.assertTrue(after.isPresent() && MegumiShikigami.NUE.id().equals(after.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "select", helper.getTick(), ownerId,
								"pack type after the select", MegumiShikigami.NUE.id(),
								after.map(PackView::type).orElse("absent")));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				clearLedger(caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * A cooling entry is refused by the vessel seam and changes nothing, while an entry that is up is
	 * accepted in the same state (R16 server half, and the ledger is per type, not per player).
	 */
	@GameTest(maxTicks = 60)
	public void aCoolingTypeIsRefusedAndAFreeOneIsAccepted(GameTestHelper helper) {
		String fixture = "aCoolingTypeIsRefusedAndAFreeOneIsAccepted";
		helper.setBlock(CASTER_FEET.below(), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// Somewhere other than the default, so "unchanged" is a real assertion.
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
				MegumiSummonCooldowns.start(ownerId, MegumiShikigami.RABBITS, helper.getLevel().getGameTime() + MegumiShikigamiProfile.RABBITS_RECALL_COOLDOWN_TICKS);

				boolean refused = JujutsuCharacters.of(caster).selectShikigami(caster, MegumiShikigami.RABBITS.id());
				helper.assertFalse(refused, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"refuse", helper.getTick(), ownerId, "selectShikigami on a cooling type", "false", refused));
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.TOAD,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(), ownerId,
								"selection after the refused click", MegumiShikigami.TOAD, selected));
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(), ownerId,
								"PRIMARY cooldown (a refusal costs nothing)", "0", remaining));

				boolean accepted = JujutsuCharacters.of(caster).selectShikigami(caster, MegumiShikigami.ELEPHANT.id());
				helper.assertTrue(accepted, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"accept", helper.getTick(), ownerId, "selectShikigami on a type that is up", "true", accepted));
				MegumiShikigami after = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(after == MegumiShikigami.ELEPHANT,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "accept", helper.getTick(), ownerId,
								"selection after the accepted click", MegumiShikigami.ELEPHANT, after));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				clearLedger(caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** An id this roster does not have is refused rather than silently accepted (R16 server half). */
	@GameTest(maxTicks = 60)
	public void anUnknownRosterIdIsRefused(GameTestHelper helper) {
		String fixture = "anUnknownRosterIdIsRefused";
		helper.setBlock(CASTER_FEET.below(), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
				boolean accepted = JujutsuCharacters.of(caster).selectShikigami(caster, "nonexistent");
				helper.assertFalse(accepted, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"refuse", helper.getTick(), ownerId, "selectShikigami with an unknown id", "false", accepted));
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.NUE,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(), ownerId,
								"selection after the refused id", MegumiShikigami.NUE, selected));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				clearLedger(caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** The short press steps over a cooling entry instead of landing on it (R1 server half). */
	@GameTest(maxTicks = 60)
	public void theCycleStepsOverACoolingType(GameTestHelper helper) {
		String fixture = "theCycleStepsOverACoolingType";
		helper.setBlock(CASTER_FEET.below(), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// The selection is DOGS, so the canonical next entry is NUE; cooling NUE must make the cycle
				// land one step further along instead of on the entry the player cannot use.
				MegumiSummonCooldowns.start(ownerId, MegumiShikigami.NUE, helper.getLevel().getGameTime() + MegumiShikigamiProfile.NUE_RECALL_COOLDOWN_TICKS);
				boolean cycled = MegumiShikigamiRuntime.tryCycle(caster, false);
				helper.assertTrue(cycled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cycle", helper.getTick(), ownerId, "tryCycle result", "true", cycled));
				MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
				helper.assertTrue(selected == MegumiShikigami.TOAD,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cycle", helper.getTick(), ownerId,
								"selection after cycling past a cooling NUE", MegumiShikigami.TOAD, selected));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				clearLedger(caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * Several selects in a row land on the last one (R15 server half) and none of them despawns what is
	 * already out (R19): a Nue pack is summoned first and must still be standing at the end.
	 */
	@GameTest(maxTicks = 100)
	public void repeatedSelectsLandOnTheLastOneAndDespawnNothing(GameTestHelper helper) {
		String fixture = "repeatedSelectsLandOnTheLastOneAndDespawnNothing";
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, CASTER_FEET, 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
			clearLedger(caster);
		}));

		helper.runAtTickTime(CLICK_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			for (String id : new String[] { MegumiShikigami.NUE.id(), MegumiShikigami.RABBITS.id(),
					MegumiShikigami.ELEPHANT.id() }) {
				boolean accepted = JujutsuCharacters.of(caster).selectShikigami(caster, id);
				helper.assertTrue(accepted, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"clicks", helper.getTick(), ownerId, "selectShikigami " + id, "true", accepted));
			}
			MegumiShikigami selected = MegumiShikigamiSelection.selected(ownerId);
			helper.assertTrue(selected == MegumiShikigami.ELEPHANT,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "clicks", helper.getTick(), ownerId,
							"selection after three clicks", MegumiShikigami.ELEPHANT, selected));
			int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
			helper.assertTrue(remaining == 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "clicks", helper.getTick(), ownerId,
							"PRIMARY cooldown (selecting is free)", "0", remaining));
			Optional<PackView> pack = MegumiShikigamiRuntime.packView(helper.getLevel().getServer(), ownerId);
			helper.assertTrue(pack.isPresent() && pack.get().aliveBodies() > 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "clicks", helper.getTick(), ownerId,
							"live Nue pack after selecting two other types", "present with bodies", pack));
			helper.assertTrue(pack.isPresent() && MegumiShikigami.NUE.id().equals(pack.get().type()),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "clicks", helper.getTick(), ownerId,
							"pack type (a selection never swaps the world)", MegumiShikigami.NUE.id(),
							pack.map(PackView::type).orElse("absent")));
		}));

		helper.runAtTickTime(CLICK_TICK + 2, () -> {
			try {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				clearLedger(caster);
			} finally {
				helper.succeed();
			}
		});
	}

	/**
	 * The roster ledger is not part of the shared fixture, so each scenario clears exactly its own row
	 * here — never the whole map, which would wipe a scenario running beside it.
	 */
	private static void clearLedger(ServerPlayer caster) {
		MegumiSummonCooldowns.clear(caster.getUUID());
	}
}
