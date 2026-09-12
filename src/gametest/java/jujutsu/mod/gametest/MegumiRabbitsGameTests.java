package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiRabbitEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;

/**
 * Rabbit Escape (Ten Shadows selection layer) server scenarios — summon + upkeep top-up (S1),
 * anchor loss (S2), non-anchor replacement (S3), lifetime expiry (S4), and the bump shove (S5) —
 * exercised through the production runtime call {@code MegumiShikigamiRuntime.tryPrimary}, the
 * same hop the vessel router reaches for the PRIMARY slot.
 *
 * <p><b>Pinned literals.</b> S2/S4 assert the literal 200/120 ticks rather than the profile
 * constants ON PURPOSE: the red-proof mutates the profile row (200-&gt;201, 120-&gt;121) and the
 * assert must follow the balance contract, not the constant. Every other number references
 * {@link MegumiShikigamiProfile} directly.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * bodies are found by owner-UUID scan, never by bounds — and the S5 victim is parked by
 * subtracting the structure origin from the rabbit's absolute position first. The summon/kill
 * steps sit on different ticks — the runtime drops same-tick duplicate technique presses.
 * {@code hurtServer} on the body is gated on the ACTIVE phase, so kills wait past the 10-tick
 * materialization with an explicit ACTIVE premise assert and go through the production damage
 * pipeline (a bare {@code die(...)} never synchronously reconciles, so a same-tick cooldown read
 * after it sees 0). Static state (selection map, both pack maps, both cooldown slots) is cleared
 * in setup and on every success/failure path.
 */
