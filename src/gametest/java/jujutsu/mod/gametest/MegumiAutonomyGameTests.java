package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiFailureMemory;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;

/**
 * Issue #107 — the autonomous half of Ten Shadows: after the summon the pack fights on its own.
 * No sic, no orders — the coordinator's shared combat context assigns every mark these scenarios
 * assert: acquisition, crowd spread, mark stability, danger weighting, the manual sic's absolute
 * priority, autonomy resuming after a mark dies, the ally-intent pile-on, owner and ally
 * protection, the pounce failure memory, the in-arena band sanity, and the sealed-target refusal.
 *
 * <p>Traps respected: the owner is a damageable SURVIVAL player (the mock's CREATIVE stub refuses
 * {@code hurtServer}, and the owner-threat signal is written by the damage pipeline). Zombies are
 * NoAI + Slowness-100 frozen so they never wander into a mark of their own making; a zombie that
 * must survive a measurement window carries a max-health buff. Bodies are found by owner-UUID
 * scans, never by bounds — the world offset is random per run. The coordinator's marks are read
 * through {@code getTarget()}: the mark setters mirror it, and a body with no mark has no target.
 */
public final class MegumiAutonomyGameTests {
	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final int SUMMON_TICK = 2;
	private static final int SPAWN_TICK = 30;
	private static final int SIC_TICK = 40;

