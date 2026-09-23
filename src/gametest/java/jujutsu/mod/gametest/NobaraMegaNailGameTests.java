package jujutsu.mod.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.HairpinRuntime;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorRegistry;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkMegaNailRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraProfile;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Block 3 server scenarios for Mega Nail's owner-scoped consumption and world-event gather.
 *
 * <p>The tests deliberately query the anchor registry after the cast instead of inferring state from
 * entity counts alone: this catches stale registry entries and proves the foreign-owner branch is
 * untouched. The R/B scenario samples R before its delayed chain fires, while B consumes at t0.
 */
public final class NobaraMegaNailGameTests {

	@GameTest(maxTicks = 100, skyAccess = true)
	public void megaNailConsumesOnlyCasterSetupAndSpawnsCriticalVariant(GameTestHelper helper) {
		String fixture = "megaNailConsumesOnlyCasterSetupAndSpawnsCriticalVariant";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		ServerPlayer foreign = helper.makeMockServerPlayerInLevel();
		// Park the second caster far off the aim ray: TargetResolver ranks any living entity in the
		// sweep, and a mock player standing next to the caster would steal the aimed target.
		foreign.teleportTo(level, helper.absolutePos(new BlockPos(1, 1, 1)).getX() - 40.0,
				helper.absolutePos(new BlockPos(1, 1, 1)).getY(), helper.absolutePos(new BlockPos(1, 1, 1)).getZ(),
				java.util.Set.of(), 0.0f, 0.0f, false);
		CursedSpiritEntity target = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(5, 1, 1));
		CursedSpiritTestFixtures.freezeGround(target);
		AtomicReference<Float> healthBefore = new AtomicReference<>();
		java.util.List<String> nailTrail = new java.util.concurrent.CopyOnWriteArrayList<>();
		helper.runAtTickTime(2, () -> {
			healthBefore.set(target.getHealth());
			long gameTime = level.getGameTime();
			ProjectJjkNailMarks.apply(caster.getUUID(), target.getUUID(), gameTime);
			ProjectJjkNailMarks.apply(foreign.getUUID(), target.getUUID(), gameTime);
			ProjectJjkNailEntity depthOne = embedNail(level, caster, target, 1);
			ProjectJjkNailEntity depthThree = embedNail(level, caster, target, 3);
			ProjectJjkNailEntity foreignNail = embedNail(level, foreign, target, 1);
			TodoSwapTestFixtures.aimAt(caster,
					target.position().add(0.0, target.getBbHeight() * 0.55, 0.0));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID()).size() == 2,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"premise: caster owns two target anchors", 2,
							NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID()).size()));
			AbilityResult result = ProjectJjkMegaNailRuntime.start(caster);
			helper.assertTrue(result == AbilityResult.SUCCESS,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"Mega Nail cast", AbilityResult.SUCCESS, result));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID()).isEmpty(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"atomic t0 consumption clears caster anchors", "empty", 
							NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID())));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, foreign.getUUID(), target.getUUID()).size() == 1,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), foreign.getUUID(), target.getUUID(),
							"foreign owner's anchor survives", 1,
							NailAnchorRegistry.anchorsOnTarget(level, foreign.getUUID(), target.getUUID()).size()));
			helper.assertTrue(depthOne.isRemoved() && depthThree.isRemoved() && !foreignNail.isRemoved(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"only caster nails are discarded at t0", "caster removed / foreign live",
							depthOne.isRemoved() + " / " + depthThree.isRemoved() + " / " + foreignNail.isRemoved()));
			helper.assertTrue(ProjectJjkNailMarks.marks(caster.getUUID(), target.getUUID(), level.getGameTime()) == 0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"caster mark is consumed at t0", 0,
							ProjectJjkNailMarks.marks(caster.getUUID(), target.getUUID(), level.getGameTime())));
			helper.assertTrue(ProjectJjkNailMarks.marks(foreign.getUUID(), target.getUUID(), level.getGameTime()) == 1,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), foreign.getUUID(), target.getUUID(),
							"foreign mark survives owner-scoped consumption", 1,
							ProjectJjkNailMarks.marks(foreign.getUUID(), target.getUUID(), level.getGameTime())));
		});

		helper.runAtTickTime(ProjectJjkNobaraProfile.MEGA_GATHER_TICKS + 4, () -> {
			List<ProjectJjkNailEntity> candidates = level.getEntitiesOfClass(ProjectJjkNailEntity.class,
					new AABB(caster.position(), caster.position()).inflate(16.0),
					nail -> nail.isMegaNail() && caster.getUUID().equals(nail.ownerUuid()));
			helper.assertTrue(!candidates.isEmpty(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"gather completes and spawns a mega nail", "one live mega nail", candidates));
			ProjectJjkNailEntity spawned = candidates.get(0);
			helper.assertTrue(spawned.megaCount() == 2,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"mega count snapshots caster setup", 2, spawned.megaCount()));
			helper.assertTrue(spawned.megaCritical(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"depth-3 setup selects critical variant", true, spawned.megaCritical()));
			helper.assertTrue(!spawned.isLaunched(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"gather transitions into charge before release", false, spawned.isLaunched()));
		});
		for (int probeTick = 20; probeTick <= 44; probeTick += 4) {
			helper.runAtTickTime(probeTick, () -> level.getEntitiesOfClass(ProjectJjkNailEntity.class,
					new AABB(caster.position(), caster.position()).inflate(256.0),
					ProjectJjkNailEntity::isMegaNail).forEach(n -> nailTrail.add(
							"t" + helper.getTick() + " pos=" + n.position() + " launched=" + n.isLaunched())));
		}


		helper.runAtTickTime(45, () -> {
			try {
				List<ProjectJjkNailEntity> megaLeft = level.getEntitiesOfClass(ProjectJjkNailEntity.class,
						new AABB(caster.position(), caster.position()).inflate(256.0),
						ProjectJjkNailEntity::isMegaNail);
				String nailState = megaLeft.isEmpty() ? "no mega entity"
						: megaLeft.stream().map(n -> "pos=" + n.position() + " launched=" + n.isLaunched())
								.collect(java.util.stream.Collectors.joining("; "));
				helper.assertTrue(!target.isAlive() || target.getHealth() < healthBefore.get(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
								"charge releases and impacts the aimed target", "dead or hp < " + healthBefore.get(),
								(target.isAlive() ? target.getHealth() : "dead") + " | " + nailState
										+ " | targetPos=" + target.position() + " | trail=" + nailTrail));
				// The foreign anchor's survival is proven at t0 above; by impact time the target is
				// dead or dying, so its nails drop with the body — that is the design, not a leak.
			} finally {
				cleanup(helper, caster, foreign, target);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void identicalFixtureKeepsRNetworkButBConsumesIt(GameTestHelper helper) {
		String fixture = "identicalFixtureKeepsRNetworkButBConsumesIt";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 3));
		CursedSpiritEntity rTarget = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(5, 1, 1));
		CursedSpiritEntity bTarget = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(5, 1, 5));
		CursedSpiritTestFixtures.freezeGround(rTarget);
		CursedSpiritTestFixtures.freezeGround(bTarget);

		helper.runAtTickTime(2, () -> {
			// Identical two-nail fixtures: only the selected branch differs.
			embedNail(level, caster, rTarget, 1);
			embedNail(level, caster, rTarget, 2);
			embedNail(level, caster, bTarget, 1);
			embedNail(level, caster, bTarget, 2);

			TodoSwapTestFixtures.aimAt(caster,
					rTarget.position().add(0.0, rTarget.getBbHeight() * 0.55, 0.0));
			AbilityResult rResult = HairpinRuntime.startDirectedHairpin(caster);
			helper.assertTrue(rResult == AbilityResult.SUCCESS,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), rTarget.getUUID(),
							"R branch starts on identical setup", AbilityResult.SUCCESS, rResult));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), rTarget.getUUID()).size() == 2,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), rTarget.getUUID(),
							"R keeps distributed network at selection", 2,
							NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), rTarget.getUUID()).size()));

			TodoSwapTestFixtures.aimAt(caster,
					bTarget.position().add(0.0, bTarget.getBbHeight() * 0.55, 0.0));
			AbilityResult bResult = ProjectJjkMegaNailRuntime.start(caster);
			helper.assertTrue(bResult == AbilityResult.SUCCESS,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), bTarget.getUUID(),
							"B branch starts on identical setup", AbilityResult.SUCCESS, bResult));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), bTarget.getUUID()).isEmpty(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), bTarget.getUUID(),
							"B consumes the selected network at t0", "empty",
							NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), bTarget.getUUID())));
		});


		helper.runAtTickTime(55, () -> {
			try {
				helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), bTarget.getUUID()).isEmpty(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), bTarget.getUUID(),
								"B branch remains consumed after gather", "empty",
								NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), bTarget.getUUID())));
			} finally {
				cleanup(helper, caster, null, rTarget, bTarget);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 50, skyAccess = true)
	public void megaNailMisAimFailsWithoutCoolingAnySlot(GameTestHelper helper) {
		String fixture = "megaNailMisAimFailsWithoutCoolingAnySlot";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		CursedSpiritEntity target = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(8, 1, 1));
		CursedSpiritTestFixtures.freezeGround(target);

		helper.runAtTickTime(2, () -> {
			embedNail(level, caster, target, 1);
			caster.setXRot(-90.0f);
			caster.setYRot(0.0f);
			caster.setYHeadRot(0.0f);
			CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
			CharacterAbilityCooldowns.clear(caster, CharacterAbility.SECONDARY);
			AbilityResult result = CharacterAbilityExecutor.tryCast(caster, CharacterAbility.SECONDARY, true);
			helper.assertTrue(result == AbilityResult.HANDLED_FAILURE,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"mis-aim result", AbilityResult.HANDLED_FAILURE, result));
			helper.assertTrue(CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0
					&& CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY) == 0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"mis-aim cools no slot", "PRIMARY 0 / SECONDARY 0",
							CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) + " / "
									+ CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY)));
			helper.assertTrue(NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID()).size() == 1,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
							"mis-aim consumes no setup", 1,
							NailAnchorRegistry.anchorsOnTarget(level, caster.getUUID(), target.getUUID()).size()));
		});

		helper.runAtTickTime(20, () -> {
			try {
				helper.assertTrue(level.getEntitiesOfClass(ProjectJjkNailEntity.class,
						new AABB(caster.position(), caster.position()).inflate(16.0),
						nail -> nail.isMegaNail() && caster.getUUID().equals(nail.ownerUuid())).isEmpty(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), target.getUUID(),
								"mis-aim spawns no mega nail", "empty", "mega entity present"));
			} finally {
				cleanup(helper, caster, null, target);
			}
			helper.succeed();
		});
	}

	private static ServerPlayer setupCaster(GameTestHelper helper, BlockPos relativeFeet) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(relativeFeet);
		caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				java.util.Set.of(), 0.0f, 0.0f, false);
		CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.SECONDARY);
		return caster;
	}

	private static ProjectJjkNailEntity embedNail(ServerLevel level, ServerPlayer owner,
			CursedSpiritEntity target, int depth) {
		ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
		if (nail == null) {
			throw new IllegalStateException("projectjjk_nail entity type did not create an instance");
		}
		Vec3 point = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
		nail.prepare(owner, point, new Vec3(1.0, 0.0, 0.0));
		nail.attachToEntity(target, point);
		if (!level.addFreshEntity(nail)) {
			throw new IllegalStateException("embedded nail could not be added to the test level");
		}
		for (int i = 1; i < depth; i++) {
			nail.deepen();
		}
		if (!NailAnchorRegistry.track(level, nail)) {
			throw new IllegalStateException("embedded nail was rejected by NailAnchorRegistry");
		}
		NailAnchorRegistry.updateDepth(level, nail.getUUID(), depth);
		return nail;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer caster, ServerPlayer foreign,
			CursedSpiritEntity... targets) {
		ServerLevel level = helper.getLevel();
		// Owner-scoped only: GameTest arenas stand ~25 blocks apart, so global clearAll or a
		// radius sweep would murder a neighboring test's anchors and in-flight mega nails.
		NailAnchorRegistry.discardOwned(level, caster.getUUID());
		ProjectJjkNailMarks.clearOwner(caster.getUUID());
		if (foreign != null) {
			NailAnchorRegistry.discardOwned(level, foreign.getUUID());
			ProjectJjkNailMarks.clearOwner(foreign.getUUID());
		}
		for (CursedSpiritEntity target : targets) {
			if (target != null) {
				target.discard();
			}
		}
		// Owner-scoped only: GameTest arenas stand ~25 blocks apart, so a radius sweep would
		// murder a neighboring test's in-flight mega nail (the flake this file kept hitting).
		for (ProjectJjkNailEntity nail : level.getEntitiesOfClass(ProjectJjkNailEntity.class,
				new AABB(caster.position(), caster.position()).inflate(256.0),
				nail -> nail.isOwnedBy(caster.getUUID()))) {
			nail.discard();
		}
		if (foreign != null) {
			level.getServer().getPlayerList().remove(foreign);
		}
		level.getServer().getPlayerList().remove(caster);
	}
}