public final class MegumiRabbitsGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int KILL_TICK = 25;
	private static final int ZOMBIE_TICK = 14;

	/**
	 * S2 pins this row: losing the anchor disperses the pack at exactly the Rabbit Escape death
	 * price. Deliberately NOT {@code MegumiShikigamiProfile.RABBITS_DEATH_COOLDOWN_TICKS} — the
	 * red-proof mutates that row.
	 */
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 200;
	/**
	 * S4 pins this row: lifetime expiry dismisses the pack at exactly the expiry price.
	 * Deliberately NOT {@code MegumiShikigamiProfile.RABBITS_EXPIRY_COOLDOWN_TICKS} — the
	 * red-proof mutates that row.
	 */
	private static final int EXPECTED_EXPIRY_COOLDOWN_TICKS = 120;

	/**
	 * S1 — selecting RABBITS and pressing the technique key summons the swarm with its hidden
	 * anchor, and one upkeep window later the pack reads full strength: the ring may place fewer
	 * bodies than the swarm row, so the binding assert is the topped-up count, not the initial one.
	 */
	@GameTest(maxTicks = 60)
	public void rabbitsSummonTopsUpToFullSwarm(GameTestHelper helper) {
		String fixture = "rabbitsSummonTopsUpToFullSwarm";
		layFloor(helper);
		BlockPos casterFeet = new BlockPos(2, 1, 2);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

			Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
			helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
			helper.assertTrue(MegumiShikigami.RABBITS.id().equals(view.get().type()),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"pack type", MegumiShikigami.RABBITS.id(), view.get().type()));
			helper.assertTrue(view.get().anchorId() != null && !view.get().anchorId().isEmpty(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"anchor id present", "non-empty", String.valueOf(view.get().anchorId())));
			helper.assertTrue(level.getEntity(UUID.fromString(view.get().anchorId()))
					instanceof MegumiRabbitEntity anchor && ownerId.equals(anchor.ownerUuid()),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"anchor body owned by caster", "owned rabbit", String.valueOf(view.get().anchorId())));
		}));

		helper.runAtTickTime(
				SUMMON_TICK + MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS + 5, () -> {
					try {
						UUID ownerId = caster.getUUID();
						Optional<PackView> view =
								MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
						helper.assertTrue(view.isPresent(),
								MegumiShikigamiTestFixtures.diagnostic(fixture,
										"topup", helper.getTick(), ownerId, "pack view present", "present", "absent"));
						helper.assertTrue(view.get().aliveBodies() == MegumiShikigamiProfile.RABBITS_SWARM_SIZE,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "topup", helper.getTick(), ownerId,
										"alive bodies after one upkeep window",
										MegumiShikigamiProfile.RABBITS_SWARM_SIZE, view.get().aliveBodies()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * S2 — killing the anchor disperses the whole swarm through the death reconciliation: PRIMARY
	 * reads exactly 200 ticks and the pack record is gone, even though most bodies are untouched.
	 */
	@GameTest(maxTicks = 60)
	public void rabbitsAnchorLossChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "rabbitsAnchorLossChargesDeathCooldownAndClearsPack";
		layFloor(helper);
		BlockPos casterFeet = new BlockPos(2, 1, 2);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(view.get().anchorAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "anchor alive before kill", "true", view.get().anchorAlive()));
				if (!(level.getEntity(UUID.fromString(view.get().anchorId()))
						instanceof MegumiRabbitEntity anchor)) {
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"kill", helper.getTick(), ownerId, "anchor body present", "present", "absent"));
					return;
				}
				helper.assertTrue(anchor.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "anchor ACTIVE before kill", "true", anchor.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), exactly like
				// the proven Nue death scenario: a bare die() never synchronously reconciles, so the
				// same-tick cooldown read below would see 0.
				boolean damaged = anchor.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"PRIMARY death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * S3 — killing a NON-anchor rabbit proves the upkeep: within one respawn window plus slack the
	 * alive count is back at full strength while the pack (and its anchor) survives.
	 */
	@GameTest(maxTicks = 100)
	public void rabbitsNonAnchorLossIsReplacedByUpkeep(GameTestHelper helper) {
		String fixture = "rabbitsNonAnchorLossIsReplacedByUpkeep";
		layFloor(helper);
		BlockPos casterFeet = new BlockPos(2, 1, 2);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
			helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"kill", helper.getTick(), ownerId, "pack view present", "present", "absent"));
			UUID anchorId = UUID.fromString(view.get().anchorId());
			MegumiRabbitEntity victim = rabbitsOwnedBy(level, ownerId).stream()
					.filter(body -> !body.getUUID().equals(anchorId))
					.findFirst()
					.orElse(null);
			helper.assertTrue(victim != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"kill", helper.getTick(), ownerId, "non-anchor rabbit present", "present", "absent"));
			helper.assertTrue(victim.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"kill", helper.getTick(), ownerId, "victim ACTIVE before kill", "true", victim.combatEnabled()));
			// Production damage pipeline, not die(): die() never synchronously reconciles the pack.
			boolean damaged = victim.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));
		}));

		helper.runAtTickTime(
				KILL_TICK + MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS + 5, () -> {
					try {
						UUID ownerId = caster.getUUID();
						Optional<PackView> view =
								MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
						helper.assertTrue(view.isPresent(),
								MegumiShikigamiTestFixtures.diagnostic(fixture,
										"replaced", helper.getTick(), ownerId, "pack survives", "present", "absent"));
						helper.assertTrue(view.get().anchorAlive(),
								MegumiShikigamiTestFixtures.diagnostic(fixture,
										"replaced", helper.getTick(), ownerId, "anchor alive", "true", view.get().anchorAlive()));
						helper.assertTrue(view.get().aliveBodies() == MegumiShikigamiProfile.RABBITS_SWARM_SIZE,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "replaced", helper.getTick(), ownerId,
										"alive bodies after replacement",
										MegumiShikigamiProfile.RABBITS_SWARM_SIZE, view.get().aliveBodies()));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * S4 — the swarm expires after its lifetime: the pack record is gone and PRIMARY carries the
	 * expiry price. The cooldown read is a band, not an exact tick: expiry fires on a body tick a
	 * few ticks before this assert, so the remaining time has already decayed.
	 */
	@GameTest(maxTicks = 360)
	public void rabbitsSwarmExpiresAfterLifetime(GameTestHelper helper) {
		String fixture = "rabbitsSwarmExpiresAfterLifetime";
		layFloor(helper);
		BlockPos casterFeet = new BlockPos(2, 1, 2);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(
				SUMMON_TICK + MegumiShikigamiProfile.RABBITS_LIFETIME_TICKS + 8, () -> {
					try {
						UUID ownerId = caster.getUUID();
						MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "expired", caster);
						int remaining =
								CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
						helper.assertTrue(remaining > 0 && remaining <= EXPECTED_EXPIRY_COOLDOWN_TICKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "expired", helper.getTick(), ownerId,
										"PRIMARY expiry cooldown", "(0, " + EXPECTED_EXPIRY_COOLDOWN_TICKS + "]",
										remaining));
					} finally {
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
				});
		helper.runAtTickTime(320, () -> helper.succeed());
	}

	/**
	 * S5 — a NoAI zombie parked next to a live rabbit takes the swarm's only hostile interaction
	 * within one bump window plus slack: a shove away from a swarm body plus SLOWNESS. NoAI so the
	 * zombie's own pathing cannot fake the impulse; the direction assert accepts ANY swarm body
	 * because neighbours hop within bump range of the victim.
	 *
	 * <p><b>NoAI oracle note.</b> A NoAI mob is fully frozen: an external velocity lands in the
	 * velocity field but never integrates into position. So this scenario asserts the FIELD
	 * (knockback speed + direction) and the SLOWNESS state — never displacement. The slowed-AI
	 * idiom (an AI zombie with Slowness 100, which does travel) is deliberately NOT used here: a
	 * moving-capable zombie parked adjacent to 4-HP bodies would melee them during the window and
	 * could disperse the pack mid-assert.
	 */
	@GameTest(maxTicks = 80)
	public void rabbitsBumpShovesAdjacentZombie(GameTestHelper helper) {
		String fixture = "rabbitsBumpShovesAdjacentZombie";
		layFloor(helper);
		layRoof(helper);
		BlockPos casterFeet = new BlockPos(2, 1, 2);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		long pollStart = ZOMBIE_TICK + 1;
		long pollEnd = ZOMBIE_TICK + MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS + 5;
		AtomicBoolean bumped = new AtomicBoolean();

		helper.runAtTickTime(ZOMBIE_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiRabbitEntity> bodies = rabbitsOwnedBy(level, ownerId);
				helper.assertTrue(!bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"park", helper.getTick(), ownerId, "live rabbits present", "non-empty", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture,
								"park", helper.getTick(), ownerId, "swarm ACTIVE before parking", "true",
								bodies.get(0).combatEnabled()));
				MegumiRabbitEntity nearest = bodies.get(0);
				// Structure-relative parking: rabbit positions are absolute world coordinates with
				// the run's random offset, while spawnMob takes structure-relative ones. Passing an
				// absolute pos as relative strands the victim millions of blocks out (alive, but the
				// bump can never reach it) — so subtract the structure origin first.
				BlockPos origin = helper.absolutePos(BlockPos.ZERO);
				BlockPos zombieFeet = BlockPos.containing(nearest.position()).subtract(origin).offset(1, 0, 0);
				Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
				zombie.setPersistenceRequired();
				ZombieHolder.hold(zombie);
			} catch (RuntimeException | AssertionError failure) {
				ZombieHolder.release(level);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		for (long tick = pollStart; tick <= pollEnd; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = ZombieHolder.zombie();
				if (bumped.get() || zombie == null || zombie.isRemoved()) {
					return;
				}
				if (!zombie.hasEffect(MobEffects.SLOWNESS)) {
					if (pollTick == pollEnd) {
						try {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"bump", helper.getTick(), caster.getUUID(), "bump landed within window",
									"slowness present by tick " + pollEnd, "absent"));
						} finally {
							ZombieHolder.release(level);
							MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						}
					}
					return;
				}
				try {
					assertBump(helper, fixture, caster, level, zombie);
					bumped.set(true);
					ZombieHolder.release(level);
				} finally {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}

	private static void assertBump(GameTestHelper helper, String fixture, ServerPlayer caster,
			ServerLevel level, Zombie zombie) {
		UUID ownerId = caster.getUUID();
		long tick = helper.getTick();
		helper.assertTrue(zombie.getEffect(MobEffects.SLOWNESS) != null,
				MegumiShikigamiTestFixtures.diagnostic(fixture,
						"bump", tick, ownerId, "slowness applied", "present", "absent"));
		int duration = zombie.getEffect(MobEffects.SLOWNESS).getDuration();
		helper.assertTrue(duration >= 15 && duration <= MegumiShikigamiProfile.RABBITS_BUMP_SLOWNESS_TICKS,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "bump", tick, ownerId,
						"slowness duration", "[15, " + MegumiShikigamiProfile.RABBITS_BUMP_SLOWNESS_TICKS + "]",
						duration));
		helper.assertTrue(zombie.getEffect(MobEffects.SLOWNESS).getAmplifier() == 0,
				MegumiShikigamiTestFixtures.diagnostic(fixture, "bump", tick, ownerId,
						"slowness amplifier", "0", zombie.getEffect(MobEffects.SLOWNESS).getAmplifier()));

		double vx = zombie.getDeltaMovement().x;
		double vz = zombie.getDeltaMovement().z;
		double speed = Math.sqrt(vx * vx + vz * vz);
		helper.assertTrue(speed >= 0.05, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"bump", tick, ownerId, "knockback speed", ">= 0.05", speed));
		boolean awayFromSomeBody = false;
		for (MegumiRabbitEntity body : rabbitsOwnedBy(level, ownerId)) {
			double dx = zombie.getX() - body.getX();
			double dz = zombie.getZ() - body.getZ();
			if (dx * vx + dz * vz > 0.0) {
				awayFromSomeBody = true;
				break;
			}
		}
		helper.assertTrue(awayFromSomeBody, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"bump", tick, ownerId, "impulse away from a swarm body", "dot > 0", "dot <= 0"));
	}

	/** Every live rabbit owned by {@code ownerId}: owner-filtered, never bounds-filtered. */
	private static List<MegumiRabbitEntity> rabbitsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiRabbitEntity> owned = new ArrayList<>();
		for (MegumiRabbitEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiRabbitEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/** Stone floor under the whole ring patch: the swarm spawns on a 2.2-block ring. */
	private static void layFloor(GameTestHelper helper) {
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 5; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}

	/** One opaque lid over the patch: kills sky-burn flakiness on the parked zombie. */
	private static void layRoof(GameTestHelper helper) {
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 5; dz++) {
				helper.setBlock(new BlockPos(dx, 4, dz), Blocks.STONE);
			}
		}
	}

	/**
	 * The parked zombie outlives its tick callback, so it rides a static holder instead of a
	 * captured local. Single-test use: every path (bump, timeout, setup failure) releases it.
	 */
	private static final class ZombieHolder {
		private static Zombie zombie;

		private ZombieHolder() {}

		private static void hold(Zombie parked) {
			zombie = parked;
		}

		private static Zombie zombie() {
			return zombie;
		}

		private static void release(ServerLevel level) {
			try {
				if (zombie != null && !zombie.isRemoved()) {
					zombie.discard();
				}
			} catch (RuntimeException ignored) {
				// Best-effort cleanup on an already-failing test.
			} finally {
				zombie = null;
			}
		}
	}
}
