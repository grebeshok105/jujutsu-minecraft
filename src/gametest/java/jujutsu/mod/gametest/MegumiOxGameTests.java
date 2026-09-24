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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.gametest.MegumiShikigamiTestFixtures;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.character.megumi.MegumiToadEntity;

/** Server GameTests for Piercing Ox lifecycle, committed movement, and multi-target impact. */
public final class MegumiOxGameTests {
	private static final int SUMMON_TICK = 2;
	private static final int ACTIVE_TICK = 20;
	private static final int SIC_TICK = 24;
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 400;

	private static void layFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void layStandardFloor(GameTestHelper helper) {
		layFloor(helper, -2, 18, -2, 40);
		// The default GameTest placement can sit inside generated terrain. Clear a three-block
		// interior volume above the floor so aimed-target and entity-LOS paths start unobstructed;
		// scenario-specific walls are built after setup and remain deliberate blockers.
		for (int x = 0; x <= 12; x++) {
			for (int z = 0; z <= 20; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static ServerPlayer setup(GameTestHelper helper, String fixture) {
		layStandardFloor(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture,
				new BlockPos(2, 1, 2), 0.0f, 0.0f);
		// Cross-test isolation: bar every foreign coordinator's autonomous mark on the caster so
		// neighbouring packs can't sic onto him mid-charge.
		caster.addTag("jujutsu.autonomous_mark.none");
		// A foreign mob's hit would poison lastHurtByMob: retaliation would then pick THAT
		// aggressor instead of the scripted attacker. Invulnerable keeps the stamp clean.
		caster.setInvulnerable(true);
		return caster;
	}

	private static boolean summonOx(GameTestHelper helper, ServerPlayer caster, String fixture) {
		MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.OX);
		boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
		helper.assertTrue(summoned, diagnostic(helper, fixture, "summon", caster.getUUID(), "Ox summoned", true, summoned));
		// The ox body must never become a foreign pack's mark — a foreign bind/kill mid-charge
		// produces the RECOVERY-with-null-mark signature seen in earlier runs.
		oxOwnedBy(helper.getLevel(), caster.getUUID())
				.forEach(ox -> ox.addTag("jujutsu.autonomous_mark.none"));
		return summoned;
	}

	private static List<MegumiOxEntity> oxOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiOxEntity> owned = new ArrayList<>();
		for (MegumiOxEntity ox : level.getEntities(EntityTypeTest.forClass(MegumiOxEntity.class), candidate -> true)) {
			if (ownerId.equals(ox.ownerUuid())) {
				owned.add(ox);
			}
		}
		return owned;
	}

	private static List<MegumiToadEntity> toadOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiToadEntity> owned = new ArrayList<>();
		for (MegumiToadEntity toad : level.getEntities(EntityTypeTest.forClass(MegumiToadEntity.class), candidate -> true)) {
			if (ownerId.equals(toad.ownerUuid())) {
				owned.add(toad);
			}
		}
		return owned;
	}

	private static void assertOneActiveOx(GameTestHelper helper, String fixture, ServerPlayer caster) {
		List<MegumiOxEntity> bodies = oxOwnedBy(helper.getLevel(), caster.getUUID());
		helper.assertTrue(bodies.size() == 1, diagnostic(helper, fixture, "active", caster.getUUID(),
				"owned Ox bodies", 1, bodies.size()));
		helper.assertTrue(bodies.get(0).combatEnabled(), diagnostic(helper, fixture, "active", caster.getUUID(),
				"Ox is ACTIVE", true, bodies.get(0).combatEnabled()));
	}

	private static void assertNoOxPack(GameTestHelper helper, String fixture, ServerPlayer caster) {
		boolean absent = MegumiShikigamiRuntime.packViews(helper.getLevel().getServer(), caster.getUUID()).stream()
				.noneMatch(pack -> MegumiShikigami.OX.id().equals(pack.type()));
		helper.assertTrue(absent, diagnostic(helper, fixture, "teardown", caster.getUUID(), "Ox pack absent", true, absent));
	}

	private static net.minecraft.network.chat.Component diagnostic(GameTestHelper helper, String fixture,
			String phase, UUID ownerId, String what, Object expected, Object actual) {
		return MegumiShikigamiTestFixtures.diagnostic(fixture, phase, helper.getTick(), ownerId,
				what, expected, actual);
	}

