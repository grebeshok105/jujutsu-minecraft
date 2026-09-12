package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiAbilityRouter;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;

/**
 * Issue #76 — the pack answers for its owner without a key press: whoever hits the owner, or
 * whatever already hunts the owner, becomes the mark of every body that carries none, while a
 * manual sic keeps priority and the owner is never caught in the crossfire.
 *
 * <p>Traps respected: the owner is a directly-constructed SURVIVAL player (the GameTest mock's
 * {@code gameMode()} is a hard CREATIVE stub, and a creative player refuses {@code hurtServer} —
 * the retaliation signal is written by the damage pipeline, so the owner must be damageable). The
 * attacker is parked with Slowness 100 so it cannot reach the owner and muddle the friendly-fire
 * assert; hits are attributed through {@code damageSources().mobAttack(zombie)} exactly as a real
 * zombie swing would.
 */
public final class MegumiShikigamiRetaliationGameTests {
	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final int SUMMON_TICK = 2;
	private static final int ATTACK_TICK = 40;

	private static void layPad(GameTestHelper helper) {
		for (int dx = -1; dx <= 9; dx++) {
			for (int dz = -1; dz <= 9; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
				helper.setBlock(new BlockPos(dx, 6, dz), Blocks.STONE);
			}
		}
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

	/** The dog pack lives on its own entity class — the shikigami lookup above never sees it. */
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

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void summonToad(GameTestHelper helper, String fixture, ServerPlayer owner, AtomicBoolean summoned) {
		MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.TOAD);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(owner, false);
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "toad summoned", "true", ok));
		});
	}

	/**
	 * R1 + R4 — something swings at the owner (the hit is attributed through the real damage
	 * pipeline) and the toad must take that attacker as its mark and punish it, while the owner's
	 * health only carries the scripted hit.
	 *
	 * <p>Two-stage oracle: the mark is asserted first, then the punishment — the first tick the
	 * pack answers is usually before its first blow lands, so a single-shot assert would flake.
	 * The attacker is a zombie parked INSIDE the 8x8 arena (a body placed past rel 7 spawns
	 * outside the barrier and drops out of the world, which silently clears lastHurtByMob) and a
	 * player is not usable here: the test server refuses player-vs-player damage.
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void retaliationAnswersWhoeverHitsTheOwner(GameTestHelper helper) {
		String fixture = "retaliationAnswersWhoeverHitsTheOwner";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean marked = new AtomicBoolean();
		AtomicBoolean answered = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonToad(helper, fixture, owner, summoned));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			helper.assertTrue(summoned.get(), MegumiShikigamiTestFixtures.diagnostic(fixture, "attack",
					helper.getTick(), owner.getUUID(), "summon succeeded before the attack", "true",
					summoned.get()));
			Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			attacker.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(attacker);
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			helper.assertTrue(owner.getHealth() < owner.getMaxHealth(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "scripted hit landed", "< " + owner.getMaxHealth(),
							owner.getHealth()));
		});

		for (long tick = ATTACK_TICK + 1; tick <= ATTACK_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (answered.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				List<MegumiShikigamiEntity> bodies = bodiesOwnedBy(level, owner.getUUID());
				if (attacker == null || bodies.isEmpty()) {
					return;
				}
				if (!marked.get() && bodies.stream().anyMatch(body -> body.getTarget() == attacker)) {
					marked.set(true);
					return;
				}
				if (marked.get() && attacker.getHealth() < attacker.getMaxHealth()) {
					answered.set(true);
					helper.assertTrue(owner.getHealth() >= owner.getMaxHealth() - 1.0f,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate", helper.getTick(),
									owner.getUUID(), "owner never caught in the crossfire",
									owner.getMaxHealth() - 1.0f, owner.getHealth()));
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == ATTACK_TICK + 200) {
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate",
							helper.getTick(), owner.getUUID(),
							marked.get() ? "pack punishes the attacker it acquired"
									: "pack acquires the owner's attacker without a sic",
							"attacker " + attacker.getUUID()
									+ " alive=" + attacker.isAlive()
									+ " lastHurtBy=" + owner.getLastHurtByMob()
									+ " stamp=" + owner.getLastHurtByMobTimestamp()
									+ " gameTime=" + level.getGameTime()
									+ " ownerHp=" + owner.getHealth() + "/" + owner.getMaxHealth(),
							bodies.isEmpty() ? "no bodies" : bodies.getFirst().getTarget()));
				}
			});
		}
	}

	/**
	 * R1 (the clock half) — the attacker lands a hit but never targets the owner, so the ONLY signal
	 * that can name it is the owner's own last-hurt record. This is the shape a real fight takes when
	 * the swing comes from something that then turns away, and it is the shape a fresh world hides:
	 * {@code lastHurtByMobTimestamp} is stamped with the victim's {@code tickCount}, not with the
	 * level's game time, so a policy that compares it against {@code level().getGameTime()} treats
	 * every hit as ancient in any world that has been ticking for longer than the entity has existed.
	 * The zombie keeps its AI off on purpose: an aggroed attacker would let the second signal answer
	 * and mask the defect (that is what the R1 test next door does).
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void retaliationAnswersAHitFromAMobWhichNeverTargetsTheOwner(GameTestHelper helper) {
		String fixture = "retaliationAnswersAHitFromAMobWhichNeverTargetsTheOwner";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean answered = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			AbilityResult result = MegumiAbilityRouter.tryCast(owner, CharacterAbility.PRIMARY, false);
			boolean ok = result == AbilityResult.SUCCESS;
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "dogs summoned", AbilityResult.SUCCESS, result));
		}));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			helper.assertTrue(summoned.get(), MegumiShikigamiTestFixtures.diagnostic(fixture, "attack",
					helper.getTick(), owner.getUUID(), "summon succeeded before the attack", "true",
					summoned.get()));
			Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			attacker.setPersistenceRequired();
			attacker.setNoAi(true);
			CursedSpiritTestFixtures.freezeGround(attacker);
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			// Premise check: without a recorded hit (and with the attacker holding no target of its
			// own) this scenario proves nothing, so assert the signal itself before judging the pack.
			helper.assertTrue(owner.getLastHurtByMob() == attacker,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the hit is attributed to the attacker",
							attacker.getUUID(), owner.getLastHurtByMob() == null
									? "null" : owner.getLastHurtByMob().getUUID()));
			helper.assertTrue(attacker.getTarget() == null,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the attacker holds no target of its own", "null",
							attacker.getTarget()));
			helper.assertTrue(owner.getLastHurtByMobTimestamp() > 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(),
							owner.getUUID(), "the hit is time-stamped", "> 0",
							owner.getLastHurtByMobTimestamp()));
		});

		for (long tick = ATTACK_TICK + 1; tick <= ATTACK_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (answered.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (attacker == null || dogs.isEmpty()) {
					if (pollTick == ATTACK_TICK + 200) {
						attacker.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"retaliate", helper.getTick(), owner.getUUID(), "the pack is out",
								"dogs > 0", "dogs=" + dogs.size()));
					}
					return;
				}
				if (dogs.stream().anyMatch(dog -> dog.getTarget() == attacker)) {
					answered.set(true);
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == ATTACK_TICK + 200) {
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate",
							helper.getTick(), owner.getUUID(),
							"pack marks an attacker which never targeted the owner",
							attacker.getUUID(),
							"lastHurtBy=" + owner.getLastHurtByMob()
									+ " stamp=" + owner.getLastHurtByMobTimestamp()
									+ " ownerTickCount=" + owner.tickCount
									+ " gameTime=" + level.getGameTime()
									+ " dogTargets=" + dogs.stream()
											.map(dog -> String.valueOf(dog.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * R1 (the pounce half) — a mark that stays put must keep being punished. The retaliation pass
	 * re-marks every body on every tick the window is fresh, and the dog's mark setter drops any
	 * leap in progress; a pass that re-marks unconditionally therefore cancels the very pounce that
	 * lands the blow, which is why a pack can bite once and then stand still for as long as the
	 * owner keeps being hit. The attacker keeps swinging on a scripted cadence so the window stays
	 * fresh for the whole measurement.
	 */
	@GameTest(maxTicks = 400, skyAccess = true)
	public void thePackKeepsPunishingWhileTheMarkIsFresh(GameTestHelper helper) {
		String fixture = "thePackKeepsPunishingWhileTheMarkIsFresh";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean firstBlow = new AtomicBoolean();
		AtomicBoolean secondBlow = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			AbilityResult result = MegumiAbilityRouter.tryCast(owner, CharacterAbility.PRIMARY, false);
			boolean ok = result == AbilityResult.SUCCESS;
			summoned.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "dogs summoned", AbilityResult.SUCCESS, result));
		}));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			helper.assertTrue(summoned.get(), MegumiShikigamiTestFixtures.diagnostic(fixture, "attack",
					helper.getTick(), owner.getUUID(), "summon succeeded before the attack", "true",
					summoned.get()));
			Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			attacker.setPersistenceRequired();
			attacker.setNoAi(true);
			CursedSpiritTestFixtures.freezeGround(attacker);
			attackerRef.set(attacker);
		});

		for (long hit = ATTACK_TICK; hit <= ATTACK_TICK + 240; hit += 40) {
			final long hitTick = hit;
			helper.runAtTickTime(hitTick, () -> {
				Zombie attacker = attackerRef.get();
				if (attacker != null) {
					owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
				}
			});
		}

		for (long tick = ATTACK_TICK + 1; tick <= ATTACK_TICK + 300; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (secondBlow.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				if (attacker == null) {
					return;
				}
				boolean hurt = attacker.getHealth() < attacker.getMaxHealth();
				if (hurt && !firstBlow.get()) {
					firstBlow.set(true);
					return;
				}
				if (hurt && firstBlow.get() && !secondBlow.get()) {
					secondBlow.set(true);
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == ATTACK_TICK + 300) {
					String state = attacker.getHealth() + "/" + attacker.getMaxHealth();
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "punish",
							helper.getTick(), owner.getUUID(),
							firstBlow.get() ? "the pack keeps punishing a fresh mark"
									: "the pack lands its first blow",
							firstBlow.get() ? "a second blow" : "any blow", state));
				}
			});
		}
	}

	/**
	 * R3 (the vanilla half) — the owner's own swing must not steal a sic mark either. Vanilla pets
	 * carry {@code OwnerHurtTargetGoal}, which copies the target straight out of the owner's last
	 * victim without passing the sic's precedence; a shikigami that takes orders cannot also take
	 * its target from whatever the owner happened to hit. The theft is asserted as "the pack left
	 * the sic mark for the owner's victim while the mark still stood".
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void anOwnersOwnSwingDoesNotStealTheSicMark(GameTestHelper helper) {
		String fixture = "anOwnersOwnSwingDoesNotStealTheSicMark";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> markRef = new AtomicReference<>();
		AtomicReference<Zombie> victimsRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonToad(helper, fixture, owner, summoned));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			owner.setYRot(0.0f);
			owner.setXRot(0.0f);
			owner.yHeadRot = 0.0f;
			Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			mark.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(mark);
			markRef.set(mark);
			boolean ok = MegumiShikigamiRuntime.trySic(owner, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
					owner.getUUID(), "sic resolved the aimed mark", "true", ok));
		});

		helper.runAtTickTime(ATTACK_TICK + 5, () -> {
			Zombie victim = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(5, 1, 3));
			victim.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(victim);
			victimsRef.set(victim);
			owner.attack(victim);
			helper.assertTrue(owner.getLastHurtMob() == victim,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "swing", helper.getTick(),
							owner.getUUID(), "the owner's swing is attributed to the victim",
							victim.getUUID(), owner.getLastHurtMob() == null
									? "null" : owner.getLastHurtMob().getUUID()));
		});

		AtomicBoolean answered = new AtomicBoolean();
		AtomicBoolean heldMark = new AtomicBoolean();
		for (long tick = ATTACK_TICK + 6; tick <= ATTACK_TICK + 200; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (answered.get()) {
					return;
				}
				Zombie mark = markRef.get();
				Zombie victim = victimsRef.get();
				ServerLevel level = helper.getLevel();
				List<MegumiShikigamiEntity> bodies = bodiesOwnedBy(level, owner.getUUID());
				if (mark == null || victim == null) {
					return;
				}
				if (bodies.isEmpty()) {
					if (pollTick == ATTACK_TICK + 200) {
						mark.discard();
						victim.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "theft",
								helper.getTick(), owner.getUUID(), "the pack is out", "bodies > 0",
								"bodies=0"));
					}
					return;
				}
				boolean markLives = mark.isAlive() && !mark.isRemoved();
				boolean onVictim = bodies.stream().anyMatch(body -> body.getTarget() == victim);
				if (markLives && onVictim) {
					answered.set(true);
					mark.discard();
					victim.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "theft",
							helper.getTick(), owner.getUUID(),
							"the owner's own swing leaves the sic mark alone",
							"body on " + mark.getUUID(), "body on " + victim.getUUID()));
					return;
				}
				if (bodies.stream().anyMatch(body -> body.getTarget() == mark)) {
					heldMark.set(true);
				}
				if (!markLives || pollTick == ATTACK_TICK + 200) {
					answered.set(true);
					mark.discard();
					victim.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(heldMark.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"theft", helper.getTick(), owner.getUUID(),
							"the pack carried the sic mark", "mark targeted at least once", heldMark.get()));
					helper.succeed();
				}
			});
		}
	}

	/**
	 * R2 — a body already hunting the owner (its own {@code getTarget()} is the owner) becomes the
	 * mark even though it has not landed a single hit: the signal is the aggro, not the damage.
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void retaliationAnswersAMobAlreadyHuntingTheOwner(GameTestHelper helper) {
		String fixture = "retaliationAnswersAMobAlreadyHuntingTheOwner";
		layPad(helper);
		BlockPos ownerFeet = new BlockPos(3, 1, 3);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, ownerFeet);
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> hunterRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> summonToad(helper, fixture, owner, summoned));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			Zombie hunter = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			hunter.setPersistenceRequired();
			hunter.setNoAi(false);
			CursedSpiritTestFixtures.freezeGround(hunter);
			hunter.setTarget(owner);
			hunterRef.set(hunter);
		});

		AtomicBoolean answered = new AtomicBoolean();
		for (long tick = ATTACK_TICK + 2; tick <= ATTACK_TICK + 160; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (answered.get()) {
					return;
				}
				Zombie hunter = hunterRef.get();
				List<MegumiShikigamiEntity> bodies = bodiesOwnedBy(level, owner.getUUID());
				if (hunter == null || bodies.isEmpty()) {
					return;
				}
				if (bodies.stream().anyMatch(body -> body.getTarget() == hunter)) {
					answered.set(true);
					hunter.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(owner.getHealth() == owner.getMaxHealth(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate", helper.getTick(),
									owner.getUUID(), "owner untouched by the hunting mob", owner.getMaxHealth(),
									owner.getHealth()));
					helper.succeed();
					return;
				}
				if (pollTick == ATTACK_TICK + 160) {
					hunter.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliate",
							helper.getTick(), owner.getUUID(),
							"pack acquires a mob that only hunts the owner", hunter.getTarget(),
							bodies.getFirst().getTarget()));
				}
			});
		}
	}
	/**
	 * R3 — a manual sic outranks the retaliation pass: with the pack already sent at one body, the
	 * owner taking a hit from another must not steal the mark. The sic goes through the production
	 * path ({@code trySic} with the owner aimed down +Z at the mark, the same reading the key uses).
	 */
	@GameTest(maxTicks = 320, skyAccess = true)
	public void manualSicOutranksRetaliation(GameTestHelper helper) {
		String fixture = "manualSicOutranksRetaliation";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(3, 1, 3));
		ServerLevel level = helper.getLevel();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicReference<Zombie> markRef = new AtomicReference<>();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean sicced = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> summonToad(helper, fixture, owner, summoned));

		helper.runAtTickTime(ATTACK_TICK, () -> {
			owner.setYRot(0.0f);
			owner.setXRot(0.0f);
			owner.yHeadRot = 0.0f;
			Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(3, 1, 6));
			mark.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(mark);
			markRef.set(mark);
			boolean ok = MegumiShikigamiRuntime.trySic(owner, false);
			sicced.set(ok);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
					owner.getUUID(), "sic resolved the aimed mark", "true", ok));
		});

		helper.runAtTickTime(ATTACK_TICK + 5, () -> {
			Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, new BlockPos(5, 1, 3));
			attacker.setPersistenceRequired();
			CursedSpiritTestFixtures.freezeGround(attacker);
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
		});

		AtomicBoolean answered = new AtomicBoolean();
		AtomicBoolean heldMark = new AtomicBoolean();
		for (long tick = ATTACK_TICK + 6; tick <= ATTACK_TICK + 240; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (answered.get()) {
					return;
				}
				Zombie mark = markRef.get();
				Zombie attacker = attackerRef.get();
				List<MegumiShikigamiEntity> bodies = bodiesOwnedBy(level, owner.getUUID());
				if (mark == null || attacker == null || bodies.isEmpty()) {
					return;
				}
				boolean markLives = mark.isAlive() && !mark.isRemoved();
				boolean holdsMark = bodies.stream().anyMatch(body -> body.getTarget() == mark);
				boolean stoleWhileTheMarkLived = markLives && !holdsMark
						&& bodies.stream().anyMatch(body -> body.getTarget() == attacker);
				if (stoleWhileTheMarkLived) {
					answered.set(true);
					mark.discard();
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "priority",
							helper.getTick(), owner.getUUID(),
							"the manual sic keeps its mark while the mark lives",
							"body on " + mark.getUUID(),
							"body on " + attacker.getUUID()));
					return;
				}
				if (holdsMark) {
					heldMark.set(true);
				}
				// The mark may legitimately die to the pack itself: what must never happen is the
				// retaliation stealing it while it stands. Once the mark is gone, the attacker is a
				// fair answer again.
				if (!markLives && heldMark.get() || pollTick == ATTACK_TICK + 240) {
					answered.set(true);
					mark.discard();
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					helper.assertTrue(heldMark.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
							"priority", helper.getTick(), owner.getUUID(),
							"the pack carried the sic mark", "mark targeted at least once", heldMark.get()));
					helper.succeed();
				}
			});
		}
	}
}
