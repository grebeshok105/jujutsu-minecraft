package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiElephantPolicy;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Max Elephant (Ten Shadows selection layer) server scenarios — summon (S1), the trunk jet on a
 * plain target (S2), the jet sparing its owner (S3), recall (S4) — exercised through the
 * production runtime calls {@code MegumiShikigamiRuntime.tryPrimary} / {@code trySic}.
 *
 * <p><b>Pinned literals.</b> S4 asserts the literal 260 ticks rather than the profile constant ON
 * PURPOSE: the red-proof mutates the profile row (260-&gt;261) and the assert must follow the
 * balance contract, not the constant. S2/S3 assert the literal [0.9, 1.1] drop band for the same
 * reason (the {@code ELEPHANT_JET_DAMAGE} row): one pulse at face value, shaved ~6% by the
 * zombie's armour, detected on the first soaked tick (exactly one pulse by construction), while
 * every timing number references {@link MegumiShikigamiProfile} directly.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so bodies are found by owner-UUID scan,
 * never by bounds. The summon/sic steps sit on different ticks — the runtime drops same-tick
 * duplicate presses. The jet brain only runs in the ACTIVE phase, so the sic waits past the
 * 30-tick materialization with an explicit premise assert. The jet target is a NoAI zombie with a
 * stone roof one block above its head (kills sky-burn flakiness; the near-horizontal jet passes
 * well below it). Static state is cleared in setup and on every success/failure path.
 */