	private static String sicTrace(ServerLevel level, ServerPlayer caster,
			net.minecraft.world.entity.LivingEntity target) {
		jujutsu.mod.combat.TargetResolver.Result aim = jujutsu.mod.combat.TargetResolver.resolve(
				level, caster, MegumiShikigamiProfile.SIC_RANGE);
		net.minecraft.world.phys.BlockHitResult losHit = level.clip(new net.minecraft.world.level.ClipContext(
				caster.getEyePosition(), target.getEyePosition(),
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
		List<String> bodies = oxOwnedBy(level, caster.getUUID()).stream()
				.map(body -> body.getId() + " owner=" + body.ownerUuid() + " pos=" + body.position()
						+ " phase=" + body.phase() + "/" + body.phaseTicks()
						+ " active=" + body.combatEnabled() + " noAi=" + body.isNoAi()
						+ " target=" + body.getTarget())
				.toList();
		return "eye=" + caster.getEyePosition() + " look=" + caster.getLookAngle()
				+ " targetId=" + target.getId() + " target=" + target.getUUID() + "@" + target.position()
				+ " alive=" + target.isAlive() + " removed=" + target.isRemoved()
				+ " sameLevel=" + (target.level() == caster.level())
				+ " allied=" + caster.isAlliedTo(target) + " visible=" + caster.hasLineOfSight(target)
				+ " losClip=" + losHit.getType() + "@" + losHit.getBlockPos() + ":"
				+ level.getBlockState(losHit.getBlockPos())
				+ " aim=" + aim.mode() + "/" + aim.entityId() + " at=" + aim.point()
				+ " hitBlock=" + BlockPos.containing(aim.point()) + ":"
				+ level.getBlockState(BlockPos.containing(aim.point()))
				+ " packs=" + MegumiShikigamiRuntime.packViews(level.getServer(), caster.getUUID())
				+ " ox=" + bodies;
	}

	private static void placeAt(GameTestHelper helper, net.minecraft.world.entity.Entity entity,
			BlockPos relativeFeet) {
		BlockPos absolute = helper.absolutePos(relativeFeet);
		entity.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
	}

	private static Cow cow(GameTestHelper helper, String fixture, BlockPos feet) {
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, feet);
		cow.setPersistenceRequired();
		cow.setNoAi(true);
		// Default: no coordinator may autonomously mark this target. Tests that exercise the
		// autonomous pass retag with the caster's UUID so only OUR coordinator can mark it.
		cow.addTag("jujutsu.autonomous_mark.none");
		return cow;
	}

	private static Zombie zombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, feet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		zombie.addTag("jujutsu.autonomous_mark.none");
		return zombie;
	}

