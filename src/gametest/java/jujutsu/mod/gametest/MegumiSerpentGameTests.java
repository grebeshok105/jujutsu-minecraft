package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiShikigamiSpawnPlacement;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEntities;

/** Great Serpent server lifecycle scenarios, including a red-proof for the recall release seam. */
public final class MegumiSerpentGameTests {
	private static final int SUMMON_TICK = 2;
	private static final int SIC_TICK = 24;
	private static final long HOLD_DEADLINE_TICK = 160;
	private static final long RELEASE_DEADLINE_TICK = 245;
	private static final BlockPos OWNER_FEET = new BlockPos(2, 1, 2);
	private static final BlockPos VICTIM_FEET = new BlockPos(7, 1, 6);

	/** Summon is free, materialization reaches ACTIVE, and manual recall clears the pack/cooldown is armed. */
	@GameTest(maxTicks = 60)
	public void serpentSummonMaterializesAndRecalls(GameTestHelper helper) {
		String fixture = "serpentSummonMaterializesAndRecalls";
		layFloor(helper);
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, OWNER_FEET, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false),
					Component.literal("Serpent summon accepted"));
			List<MegumiSerpentEntity> bodies = serpentOwnedBy(level, owner.getUUID());
			helper.assertTrue(bodies.size() == 1, diagnostic(helper, fixture, owner,
					"one owned Serpent", 1, bodies.size()));
			helper.assertTrue(bodies.get(0).phase()
					== jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING,
					diagnostic(helper, fixture, owner, "initial phase", "MATERIALIZING", bodies.get(0).phase()));
		}));
		helper.runAtTickTime(22, () -> {
			try {
				List<MegumiSerpentEntity> bodies = serpentOwnedBy(level, owner.getUUID());
				helper.assertTrue(bodies.size() == 1 && bodies.get(0).combatEnabled(),
					diagnostic(helper, fixture, owner, "materialization completes", "one ACTIVE body", bodies));
				long summonCost = MegumiSummonCooldowns.remainingTicks(owner.getUUID(), MegumiShikigami.SERPENT,
						level.getGameTime());
				helper.assertTrue(summonCost == 0, diagnostic(helper, fixture, owner,
						"summon has no cooldown", 0, summonCost));
				helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false),
					Component.literal("second press recalls the Serpent"));
				helper.assertTrue(MegumiShikigamiRuntime.packViews(level.getServer(), owner.getUUID()).isEmpty(),
					Component.literal("recall removes the pack record immediately"));
				long recallCost = MegumiSummonCooldowns.remainingTicks(owner.getUUID(), MegumiShikigami.SERPENT,
						level.getGameTime());
				helper.assertTrue(recallCost == MegumiShikigamiProfile.SERPENT_RECALL_COOLDOWN_TICKS,
					diagnostic(helper, fixture, owner, "recall cooldown", MegumiShikigamiProfile.SERPENT_RECALL_COOLDOWN_TICKS,
						recallCost));
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
	}

	/** A blocked ground placement rolls back without leaving a pack or partial body. */
	@GameTest(maxTicks = 40)
	public void serpentNoRoomRollsBackSummon(GameTestHelper helper) {
		String fixture = "serpentNoRoomRollsBackSummon";
		layFloor(helper);
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(8, 1, 8), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				BlockPos ownerFeet = BlockPos.containing(owner.position());
				Vec3 spot = MegumiShikigamiSpawnPlacement.ground(level, owner.position(), owner.getYRot(),
						JujutsuEntities.MEGUMI_SERPENT.getDimensions());
				for (int attempt = 0; spot != null && attempt < 4; attempt++) {
					BlockPos candidate = BlockPos.containing(spot);
					for (int x = candidate.getX() - 1; x <= candidate.getX() + 1; x++) {
						for (int z = candidate.getZ() - 1; z <= candidate.getZ() + 1; z++) {
							for (int y = ownerFeet.getY() - 3; y <= ownerFeet.getY() + 3; y++) {
								level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
							}
						}
					}
					spot = MegumiShikigamiSpawnPlacement.ground(level, owner.position(), owner.getYRot(),
							JujutsuEntities.MEGUMI_SERPENT.getDimensions());
				}
				helper.assertTrue(spot == null,
						diagnostic(helper, fixture, owner, "no safe Serpent spawn candidate", null, spot));
				MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
				helper.assertTrue(!summoned, diagnostic(helper, fixture, owner,
						"blocked summon rejected", false, summoned));
				helper.assertTrue(MegumiShikigamiRuntime.packViews(helper.getLevel().getServer(), owner.getUUID()).isEmpty(),
						Component.literal("failed summon leaves no pack record"));
				helper.assertTrue(serpentOwnedBy(helper.getLevel(), owner.getUUID()).isEmpty(),
						Component.literal("failed summon leaves no Serpent body"));
				helper.assertTrue(MegumiSummonCooldowns.remainingTicks(owner.getUUID(),
								MegumiShikigami.SERPENT, helper.getLevel().getGameTime()) == 0,
						Component.literal("failed summon charges no Serpent cooldown"));
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
	}

	/** One sic order reaches both coexisting packs; the Serpent's controlled target is not damaged. */
	@GameTest(maxTicks = 150)
	public void serpentCoexistsAndReceivesGlobalSic(GameTestHelper helper) {
		String fixture = "serpentCoexistsAndReceivesGlobalSic";
		layFloor(helper);
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, OWNER_FEET, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow victim = spawnCow(helper, VICTIM_FEET);
		victim.addTag("jujutsu.autonomous_mark.none");
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false), Component.literal("Serpent summon succeeds"));
		}));
		helper.runAtTickTime(20, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false), Component.literal("Nue summon coexists"));
		}));
		helper.runAtTickTime(40, () -> {
			try {
				TodoSwapTestFixtures.aimAt(owner, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
				helper.assertTrue(MegumiShikigamiRuntime.trySic(owner, false), Component.literal("global sic accepted"));
				List<MegumiSerpentEntity> serpents = serpentOwnedBy(level, owner.getUUID());
				List<? extends MegumiNueEntity> nues = level.getEntities(EntityTypeTest.forClass(MegumiNueEntity.class),
						candidate -> owner.getUUID().equals(candidate.ownerUuid()));
				helper.assertTrue(nues.size() == 1 && nues.get(0).combatEnabled(),
						diagnostic(helper, fixture, owner, "Nue active before global sic", true, nues));
				helper.assertTrue(serpents.size() == 1 && serpents.get(0).getTarget() == victim,
						diagnostic(helper, fixture, owner, "Serpent receives sic mark", victim, serpents));
				helper.assertTrue(nues.size() == 1 && nues.get(0).getTarget() == victim,
						diagnostic(helper, fixture, owner, "Nue receives same global sic", victim, nues));
				List<PackView> packs = MegumiShikigamiRuntime.packViews(level.getServer(), owner.getUUID());
				helper.assertTrue(packs.size() == 2, diagnostic(helper, fixture, owner,
						"two types coexist", 2, packs));
				LivingEntity lastHurtBy = victim.getLastHurtByMob();
				helper.assertTrue(lastHurtBy != serpents.get(0),
						diagnostic(helper, fixture, owner, "Serpent did not damage its marked target",
								"not " + serpents.get(0).getUUID(),
								lastHurtBy == null ? null : lastHurtBy.getUUID()));
				victim.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				victim.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
	}

	/** With no manual sic, the coordinator's autonomous target mark still starts a restraint ambush. */
	@GameTest(maxTicks = 180)
	public void serpentAutonomouslyAmbushesMarkedTarget(GameTestHelper helper) {
		runReleaseScenario(helper, "serpentAutonomouslyAmbushesMarkedTarget", ReleasePath.MANUAL_RECALL,
				TargetKind.ZOMBIE, false, false);
	}

	/** The retaliation pass marks the attacker; a manual order is not needed for the hold to start. */
	@GameTest(maxTicks = 180)
	public void serpentRetaliatesAgainstItsOwnersAttacker(GameTestHelper helper) {
		runReleaseScenario(helper, "serpentRetaliatesAgainstItsOwnersAttacker", ReleasePath.MANUAL_RECALL,
				TargetKind.ZOMBIE, false, true);
	}

	/** Owner, allied player, and another member of the owner's pack are all refused as bind targets. */
	@GameTest(maxTicks = 90)
	public void serpentRefusesFriendlyTargets(GameTestHelper helper) {
		String fixture = "serpentRefusesFriendlyTargets";
		layFloor(helper);
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, OWNER_FEET, 0.0f, 0.0f);
		// The ally is a second mock player, not a teamed victim: every mock player shares the
		// scoreboard name "test-mock-player", so it resolves the caster's team object whatever it
		// is — permanently allied with zero scoreboard writes. Teaming a "cursed-spirit-victim"
		// here re-seats that shared name mid-run and silently allies EVERY concurrent test's
		// victim (observed: victimDisconnect sic refused with ownerAllied=true).
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		BlockPos allyPad = helper.absolutePos(VICTIM_FEET);
		ally.teleportTo(level, allyPad.getX() + 0.5, allyPad.getY(), allyPad.getZ() + 0.5,
				java.util.Set.of(), 0.0f, 0.0f, false);
		float ownerHealth = owner.getHealth();
		float allyHealth = ally.getHealth();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false), Component.literal("Serpent summon succeeds"));
		}));
		helper.runAtTickTime(20, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false), Component.literal("own-pack body appears"));
		}));
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				List<? extends MegumiNueEntity> nues = level.getEntities(EntityTypeTest.forClass(MegumiNueEntity.class),
						candidate -> owner.getUUID().equals(candidate.ownerUuid()));
				helper.assertTrue(nues.size() == 1, Component.literal("one own-pack target exists"));
				TodoSwapTestFixtures.aimAt(owner, nues.get(0).position().add(0.0, 0.5, 0.0));
				helper.assertTrue(!MegumiShikigamiRuntime.trySic(owner, false), Component.literal("own pack member is not a sic target"));
				// No scoreboard writes: the mock ally shares the caster's "test-mock-player"
				// scoreboard name, so whatever team the name currently sits on is shared —
				// isAlliedTo holds without ever touching the scoreboard (see the comment at
				// the ally's spawn for why teaming is actively harmful here).
				TodoSwapTestFixtures.aimAt(owner, ally.position().add(0.0, ally.getBbHeight() * 0.5, 0.0));
				helper.assertTrue(!MegumiShikigamiRuntime.trySic(owner, false), Component.literal("allied player is not a sic target"));
				helper.assertTrue(!HoldSupport.isHeld(ally), Component.literal("allied player never receives a hold marker"));
				helper.assertTrue(owner.getHealth() == ownerHealth && ally.getHealth() == allyHealth,
						Component.literal("friendly targets take no damage"));
				level.getServer().getPlayerList().remove(ally);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				level.getServer().getPlayerList().remove(ally);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
	}

	/** The shared ungrabbable entity-type tag prevents a bind even after a valid sic order. */
	@GameTest(maxTicks = 150)
	public void serpentRefusesUngrabbableTarget(GameTestHelper helper) {
		String fixture = "serpentRefusesUngrabbableTarget";
		layFloor(helper);
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, OWNER_FEET, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, VICTIM_FEET);
		golem.setNoAi(true);
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(owner, false), Component.literal("Serpent summon succeeds"));
		}));
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				TodoSwapTestFixtures.aimAt(owner, golem.position().add(0.0, golem.getBbHeight() * 0.5, 0.0));
				helper.assertTrue(MegumiShikigamiRuntime.trySic(owner, false), Component.literal("sic mark itself is accepted"));
			} catch (RuntimeException | AssertionError failure) {
				golem.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
		helper.runAtTickTime(100, () -> {
			try {
				helper.assertTrue(serpentOwnedBy(level, owner.getUUID()).stream()
						.noneMatch(body -> golem.getUUID().equals(body.bindTargetUuid())),
						Component.literal("ungrabbable golem never becomes a bound victim"));
				helper.assertTrue(!HoldSupport.isHeld(golem), Component.literal("ungrabbable golem receives no held marker"));
				golem.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				helper.succeed();
			} catch (RuntimeException | AssertionError failure) {
				golem.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				throw failure;
			}
		});
	}

	/** Red-proof: establishes registry ownership, then the primary-recall route calls beginRecall() and verifies release. */
	@GameTest(maxTicks = 250)
	public void establishedHoldIsReleasedWhenManualRecallBegins(GameTestHelper helper) {
		runReleaseScenario(helper, "establishedHoldIsReleasedWhenManualRecallBegins", ReleasePath.MANUAL_RECALL,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void serpentDeathReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "serpentDeathReleasesEstablishedHold", ReleasePath.SERPENT_DEATH,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void serpentRemovalReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "serpentRemovalReleasesEstablishedHold", ReleasePath.SERPENT_REMOVAL,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void bindTimerReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "bindTimerReleasesEstablishedHold", ReleasePath.BIND_TIMER,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void bindLeashReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "bindLeashReleasesEstablishedHold", ReleasePath.BIND_LEASH,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void hardLeashTeleportReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "hardLeashTeleportReleasesEstablishedHold", ReleasePath.HARD_LEASH_TELEPORT,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void ownerDeathReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "ownerDeathReleasesEstablishedHold", ReleasePath.OWNER_DEATH,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void ownerDisconnectReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "ownerDisconnectReleasesEstablishedHold", ReleasePath.OWNER_DISCONNECT,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void ownerRespawnReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "ownerRespawnReleasesEstablishedHold", ReleasePath.OWNER_RESPAWN,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void ownerDimensionChangeReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "ownerDimensionChangeReleasesEstablishedHold", ReleasePath.OWNER_DIMENSION_CHANGE,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void victimDeathReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "victimDeathReleasesEstablishedHold", ReleasePath.VICTIM_DEATH,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void victimDisconnectReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "victimDisconnectReleasesEstablishedHold", ReleasePath.VICTIM_DISCONNECT,
				TargetKind.PLAYER, true, false);
	}

	@GameTest(maxTicks = 250)
	public void victimDimensionChangeReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "victimDimensionChangeReleasesEstablishedHold", ReleasePath.VICTIM_DIMENSION_CHANGE,
				TargetKind.PLAYER, true, false);
	}

	@GameTest(maxTicks = 250)
	public void victimUnloadReleasesRegistryByUuid(GameTestHelper helper) {
		runReleaseScenario(helper, "victimUnloadReleasesRegistryByUuid", ReleasePath.VICTIM_UNLOAD,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void newlyPassengerVictimIsReleased(GameTestHelper helper) {
		runReleaseScenario(helper, "newlyPassengerVictimIsReleased", ReleasePath.VICTIM_PASSENGER,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void newlyAlliedVictimIsReleased(GameTestHelper helper) {
		runReleaseScenario(helper, "newlyAlliedVictimIsReleased", ReleasePath.VICTIM_ALLIED,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void holdOwnershipMismatchDoesNotStealForeignHold(GameTestHelper helper) {
		runReleaseScenario(helper, "holdOwnershipMismatchDoesNotStealForeignHold", ReleasePath.HOLD_OWNERSHIP_MISMATCH,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void serverTeardownReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "serverTeardownReleasesEstablishedHold", ReleasePath.SERVER_TEARDOWN,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void deselectionTeardownReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "deselectionTeardownReleasesEstablishedHold", ReleasePath.DESELECTED,
				TargetKind.ZOMBIE, true, false);
	}

	@GameTest(maxTicks = 250)
	public void fixtureResetReleasesEstablishedHold(GameTestHelper helper) {
		runReleaseScenario(helper, "fixtureResetReleasesEstablishedHold", ReleasePath.FIXTURE_RESET,
				TargetKind.ZOMBIE, true, false);
	}

	private static void runReleaseScenario(GameTestHelper helper, String fixture, ReleasePath releasePath,
			TargetKind targetKind, boolean manualSic, boolean retaliate) {
		layFloor(helper);
		// Cross-test isolation: the caster gets a unique scoreboard name, and the victim joins a
		// team holding the shared "test-mock-player" name. Every foreign mock caster is allied to
		// the victim (ineligible to their sic/coordinator), while our uniquely-named caster is not
		// on that team and keeps the victim as a legal mark. Foreign autonomous marks can no longer
		// reach the victim, so no foreign bind/kill can preempt our hold.
		ServerPlayer owner = MegumiShikigamiTestFixtures.setupNamedMegumiCaster(
				helper, fixture, OWNER_FEET, 0.0f, 0.0f, "serpent-caster-" + fixture);
		ServerLevel level = helper.getLevel();
		LivingEntity victim = targetKind == TargetKind.PLAYER
				? CursedSpiritTestFixtures.setupVictim(helper, fixture, VICTIM_FEET,
						"serpent-victim-" + fixture)
				: spawnCow(helper, VICTIM_FEET);
		// Cross-test isolation: the autonomous-mark tag makes every FOREIGN coordinator skip this
		// victim — "none" bars all autonomous marks, "<ownerUuid>" allows only ours (the
		// autonomous test needs our coordinator to mark it). Manual sic and retaliation bypass
		// the gate, so every release path still works. Foreign packs can no longer mark, bind,
		// or kill the victim before our hold lands.
		victim.addTag("jujutsu.autonomous_mark."
				+ (manualSic || retaliate ? "none" : owner.getUUID().toString()));
		if (victim instanceof Cow cow) {
			cow.setNoAi(true);
			cow.setInvulnerable(true);
		}
		float victimHealth = victim.getHealth();
		float ownerHealth = owner.getHealth();
		// Foreign packs must never mark our owner either — a foreign mark would make them attack
		// him and trip the no-owner-damage oracle.
		owner.addTag("jujutsu.autonomous_mark.none");
		net.minecraft.world.scores.PlayerTeam ownerTeam = level.getScoreboard()
				.addPlayerTeam("serpent-owner-" + fixture);
		level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(), ownerTeam);
		// Every unteamed non-player living entity near the arena joins the owner's team: allied
		// entities are ineligible to our coordinator, so the autonomous pass can only ever pick
		// our victim. Teamed entities are left alone — re-seating them would strip whatever
		// alliance another fixture set up (mutual sabotage). ServerPlayers are skipped so
		// concurrent fixtures never steal each other's caster seat.
		List<String> seatedNames = new ArrayList<>();
		for (LivingEntity foreign : level.getEntitiesOfClass(LivingEntity.class,
				owner.getBoundingBox().inflate(MegumiShikigamiProfile.AUTONOMY_RADIUS + 5.0),
				entity -> entity != victim && entity != owner
						&& !(entity instanceof ServerPlayer)
						&& entity.getTeam() == null)) {
			level.getScoreboard().addPlayerToTeam(foreign.getScoreboardName(), ownerTeam);
			seatedNames.add(foreign.getScoreboardName());
		}
		// The temporary team must die with the fixture: surviving mobs left on it would be
		// skipped by later isolation passes (getTeam() != null) and become stray targets.
		// cleanupScenario unseats them and removes the team on every exit path.
		AtomicBoolean holdObserved = new AtomicBoolean();
		AtomicBoolean triggerApplied = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();
		List<Entity> foreignHolder = new ArrayList<>(1);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			double ownerVictimYDelta = Math.abs(owner.getY() - victim.getY());
			helper.assertTrue(ownerVictimYDelta <= 0.5,
					diagnostic(helper, fixture, owner, "owner and victim share fixture floor", "<= 0.5",
							ownerVictimYDelta));
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(summoned, diagnostic(helper, fixture, owner, "Serpent summon", true, summoned));
			List<MegumiSerpentEntity> bodies = serpentOwnedBy(level, owner.getUUID());
			helper.assertTrue(bodies.size() == 1, diagnostic(helper, fixture, owner,
					"one summoned Serpent body", 1, bodies.size()));
			// Our serpent must never become a foreign pack's mark — a foreign bind/kill on it is
			// exactly what zeroed serpentCount in earlier runs.
			bodies.get(0).addTag("jujutsu.autonomous_mark.none");
			double spawnYDelta = Math.abs(bodies.get(0).getY() - owner.getY());
			helper.assertTrue(spawnYDelta <= 0.5,
					diagnostic(helper, fixture, owner, "Serpent spawns on owner floor", "<= 0.5", spawnYDelta));
		}));
		if (retaliate) {
			helper.runAtTickTime(22, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
				if (victim instanceof Cow cow) {
					cow.setTarget(owner);
				}
				if (victim instanceof Cow cow) {
					owner.hurtServer(level, level.damageSources().mobAttack(cow), 1.0f);
				}
			}));
		}
		if (manualSic) {
			helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
				TodoSwapTestFixtures.aimAt(owner, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(owner, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, owner, "global sic accepted", true, sicced));
			}));
		}
		for (long tick = SIC_TICK + 1; tick <= RELEASE_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					// Re-seat every unteamed foreign living entity (incl. late-summoned shikigami)
					// onto the owner's team each poll: allied entities are ineligible to our
					// coordinator. Teamed entities keep their seat — stealing it would sabotage
					// whatever isolation another fixture configured.
					for (LivingEntity foreign : level.getEntitiesOfClass(LivingEntity.class,
							owner.getBoundingBox().inflate(MegumiShikigamiProfile.AUTONOMY_RADIUS + 5.0),
							entity -> entity != victim && entity != owner
									&& !(entity instanceof ServerPlayer)
									&& entity.getTeam() == null)) {
						level.getScoreboard().addPlayerToTeam(foreign.getScoreboardName(), ownerTeam);
					}
					List<MegumiSerpentEntity> bodies = serpentOwnedBy(level, owner.getUUID());
					if (!holdObserved.get() && HoldSupport.isHeld(victim)) {
						helper.assertTrue(bodies.size() == 1,
							diagnostic(helper, fixture, owner, "one active bound Serpent", 1, bodies.size()));
						MegumiSerpentEntity body = bodies.get(0);
						helper.assertTrue(victim.getUUID().equals(body.bindTargetUuid()),
							diagnostic(helper, fixture, owner, "bind intent UUID", victim.getUUID(), body.bindTargetUuid()));
						helper.assertTrue(HeldVictimRegistry.isHeldBy(body, victim),
							diagnostic(helper, fixture, owner, "registry confirms holder ownership", body.getUUID(),
								HeldVictimRegistry.holderUuid(victim.getUUID())));
						if (victim.getHealth() != victimHealth) {
							net.minecraft.world.damagesource.DamageSource lastSource = victim.getLastDamageSource();
							Entity sourceEntity = lastSource == null ? null : lastSource.getEntity();
							// Creative mock players punch through setInvulnerable (isInvulnerableTo
							// returns false for creative-player sources), so a foreign pack's hit
							// can still land. Absorb cross-arena contamination: damage attributed
							// to a player who is NOT our owner is healed back and skipped; damage
							// from our owner (our serpent attributes to him) still fails the
							// "hold deals no damage" oracle honestly.
							if (sourceEntity instanceof ServerPlayer sourcePlayer
									&& !sourcePlayer.getUUID().equals(owner.getUUID())) {
								victim.setHealth(victimHealth);
							}
						}
						helper.assertTrue(victim.getHealth() == victimHealth,
							diagnostic(helper, fixture, owner, "hold deals no damage", victimHealth, victim.getHealth()));
						if (!retaliate) {
							assertNoOwnerDamage(helper, fixture, owner, ownerHealth);
						}
						holdObserved.set(true);
						if (releasePath != ReleasePath.BIND_TIMER) {
							performReleasePath(helper, fixture, releasePath, owner, victim, body, foreignHolder);
						}
						triggerApplied.set(true);
					}
					if (holdObserved.get() && triggerApplied.get()
							&& releasedAsExpected(releasePath, victim, foreignHolder, bodies)) {
						List<MegumiSerpentEntity> remaining = serpentOwnedBy(level, owner.getUUID());
						helper.assertTrue(remaining.stream().noneMatch(body -> victim.getUUID().equals(body.bindTargetUuid())),
							diagnostic(helper, fixture, owner, "bind intent cleared", "none", remaining));
						if (!victim.isRemoved() && releasePath != ReleasePath.HOLD_OWNERSHIP_MISMATCH) {
							helper.assertTrue(!victim.hasEffect(jujutsu.mod.registry.JujutsuEffects.GRIPPED),
								diagnostic(helper, fixture, owner, "GRIPPED marker cleared", false,
									victim.hasEffect(jujutsu.mod.registry.JujutsuEffects.GRIPPED)));
						}
						// Teardown completeness: owner/teardown paths drop the pack record, and
						// vanish-family paths (death/removal/disconnect/respawn/server-stop)
						// discard the body outright — sink-out paths may leave a RECALLING body.
						// Victim-side releases (bind timer/leash, victim death/disconnect/unload,
						// passenger, allied, ownership mismatch) free the hold but keep the pack
						// alive — the serpent returns to FOLLOW.
						boolean packTeardown = releasePath == ReleasePath.MANUAL_RECALL
								|| releasePath == ReleasePath.SERPENT_DEATH || releasePath == ReleasePath.SERPENT_REMOVAL
								|| releasePath == ReleasePath.OWNER_DEATH || releasePath == ReleasePath.OWNER_DISCONNECT
								|| releasePath == ReleasePath.OWNER_RESPAWN || releasePath == ReleasePath.OWNER_DIMENSION_CHANGE
								|| releasePath == ReleasePath.SERVER_TEARDOWN || releasePath == ReleasePath.DESELECTED
								|| releasePath == ReleasePath.FIXTURE_RESET;
						boolean packPresent = MegumiShikigamiRuntime.packViews(level.getServer(), owner.getUUID()).stream()
								.anyMatch(pack -> MegumiShikigami.SERPENT.id().equals(pack.type()));
						helper.assertTrue(packPresent != packTeardown,
								diagnostic(helper, fixture, owner, "serpent pack record state",
										packTeardown ? "dropped" : "alive",
										MegumiShikigamiRuntime.packViews(level.getServer(), owner.getUUID())));
						if (releasePath == ReleasePath.SERPENT_DEATH || releasePath == ReleasePath.SERPENT_REMOVAL
								|| releasePath == ReleasePath.OWNER_DEATH || releasePath == ReleasePath.OWNER_DISCONNECT
								|| releasePath == ReleasePath.OWNER_RESPAWN || releasePath == ReleasePath.SERVER_TEARDOWN) {
							helper.assertTrue(remaining.isEmpty(),
								diagnostic(helper, fixture, owner, "serpent body discarded on vanish teardown",
										0, remaining.size()));
						}
						done.set(true);
						cleanupScenario(helper, owner, victim, foreignHolder);
						helper.succeed();
						return;
					}
					if (pollTick == HOLD_DEADLINE_TICK && !holdObserved.get()) {
						helper.assertTrue(false, diagnostic(helper, fixture, owner,
								"a real registry-owned hold is established before release", "held by " + HOLD_DEADLINE_TICK,
								Component.literal("not held")));
					}
					if (pollTick == RELEASE_DEADLINE_TICK) {
						helper.assertTrue(false, diagnostic(helper, fixture, owner,
								"release trigger completes", releasePath, Component.literal("hold or registry still active")));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					cleanupScenario(helper, owner, victim, foreignHolder);
					throw failure;
				}
			});
		}
	}

	private static void performReleasePath(GameTestHelper helper, String fixture, ReleasePath path,
			ServerPlayer owner, LivingEntity victim, MegumiSerpentEntity serpent, List<Entity> foreignHolder) {
		ServerLevel level = helper.getLevel();
		UUID ownerId = owner.getUUID();
		switch (path) {
			case MANUAL_RECALL -> {
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(owner, false);
				helper.assertTrue(recalled, diagnostic(helper, fixture, owner, "manual recall begins", true, recalled));
			}
			case SERPENT_DEATH -> {
				boolean killed = serpent.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(killed, diagnostic(helper, fixture, owner, "Serpent death pipeline", true, killed));
				long deathCost = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.SERPENT,
						level.getGameTime());
				helper.assertTrue(deathCost == MegumiShikigamiProfile.SERPENT_DEATH_COOLDOWN_TICKS,
						diagnostic(helper, fixture, owner, "death cooldown family",
								MegumiShikigamiProfile.SERPENT_DEATH_COOLDOWN_TICKS, deathCost));
			}
			case SERPENT_REMOVAL -> serpent.discard();
			case BIND_TIMER -> throw new AssertionError("timer is automatic");
			case BIND_LEASH -> owner.teleportTo(level, owner.getX() + 25.0, owner.getY(), owner.getZ(),
					java.util.Set.of(), owner.getYRot(), owner.getXRot(), false);
			case HARD_LEASH_TELEPORT -> serpent.teleportTo(level, serpent.getX(), serpent.getY() + 25.0,
					serpent.getZ(), java.util.Set.of(), serpent.getYRot(), serpent.getXRot(), false);
			case OWNER_DEATH -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.DEATH);
			case OWNER_DISCONNECT -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.DISCONNECT);
			case OWNER_RESPAWN -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.RESPAWN);
			case OWNER_DIMENSION_CHANGE -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);
			case VICTIM_DEATH -> {
				boolean killed = victim.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(killed, diagnostic(helper, fixture, owner, "victim death pipeline", true, killed));
			}
			case VICTIM_DISCONNECT -> {
				if (!(victim instanceof ServerPlayer player)) {
					throw new AssertionError("victim disconnect scenario requires a player");
				}
				level.getServer().getPlayerList().remove(player);
			}
			case VICTIM_DIMENSION_CHANGE -> victim.remove(Entity.RemovalReason.CHANGED_DIMENSION);
			case VICTIM_UNLOAD -> victim.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			case VICTIM_PASSENGER -> {
				Entity vehicle = helper.spawn(EntityType.MINECART, VICTIM_FEET);
				foreignHolder.add(vehicle);
				boolean mounted = victim.startRiding(vehicle, true);
				helper.assertTrue(mounted, diagnostic(helper, fixture, owner, "victim becomes passenger", true, mounted));
			}
			case VICTIM_ALLIED -> {
				// The victim joins whatever team the shared "test-mock-player" name currently
				// resolves — never the reverse. addPlayerToTeam on the OWNER would re-seat the
				// shared name mid-run and silently ally every concurrent fixture's caster
				// (observed: elephant presence test failing with casterTeam=bind… vs cowTeam=ele…).
				// A null owner team still allies: Team.isAlliedTo treats null==null as same side.
				Scoreboard scoreboard = level.getScoreboard();
				PlayerTeam ownerTeam = owner.getTeam();
				if (ownerTeam == null) {
					ownerTeam = scoreboard.addPlayerTeam("bind" + ownerId.toString().replace("-", "").substring(0, 8));
					scoreboard.addPlayerToTeam(owner.getScoreboardName(), ownerTeam);
				}
				scoreboard.addPlayerToTeam(victim.getScoreboardName(), ownerTeam);
			}
			case HOLD_OWNERSHIP_MISMATCH -> {
				HeldVictimRegistry.release(victim);
				Cow foreign = helper.spawn(EntityType.COW, VICTIM_FEET);
				foreign.setNoAi(true);
				helper.assertTrue(HeldVictimRegistry.hold(foreign, victim),
					diagnostic(helper, fixture, owner, "foreign holder obtains the pair", true,
						HeldVictimRegistry.holderUuid(victim.getUUID())));
				foreignHolder.add(foreign);
			}
			case SERVER_TEARDOWN -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.SERVER_STOPPING);
			case DESELECTED -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.DESELECTED);
			case FIXTURE_RESET -> MegumiShikigamiRuntime.teardown(level.getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.FIXTURE_RESET);
		}
	}

	private static boolean releasedAsExpected(ReleasePath path, LivingEntity victim,
			List<Entity> foreignHolder, List<MegumiSerpentEntity> bodies) {
		if (path == ReleasePath.HOLD_OWNERSHIP_MISMATCH) {
			return !foreignHolder.isEmpty()
					&& foreignHolder.get(0).getUUID().equals(HeldVictimRegistry.holderUuid(victim.getUUID()))
					&& bodies.stream().noneMatch(body -> victim.getUUID().equals(body.bindTargetUuid()));
		}
		return HeldVictimRegistry.holderUuid(victim.getUUID()) == null
				&& (!victim.hasEffect(jujutsu.mod.registry.JujutsuEffects.GRIPPED) || victim.isRemoved());
	}

	private static void cleanupScenario(GameTestHelper helper, ServerPlayer owner,
			LivingEntity victim, List<Entity> foreignHolder) {
		// Unseat every entity this fixture teamed and drop the temporary team: a stale
		// "serpent-owner-*" team would make later isolation passes skip those mobs
		// (getTeam() != null) and leave them as stray targets in the shared world.
		net.minecraft.world.scores.Scoreboard scoreboard = helper.getLevel().getScoreboard();
		for (net.minecraft.world.scores.PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
			if (team.getName().startsWith("serpent-owner-")) {
				for (String name : new ArrayList<>(team.getPlayers())) {
					scoreboard.removePlayerFromTeam(name, team);
				}
				scoreboard.removePlayerTeam(team);
			}
		}
		try {
			HoldSupport.release(victim);
		} catch (RuntimeException ignored) {
			// The victim may already be unloaded or removed.
		}
		for (Entity holder : foreignHolder) {
			holder.discard();
		}
		if (victim instanceof ServerPlayer player) {
			CursedSpiritTestFixtures.cleanupVictim(helper, player);
		} else {
			victim.discard();
		}
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
	}

	private static List<MegumiSerpentEntity> serpentOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiSerpentEntity> owned = new ArrayList<>();
		for (MegumiSerpentEntity serpent : level.getEntities(
				EntityTypeTest.forClass(MegumiSerpentEntity.class), candidate -> true)) {
			if (ownerId.equals(serpent.ownerUuid())) {
				owned.add(serpent);
			}
		}
		return owned;
	}

	private static Cow spawnCow(GameTestHelper helper, BlockPos feet) {
		Cow cow = helper.spawn(EntityType.COW, feet);
		cow.setNoAi(true);
		return cow;
	}


	private static void assertNoOwnerDamage(GameTestHelper helper, String fixture,
			ServerPlayer owner, float healthBefore) {
		helper.assertTrue(owner.getHealth() == healthBefore,
			diagnostic(helper, fixture, owner, "Serpent restraint does not hurt owner", healthBefore, owner.getHealth()));
	}

	private static net.minecraft.network.chat.Component diagnostic(GameTestHelper helper,
			String fixture, ServerPlayer owner, String what, Object expected, Object actual) {
		return MegumiShikigamiTestFixtures.diagnostic(fixture, "Serpent", helper.getTick(),
				owner.getUUID(), what, expected, actual);
	}

	private static void layFloor(GameTestHelper helper) {
		for (int x = -2; x <= 18; x++) {
			for (int z = -2; z <= 18; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// GameTest structures may overlap generated terrain. Start every Serpent fixture on a
		// consistent floor with enough clear headroom for the owner, victim, and full body boxes.
		for (int x = -2; x <= 18; x++) {
			for (int z = -2; z <= 18; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		// Prevent zombie victims from burning during long holds; this also removes weather variance.
		for (int x = -2; x <= 18; x++) {
			for (int z = -2; z <= 18; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
			}
		}
	}

	private enum TargetKind {
		ZOMBIE,
		PLAYER
	}

	private enum ReleasePath {
		MANUAL_RECALL,
		SERPENT_DEATH,
		SERPENT_REMOVAL,
		BIND_TIMER,
		BIND_LEASH,
		HARD_LEASH_TELEPORT,
		OWNER_DEATH,
		OWNER_DISCONNECT,
		OWNER_RESPAWN,
		OWNER_DIMENSION_CHANGE,
		VICTIM_DEATH,
		VICTIM_DISCONNECT,
		VICTIM_DIMENSION_CHANGE,
		VICTIM_UNLOAD,
		VICTIM_PASSENGER,
		VICTIM_ALLIED,
		HOLD_OWNERSHIP_MISMATCH,
		SERVER_TEARDOWN,
		DESELECTED,
		FIXTURE_RESET
	}
}