public final class MegumiElephantGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	/** Past the 30-tick materialization with margin: the jet brain needs the ACTIVE phase. */
	private static final int SIC_TICK = 36;
	private static final int JET_WINDOW_TICKS = MegumiShikigamiProfile.ELEPHANT_JET_WINDUP_TICKS
			+ MegumiShikigamiProfile.ELEPHANT_JET_DURATION_TICKS + 5;

	/**
	 * S4 pins this row: a manual recall costs exactly the elephant recall cooldown. Deliberately
	 * NOT {@code MegumiShikigamiProfile.ELEPHANT_RECALL_COOLDOWN_TICKS}.
	 */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 260;
	/**
	 * S2/S3 pin this band, not the profile row: one jet pulse deals
	 * {@code ELEPHANT_JET_DAMAGE} face value, but the zombie's 2 armour points shave ~6% off
	 * (observed 0.94 in the gate), so the floor sits just below face value. Deliberately NOT
	 * {@code MegumiShikigamiProfile.ELEPHANT_JET_DAMAGE} — the red-proof mutates that row.
	 * The poll succeeds on the FIRST soaked tick, which always carries exactly one pulse (pulses
	 * fire every 2 ticks, polls run every tick), so the ceiling pins single-pulse detection: two
	 * pulses would read ~1.88 and trip it.
	 */
	private static final double EXPECTED_JET_DROP_LOW = 0.9;
	private static final double EXPECTED_JET_DROP_HIGH = 1.1;
	/**
	 * S1 — selecting ELEPHANT and pressing the technique key summons exactly one live body with no
	 * cooldown: the pack view reads type "elephant" with one anchored body, one
	 * {@link MegumiElephantEntity} owned by the caster sits in the level, and PRIMARY stays at 0.
	 */
	@GameTest(maxTicks = 60)
	public void elephantSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "elephantSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 4, 0, 4);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.ELEPHANT.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.ELEPHANT.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));
				helper.assertTrue(pack.anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"anchor alive", "true", pack.anchorAlive()));

				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));

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
	 * S2 — sic on a zombie 5 blocks away on a diagonal ends in a trunk jet: the zombie's health
	 * drops by one armour-shaved pulse and it carries {@code MEGUMI_SOAKED} within
	 * windup+duration+5 ticks. The diagonal (never axis-aligned) keeps a mirrored aim honest: an
	 * X-flipped yaw would hose the empty lane beside the zombie instead.
	 */
	@GameTest(maxTicks = 150)
	public void elephantJetSoaksAndDamagesTarget(GameTestHelper helper) {
		runJetScenario(helper, "elephantJetSoaksAndDamagesTarget", false);
	}

	/**
	 * S3 — same jet with the owner teleported into the corridor (3 blocks down-jet, 1 block to
	 * the side): the zombie in the same corridor takes damage + soak while the owner takes NO
	 * damage and NO soak. The soak contrast is the binding oracle — the mock damage pipeline
	 * refuses health edits on players, but effect application is real, so an owner carrying soak
	 * proves the friendly-fire gate is missing.
	 */
	@GameTest(maxTicks = 150)
	public void elephantJetSparesItsOwner(GameTestHelper helper) {
		runJetScenario(helper, "elephantJetSparesItsOwner", true);
	}

	/**
	 * S4 — pressing the key again while the elephant is out recalls it: PRIMARY reads exactly
	 * 260 ticks and the pack record is gone.
	 */
	@GameTest(maxTicks = 60)
	public void elephantRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "elephantRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 4, 0, 4);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
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

				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});

		// The recall sink plays out after the record is gone; past it +2 no owned body remains.
		helper.runAtTickTime(RECALL_TICK + MegumiShikigamiProfile.ELEPHANT_RECALL_TICKS + 2, () -> {
			List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"gone", helper.getTick(), caster.getUUID(), "owned elephant bodies in level", "0", bodies.size()));
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	private void runJetScenario(GameTestHelper helper, String fixture, boolean ownerInCorridor) {
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 6);
		paveFloor(helper, 0, 6, 0, 7);
		// One opaque block above the zombie's head: kills sky-burn flakiness. The near-horizontal
		// jet (trunk ~+1.9 descending to the zombie's chest ~+1.0) passes well below it, as do all
		// sight lines.
		helper.setBlock(new BlockPos(5, 4, 6), Blocks.STONE);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<BlockPos> laidFloor = new AtomicReference<>();
		AtomicBoolean jetted = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiElephantEntity> bodies = elephantsOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned elephant bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));
				if (ownerInCorridor) {
					// Stand the owner inside the coming corridor: 3 blocks down-jet from the body,
					// 0.7 blocks to the side. The owner's chest rides ~1 below the trunk, so the true
					// 3D lateral reads ~1.2 against the 1.4 half-width — inside with margin, while a
					// missing friendly-fire gate still hoses them. Everything here is world
					// coordinates straight off live entities: structure-relative math would silently
					// misplace the owner whenever the test structure is rotated, so the floor is
					// laid with ServerLevel.setBlock (absolute) and the feet level is read off the
					// live body. The brain plants the body for the jet's duration, so this geometry
					// holds from sic tick to impact with nothing moving but the water.
					Vec3 flat = zombie.position().subtract(bodies.get(0).position());
					flat = new Vec3(flat.x, 0.0, flat.z).normalize();
					Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
					Vec3 bodyFeet = bodies.get(0).position();
					Vec3 spot = bodyFeet.add(flat.scale(3.0)).add(side.scale(0.7));
					BlockPos floor = new BlockPos((int) Math.floor(spot.x),
							(int) Math.floor(bodyFeet.y) - 1, (int) Math.floor(spot.z));
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
						}
					}
					caster.teleportTo(level, spot.x, bodyFeet.y, spot.z, Set.of(), 0.0f, 0.0f, false);
					laidFloor.set(floor);
				}

				double before = zombie.getHealth();
				helper.assertTrue(before == zombie.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"zombie at full health", zombie.getMaxHealth(), before));
				healthBefore.set(before);

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

		long deadline = SIC_TICK + JET_WINDOW_TICKS;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (jetted.get() || zombie.isRemoved()) {
					return;
				}
				boolean soaked = zombie.hasEffect(JujutsuEffects.MEGUMI_SOAKED);
				double dropped = healthBefore.get() == null ? 0.0 : healthBefore.get() - zombie.getHealth();
				if (!soaked || dropped < EXPECTED_JET_DROP_LOW) {
					if (pollTick == deadline) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"jet", helper.getTick(), caster.getUUID(), "jet soaked and damaged within window",
									"soaked + drop in [" + EXPECTED_JET_DROP_LOW + ", " + EXPECTED_JET_DROP_HIGH + "]",
									"soaked=" + soaked + " dropped=" + dropped));
						} finally {
							zombie.discard();
							clearLaidFloor(level, laidFloor);
						}
					}
					return;
				}
			try {
				helper.assertTrue(dropped <= EXPECTED_JET_DROP_HIGH,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
								caster.getUUID(), "first soaked tick carries one pulse",
								"drop <= " + EXPECTED_JET_DROP_HIGH, dropped));
				if (ownerInCorridor) {
					List<MegumiElephantEntity> live = elephantsOwnedBy(level, caster.getUUID());
					helper.assertTrue(live.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owned elephant bodies in level", "1", live.size()));
					Vec3 look = live.get(0).getLookAngle();
					Vec3 trunk = live.get(0).getEyePosition()
							.add(look.scale(MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD));
					Vec3 chest = caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0);
					helper.assertTrue(MegumiElephantPolicy.inJetCorridor(chest, trunk, look.normalize(),
							MegumiShikigamiProfile.ELEPHANT_JET_LENGTH,
							MegumiShikigamiProfile.ELEPHANT_JET_HALF_WIDTH),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner inside the live corridor", "inside", "outside"));
					helper.assertTrue(!caster.hasEffect(JujutsuEffects.MEGUMI_SOAKED),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner carries no soak", "absent", "present"));
					helper.assertTrue(caster.getHealth() == caster.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", helper.getTick(),
									caster.getUUID(), "owner takes no damage",
									caster.getMaxHealth(), caster.getHealth()));
				}
					jetted.set(true);
					zombie.discard();
				} finally {
					clearLaidFloor(level, laidFloor);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	/**
	 * Every live elephant body owned by {@code ownerId} in {@code level}. Owner-filtered, never
	 * bounds-filtered: the world offset is random per run, so a structure bounds scan could miss a
	 * live body. Fresh mock UUIDs per test make the owner filter exact.
	 */
	private static List<MegumiElephantEntity> elephantsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiElephantEntity> owned = new ArrayList<>();
		for (MegumiElephantEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiElephantEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/**
	 * Removes the S3 owner's world-placed footing: absolute blocks outside the structure bounds
	 * are not cleared by the test teardown and would leak into the shared world.
	 */
	private static void clearLaidFloor(ServerLevel level, AtomicReference<BlockPos> laidFloor) {
		BlockPos floor = laidFloor.getAndSet(null);
		if (floor == null) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(floor.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}
	/** Stone floor patch so the ground-placement scan finds footing under every candidate. */
	private static void paveFloor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