	/** Common S1/S2: successful summon, no summon price, and materialization to ACTIVE. */
	@GameTest(maxTicks = 50)
	public void oxSummonCreatesOneBodyAndMaterializesWithoutCooldown(GameTestHelper helper) {
		String fixture = "oxSummonCreatesOneBodyAndMaterializesWithoutCooldown";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				assertOneActiveOx(helper, fixture, caster);
				PackView pack = MegumiShikigamiRuntime.packViews(level.getServer(), caster.getUUID()).stream()
						.filter(view -> MegumiShikigami.OX.id().equals(view.type())).findFirst().orElseThrow();
				helper.assertTrue(pack.aliveBodies() == 1 && pack.anchorAlive(), diagnostic(helper, fixture,
						"active", caster.getUUID(), "one anchored Ox", "1/true", pack.aliveBodies() + "/" + pack.anchorAlive()));
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(cooldown == 0, diagnostic(helper, fixture, "active", caster.getUUID(),
						"summon cooldown", 0, cooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S3: blocked spawn candidates roll back without a body, pack, or cooldown. */
	@GameTest(maxTicks = 40)
	public void oxNoRoomRollsBackTheSummon(GameTestHelper helper) {
		String fixture = "oxNoRoomRollsBackTheSummon";
		ServerPlayer caster = setup(helper, fixture);
		int[][] centers = {{1, 2}, {4, 2}, {2, 4}, {2, 2}};
		for (int[] center : centers) {
			for (int x = center[0] - 1; x <= center[0] + 1; x++) {
				for (int z = center[1] - 1; z <= center[1] + 1; z++) {
					for (int y = 1; y <= 8; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}
		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.OX);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, diagnostic(helper, fixture, "rollback", caster.getUUID(),
						"summon rejected without clearance", false, summoned));
				assertNoOxPack(helper, fixture, caster);
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						helper.getLevel().getGameTime());
				helper.assertTrue(cooldown == 0, diagnostic(helper, fixture, "rollback", caster.getUUID(),
						"failed summon cooldown", 0, cooldown));
				helper.assertTrue(oxOwnedBy(helper.getLevel(), caster.getUUID()).isEmpty(), diagnostic(helper,
						fixture, "rollback", caster.getUUID(), "no spawned Ox", 0,
						oxOwnedBy(helper.getLevel(), caster.getUUID()).size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S4: manual recall arms the type-local recall price and lets the body sink out. */
	@GameTest(maxTicks = 55)
	public void oxRecallChargesItsCooldownAndRemovesTheBody(GameTestHelper helper) {
		String fixture = "oxRecallChargesItsCooldownAndRemovesTheBody";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> assertOneActiveOx(helper, fixture, caster)));
		helper.runAtTickTime(22, () -> {
			try {
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, diagnostic(helper, fixture, "recall", caster.getUUID(),
						"recall accepted", true, recalled));
				long remaining = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS, diagnostic(helper, fixture,
						"recall", caster.getUUID(), "Ox recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0, diagnostic(helper, fixture, "recall", caster.getUUID(),
						"shared PRIMARY slot", 0, primary));
				assertNoOxPack(helper, fixture, caster);
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		helper.runAtTickTime(36, () -> {
			try {
				helper.assertTrue(oxOwnedBy(level, caster.getUUID()).isEmpty(), diagnostic(helper, fixture,
						"gone", caster.getUUID(), "recalled Ox body gone", 0, oxOwnedBy(level, caster.getUUID()).size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S5: body death uses the death-family cooldown, not the recall-family price. */
	@GameTest(maxTicks = 45)
	public void oxDeathChargesTheDeathCooldownAndClearsItsPack(GameTestHelper helper) {
		String fixture = "oxDeathChargesTheDeathCooldownAndClearsItsPack";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				assertOneActiveOx(helper, fixture, caster);
				MegumiOxEntity body = oxOwnedBy(level, caster.getUUID()).get(0);
				boolean killed = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(killed, diagnostic(helper, fixture, "death", caster.getUUID(),
						"lethal damage accepted", true, killed));
				long remaining = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS, diagnostic(helper, fixture,
						"death", caster.getUUID(), "Ox death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));
				assertNoOxPack(helper, fixture, caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S6: PRIMARY_SNEAK's global sic order reaches the Ox through the shared mark pipeline. */
	@GameTest(maxTicks = 80)
	public void oxAcceptsTheGlobalSicMark(GameTestHelper helper) {
		String fixture = "oxAcceptsTheGlobalSicMark";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> targetRef = new AtomicReference<>();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(21, () -> targetRef.set(zombie(helper, fixture, new BlockPos(6, 1, 8))));
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				Zombie target = targetRef.get();
				TodoSwapTestFixtures.aimAt(caster, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"global sic accepted", true, sicced + "; " + sicTrace(level, caster, target)));
				MegumiOxEntity body = oxOwnedBy(level, caster.getUUID()).get(0);
				helper.assertTrue(body.getTarget() == target, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"Ox target from global sic", target.getUUID(), body.getTarget() == null ? null : body.getTarget().getUUID()));
			} catch (RuntimeException | AssertionError failure) {
				targetRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		helper.runAtTickTime(38, () -> {
			if (targetRef.get() != null) {
				targetRef.get().discard();
			}
			MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			helper.succeed();
		});
	}

	/** Common S7: without a key press, the shared combat coordinator assigns an eligible nearby target. */
	@GameTest(maxTicks = 90)
	public void oxAcquiresAnAutonomousTarget(GameTestHelper helper) {
		String fixture = "oxAcquiresAnAutonomousTarget";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> targetRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			Zombie spawned = zombie(helper, fixture, new BlockPos(5, 1, 6));
			// Only OUR coordinator may autonomously mark this zombie — every foreign pack skips it.
			spawned.removeTag("jujutsu.autonomous_mark.none");
			spawned.addTag("jujutsu.autonomous_mark." + caster.getUUID());
			targetRef.set(spawned);
		});
		for (long tick = ACTIVE_TICK + 1; tick <= ACTIVE_TICK + 50; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				Zombie target = targetRef.get();
				if (!bodies.isEmpty() && target != null
						&& (bodies.get(0).getTarget() == target
								|| target.getUUID().equals(bodies.get(0).chargeTargetUuid()))) {
					done.set(true);
					target.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
					return;
				}
				if (pollTick == ACTIVE_TICK + 50) {
					done.set(true);
					target.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "autonomy", caster.getUUID(),
							"coordinator assigns the nearby zombie", target.getUUID(),
							bodies.isEmpty() ? "no Ox" : String.valueOf(bodies.get(0).getTarget())));
				}
			});
		}
	}

	/** Common S8: retaliation marks a hostile mob targeting the owner, even outside autonomy radius. */
	@GameTest(maxTicks = 90)
	public void oxRetaliatesAgainstAMobTargetingItsOwner(GameTestHelper helper) {
		String fixture = "oxRetaliatesAgainstAMobTargetingItsOwner";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			Zombie attacker = zombie(helper, fixture, new BlockPos(5, 1, 17));
			attacker.setNoAi(false);
			// The attacker must stay damageable: MegumiOxBrain.validMarkedTarget rejects
			// invulnerable marks, so an invulnerable attacker would make the retaliation
			// assertion vacuous. Its `autonomous_mark.none` tag already bars foreign
			// autonomous marks; retaliation bypasses that gate by design.
			CursedSpiritTestFixtures.freezeGround(attacker);
			attacker.setTarget(caster);
			helper.assertTrue(attacker.getTarget() == caster, diagnostic(helper, fixture, "retaliation-premise",
					caster.getUUID(), "frozen mob targets the owner", caster.getUUID(),
					attacker.getTarget() == null ? null : attacker.getTarget().getUUID()));
			double attackerDistance = attacker.distanceTo(caster);
			helper.assertTrue(attackerDistance > MegumiShikigamiProfile.AUTONOMY_RADIUS
							&& attackerDistance < MegumiProfile.RETALIATION_RADIUS,
					diagnostic(helper, fixture, "retaliation-premise", caster.getUUID(),
							"attacker remains outside autonomy but inside retaliation radius",
							"> " + MegumiShikigamiProfile.AUTONOMY_RADIUS + " and < "
									+ MegumiProfile.RETALIATION_RADIUS,
							attackerDistance));
			attackerRef.set(attacker);
		});
		for (long tick = ACTIVE_TICK + 1; tick <= ACTIVE_TICK + 50; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				if (!bodies.isEmpty() && attacker != null
						&& (bodies.get(0).getTarget() == attacker
								|| attacker.getUUID().equals(bodies.get(0).chargeTargetUuid()))) {
					done.set(true);
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
					return;
				}
				if (pollTick == ACTIVE_TICK + 50) {
					done.set(true);
					attacker.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "retaliation", caster.getUUID(),
							"retaliation assigns the owner's attacker", attacker.getUUID(),
							bodies.isEmpty() ? "no Ox" : String.valueOf(bodies.get(0).getTarget())));
				}
			});
		}
	}

