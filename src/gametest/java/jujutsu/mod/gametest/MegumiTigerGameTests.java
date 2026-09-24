package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiShikigamiSpawnPlacement;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiTigerEntity;
import jujutsu.mod.registry.JujutsuEntities;

/** Tiger Funeral (Ten Shadows selection layer) lifecycle, targeting, and authored-combo scenarios. */
public final class MegumiTigerGameTests {
	private static final int SUMMON_TICK = 2;
	private static final int SIC_TICK = 24;

	@GameTest(maxTicks = 60)
	public void tigerSummonMaterializesOneBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "tigerSummonMaterializesOneBodyWithoutCooldown";
		paveFloor(helper, 0, 8);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> bodyRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					diagnostic(helper, fixture, "summon", caster, "Tiger summon accepted", true, false));
			List<MegumiTigerEntity> bodies = tigersOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.size() == 1,
					diagnostic(helper, fixture, "summon", caster, "owned Tiger bodies", 1, bodies.size()));
			bodyRef.set(bodies.getFirst());
			helper.assertTrue(bodyRef.get().phase()
					== jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING,
					diagnostic(helper, fixture, "summon", caster, "initial phase", "MATERIALIZING",
						bodyRef.get().phase()));
			long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(),
					MegumiShikigami.TIGER, level.getGameTime());
			helper.assertTrue(cooldown == 0L,
					diagnostic(helper, fixture, "summon", caster, "summon cooldown", 0, cooldown));
		}));

		helper.runAtTickTime(24, () -> {
			try {
				MegumiTigerEntity body = bodyRef.get();
				helper.assertTrue(body != null && body.combatEnabled(),
						diagnostic(helper, fixture, "active", caster, "materialization completed", true,
							body != null && body.combatEnabled()));
				helper.assertTrue(CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0,
						diagnostic(helper, fixture, "active", caster, "shared PRIMARY cooldown", 0,
							CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY)));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, helper::succeed);
	}

	@GameTest(maxTicks = 40)
	public void tigerNoRoomRollsBackWithoutCreatingPackOrCooldown(GameTestHelper helper) {
		String fixture = "tigerNoRoomRollsBackWithoutCreatingPackOrCooldown";
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		// Floor the arena; the placement volume itself is blocked at summon time around the live caster position.
		paveFloor(helper, 0, 8);

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				BlockPos ownerFeet = BlockPos.containing(caster.position());
				Vec3 spot = MegumiShikigamiSpawnPlacement.ground(level, caster.position(), caster.getYRot(),
						JujutsuEntities.MEGUMI_TIGER.getDimensions());
				for (int attempt = 0; spot != null && attempt < 4; attempt++) {
					BlockPos candidate = BlockPos.containing(spot);
					for (int x = candidate.getX() - 1; x <= candidate.getX() + 1; x++) {
						for (int z = candidate.getZ() - 1; z <= candidate.getZ() + 1; z++) {
							for (int y = ownerFeet.getY() - 3; y <= ownerFeet.getY() + 3; y++) {
								level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
							}
						}
					}
					spot = MegumiShikigamiSpawnPlacement.ground(level, caster.position(), caster.getYRot(),
							JujutsuEntities.MEGUMI_TIGER.getDimensions());
				}
				helper.assertTrue(spot == null,
						diagnostic(helper, fixture, "no-room", caster, "no safe Tiger spawn candidate",
								null, spot));
				MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned,
						diagnostic(helper, fixture, "no-room", caster, "summon refused", false, summoned));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "no-room", caster);
				helper.assertTrue(tigersOwnedBy(level, caster.getUUID()).isEmpty(),
					diagnostic(helper, fixture, "no-room", caster, "spawned Tiger bodies", 0,
							tigersOwnedBy(level, caster.getUUID()).size()));
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(),
						MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(cooldown == 0L,
						diagnostic(helper, fixture, "no-room", caster, "Tiger cooldown after refusal", 0, cooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, helper::succeed);
	}

	@GameTest(maxTicks = 60)
	public void tigerRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "tigerRecallChargesRecallCooldownAndClearsPack";
		paveFloor(helper, 0, 8);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					diagnostic(helper, fixture, "summon", caster, "Tiger summon accepted", true, false));
		}));
		helper.runAtTickTime(4, () -> {
			try {
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled,
						diagnostic(helper, fixture, "recall", caster, "recall accepted", true, recalled));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(),
						MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(cooldown == MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS,
						diagnostic(helper, fixture, "recall", caster, "Tiger recall cooldown",
							MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS, cooldown));
				helper.assertTrue(CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0,
						diagnostic(helper, fixture, "recall", caster, "shared PRIMARY cooldown", 0,
							CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY)));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, helper::succeed);
	}

	@GameTest(maxTicks = 80)
	public void tigerDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "tigerDeathChargesDeathCooldownAndClearsPack";
		paveFloor(helper, 0, 8);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					diagnostic(helper, fixture, "summon", caster, "Tiger summon accepted", true, false));
		}));
		helper.runAtTickTime(25, () -> {
			try {
				List<MegumiTigerEntity> bodies = tigersOwnedBy(level, caster.getUUID());
				helper.assertTrue(bodies.size() == 1,
					diagnostic(helper, fixture, "death", caster, "owned Tiger bodies", 1, bodies.size()));
				MegumiTigerEntity body = bodies.getFirst();
				helper.assertTrue(body.combatEnabled(),
					diagnostic(helper, fixture, "death", caster, "body active before lethal hit", true,
							body.combatEnabled()));
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged,
					diagnostic(helper, fixture, "death", caster, "lethal damage accepted", true, damaged));
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(),
						MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(cooldown == MegumiShikigamiProfile.TIGER_DEATH_COOLDOWN_TICKS,
					diagnostic(helper, fixture, "death", caster, "Tiger death cooldown",
							MegumiShikigamiProfile.TIGER_DEATH_COOLDOWN_TICKS, cooldown));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "death", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, helper::succeed);
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void tigerGlobalSicApproachesAndLocksTheOrderedTarget(GameTestHelper helper) {
		String fixture = "tigerGlobalSicApproachesAndLocksTheOrderedTarget";
		// The Tiger spawns offset from the owner; keep its five-block approach target on the floor.
		paveFloor(helper, 0, 12);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> targetRef = new AtomicReference<>();
		AtomicBoolean navigationStartedBeforeLock = new AtomicBoolean();
		AtomicReference<Double> startingDistanceSq = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow target = durableCow(helper, new BlockPos(6, 1, 4));
			targetRef.set(target);
			faceForward(tiger);
			issueSic(helper, fixture, caster, target);
			putInFront(tiger, target, MegumiShikigamiProfile.TIGER_STRIKE_RANGE + 2.0);
			startingDistanceSq.set(tiger.distanceToSqr(target));
		}));
		helper.runAtTickTime(SIC_TICK + 2, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow target = targetRef.get();
			Double startingDistance = startingDistanceSq.get();
			navigationStartedBeforeLock.set(tiger != null && target != null && startingDistance != null
					&& tiger.distanceToSqr(target) < startingDistance
					&& tiger.comboTargetUuid() == null && tiger.getNavigation().isInProgress());
		});
		AtomicBoolean completed = new AtomicBoolean();
		for (int tick = SIC_TICK + 3; tick <= SIC_TICK + 60; tick++) {
			int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (completed.get()) {
					return;
				}
				MegumiTigerEntity tiger = tigerRef.get();
				Cow target = targetRef.get();
				if (tiger != null && target != null && target.getUUID().equals(tiger.comboTargetUuid())) {
					completed.set(true);
					try {
						helper.assertTrue(navigationStartedBeforeLock.get(),
								diagnostic(helper, fixture, "approach", caster,
										"navigation advances the target before combo lock", true,
										navigationStartedBeforeLock.get()));
						Double startingDistance = startingDistanceSq.get();
						helper.assertTrue(startingDistance != null && tiger.distanceToSqr(target) < startingDistance,
								diagnostic(helper, fixture, "approach", caster,
										"target distance decreases before combo lock",
										startingDistance, tiger.distanceToSqr(target)));
					} finally {
						discard(target);
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 60) {
					discard(target);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "approach", caster,
							"nearby sic target becomes Tiger's locked target",
							target == null ? "target" : target.getUUID(),
							tiger == null ? null : tiger.comboTargetUuid()));
				}
			});
		}
	}

	@GameTest(maxTicks = 180, skyAccess = true)
	public void tigerAutonomouslyApproachesAThreatToItsOwner(GameTestHelper helper) {
		String fixture = "tigerAutonomouslyApproachesAThreatToItsOwner";
		paveFloor(helper, 0, 9);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> threatRef = new AtomicReference<>();
		AtomicBoolean completed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow threat = durableCow(helper, new BlockPos(6, 1, 4));
			// Only OUR coordinator may autonomously mark the threat — foreign packs skip it.
			threat.removeTag("jujutsu.autonomous_mark.none");
			threat.addTag("jujutsu.autonomous_mark." + caster.getUUID());
			threat.setTarget(caster);
			putInFront(tiger, threat, 2.0);
			threatRef.set(threat);
		}));
		for (int tick = SIC_TICK + 1; tick <= SIC_TICK + 100; tick++) {
			int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (completed.get()) {
					return;
				}
				MegumiTigerEntity tiger = tigerRef.get();
				Cow threat = threatRef.get();
				if (tiger != null && threat != null && threat.getUUID().equals(tiger.comboTargetUuid())) {
					completed.set(true);
					discard(threat);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 100) {
					discard(threat);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "autonomy", caster,
							"owner threat becomes Tiger's locked target",
							threat == null ? "threat" : threat.getUUID(),
							tiger == null ? null : tiger.comboTargetUuid()));
				}
			});
		}
	}

	@GameTest(maxTicks = 180, skyAccess = true)
	public void tigerRetaliatesAgainstTheOwnersRecordedAttacker(GameTestHelper helper) {
		String fixture = "tigerRetaliatesAgainstTheOwnersRecordedAttacker";
		paveFloor(helper, 0, 9);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupNamedMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f, "tiger-retaliation-caster");
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> attackerRef = new AtomicReference<>();
		AtomicBoolean completed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow attacker = durableCow(helper, new BlockPos(6, 1, 4));
			// Retaliation bypasses the autonomous-mark gate, so the attacker keeps its
			// `none` tag: the lock can only arrive via the recorded-attacker signal
			// (caster.hurtServer below), never via autonomous marking.
			putInFront(tiger, attacker, 2.0);
			attackerRef.set(attacker);
			caster.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			helper.assertTrue(caster.getHealth() < caster.getMaxHealth(),
					diagnostic(helper, fixture, "retaliation", caster, "scripted hit landed",
							"< " + caster.getMaxHealth(), caster.getHealth()));
		}));
		for (int tick = SIC_TICK + 1; tick <= SIC_TICK + 100; tick++) {
			int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (completed.get()) {
					return;
				}
				MegumiTigerEntity tiger = tigerRef.get();
				Cow attacker = attackerRef.get();
				if (tiger != null && attacker != null && attacker.getUUID().equals(tiger.comboTargetUuid())) {
					completed.set(true);
					discard(attacker);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
					return;
				}
				if (pollTick == SIC_TICK + 100) {
					discard(attacker);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "retaliation", caster,
							"recorded attacker becomes Tiger's locked target",
							attacker == null ? "attacker" : attacker.getUUID(),
							tiger == null ? null : tiger.comboTargetUuid()));
				}
			});
		}
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void tigerRefusesAlliesAndOwnedSummonsAsTargets(GameTestHelper helper) {
		String fixture = "tigerRefusesAlliesAndOwnedSummonsAsTargets";
		paveFloor(helper, 0, 9);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<MegumiNueEntity> nueRef = new AtomicReference<>();
		AtomicReference<ServerPlayer> allyRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		helper.runAtTickTime(4, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					diagnostic(helper, fixture, "coexist", caster, "Nue summon accepted", true, false));
		}));
		helper.runAtTickTime(24, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiNueEntity nue = level.getEntities(EntityTypeTest.forClass(MegumiNueEntity.class),
					candidate -> caster.getUUID().equals(candidate.ownerUuid())).stream().findFirst().orElse(null);
			helper.assertTrue(nue != null,
					diagnostic(helper, fixture, "owned-target", caster, "owned Nue present", true, nue != null));
			nueRef.set(nue);
			TodoSwapTestFixtures.aimAt(caster, caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0));
			boolean siccedOwner = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(!siccedOwner,
					diagnostic(helper, fixture, "owner-target", caster, "owner rejected as sic target",
							false, siccedOwner));
			TodoSwapTestFixtures.aimAt(caster, nue.position().add(0.0, nue.getBbHeight() * 0.5, 0.0));
			boolean siccedOwnedBody = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(!siccedOwnedBody,
					diagnostic(helper, fixture, "owned-target", caster, "own pack body rejected as sic target",
						false, siccedOwnedBody));

			// The ally is a second mock player, not a teamed mob: every mock player shares the
			// scoreboard name "test-mock-player", so caster and ally resolve the SAME team object
			// whatever it is — permanently allied even while a concurrent fixture re-seats the
			// shared name mid-test. A teamed cow only stays allied until that re-seat lands
			// (observed: casterTeam=serp… vs cowTeam=tig…), then the tiger lawfully marks it.
			ServerPlayer ally = helper.makeMockServerPlayerInLevel();
			BlockPos allyPad = helper.absolutePos(new BlockPos(7, 1, 7));
			ally.teleportTo(level, allyPad.getX() + 0.5, allyPad.getY(), allyPad.getZ() + 0.5,
					Set.of(), 0.0f, 0.0f, false);
			allyRef.set(ally);
			TodoSwapTestFixtures.aimAt(caster, ally.position().add(0.0, ally.getBbHeight() * 0.5, 0.0));
			boolean siccedAlly = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(!siccedAlly,
					diagnostic(helper, fixture, "ally-target", caster, "same-team target rejected",
						false, siccedAlly));
		}));
		helper.runAtTickTime(70, () -> {
			try {
				MegumiTigerEntity tiger = tigerRef.get();
				MegumiNueEntity nue = nueRef.get();
				helper.assertTrue(tiger != null && tiger.comboTargetUuid() == null,
					diagnostic(helper, fixture, "friendly-fire", caster, "Tiger remains without a friendly target",
							null, tiger == null ? "missing" : tiger.comboTargetUuid()));
				ServerPlayer ally = allyRef.get();
				helper.assertTrue(nue != null && nue.isAlive()
						&& nue.getHealth() == nue.getMaxHealth(),
						diagnostic(helper, fixture, "friendly-fire", caster, "owned Nue stays unharmed",
								"alive at max health", nue == null ? "missing"
										: nue.isAlive() + "/" + nue.getHealth() + "/" + nue.getMaxHealth()
										+ " removed=" + nue.isRemoved() + " phase=" + nue.phase()
										+ " pos=" + nue.position()));
				helper.assertTrue(ally != null && ally.getHealth() == ally.getMaxHealth(),
						diagnostic(helper, fixture, "friendly-fire", caster, "allied target stays unharmed",
								ally == null ? "ally" : ally.getMaxHealth(),
								ally == null ? "missing" : ally.getHealth()));
				helper.assertTrue(caster.getHealth() == caster.getMaxHealth(),
					diagnostic(helper, fixture, "friendly-fire", caster, "owner health unchanged",
							caster.getMaxHealth(), caster.getHealth()));
			} finally {
				ServerPlayer ally = allyRef.get();
				if (ally != null) {
					level.getServer().getPlayerList().remove(ally);
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(80, helper::succeed);
	}

	@GameTest(maxTicks = 60)
	public void tigerOwnerDisconnectTeardownRemovesItsBody(GameTestHelper helper) {
		String fixture = "tigerOwnerDisconnectTeardownRemovesItsBody";
		paveFloor(helper, 0, 8);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> bodyRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			bodyRef.set(summonTiger(helper, fixture, caster, level))));
		helper.runAtTickTime(20, () -> {
			try {
				MegumiShikigamiRuntime.teardown(level.getServer(), caster.getUUID(),
						MegumiShikigamiRuntime.TeardownReason.DISCONNECT);
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "owner-cleanup", caster);
				helper.assertTrue(tigersOwnedBy(level, caster.getUUID()).isEmpty(),
					diagnostic(helper, fixture, "owner-cleanup", caster, "owned Tiger bodies after teardown", 0,
							tigersOwnedBy(level, caster.getUUID()).size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, helper::succeed);
	}

	@GameTest(maxTicks = 60)
	public void tigerDimensionChangeTeardownUsesRecallCooldown(GameTestHelper helper) {
		String fixture = "tigerDimensionChangeTeardownUsesRecallCooldown";
		paveFloor(helper, 0, 8);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			summonTiger(helper, fixture, caster, level)));
		helper.runAtTickTime(20, () -> {
			try {
				MegumiShikigamiRuntime.teardown(level.getServer(), caster.getUUID(),
						MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "dimension", caster);
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(),
						MegumiShikigami.TIGER, level.getGameTime());
				helper.assertTrue(cooldown == MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS,
					diagnostic(helper, fixture, "dimension", caster, "Tiger recall-family cooldown",
							MegumiShikigamiProfile.TIGER_RECALL_COOLDOWN_TICKS, cooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, helper::succeed);
	}

	@GameTest(maxTicks = 60)
	public void tigerCoexistsWithNueAsAnIndependentPack(GameTestHelper helper) {
		String fixture = "tigerCoexistsWithNueAsAnIndependentPack";
		paveFloor(helper, 0, 9);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			summonTiger(helper, fixture, caster, level)));
		helper.runAtTickTime(4, () -> {
			try {
				MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.NUE);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned,
					diagnostic(helper, fixture, "coexist", caster, "Nue summon accepted", true, summoned));
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), caster.getUUID());
				helper.assertTrue(types.contains(MegumiShikigami.TIGER.id())
						&& types.contains(MegumiShikigami.NUE.id()),
					diagnostic(helper, fixture, "coexist", caster, "independent Tiger and Nue packs",
							List.of(MegumiShikigami.TIGER.id(), MegumiShikigami.NUE.id()), types));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, helper::succeed);
	}

	@GameTest(maxTicks = 120, skyAccess = true)
	public void tigerFullComboLandsThreeHitsAndLaunchesOnFinisher(GameTestHelper helper) {
		String fixture = "tigerFullComboLandsThreeHitsAndLaunchesOnFinisher";
		paveFloor(helper, 0, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> targetRef = new AtomicReference<>();
		AtomicReference<Float> afterFirstRef = new AtomicReference<>();
		AtomicReference<Float> afterSecondRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		prepareManualCombo(helper, fixture, caster, tigerRef, targetRef);
		helper.runAtTickTime(SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK - 1, () -> {
			Cow target = targetRef.get();
			helper.assertTrue(target != null && target.getHealth() == target.getMaxHealth(),
					diagnostic(helper, fixture, "pre-window", caster, "no hit before authored first window",
						target == null ? "target" : target.getMaxHealth(),
						target == null ? "missing" : target.getHealth()));
		});
		helper.runAtTickTime(SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK + 2, () -> {
			Cow target = targetRef.get();
			afterFirstRef.set(target == null ? 0.0f : target.getHealth());
			helper.assertTrue(target != null && afterFirstRef.get() < target.getMaxHealth(),
					diagnostic(helper, fixture, "strike-1", caster, "first authored hit landed",
						target == null ? "target" : "< " + target.getMaxHealth(), afterFirstRef.get()));
		});
		helper.runAtTickTime(SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE2_WINDOW_TICK + 2, () -> {
			Cow target = targetRef.get();
			afterSecondRef.set(target == null ? 0.0f : target.getHealth());
			helper.assertTrue(target != null && afterSecondRef.get() < afterFirstRef.get(),
					diagnostic(helper, fixture, "strike-2", caster, "second authored hit landed",
						afterFirstRef.get(), afterSecondRef.get()));
		});
		helper.runAtTickTime(SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK + 2, () -> {
			try {
				Cow target = targetRef.get();
				MegumiTigerEntity tiger = tigerRef.get();
				helper.assertTrue(target != null && target.getHealth() < afterSecondRef.get(),
					diagnostic(helper, fixture, "finisher", caster, "finisher hit landed",
						afterSecondRef.get(), target == null ? "missing" : target.getHealth()));
				helper.assertTrue(target != null && target.getDeltaMovement().y > 0.15,
					diagnostic(helper, fixture, "finisher", caster, "target launched vertically",
						"> 0.15", target == null ? "missing" : target.getDeltaMovement().y));
				helper.assertTrue(tiger != null && tiger.comboTargetUuid() == null,
					diagnostic(helper, fixture, "recovery", caster, "lock released after finisher", null,
						tiger == null ? "missing" : tiger.comboTargetUuid()));
			} finally {
				discard(targetRef.get());
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(80, helper::succeed);
	}

	@GameTest(maxTicks = 120, skyAccess = true)
	public void tigerMissConsumesItsBeatAndTheSequenceContinues(GameTestHelper helper) {
		String fixture = "tigerMissConsumesItsBeatAndTheSequenceContinues";
		paveFloor(helper, 0, 12);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> targetRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		prepareManualCombo(helper, fixture, caster, tigerRef, targetRef);
		int moveOutTick = SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK - 2;
		helper.runAtTickTime(moveOutTick, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow target = targetRef.get();
			if (tiger != null && target != null) {
				putInFront(tiger, target, MegumiShikigamiProfile.TIGER_STRIKE_RANGE + 5.0);
			}
		});
		helper.runAtTickTime(moveOutTick + 4, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow target = targetRef.get();
			helper.assertTrue(target != null && target.getHealth() == target.getMaxHealth(),
					diagnostic(helper, fixture, "miss", caster, "out-of-range strike did no damage",
						target == null ? "target" : target.getMaxHealth(),
						target == null ? "missing" : target.getHealth()));
			helper.assertTrue(tiger != null && target != null && target.getUUID().equals(tiger.comboTargetUuid())
					&& tiger.comboStep() == 2,
					diagnostic(helper, fixture, "miss", caster, "miss consumed beat and advanced to strike two",
						target == null ? "locked target, step 2" : target.getUUID() + "/step 2",
						tiger == null ? "missing" : tiger.comboTargetUuid() + "/step " + tiger.comboStep()));
			if (tiger != null && target != null) {
				putInFront(tiger, target, 2.0);
			}
		});
		helper.runAtTickTime(SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE2_WINDOW_TICK + 3, () -> {
			try {
				Cow target = targetRef.get();
				helper.assertTrue(target != null && target.getHealth() < target.getMaxHealth(),
					diagnostic(helper, fixture, "continue", caster, "later strike lands after miss",
						target == null ? "target" : "< " + target.getMaxHealth(),
						target == null ? "missing" : target.getHealth()));
			} finally {
				discard(targetRef.get());
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(80, helper::succeed);
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void tigerComboDoesNotRetargetAfterASecondSic(GameTestHelper helper) {
		String fixture = "tigerComboDoesNotRetargetAfterASecondSic";
		paveFloor(helper, 0, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiTigerEntity> tigerRef = new AtomicReference<>();
		AtomicReference<Cow> firstRef = new AtomicReference<>();
		AtomicReference<Cow> secondRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () ->
			tigerRef.set(summonTiger(helper, fixture, caster, level))));
		prepareManualCombo(helper, fixture, caster, tigerRef, firstRef);
		helper.runAtTickTime(SIC_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow first = firstRef.get();
			Cow second = durableCow(helper, new BlockPos(2, 1, 7));
			secondRef.set(second);
			TodoSwapTestFixtures.aimAt(caster, second.position().add(0.0, second.getBbHeight() * 0.5, 0.0));
			boolean switched = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(switched,
					diagnostic(helper, fixture, "switch", caster, "second sic is accepted as a new mark", true, switched));
			if (tiger != null && second != null) {
				putInFront(tiger, second, 2.0);
			}
			helper.assertTrue(tiger != null && first != null && first.getUUID().equals(tiger.comboTargetUuid()),
				diagnostic(helper, fixture, "switch", caster, "committed target UUID stays locked",
						first == null ? "first target" : first.getUUID(),
						tiger == null ? null : tiger.comboTargetUuid()));
		}));
		int firstWindow = SIC_TICK + MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK + 2;
		helper.runAtTickTime(firstWindow, () -> {
			try {
				Cow first = firstRef.get();
				Cow second = secondRef.get();
				MegumiTigerEntity tiger = tigerRef.get();
				helper.assertTrue(first != null && first.getHealth() < first.getMaxHealth(),
					diagnostic(helper, fixture, "locked-hit", caster, "original target receives strike one",
						first == null ? "target" : "< " + first.getMaxHealth(),
						first == null ? "missing" : first.getHealth()));
				helper.assertTrue(second != null && second.getHealth() == second.getMaxHealth(),
					diagnostic(helper, fixture, "locked-hit", caster, "new sic target receives no combo hit",
						second == null ? "target" : second.getMaxHealth(),
						second == null ? "missing" : second.getHealth()));
				helper.assertTrue(tiger != null && first != null && first.getUUID().equals(tiger.comboTargetUuid()),
					diagnostic(helper, fixture, "locked-hit", caster, "combo lock remains original target",
						first == null ? "target" : first.getUUID(), tiger == null ? null : tiger.comboTargetUuid()));
			} finally {
				discard(firstRef.get());
				discard(secondRef.get());
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(70, helper::succeed);
	}

	private static void prepareManualCombo(GameTestHelper helper, String fixture, ServerPlayer caster,
			AtomicReference<MegumiTigerEntity> tigerRef, AtomicReference<Cow> targetRef) {
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiTigerEntity tiger = tigerRef.get();
			Cow target = durableCow(helper, new BlockPos(6, 1, 4));
			targetRef.set(target);
			faceForward(tiger);
			issueSic(helper, fixture, caster, target);
			putInFront(tiger, target, 2.0);
		}));
	}

	private static MegumiTigerEntity summonTiger(GameTestHelper helper, String fixture,
			ServerPlayer caster, ServerLevel level) {
		MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TIGER);
		boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
		helper.assertTrue(summoned,
			diagnostic(helper, fixture, "summon", caster, "Tiger summon accepted", true, summoned));
		List<MegumiTigerEntity> bodies = tigersOwnedBy(level, caster.getUUID());
		helper.assertTrue(bodies.size() == 1,
			diagnostic(helper, fixture, "summon", caster, "owned Tiger bodies", 1, bodies.size()));
		// Cross-test isolation: neither the tiger nor its caster may become a foreign pack's
		// autonomous mark.
		caster.addTag("jujutsu.autonomous_mark.none");
		bodies.getFirst().addTag("jujutsu.autonomous_mark.none");
		return bodies.getFirst();
	}

	private static Cow durableCow(GameTestHelper helper, BlockPos position) {
		Cow cow = helper.spawn(EntityType.COW, position);
		cow.setPersistenceRequired();
		cow.setNoAi(true);
		// A Cow survives a PEACEFUL difficulty flip (Monster zombies self-discard) and the tag
		// bars every foreign coordinator's autonomous mark.
		cow.addTag("jujutsu.autonomous_mark.none");
		AttributeInstance maxHealth = cow.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(100.0);
		}
		cow.setHealth(cow.getMaxHealth());
		return cow;
	}

	private static void issueSic(GameTestHelper helper, String fixture, ServerPlayer caster, Cow target) {
		TodoSwapTestFixtures.aimAt(caster, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
		boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
		helper.assertTrue(sicced,
			diagnostic(helper, fixture, "sic", caster, "global sic accepted", true, sicced));
	}

	private static void faceForward(MegumiTigerEntity tiger) {
		tiger.setYRot(0.0f);
		tiger.setXRot(0.0f);
		tiger.setYHeadRot(0.0f);
	}

	private static void putInFront(MegumiTigerEntity tiger, net.minecraft.world.entity.Entity target,
			double distance) {
		target.setPos(tiger.getX(), tiger.getY(), tiger.getZ() + distance);
	}

	private static List<MegumiTigerEntity> tigersOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiTigerEntity> owned = new ArrayList<>();
		for (MegumiTigerEntity tiger : level.getEntities(EntityTypeTest.forClass(MegumiTigerEntity.class),
				candidate -> true)) {
			if (ownerId.equals(tiger.ownerUuid())) {
				owned.add(tiger);
			}
		}
		return owned;
	}

	private static void paveFloor(GameTestHelper helper, int min, int max) {
		for (int x = min; x <= max; x++) {
			for (int z = min; z <= max; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static void discard(net.minecraft.world.entity.Entity entity) {
		if (entity != null && !entity.isRemoved()) {
			entity.discard();
		}
	}

	private static net.minecraft.network.chat.Component diagnostic(GameTestHelper helper,
			String fixture, String phase, ServerPlayer caster, String what, Object expected, Object actual) {
		return MegumiShikigamiTestFixtures.diagnostic(fixture, phase, helper.getTick(),
				caster.getUUID(), what, expected, actual);
	}
}