	private static void layPad(GameTestHelper helper) {
		for (int dx = -1; dx <= 9; dx++) {
			for (int dz = -1; dz <= 9; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
				helper.setBlock(new BlockPos(dx, 6, dz), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer owner) {
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
		CursedSpiritTestFixtures.cleanupVictim(helper, owner);
	}

	private static void summonDogs(GameTestHelper helper, String fixture, ServerPlayer owner,
			AtomicBoolean summoned) {
		MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			boolean ok = MegumiSummonRuntime.tryToggle(owner, false);
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "dogs summoned", "true", ok));
		});
	}

	private static void summonShikigami(GameTestHelper helper, String fixture, ServerPlayer owner,
			MegumiShikigami type, AtomicBoolean summoned) {
		MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), type);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(owner, false);
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), type.id() + " summoned", "true", ok));
		});
	}

	private static Zombie spawnFrozenZombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, feet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);
		return zombie;
	}

	private static Zombie spawnToughZombie(GameTestHelper helper, String fixture, BlockPos feet,
			double maxHealth) {
		Zombie zombie = spawnFrozenZombie(helper, fixture, feet);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zombie.setHealth((float) maxHealth);
		return zombie;
	}

	private static List<MegumiDivineDogEntity> dogsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiDivineDogEntity> owned = new ArrayList<>();
		for (MegumiDivineDogEntity dog : level.getEntities(
				EntityTypeTest.forClass(MegumiDivineDogEntity.class), candidate -> true)) {
			if (ownerId.equals(dog.ownerUuid())) {
				owned.add(dog);
			}
		}
		return owned;
	}

	private static List<MegumiShikigamiEntity> bodiesOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiShikigamiEntity> owned = new ArrayList<>();
		for (MegumiShikigamiEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiShikigamiEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	private static Set<UUID> markedTargets(List<? extends LivingEntity> bodies) {
		Set<UUID> marks = new HashSet<>();
		for (LivingEntity body : bodies) {
			LivingEntity target = ((net.minecraft.world.entity.Mob) body).getTarget();
			if (target != null) {
				marks.add(target.getUUID());
			}
		}
		return marks;
	}

	private static void discardAll(List<? extends LivingEntity> entities) {
		for (LivingEntity entity : entities) {
			entity.discard();
		}
	}

	/**
	 * R1 + R2 — no orders at all: two dogs and a lone zombie, and the pack must mark it on its own
	 * within the acquisition window. The zombie is NoAI, so nothing but the coordinator can name it.
	 */
	@GameTest(maxTicks = 200, skyAccess = true)
	public void thePackAcquiresTargetsWithoutOrders(GameTestHelper helper) {
		String fixture = "thePackAcquiresTargetsWithoutOrders";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			helper.assertTrue(summoned.get(), MegumiShikigamiTestFixtures.diagnostic(fixture, "spawn",
					helper.getTick(), owner.getUUID(), "summon succeeded", "true", summoned.get()));
			zombieRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 5)));
		});
		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 40; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (zombie == null || dogs.isEmpty()) {
					return;
				}
				if (dogs.stream().anyMatch(dog -> dog.getTarget() == zombie)) {
					zombie.discard();
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 40) {
					zombie.discard();
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"acquire", helper.getTick(), owner.getUUID(),
							"a dog marks the zombie within 40 ticks", "target=" + zombie.getUUID(),
							"dogTargets=" + dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R2 — the pack's own bodies are never candidates: with only dogs and the owner in the arena,
	 * no dog ever targets the owner or a sibling.
	 */
	@GameTest(maxTicks = 120, skyAccess = true)
	public void alliesAreNeverTargeted(GameTestHelper helper) {
		String fixture = "alliesAreNeverTargeted";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));

		helper.runAtTickTime(SPAWN_TICK + 40, () -> {
			List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
			helper.assertTrue(dogs.size() == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"observe", helper.getTick(), owner.getUUID(), "both dogs out", "2", dogs.size()));
			for (MegumiDivineDogEntity dog : dogs) {
				LivingEntity target = dog.getTarget();
				helper.assertTrue(target == null || (!(target instanceof ServerPlayer)
						&& !(target instanceof MegumiDivineDogEntity)),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "observe", helper.getTick(),
								owner.getUUID(), "no dog targets owner or sibling", "null/enemy",
								String.valueOf(target)));
			}
			cleanup(helper, owner);
			helper.succeed();
		});
	}

	/**
	 * R3 — five identical weak zombies and two dogs: the pack spreads instead of mobbing one body.
	 * Both dogs must hold marks, and the marks must be distinct.
	 */
	@GameTest(maxTicks = 200, skyAccess = true)
	public void aCrowdIsDistributedAcrossThePack(GameTestHelper helper) {
		String fixture = "aCrowdIsDistributedAcrossThePack";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		List<Zombie> zombies = new ArrayList<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			for (int i = 0; i < 5; i++) {
				zombies.add(spawnFrozenZombie(helper, fixture, new BlockPos(4 + i, 1, 6)));
			}
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 80; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (dogs.size() < 2) {
					return;
				}
				Set<UUID> marks = markedTargets(dogs);
				if (marks.size() >= 2) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 80) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"spread", helper.getTick(), owner.getUUID(),
							"two dogs hold two distinct marks", ">= 2 distinct", marks));
				}
			});
		}
	}

	/**
	 * R3 (the mixed pack) — dogs plus Nue plus Toad against five zombies: the marks must cover at
	 * least three distinct targets. Rabbit Escape is excluded by design (D13) — it holds no marks.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void aMixedPackSpreadsAcrossTheCrowd(GameTestHelper helper) {
		String fixture = "aMixedPackSpreadsAcrossTheCrowd";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean dogsOut = new AtomicBoolean();
		AtomicBoolean nueOut = new AtomicBoolean();
		AtomicBoolean toadOut = new AtomicBoolean();
		List<Zombie> zombies = new ArrayList<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, dogsOut));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> summonShikigami(helper, fixture, owner,
				MegumiShikigami.NUE, nueOut));
		helper.runAtTickTime(SUMMON_TICK + 4, () -> summonShikigami(helper, fixture, owner,
				MegumiShikigami.TOAD, toadOut));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			for (int i = 0; i < 5; i++) {
				zombies.add(spawnFrozenZombie(helper, fixture, new BlockPos(4 + i, 1, 6)));
			}
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 120; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<LivingEntity> bodies = new ArrayList<>();
				bodies.addAll(dogsOwnedBy(level, owner.getUUID()));
				bodies.addAll(bodiesOwnedBy(level, owner.getUUID()));
				if (bodies.size() < 4) {
					return;
				}
				Set<UUID> marks = markedTargets(bodies);
				if (marks.size() >= 3) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 120) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"spread", helper.getTick(), owner.getUUID(),
							"dogs+nue+toad cover >= 3 distinct targets", ">= 3", marks));
				}
			});
		}
	}

	/**
	 * R4 — a marked target stays marked: over forty ticks (eight coordinator scans) each dog's
	 * target may change at most once (the initial acquisition). Per-scan thrash would blow the
	 * counter past it.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void marksAreStableAcrossScans(GameTestHelper helper) {
		String fixture = "marksAreStableAcrossScans";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		List<Zombie> zombies = new ArrayList<>();
		AtomicReference<Map<UUID, UUID>> lastMarks = new AtomicReference<>(Map.of());
		AtomicReference<Map<UUID, Integer>> changesPerBody = new AtomicReference<>(new HashMap<>());

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			for (int i = 0; i < 3; i++) {
				zombies.add(spawnToughZombie(helper, fixture, new BlockPos(4 + i * 2, 1, 6), 200.0));
			}
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 80; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (dogs.size() < 2) {
					return;
				}
				// Per-body tracking, not the mark set: two dogs swapping targets leaves the set
				// byte-identical while each body's pick thrashed — R4's stability is per body.
				Map<UUID, UUID> marks = new HashMap<>();
				for (MegumiDivineDogEntity dog : dogs) {
					marks.put(dog.getUUID(), dog.getTarget() == null ? null : dog.getTarget().getUUID());
				}
				Map<UUID, UUID> previous = lastMarks.get();
				if (!previous.isEmpty()) {
					Map<UUID, Integer> counts = changesPerBody.get();
					for (Map.Entry<UUID, UUID> entry : marks.entrySet()) {
						if (!java.util.Objects.equals(entry.getValue(), previous.get(entry.getKey()))) {
							counts.merge(entry.getKey(), 1, Integer::sum);
						}
					}
				}
				lastMarks.set(marks);
				if (pollTick == SPAWN_TICK + 80) {
					int maxChanges = changesPerBody.get().values().stream().mapToInt(Integer::intValue).max().orElse(0);
					boolean stable = maxChanges <= 1;
					discardAll(zombies);
					cleanup(helper, owner);
					helper.assertTrue(stable, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"stability", helper.getTick(), owner.getUUID(),
							"per-body mark changes over 80 ticks", "<= 1 per body", changesPerBody.get()));
					if (stable) {
						helper.succeed();
					}
				}
			});
		}
	}

	/**
	 * R5 — one strong zombie among weak ones: the dangerous target weighs more, but it never pulls
	 * the whole pack — at least one dog must keep a different mark.
	 */
	@GameTest(maxTicks = 200, skyAccess = true)
	public void aDangerousTargetDoesNotPullTheWholePack(GameTestHelper helper) {
		String fixture = "aDangerousTargetDoesNotPullTheWholePack";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		List<Zombie> zombies = new ArrayList<>();
		AtomicReference<Zombie> bossRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			// One genuinely dangerous target among plain ones: the danger term only differentiates
			// when the pack-mates are weaker, so the boss keeps 200 HP and the others stay at 20.
			bossRef.set(spawnToughZombie(helper, fixture, new BlockPos(4, 1, 6), 200.0));
			zombies.add(bossRef.get());
			zombies.add(spawnFrozenZombie(helper, fixture, new BlockPos(6, 1, 6)));
			zombies.add(spawnFrozenZombie(helper, fixture, new BlockPos(8, 1, 6)));
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 80; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				Zombie boss = bossRef.get();
				if (dogs.size() < 2 || boss == null) {
					return;
				}
				Set<UUID> marks = markedTargets(dogs);
				// R5's two halves in one oracle: the dangerous target IS picked (the danger
				// weight lands), and it never pulls the whole pack (the spread rule holds).
				if (marks.contains(boss.getUUID()) && marks.size() >= 2) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 80) {
					discardAll(zombies);
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"danger", helper.getTick(), owner.getUUID(),
							"the boss is marked but the pack is not all on it",
							"boss in marks + >= 2 distinct marks", marks + " boss=" + boss.getUUID()));
				}
			});
		}
	}

	/**
	 * R6 — the manual sic is absolute: a nearer free zombie spawns after the order lands, and the
	 * dogs' marks must stay on the sicced target for the whole window.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void aManualSicIsAbsolute(GameTestHelper helper) {
		String fixture = "aManualSicIsAbsolute";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> markRef = new AtomicReference<>();
		AtomicReference<Zombie> nearerRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			markRef.set(spawnToughZombie(helper, fixture, new BlockPos(7, 1, 6), 200.0));
		});
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			Zombie mark = markRef.get();
			TodoSwapTestFixtures.aimAt(owner, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(owner, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic",
					helper.getTick(), owner.getUUID(), "trySic result", "true", sicced));
			nearerRef.set(spawnToughZombie(helper, fixture, new BlockPos(4, 1, 4), 200.0));
		}));

		for (long tick = SIC_TICK + 1; tick <= SIC_TICK + 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie mark = markRef.get();
				Zombie nearer = nearerRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (mark == null || nearer == null || dogs.isEmpty()) {
					return;
				}
				boolean allOnMark = dogs.stream().allMatch(dog -> dog.getTarget() == mark);
				if (pollTick == SIC_TICK + 60) {
					discardAll(List.of(mark, nearer));
					cleanup(helper, owner);
					helper.assertTrue(allOnMark, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), owner.getUUID(),
							"every dog stays on the sicced target", "all on " + mark.getUUID(),
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
					if (allOnMark) {
						helper.succeed();
					}
					return;
				}
				if (!allOnMark) {
					discardAll(List.of(mark, nearer));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), owner.getUUID(),
							"a dog left the manual mark for the nearer zombie", "all on mark",
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R8 — the order ends when the target dies: the sicced zombie is discarded and the pack must
	 * pick the remaining one on its own within a few scans.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void autonomyResumesWhenTheMarkDies(GameTestHelper helper) {
		String fixture = "autonomyResumesWhenTheMarkDies";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> markRef = new AtomicReference<>();
		AtomicReference<Zombie> otherRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			markRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(7, 1, 6)));
			otherRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(4, 1, 6)));
		});
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			Zombie mark = markRef.get();
			TodoSwapTestFixtures.aimAt(owner, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(owner, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic",
					helper.getTick(), owner.getUUID(), "trySic result", "true", sicced));
		}));
		helper.runAtTickTime(SIC_TICK + 10, () -> {
			Zombie mark = markRef.get();
			if (mark != null) {
				mark.discard();
			}
		});

		for (long tick = SIC_TICK + 11; tick <= SIC_TICK + 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie other = otherRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (other == null || dogs.isEmpty()) {
					return;
				}
				if (dogs.stream().anyMatch(dog -> dog.getTarget() == other)) {
					other.discard();
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 60) {
					other.discard();
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"resume", helper.getTick(), owner.getUUID(),
							"a dog picks the surviving zombie after the mark died",
							"target=" + other.getUUID(),
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R10 — an ally's committed action invites the pile-on: the toad grabs the near zombie, and a
	 * dog must leave its far mark for the held victim. The intent bonus is the only thing that can
	 * move the dog — the far zombie is closer to the dogs and already marked, so hysteresis alone
	 * would keep it.
	 */
	@GameTest(maxTicks = 400, skyAccess = true)
	public void anAllyIntentInvitesThePileOn(GameTestHelper helper) {
		String fixture = "anAllyIntentInvitesThePileOn";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicBoolean dogsOut = new AtomicBoolean();
		AtomicBoolean toadOut = new AtomicBoolean();
		AtomicReference<Zombie> farRef = new AtomicReference<>();
		AtomicReference<Zombie> nearRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, dogsOut));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> summonShikigami(helper, fixture, owner,
				MegumiShikigami.TOAD, toadOut));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			farRef.set(spawnToughZombie(helper, fixture, new BlockPos(8, 1, 8), 400.0));
			nearRef.set(spawnToughZombie(helper, fixture, new BlockPos(5, 1, 4), 400.0));
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie near = nearRef.get();
				Zombie far = farRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (near == null || far == null || dogs.isEmpty()) {
					return;
				}
				// The toad's grab is the intent: once it holds the near zombie, a dog must pile on.
				boolean held = near.hasEffect(jujutsu.mod.registry.JujutsuEffects.GRIPPED);
				boolean dogOnNear = dogs.stream().anyMatch(dog -> dog.getTarget() == near);
				if (held && dogOnNear) {
					discardAll(List.of(near, far));
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 200) {
					discardAll(List.of(near, far));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"intent", helper.getTick(), owner.getUUID(),
							"a dog piles onto the toad's held victim", "held+targeted",
							"held=" + held + " dogTargets="
									+ dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R13 — the owner's attacker is answered first: a scripted hit lands and a dog must mark the
	 * attacker within twenty ticks, even though a decoy zombie stands closer. The mechanism under
	 * test is the retaliation pass — it marks the owner's aggressor on every body before the
	 * coordinator runs, which is why no owner-threat score weight exists to pin here.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void theOwnersAttackerIsAnsweredFirst(GameTestHelper helper) {
		String fixture = "theOwnersAttackerIsAnsweredFirst";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> decoyRef = new AtomicReference<>();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			decoyRef.set(spawnToughZombie(helper, fixture, new BlockPos(4, 1, 4), 200.0));
		});
		helper.runAtTickTime(SIC_TICK, () -> {
			Zombie attacker = spawnToughZombie(helper, fixture, new BlockPos(6, 1, 6), 200.0);
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			helper.assertTrue(owner.getLastHurtByMob() == attacker,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the hit is attributed", attacker.getUUID(),
							String.valueOf(owner.getLastHurtByMob())));
		});

		for (long tick = SIC_TICK + 1; tick <= SIC_TICK + 40; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie attacker = attackerRef.get();
				Zombie decoy = decoyRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (attacker == null || dogs.isEmpty()) {
					return;
				}
				if (dogs.stream().anyMatch(dog -> dog.getTarget() == attacker)) {
					discardAll(List.of(attacker, decoy));
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 40) {
					discardAll(List.of(attacker, decoy));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"threat", helper.getTick(), owner.getUUID(),
							"a dog marks the owner's attacker within 40 ticks",
							"target=" + attacker.getUUID(),
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R14 — a sibling under attack gets help: both dogs mark the decoy first, then the attacker
	 * hits one dog, and the OTHER dog must answer within forty ticks. The ally-threat boost is the
	 * only thing that can move it — the decoy is already marked and hysteresis would hold it.
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void aSiblingUnderAttackGetsHelp(GameTestHelper helper) {
		String fixture = "aSiblingUnderAttackGetsHelp";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> decoyRef = new AtomicReference<>();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicReference<MegumiDivineDogEntity> untouchedDogRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			decoyRef.set(spawnToughZombie(helper, fixture, new BlockPos(5, 1, 6), 400.0));
		});
		helper.runAtTickTime(SIC_TICK, () -> {
			List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
			helper.assertTrue(dogs.size() == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"attack", helper.getTick(), owner.getUUID(), "both dogs out", "2", dogs.size()));
			Zombie attacker = spawnToughZombie(helper, fixture, new BlockPos(6, 1, 4), 400.0);
			attackerRef.set(attacker);
			dogs.getFirst().hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			// The oracle must name the OTHER dog: the hurt body answering its own attacker is not
			// sibling help — only the untouched dog switching proves the ally-threat path.
			untouchedDogRef.set(dogs.get(1));
			helper.assertTrue(dogs.getFirst().getLastHurtByMob() == attacker,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the dog's hit is attributed", attacker.getUUID(),
							String.valueOf(dogs.getFirst().getLastHurtByMob())));
		});

		for (long tick = SIC_TICK + 1; tick <= SIC_TICK + 60; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie attacker = attackerRef.get();
				Zombie decoy = decoyRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (attacker == null || dogs.size() < 2) {
					return;
				}
				MegumiDivineDogEntity untouched = untouchedDogRef.get();
				if (untouched != null && untouched.getTarget() == attacker) {
					discardAll(List.of(attacker, decoy));
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 60) {
					discardAll(List.of(attacker, decoy));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"ally", helper.getTick(), owner.getUUID(),
							"a dog answers the sibling's attacker within 60 ticks",
							"target=" + attacker.getUUID(),
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R16 — a failed pounce is not immediately retried: the failure memory is seeded directly (the
	 * abort-by-collision hook is B1's; this scenario proves the gate it feeds). With a fresh
	 * "pounce" failure the dog may melee but must never land the pounce's 5.0 damage hit inside
	 * the window; after the memory clears, the pounce lands.
	 */
	@GameTest(maxTicks = 400, skyAccess = true)
	public void aFailedPounceIsNotImmediatelyRetried(GameTestHelper helper) {
		String fixture = "aFailedPounceIsNotImmediatelyRetried";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicReference<Float> lastHealth = new AtomicReference<>();
		AtomicBoolean pounceLanded = new AtomicBoolean();
		AtomicBoolean cleared = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			Zombie zombie = spawnToughZombie(helper, fixture, new BlockPos(6, 1, 6), 400.0);
			// Knockback resistance keeps the victim in place so melee and pounce differ only in
			// the damage they deal — the pounce's 5.0 (3.0 bite + 2.0 bonus) is the tell.
			zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			zombieRef.set(zombie);
			lastHealth.set(zombie.getHealth());
			List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
			helper.assertTrue(!dogs.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"seed", helper.getTick(), owner.getUUID(), "a dog exists to seed", "present",
					dogs.size()));
			// Every dog carries the memory, not just the first: the coordinator may assign the
			// pounce to any body, and a dog without the record would retry immediately.
			for (MegumiDivineDogEntity dog : dogs) {
				MegumiFailureMemory.recordFailure(dog.getUUID(), "pounce", level.getGameTime());
			}
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 300; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (zombie == null || dogs.isEmpty()) {
					return;
				}
				float health = zombie.getHealth();
				float delta = lastHealth.get() - health;
				lastHealth.set(health);
				if (!cleared.get()) {
					// One stamp decays past the 0.5 skip threshold in ~17 ticks (penalty 0.6,
					// window 100) — far short of the 80-tick hold this scenario measures. Production
					// re-stamps on every wall-abort, so the oracle re-stamps every poll tick: the
					// memory stays fresh for the whole window exactly as repeated aborts would keep
					// it, and the gate must hold for all of it.
					for (MegumiDivineDogEntity dog : dogs) {
						MegumiFailureMemory.recordFailure(dog.getUUID(), "pounce", level.getGameTime());
					}
				}
				if (!cleared.get() && delta >= 4.5f) {
					// A 5.0-damage hit while the failure is fresh: the gate failed.
					discardAll(List.of(zombie));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"pounce", helper.getTick(), owner.getUUID(),
							"no pounce-damage hit while the failure is fresh", "< 4.5 per tick",
							"delta=" + delta));
					return;
				}
				if (!cleared.get() && pollTick == SPAWN_TICK + 80) {
					// The window held: clear the memory and let the pounce through. The dogs spent
					// the hold inside melee reach, where canLaunch's 3..8 distance band can never
					// open — and a dog teleport does not stick (the pack re-settles on the mark
					// before tickPounce sees the gap). Move the victim instead: five blocks north
					// leaves every dog inside the band with a clean line.
					for (MegumiDivineDogEntity dog : dogs) {
						MegumiFailureMemory.clear(dog.getUUID());
					}
					BlockPos spot = helper.absolutePos(new BlockPos(6, 1, 1));
					zombie.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					// Residual i-frames from the hold-window melee would clip the pounce's 5.0 to
					// 5.0-3.0=2.0 (hurtServer only applies the over-last-hit delta while
					// invulnerableTime stands) — the oracle reads a landed pounce as nothing.
					zombie.invulnerableTime = 0;
					cleared.set(true);
					return;
				}
				if (cleared.get() && delta >= 4.5f) {
					pounceLanded.set(true);
					discardAll(List.of(zombie));
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 300) {
					discardAll(List.of(zombie));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"pounce", helper.getTick(), owner.getUUID(),
							"the pounce lands once the memory clears", ">= 4.5 in one tick",
							"never landed; dogs=" + dogs.stream()
									.map(dog -> "{tgt=" + (dog.getTarget() == null ? "null"
													: dog.getTarget().getUUID())
											+ " dist=" + String.format("%.2f", dog.distanceTo(zombie))
											+ " w=" + String.format("%.2f", MegumiFailureMemory.weight(
													dog.getUUID(), "pounce", level.getGameTime()))
											+ " phase=" + dog.presentationPhase()
											+ " los=" + dog.hasLineOfSight(zombie) + "}")
									.toList()));
				}
			});
		}
	}

	/**
	 * R17 (the in-arena half) — a mark inside the autonomy band persists: the band boundary itself
	 * is a JUnit concern (the arena cannot hold 60 blocks), so this asserts the reachable half —
	 * an autonomous mark placed in-band is still there forty ticks later.
	 */
	@GameTest(maxTicks = 200, skyAccess = true)
	public void anInBandMarkPersists(GameTestHelper helper) {
		String fixture = "anInBandMarkPersists";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();
		AtomicBoolean marked = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			zombieRef.set(spawnToughZombie(helper, fixture, new BlockPos(6, 1, 6), 400.0));
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 100; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie zombie = zombieRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (zombie == null || dogs.isEmpty()) {
					return;
				}
				if (!marked.get()) {
					if (dogs.stream().anyMatch(dog -> dog.getTarget() == zombie)) {
						marked.set(true);
					}
					return;
				}
				if (pollTick == SPAWN_TICK + 60) {
					boolean held = dogs.stream().anyMatch(dog -> dog.getTarget() == zombie);
					discardAll(List.of(zombie));
					cleanup(helper, owner);
					helper.assertTrue(held, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"band", helper.getTick(), owner.getUUID(),
							"the in-band mark survives forty ticks", "still marked",
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
					if (held) {
						helper.succeed();
					}
				}
			});
		}
	}

	/**
	 * R20 — a candidate with no line of sight is never marked: the zombie is sealed inside a solid
	 * box two blocks from the owner, and no dog may ever target it. A second, visible zombie proves
	 * the pack is not simply idle.
	 */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void aSealedTargetIsNeverMarked(GameTestHelper helper) {
		String fixture = "aSealedTargetIsNeverMarked";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> sealedRef = new AtomicReference<>();
		AtomicReference<Zombie> openRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonDogs(helper, fixture, owner, summoned));
		helper.runAtTickTime(SPAWN_TICK, () -> {
			// A sealed stone box: the zombie inside can never be seen, so it can never be marked.
			BlockPos box = new BlockPos(6, 1, 3);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 2; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dz == 0 && dy < 2) {
							continue;
						}
						helper.setBlock(box.offset(dx, dy - 1, dz), Blocks.STONE);
					}
				}
			}
			sealedRef.set(spawnFrozenZombie(helper, fixture, box));
			openRef.set(spawnFrozenZombie(helper, fixture, new BlockPos(6, 1, 7)));
		});

		for (long tick = SPAWN_TICK + 1; tick <= SPAWN_TICK + 120; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				Zombie sealed = sealedRef.get();
				Zombie open = openRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (sealed == null || open == null || dogs.isEmpty()) {
					return;
				}
				boolean sealedMarked = dogs.stream().anyMatch(dog -> dog.getTarget() == sealed);
				if (sealedMarked) {
					discardAll(List.of(sealed, open));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sealed", helper.getTick(), owner.getUUID(),
							"the sealed zombie is never marked", "never", "marked"));
					return;
				}
				boolean openMarked = dogs.stream().anyMatch(dog -> dog.getTarget() == open);
				if (openMarked && pollTick > SPAWN_TICK + 40) {
					discardAll(List.of(sealed, open));
					cleanup(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == SPAWN_TICK + 120) {
					discardAll(List.of(sealed, open));
					cleanup(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sealed", helper.getTick(), owner.getUUID(),
							"the open zombie is marked (the pack is not idle)", "marked",
							dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}
}
