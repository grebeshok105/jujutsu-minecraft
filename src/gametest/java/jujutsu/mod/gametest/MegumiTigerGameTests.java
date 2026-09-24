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
import jujutsu.mod.character.megumi.MegumiTigerEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;

/**
 * Tiger Funeral server scenarios — roster-common skeleton only for now (summon S1, manual recall
 * S2). The combo signature matrix lands with the tiger brain worker; every row it adds
 * must follow the same traps as the Nue suite: owner-UUID body scans (random world offset),
 * literal cooldown pins (the red-proof mutates the profile row), intermediate steps wrapped in
 * {@link MegumiShikigamiTestFixtures#runGuarded}, and {@code cleanupCaster} on every terminal path.
 */
public final class MegumiTigerGameTests {

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;

	/** S2 pins the literal row, NOT {@code MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS}. */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 250;

	/** S1 — selecting TIGER and pressing the technique key summons exactly one live body. */
	@GameTest(maxTicks = 60)
	public void tigerSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "tigerSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TIGER);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.TIGER.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.TIGER.id(), view.get().type()));

				List<MegumiTigerEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiTigerEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned tiger bodies", "1", bodies.size()));
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

	/** S2 — a manual recall removes the body and costs exactly the tiger recall cooldown. */
	@GameTest(maxTicks = 80)
	public void tigerRecallRemovesBodyAndChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "tigerRecallRemovesBodyAndChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
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
						ownerId, MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"tiger recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
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
}
