package jujutsu.mod.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.HairpinNetwork;
import jujutsu.mod.character.nobara.projectjjk.HairpinRuntime;
import jujutsu.mod.character.nobara.projectjjk.HairpinSeedResolver;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorRegistry;
import jujutsu.mod.character.nobara.projectjjk.NailTrap;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.registry.JujutsuEntities;


/** Block-2 live oracles for Hairpin seed parity, chain execution, and trap-corner anchors. */
public final class NobaraHairpinGameTests {
	private static final int POLL_DEADLINE = 100;

	@GameTest(maxTicks = 180, skyAccess = true)
	public void nailImpactMarksTheSpiritAndDirectedHairpinPunishes(GameTestHelper helper) {
		String fixture = "nailImpactMarksTheSpiritAndDirectedHairpinPunishes";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = setupCaster(helper, fixture, new BlockPos(1, 1, 1), -90.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
		AtomicBoolean marked = new AtomicBoolean();
		AtomicBoolean cast = new AtomicBoolean();
		AtomicBoolean punished = new AtomicBoolean();
		AtomicReference<Double> healthBefore = new AtomicReference<>(0.0);
		AtomicInteger marksBefore = new AtomicInteger(0);
		helper.runAtTickTime(2, () -> {
			try {
				TodoSwapTestFixtures.aimAt(caster,
						spirit.position().add(0.0, spirit.getBbHeight() * 0.5, 0.0));
				CursedSpiritTestFixtures.freezeGround(spirit);
				// Production path: a real launched nail must embed, mark, and register — the
				// oracle below proves resolveNailImpact wiring, not a hand-built fixture.
				ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
				helper.assertTrue(nail != null, Component.literal("projectjjk_nail did not create"));
				Vec3 from = caster.getEyePosition();
				Vec3 hit = spirit.position().add(0.0, spirit.getBbHeight() * 0.5, 0.0);
				nail.prepare(caster, from, hit.subtract(from).normalize());
				nail.launchAt(hit, 0, false);
				helper.assertTrue(level.addFreshEntity(nail), Component.literal("launched nail was not spawned"));
			} catch (RuntimeException | AssertionError failure) {
				cleanup(helper, caster, spirit);
				throw failure;
			}
		});
		for (int tick = 3; tick <= POLL_DEADLINE; tick++) {
			final int poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (cast.get()) {
					if (spirit.getHealth() < healthBefore.get()
							&& ProjectJjkNailMarks.marks(caster.getUUID(), spirit.getUUID(), level.getGameTime()) < marksBefore.get()) {
						punished.set(true);
					}
					return;
				}
				if (ProjectJjkNailMarks.marks(caster.getUUID(), spirit.getUUID(), level.getGameTime()) >= 1) {
					marked.set(true);
				}
				if (!marked.get()) {
					return;
				}
				try {
					List<NailAnchorRegistry.Entry> chained = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
					if (chained.isEmpty()) {
						return;
					}
					TodoSwapTestFixtures.aimAt(caster,
							spirit.position().add(0.0, spirit.getBbHeight() * 0.5, 0.0));
					healthBefore.set((double) spirit.getHealth());
					marksBefore.set(ProjectJjkNailMarks.marks(caster.getUUID(), spirit.getUUID(), level.getGameTime()));
					AbilityResult result = CharacterAbilityExecutor.tryCast(caster, CharacterAbility.PRIMARY, true);
					helper.assertTrue(result == AbilityResult.SUCCESS,
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
									spirit.getUUID(), "directed Hairpin cast via PRIMARY slot",
									AbilityResult.SUCCESS, result));
					cast.set(true);
				} catch (RuntimeException | AssertionError failure) {
					cleanup(helper, caster, spirit);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(POLL_DEADLINE + 40, () -> {
			helper.assertTrue(marked.get(), Component.literal("launched nail never marked the spirit"));
			helper.assertTrue(cast.get(), Component.literal("PRIMARY slot did not route to directed Hairpin"));
			helper.assertTrue(punished.get(), Component.literal(
					"directed Hairpin did not damage and consume the mark on its target"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, skyAccess = true)
	public void directedHairpinSeedMatrixIsDeterministic(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, "directedHairpinSeedMatrixIsDeterministic",
				new BlockPos(1, 1, 1), -90.0f, 0.0f);
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, "seed-matrix",
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
		helper.runAtTickTime(2, () -> {
			UUID envA = new UUID(0L, 1L);
			UUID envB = new UUID(0L, 2L);
			UUID entityA = new UUID(0L, 3L);
			UUID entityB = new UUID(0L, 4L);
			List<HairpinNetwork.Node> nodes = List.of(
					new HairpinNetwork.Node(envA, new Vec3(2.0, 1.0, 1.0), null, 1,
							NailAnchorRegistry.NailOrigin.LAUNCHED),
					new HairpinNetwork.Node(envB, new Vec3(3.0, 1.0, 1.0), null, 1,
							NailAnchorRegistry.NailOrigin.TRAP_CORNER),
					new HairpinNetwork.Node(entityA, new Vec3(4.0, 1.0, 1.0), spirit.getUUID(), 2,
							NailAnchorRegistry.NailOrigin.TRAP_IMPACT),
					new HairpinNetwork.Node(entityB, new Vec3(5.0, 1.0, 1.0), spirit.getUUID(), 3,
							NailAnchorRegistry.NailOrigin.LAUNCHED));
			BlockHitResult blockHit = new BlockHitResult(new Vec3(2.0, 1.0, 1.0), Direction.UP,
					helper.absolutePos(new BlockPos(2, 1, 1)), false);
			BlockHitResult farBlockHit = new BlockHitResult(new Vec3(3.0, 1.0, 1.0), Direction.UP,
					helper.absolutePos(new BlockPos(3, 1, 1)), false);
			List<SeedCase> cases = List.of(
					new SeedCase("env-to-env", null, blockHit, envA),
					new SeedCase("env-to-env-far", null, farBlockHit, envB),
					new SeedCase("entity-aim", spirit, null, entityA),
					// Entity aim wins over a simultaneous block hit — precedence, not a duplicate row.
					new SeedCase("entity-over-block", spirit, farBlockHit, entityA));
			for (SeedCase seedCase : cases) {
				UUID resolved = HairpinSeedResolver.resolveSeed(level, caster.getEyePosition(), new Vec3(1, 0, 0),
						seedCase.aimedEntity(), seedCase.blockHit(), nodes);
				helper.assertTrue(seedCase.expected().equals(resolved), Component.literal(seedCase.name() + " resolved " + resolved));
			}
			List<UUID> baseline = HairpinNetwork.build(nodes, nodes.getFirst(), 10.0).stream()
					.map(HairpinNetwork.Node::nailId).toList();
			for (int i = 0; i < 50; i++) {
				List<UUID> repeat = HairpinNetwork.build(nodes.reversed(), nodes.getFirst(), 10.0).stream()
						.map(HairpinNetwork.Node::nailId).toList();
				helper.assertTrue(baseline.equals(repeat), Component.literal("network order changed at iteration " + i));
			}
			spirit.discard();
			CursedSpiritTestFixtures.cleanupVictim(helper, caster);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void trapCornersAndDepthCriticalNodesRemainOwned(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, "trapCornersAndDepthCriticalNodesRemainOwned",
				new BlockPos(1, 1, 1), -90.0f, 0.0f);
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, "trap-depth",
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
		helper.runAtTickTime(2, () -> {
			NailTrap trap = new NailTrap(caster.getUUID(), level.dimension().location().toString(),
					new NailTrap.Point(4, 1, 1),
					List.of(new NailTrap.Point(4, 1, 1), new NailTrap.Point(4, 1, 2), new NailTrap.Point(5, 1, 1)),
					List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), 600, 6);
			helper.assertTrue(trap.nailIds().size() == 3, Component.literal("trap must expose three corner anchors"));
			HairpinNetwork.Node critical = new HairpinNetwork.Node(trap.nailIds().getFirst(),
					new Vec3(4, 1, 1), spirit.getUUID(), 3, NailAnchorRegistry.NailOrigin.TRAP_CORNER);
			HairpinNetwork.Node impact = new HairpinNetwork.Node(trap.nailIds().get(1),
					new Vec3(5, 1, 1), spirit.getUUID(), 1, NailAnchorRegistry.NailOrigin.TRAP_IMPACT);
			List<HairpinNetwork.Node> chain = HairpinNetwork.build(List.of(impact, critical), critical, 10.0);
			helper.assertTrue(chain.getFirst().origin() == NailAnchorRegistry.NailOrigin.TRAP_CORNER,
					Component.literal("trap corner was not retained as chain seed"));
			helper.assertTrue(critical.depth() == 3 && impact.depth() < critical.depth(),
					Component.literal("depth-3 critical response lost its depth metadata"));
			ProjectJjkNailMarks.clearOwner(caster.getUUID());
			spirit.discard();
			CursedSpiritTestFixtures.cleanupVictim(helper, caster);
			helper.succeed();
		});
	}

	private static ProjectJjkNailEntity entityNail(ServerLevel level, ServerPlayer caster, Object target, Vec3 hit) {
		ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
		if (nail == null) throw new IllegalStateException("projectjjk_nail did not create");
		if (!(target instanceof net.minecraft.world.entity.Entity entity)) throw new IllegalArgumentException("target");
		Vec3 from = caster.getEyePosition();
		nail.prepare(caster, from, hit.subtract(from).normalize());
		nail.attachToEntity(entity, hit);
		return nail;
	}

	private static ServerPlayer setupCaster(GameTestHelper helper, String fixture, BlockPos relativeFeet,
			float yaw, float pitch) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(relativeFeet);
		caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				java.util.Set.of(), yaw, pitch, false);
		CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
		return caster;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer caster, net.minecraft.world.entity.Entity spirit) {
		ProjectJjkNailMarks.clearOwner(caster.getUUID());
		NailAnchorRegistry.discardOwned(helper.getLevel(), caster.getUUID());
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, caster);
	}

	private record SeedCase(String name, net.minecraft.world.entity.Entity aimedEntity,
			BlockHitResult blockHit, UUID expected) {}
}