	/** Common S9: owner teardown removes the Ox pack without charging a death or recall price. */
	@GameTest(maxTicks = 45)
	public void oxOwnerDisconnectCleanupRemovesThePack(GameTestHelper helper) {
		String fixture = "oxOwnerDisconnectCleanupRemovesThePack";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				MegumiShikigamiRuntime.teardown(level.getServer(), caster.getUUID(),
						MegumiShikigamiRuntime.TeardownReason.DISCONNECT);
				assertNoOxPack(helper, fixture, caster);
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(cooldown == 0, diagnostic(helper, fixture, "owner cleanup", caster.getUUID(),
						"disconnect cooldown", 0, cooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S10: dimension teardown recalls the body and charges only the Ox's recall-family deadline. */
	@GameTest(maxTicks = 55)
	public void oxDimensionCleanupUsesTheRecallCooldown(GameTestHelper helper) {
		String fixture = "oxDimensionCleanupUsesTheRecallCooldown";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				MegumiShikigamiRuntime.teardown(level.getServer(), caster.getUUID(),
						MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);
				assertNoOxPack(helper, fixture, caster);
				long cooldown = MegumiSummonCooldowns.remainingTicks(caster.getUUID(), MegumiShikigami.OX,
						level.getGameTime());
				helper.assertTrue(cooldown == EXPECTED_RECALL_COOLDOWN_TICKS, diagnostic(helper, fixture,
						"dimension cleanup", caster.getUUID(), "Ox dimension cooldown",
						EXPECTED_RECALL_COOLDOWN_TICKS, cooldown));
			} catch (RuntimeException | AssertionError failure) {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		helper.runAtTickTime(ACTIVE_TICK + MegumiShikigamiProfile.OX_RECALL_TICKS + 2, () -> {
			try {
				helper.assertTrue(oxOwnedBy(level, caster.getUUID()).isEmpty(), diagnostic(helper, fixture,
						"dimension cleanup", caster.getUUID(), "recalled Ox gone", 0,
						oxOwnedBy(level, caster.getUUID()).size()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Common S11: the Dogs and Ox occupy independent packs at the same time. */
	@GameTest(maxTicks = 45)
	public void oxCoexistsWithTheDivineDogs(GameTestHelper helper) {
		String fixture = "oxCoexistsWithTheDivineDogs";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, diagnostic(helper, fixture, "dogs", caster.getUUID(), "Dogs summoned", true, dogs));
		}));
		helper.runAtTickTime(4, () -> {
			try {
				boolean ox = summonOx(helper, caster, fixture);
				helper.assertTrue(ox && MegumiSummonRuntime.packView(level.getServer(), caster.getUUID()).isPresent(),
						diagnostic(helper, fixture, "coexist", caster.getUUID(), "Dogs and Ox pack rows",
								"both present", "ox=" + ox + ", dogs="
										+ MegumiSummonRuntime.packView(level.getServer(), caster.getUUID()).isPresent()));
				boolean oxPresent = MegumiShikigamiRuntime.packViews(level.getServer(), caster.getUUID()).stream()
						.anyMatch(pack -> MegumiShikigami.OX.id().equals(pack.type()));
				helper.assertTrue(oxPresent, diagnostic(helper, fixture, "coexist", caster.getUUID(),
						"Ox pack remains beside Dogs", true, oxPresent));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
			helper.succeed();
		});
	}

	/** Signature: one charge hits short- and long-distance bodies once; the later impact is stronger. */
	@GameTest(maxTicks = 150)
	public void oxHitsMultipleBodiesOnceAndLongerTravelHitsHarder(GameTestHelper helper) {
		String fixture = "oxHitsMultipleBodiesOnceAndLongerTravelHitsHarder";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Cow> shortTargetRef = new AtomicReference<>();
		AtomicReference<Cow> longTargetRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean started = new AtomicBoolean();
		AtomicReference<net.minecraft.world.phys.Vec3> chargeOriginRef = new AtomicReference<>();
		AtomicReference<net.minecraft.world.phys.Vec3> chargeDirectionRef = new AtomicReference<>();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				shortTargetRef.set(cow(helper, fixture, new BlockPos(6, 1, 6)));
				placeAt(helper, shortTargetRef.get(), new BlockPos(6, 1, 6));
				longTargetRef.set(cow(helper, fixture, new BlockPos(8, 1, 11)));
				placeAt(helper, longTargetRef.get(), new BlockPos(8, 1, 11));
				MegumiOxEntity ox = oxOwnedBy(level, caster.getUUID()).get(0);
				placeAt(helper, ox, new BlockPos(5, 1, 3));
				ox.setNoAi(true);
				Cow target = longTargetRef.get();
				TodoSwapTestFixtures.aimAt(caster, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"long target sicced", true, sicced + "; " + sicTrace(level, caster, target)));
			} catch (RuntimeException | AssertionError failure) {
				shortTargetRef.get().discard();
				longTargetRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		for (long tick = SIC_TICK + 1; tick <= 120; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				if (!bodies.isEmpty() && bodies.get(0).chargeInFlight() && !started.get()) {
					chargeOriginRef.set(bodies.get(0).position());
					chargeDirectionRef.set(bodies.get(0).chargeDirection());
					started.set(true);
				}
				if (!bodies.isEmpty() && bodies.get(0).chargeRecovering()) {
					Cow shortTarget = shortTargetRef.get();
					Cow longTarget = longTargetRef.get();
					double shortDamage = 10.0 - shortTarget.getHealth();
					double longDamage = 10.0 - longTarget.getHealth();
					try {
						helper.assertTrue(started.get(), diagnostic(helper, fixture, "impact", caster.getUUID(),
								"charge enters recovery only after traveling", "observed CHARGE",
								bodies.get(0).chargeStateName() + " distance="
										+ bodies.get(0).accumulatedChargeDistance()
										+ " mark=" + bodies.get(0).chargeTargetUuid()));
						helper.assertTrue(shortDamage > 0.0 && longDamage > 0.0,
								diagnostic(helper, fixture, "impact", caster.getUUID(), "both corridor bodies hit",
										"positive damage to both",
										shortDamage + "/" + longDamage + "; ox=" + bodies.get(0).position()
												+ " inFlight=" + bodies.get(0).chargeInFlight()
												+ " distance=" + bodies.get(0).accumulatedChargeDistance()
												+ " target=" + bodies.get(0).getTarget()
												+ " targetLos=" + bodies.get(0).hasLineOfSight(longTarget)
												+ " sameTarget=" + (bodies.get(0).getTarget() == longTarget)
												+ " targetInvulnerable=" + longTarget.isInvulnerable()
												+ " ownerOnline=" + (level.getServer().getPlayerList()
														.getPlayer(caster.getUUID()) != null)
												+ " range=" + bodies.get(0).distanceTo(longTarget)
												+ " chargeTarget=" + bodies.get(0).chargeTargetUuid()
												+ " short=" + shortTarget.position()
												+ "/" + shortTarget.getHealth()
												+ " long=" + longTarget.position()
												+ "/" + longTarget.getHealth()
												+ " phase=" + bodies.get(0).chargeStateName()
												+ " frozen=" + bodies.get(0).chargeDirection()
												+ " committed=" + chargeOriginRef.get() + "/" + chargeDirectionRef.get()
												+ " started=" + started.get()));
						helper.assertTrue(longDamage > shortDamage, diagnostic(helper, fixture, "impact",
								caster.getUUID(), "longer accumulated travel hits harder", "> " + shortDamage,
								longDamage));
						helper.assertTrue(shortTarget.isAlive() && longTarget.isAlive(), diagnostic(helper, fixture,
								"impact", caster.getUUID(), "single impacts leave both cows alive", "both alive",
								shortTarget.isAlive() + "/" + longTarget.isAlive()));
					} finally {
						done.set(true);
						shortTarget.discard();
						longTarget.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == 120) {
					done.set(true);
					shortTargetRef.get().discard();
					longTargetRef.get().discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "impact", caster.getUUID(),
							"charge reaches recovery after both targets", "RECOVERY",
							bodies.isEmpty() ? "no Ox" : bodies.get(0).chargeStateName()
									+ " distance=" + bodies.get(0).accumulatedChargeDistance()
									+ " mark=" + bodies.get(0).chargeTargetUuid()));
				}
			});
		}
	}
 
	/** Red-proof: a target moved after commit is missed; a mid-charge re-aim mutation must fail. */
	@GameTest(maxTicks = 150)
	public void oxDoesNotSteerTowardASidesteppingTarget(GameTestHelper helper) {
		String fixture = "oxDoesNotSteerTowardASidesteppingTarget";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Cow> targetRef = new AtomicReference<>();
		AtomicReference<net.minecraft.world.phys.Vec3> committedPositionRef = new AtomicReference<>();
		AtomicReference<net.minecraft.world.phys.Vec3> committedDirectionRef = new AtomicReference<>();
		AtomicBoolean shifted = new AtomicBoolean();
		AtomicBoolean done = new AtomicBoolean();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				targetRef.set(cow(helper, fixture, new BlockPos(5, 1, 10)));
				placeAt(helper, targetRef.get(), new BlockPos(5, 1, 10));
				MegumiOxEntity ox = oxOwnedBy(level, caster.getUUID()).get(0);
				placeAt(helper, ox, new BlockPos(5, 1, 3));
				ox.setNoAi(true);
				Cow target = targetRef.get();
				TodoSwapTestFixtures.aimAt(caster, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"target sicced", true, sicced + "; " + sicTrace(level, caster, target)));
			} catch (RuntimeException | AssertionError failure) {
				targetRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		for (long tick = SIC_TICK + 1; tick <= 120; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				if (bodies.isEmpty()) {
					if (pollTick == 120) {
						done.set(true);
						if (targetRef.get() != null) {
							targetRef.get().discard();
						}
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.assertTrue(false, diagnostic(helper, fixture, "sidestep", caster.getUUID(),
								"Ox remains present through committed charge", "one Ox", "no Ox"));
					}
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				Cow target = targetRef.get();
				if (!shifted.get() && ox.chargeInFlight()) {
					helper.assertTrue(target.getUUID().equals(ox.chargeTargetUuid()),
							diagnostic(helper, fixture, "intent", caster.getUUID(),
									"locked mark survives ALIGN through CHARGE", target.getUUID(), ox.chargeTargetUuid()));
					net.minecraft.world.phys.Vec3 direction = ox.chargeDirection();
					committedPositionRef.set(ox.position());
					committedDirectionRef.set(direction);
					placeAt(helper, target, new BlockPos(10, 1, 10));
					shifted.set(true);
				}
				if (ox.chargeRecovering()) {
					done.set(true);
					try {
						net.minecraft.world.phys.Vec3 committedPosition = committedPositionRef.get();
						net.minecraft.world.phys.Vec3 committedDirection = committedDirectionRef.get();
						net.minecraft.world.phys.Vec3 horizontalTravel = committedPosition == null ? null
								: new net.minecraft.world.phys.Vec3(ox.getX() - committedPosition.x, 0.0,
										ox.getZ() - committedPosition.z);
						double lateralDrift = committedPosition == null || committedDirection == null
								? Double.POSITIVE_INFINITY
								: horizontalTravel.subtract(committedDirection.scale(
										horizontalTravel.dot(committedDirection))).length();
						helper.assertTrue(shifted.get(), diagnostic(helper, fixture, "sidestep", caster.getUUID(),
								"target moved after commit", true, "shifted=" + shifted.get()
										+ " phase=" + ox.chargeStateName()
										+ " mark=" + ox.chargeTargetUuid()));
						helper.assertTrue(target.getHealth() == 10.0f, diagnostic(helper, fixture, "sidestep",
								caster.getUUID(), "sidestepped target is missed", 10.0f, target.getHealth()));
						helper.assertTrue(lateralDrift < 0.2, diagnostic(helper, fixture, "sidestep",
								caster.getUUID(), "charge remains within 0.2 of committed line from "
										+ committedPosition + " along " + committedDirection,
								"< 0.2", lateralDrift));
						helper.assertTrue(ox.chargeTargetUuid() == null, diagnostic(helper, fixture, "recovery",
								caster.getUUID(), "coordinator intent cleared after charge", null, ox.chargeTargetUuid()));
					} finally {
						target.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == 120) {
					done.set(true);
					target.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "sidestep", caster.getUUID(),
							"charge reaches recovery after miss", "RECOVERY",
							ox.chargeStateName() + " inFlight=" + ox.chargeInFlight()
									+ " shifted=" + shifted.get() + " mark=" + ox.chargeTargetUuid()));
				}
			});
		}
	}

	/** Signature: a physical wall aborts the charge at collision-resolved position, never through it. */
	@GameTest(maxTicks = 100)
	public void oxAbortsAtAWallWithoutPassingThrough(GameTestHelper helper) {
		String fixture = "oxAbortsAtAWallWithoutPassingThrough";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Cow> targetRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		for (int x = 2; x <= 9; x++) {
			helper.setBlock(new BlockPos(x, 1, 7), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 2, 7), Blocks.STONE);
		}
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster,
				() -> summonOx(helper, caster, fixture)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			Cow target = cow(helper, fixture, new BlockPos(5, 1, 5));
			placeAt(helper, target, new BlockPos(5, 1, 5));
			targetRef.set(target);
		});
		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				MegumiOxEntity ox = oxOwnedBy(level, caster.getUUID()).get(0);
				placeAt(helper, ox, new BlockPos(5, 1, 3));
				Cow target = targetRef.get();
				TodoSwapTestFixtures.aimAt(caster, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"pre-wall target sicced", true, sicced + "; " + sicTrace(level, caster, target)));
			} catch (RuntimeException | AssertionError failure) {
				targetRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		for (long tick = SIC_TICK + 1; tick <= 80; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				if (bodies.isEmpty()) {
					done.set(true);
					targetRef.get().discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "wall", caster.getUUID(),
							"ox body alive during wall scenario", "present", "missing"));
					return;
				}
				MegumiOxEntity ox = bodies.get(0);
				if (ox.chargeRecovering()) {
					done.set(true);
					try {
						helper.assertTrue(targetRef.get().getHealth() < 10.0f, diagnostic(helper, fixture,
								"wall", caster.getUUID(), "visible target before wall is hit", "< 10.0",
								"target health=" + targetRef.get().getHealth()
										+ " ox=" + ox.position() + " inFlight=" + ox.chargeInFlight()
										+ " distance=" + ox.accumulatedChargeDistance()
										+ " chargeTarget=" + ox.chargeTargetUuid()
										+ " target=" + targetRef.get().position()
										+ " targetLos=" + ox.hasLineOfSight(targetRef.get())));
						double relativeZ = ox.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
						helper.assertTrue(relativeZ < 7.0, diagnostic(helper, fixture, "wall", caster.getUUID(),
								"Ox remains on the near side of wall", "< 7", relativeZ));
						helper.assertTrue(ox.accumulatedChargeDistance() < 4.0, diagnostic(helper, fixture,
								"wall", caster.getUUID(), "charge stops at wall travel", "< 4", ox.accumulatedChargeDistance()));
					} finally {
						targetRef.get().discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					}
					helper.succeed();
					return;
				}
				if (pollTick == 80) {
					done.set(true);
					targetRef.get().discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.assertTrue(false, diagnostic(helper, fixture, "wall", caster.getUUID(),
							"horizontal collision aborts into recovery", "RECOVERY", ox.chargeRecovering()));
				}
			});
		}
	}

	/** Cross-type signature: an allied shikigami in the corridor is skipped while an enemy is hit. */
	@GameTest(maxTicks = 140)
	public void oxLeavesAnAlliedShikigamiUndamagedInItsCorridor(GameTestHelper helper) {
		String fixture = "oxLeavesAnAlliedShikigamiUndamagedInItsCorridor";
		ServerPlayer caster = setup(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<MegumiToadEntity> allyRef = new AtomicReference<>();
		AtomicReference<Cow> enemyRef = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean started = new AtomicBoolean();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, diagnostic(helper, fixture, "summon", caster.getUUID(), "Toad summoned", true, summoned));
		}));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			MegumiToadEntity ally = toadOwnedBy(level, caster.getUUID()).get(0);
			ally.setNoAi(true);
			placeAt(helper, ally, new BlockPos(5, 1, 6));
			allyRef.set(ally);
			summonOx(helper, caster, fixture);
		});
		helper.runAtTickTime(42, () -> {
			try {
				MegumiOxEntity ox = oxOwnedBy(level, caster.getUUID()).get(0);
				placeAt(helper, ox, new BlockPos(5, 1, 3));
				ox.setNoAi(true);
				Cow enemy = cow(helper, fixture, new BlockPos(5, 1, 10));
				placeAt(helper, enemy, new BlockPos(5, 1, 10));
				enemyRef.set(enemy);
				TodoSwapTestFixtures.aimAt(caster, enemy.position().add(0.0, enemy.getBbHeight() * 0.5, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, diagnostic(helper, fixture, "sic", caster.getUUID(),
						"enemy sicced", true, sicced + "; " + sicTrace(level, caster, enemy)));
			} catch (RuntimeException | AssertionError failure) {
				enemyRef.get().discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
		for (long tick = 43; tick <= 120; tick++) {
			long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiOxEntity> bodies = oxOwnedBy(level, caster.getUUID());
				if (!bodies.isEmpty() && bodies.get(0).chargeInFlight()) {
					started.set(true);
				}
				if (bodies.isEmpty() || !bodies.get(0).chargeRecovering()) {
					if (pollTick == 120) {
						done.set(true);
						enemyRef.get().discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.assertTrue(false, diagnostic(helper, fixture, "friendly fire", caster.getUUID(),
								"charge ends after enemy contact", "RECOVERY",
								bodies.isEmpty() ? "no Ox"
										: bodies.get(0).chargeStateName() + " started=" + started.get()
												+ " distance=" + bodies.get(0).accumulatedChargeDistance()
												+ " enemyHealth=" + enemyRef.get().getHealth()));
					}
					return;
				}
				done.set(true);
				Cow enemy = enemyRef.get();
				MegumiToadEntity ally = allyRef.get();
				try {
					helper.assertTrue(started.get(), diagnostic(helper, fixture, "friendly fire", caster.getUUID(),
							"charge entered recovery after a real charge", "started",
							bodies.get(0).chargeStateName() + " distance="
									+ bodies.get(0).accumulatedChargeDistance()));
					helper.assertTrue(enemy.getHealth() < 10.0f, diagnostic(helper, fixture, "friendly fire",
							caster.getUUID(), "enemy takes charge damage", "< 10.0", enemy.getHealth()));
					helper.assertTrue(ally.getHealth() == ally.getMaxHealth(), diagnostic(helper, fixture,
							"friendly fire", caster.getUUID(), "allied shikigami remains undamaged",
							ally.getMaxHealth(), ally.getHealth()));
				} finally {
					enemy.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				}
				helper.succeed();
			});
		}
	}
}
