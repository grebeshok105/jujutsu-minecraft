package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Cross-body soft coordination (issue #107 §12, plan row R12): while one body is committed to a
 * victim, an ally de-prefers actions that would wreck the setup. The pinned case is the elephant's
 * jet against the toad's hold — the jet would shove and soak a victim the toad is already holding,
 * so {@code MegumiElephantBrain} skips the jet while the shared combat context names the target as
 * an ally's work and keeps melee-approaching instead.
 *
 * <p><b>Why the oracle is what it is.</b> The jet's two observable footprints are the body planting
 * itself ({@code setNoAi(true)} for the windup plus the burst, on a combat-enabled body — the same
 * plant the jet GameTests detect) and the {@code MEGUMI_SOAKED} marker its pulses apply. Neither may
 * appear while the toad holds the zombie. Health is NOT the oracle: the elephant is allowed to
 * melee the held victim, so a health drop proves nothing about the jet.
 *
 * <p><b>Sequencing.</b> The toad is sicced alone first so its grab intent exists before the
 * elephant is even summoned — the shared context can sit up to
 * {@code COORDINATION_SCAN_TICKS} stale, and siccing both bodies in one press would race the toad's
 * windup against the elephant's first jet decision. Summoning the elephant only after the hold is
 * observed makes the ordering deterministic: every jet decision the elephant ever makes happens
 * while the victim is already an ally's committed work.
 *
 * <p><b>Red-proof.</b> Deleting the {@code intentTargets} gate in {@code MegumiElephantBrain.tick}
 * lets the jet start on the first brain tick after the sic — the plant flag trips within a couple
 * of ticks, long before the hold ends.
 */
public final class MegumiCoordinationGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int SIC_TICK = 24;
	/** The toad's grab must land inside this window or the scenario never reaches the jet gate. */
	private static final int HOLD_BY_TICK = SIC_TICK + 40;
	/** Ticks the elephant must spend ACTIVE, marked, and held off before the proof counts. A
	 * missing gate lets the jet start within ~2 ticks of the mark, so ten is a wide margin. */
	private static final int REQUIRED_HELD_OFF_TICKS = 10;
	private static final int DEADLINE_TICK = 220;

	/**
	 * R12 — the elephant holds its jet while the toad holds the same victim: the toad grabs the
	 * zombie, the elephant is summoned and sicced onto it mid-hold, and for the rest of the hold
	 * the elephant never plants for a jet and the zombie is never soaked. The zombie is a 200-HP
	 * wall so the elephant's legal melee cannot end the hold early and shrink the proof window.
	 */
	@GameTest(maxTicks = 260)
	public void elephantHoldsItsJetWhileTheToadHoldsTheVictim(GameTestHelper helper) {
		String fixture = "elephantHoldsItsJetWhileTheToadHoldsTheVictim";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		paveFloor(helper, 0, 7, 0, 7);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(6, 1, 5));
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(200.0f);
		// Full AI with zeroed speed, same as the grab tests: physics stay live so the grip pin
		// works, while Slowness 100 keeps the victim from walking out of the arena.
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicLong holdTick = new AtomicLong(-1L);
		AtomicLong markTick = new AtomicLong(-1L);
		AtomicInteger heldOffTicks = new AtomicInteger();
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "toad tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> toads = toadOwnedBy(level, ownerId);
				helper.assertTrue(toads.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned toad bodies in level", "1", toads.size()));
				helper.assertTrue(toads.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"toad ACTIVE before sic", "true", toads.get(0).combatEnabled()));
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

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				try {
					List<MegumiToadEntity> toads = toadOwnedBy(level, ownerId);
					helper.assertTrue(toads.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick, ownerId,
									"toad body present", "1", toads.size()));
					MegumiToadEntity toad = toads.get(0);
					boolean holding = toad.isHolding() && zombie.getUUID().equals(toad.grabbedUuid());

					if (holdTick.get() < 0) {
						// Phase 1: wait for the grab to land, then summon the elephant mid-hold.
						helper.assertTrue(pollTick <= HOLD_BY_TICK,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick, ownerId,
										"toad grab lands within the window", "<= " + HOLD_BY_TICK, pollTick));
						if (holding) {
							holdTick.set(pollTick);
							MegumiShikigamiSelection.set(ownerId, MegumiShikigami.ELEPHANT);
							boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
							helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"summon", pollTick, ownerId, "elephant tryPrimary mid-hold", "true", summoned));
						}
						return;
					}

					if (markTick.get() < 0) {
						// Phase 2: the elephant is out — sic it onto the held zombie as soon as it
						// turns combat-enabled. The toad keeps holding through the re-mark: its
						// brain ignores sic targets while a grab is running.
						if (!holding) {
							helper.assertTrue(false,
									MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick, ownerId,
											"toad still holds while the elephant materializes", "holding",
											"released at tick " + pollTick + " (hold began " + holdTick.get() + ")"));
						}
						List<MegumiElephantEntity> elephants = elephantsOwnedBy(level, ownerId);
						helper.assertTrue(elephants.size() == 1,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", pollTick, ownerId,
										"owned elephant bodies in level", "1", elephants.size()));
						MegumiElephantEntity elephant = elephants.get(0);
						if (!elephant.combatEnabled()) {
							return;
						}
						TodoSwapTestFixtures.aimAt(caster,
								zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
						boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
						helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"sic", pollTick, ownerId, "elephant trySic onto the held zombie", "true", sicced));
						markTick.set(pollTick);
						return;
					}

					// Phase 3: the elephant is ACTIVE and marked on the held zombie. Every tick the
					// hold continues, the jet's footprints must stay absent: no plant on a
					// combat-enabled body, no soak marker on the victim.
					if (!holding) {
						helper.assertTrue(heldOffTicks.get() >= REQUIRED_HELD_OFF_TICKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", pollTick, ownerId,
										"held-off window before the release", ">= " + REQUIRED_HELD_OFF_TICKS,
										heldOffTicks.get()));
						succeed(helper, caster, zombie, done);
						return;
					}
					List<MegumiElephantEntity> elephants = elephantsOwnedBy(level, ownerId);
					if (elephants.isEmpty()) {
						return;
					}
					MegumiElephantEntity elephant = elephants.get(0);
					if (elephant.combatEnabled()) {
						// The mark must actually be on the held zombie — a sic that resolved to
						// nothing would leave the elephant unmarked and let every check below
						// pass vacuously.
						helper.assertTrue(elephant.getTarget() == zombie,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", pollTick, ownerId,
										"elephant marked on the held zombie", "zombie",
										String.valueOf(elephant.getTarget())));
						heldOffTicks.incrementAndGet();
						helper.assertTrue(!elephant.isNoAi(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", pollTick, ownerId,
										"elephant plants for a jet while the toad holds the victim",
										"never", "planted at tick " + pollTick));
						helper.assertTrue(!zombie.hasEffect(JujutsuEffects.MEGUMI_SOAKED),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "jet", pollTick, ownerId,
										"MEGUMI_SOAKED on the held victim", "absent", "present"));
					}
					if (pollTick == DEADLINE_TICK) {
						succeed(helper, caster, zombie, done);
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	private static void succeed(GameTestHelper helper, ServerPlayer caster, Zombie zombie,
			AtomicBoolean done) {
		done.set(true);
		zombie.discard();
		MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
		helper.succeed();
	}

	/**
	 * Owner-filtered body scan, not bounds-filtered: the world offset is random per run and bodies
	 * drift, so a structure bounds scan could miss a live body or catch a sibling test's.
	 */
	private static List<MegumiToadEntity> toadOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiToadEntity> owned = new ArrayList<>();
		for (MegumiToadEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiToadEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

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

	/** Stone floor patch so the ground-placement scan finds footing under every candidate. */
	private static void paveFloor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
