package jujutsu.mod.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorRegistry;
import jujutsu.mod.character.nobara.projectjjk.NobaraTeardown;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraProfile;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Block-1 live oracles for the unified nail anchor registry: tracking across all three origins,
 * depth updates, deeply-anchored derivation, the per-owner cap, and the teardown contract that
 * keeps world anchors while dropping cast-session state.
 */
public final class NobaraAnchorGameTests {
	private static final int POLL_DEADLINE = 60;

	@GameTest(maxTicks = 80, skyAccess = true)
	public void embeddedNailIsTrackedWithOriginAndDepth(GameTestHelper helper) {
		String fixture = "embeddedNailIsTrackedWithOriginAndDepth";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("owned nail entity was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			List<NailAnchorRegistry.Entry> anchors = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
			helper.assertTrue(anchors.size() == 1, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), spirit.getUUID(),
					"tracked anchors after embed", "1", anchors.size()));
			NailAnchorRegistry.Entry entry = anchors.getFirst();
			helper.assertTrue(entry.origin() == NailAnchorRegistry.NailOrigin.LAUNCHED,
					Component.literal("launched nail must carry the LAUNCHED origin, got " + entry.origin()));
			helper.assertTrue(entry.depth() == 1, Component.literal("fresh embed must start at depth 1, got " + entry.depth()));
			helper.assertTrue(spirit.getUUID().equals(entry.targetId()), Component.literal("anchor must bind the spirit"));
			helper.assertTrue(!NailAnchorRegistry.isDeeplyAnchored(level, caster.getUUID(), spirit.getUUID()),
					Component.literal("depth 1 must not read as deeply anchored"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, skyAccess = true)
	public void deepenUpdatesRegistryAndDeeplyAnchored(GameTestHelper helper) {
		String fixture = "deepenUpdatesRegistryAndDeeplyAnchored";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("owned nail entity was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			List<NailAnchorRegistry.Entry> anchors = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
			helper.assertTrue(anchors.size() == 1, Component.literal("premise: one tracked anchor, got " + anchors.size()));
			ProjectJjkNailEntity nail = (ProjectJjkNailEntity) level.getEntity(anchors.getFirst().nailId());
			helper.assertTrue(nail != null, Component.literal("tracked nail entity missing"));
			helper.assertTrue(nail.deepen() && nail.deepen(), Component.literal("deepen to 3 must succeed twice"));
			helper.assertTrue(!nail.deepen(), Component.literal("depth must cap at 3"));
			List<NailAnchorRegistry.Entry> updated = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
			helper.assertTrue(updated.getFirst().depth() == 3,
					Component.literal("registry depth must follow the entity, got " + updated.getFirst().depth()));
			helper.assertTrue(NailAnchorRegistry.isDeeplyAnchored(level, caster.getUUID(), spirit.getUUID()),
					Component.literal("depth 3 must read as deeply anchored"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, skyAccess = true)
	public void targetDeathDropsTheAnchor(GameTestHelper helper) {
		String fixture = "targetDeathDropsTheAnchor";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		AtomicBoolean dropped = new AtomicBoolean();
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("owned nail entity was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			helper.assertTrue(NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).size() == 1,
					Component.literal("premise: one tracked anchor before the kill"));
			spirit.kill(level);
		});
		for (int tick = 7; tick <= POLL_DEADLINE; tick++) {
			final int poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).isEmpty()) {
					dropped.set(true);
				}
			});
		}
		helper.runAtTickTime(POLL_DEADLINE + 1, () -> {
			helper.assertTrue(dropped.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					caster.getUUID(), spirit.getUUID(), "anchors after target death", "0", "non-empty"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, skyAccess = true)
	public void trapCornerNailIsTrackedWithTrapOrigin(GameTestHelper helper) {
		String fixture = "trapCornerNailIsTrackedWithTrapOrigin";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			// A bare markAsTrapNail would self-discard (the entity checks NailTrapRuntime.isTrapNail
			// every tick); the origin flag is the registry-facing contract this oracle needs.
			nail.setOrigin(NailAnchorRegistry.NailOrigin.TRAP_CORNER);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("trap corner nail was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			List<NailAnchorRegistry.Entry> anchors = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
			helper.assertTrue(anchors.size() == 1, Component.literal("premise: one tracked anchor, got " + anchors.size()));
			helper.assertTrue(anchors.getFirst().origin() == NailAnchorRegistry.NailOrigin.TRAP_CORNER,
					Component.literal("trap corner must carry the TRAP_CORNER origin, got " + anchors.getFirst().origin()));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void ownerCapEvictsTheOldestAnchor(GameTestHelper helper) {
		String fixture = "ownerCapEvictsTheOldestAnchor";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		int cap = ProjectJjkNobaraProfile.MAX_EMBEDDED_NAILS_PER_OWNER;
		List<UUID> spawnedIds = new java.util.ArrayList<>();
		helper.runAtTickTime(2, () -> {
			for (int i = 0; i < cap + 2; i++) {
				ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
				helper.assertTrue(level.addFreshEntity(nail), Component.literal("nail " + i + " was not spawned"));
				spawnedIds.add(nail.getUUID());
			}
		});
		helper.runAtTickTime(10, () -> {
			List<NailAnchorRegistry.Entry> anchors = NailAnchorRegistry.ownedAnchors(level, caster.getUUID());
			helper.assertTrue(anchors.size() == cap, CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), spirit.getUUID(),
					"tracked anchors past the cap", String.valueOf(cap), anchors.size()));
			Set<UUID> live = anchors.stream().map(NailAnchorRegistry.Entry::nailId).collect(java.util.stream.Collectors.toSet());
			// The cap evicts the OLDEST anchors: the first two spawned nails must be gone,
			// the last two spawned must still be tracked.
			helper.assertTrue(!live.contains(spawnedIds.get(0)) && !live.contains(spawnedIds.get(1)),
					Component.literal("cap did not evict the oldest anchors"));
			helper.assertTrue(live.contains(spawnedIds.get(cap)) && live.contains(spawnedIds.get(cap + 1)),
					Component.literal("cap evicted the newest anchors instead of the oldest"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, skyAccess = true)
	public void vesselSwitchClearsCastStateButKeepsAnchors(GameTestHelper helper) {
		String fixture = "vesselSwitchClearsCastStateButKeepsAnchors";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("owned nail entity was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			helper.assertTrue(NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).size() == 1,
					Component.literal("premise: one tracked anchor before the switch"));
			CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
			helper.assertTrue(CharacterSelectionManager.selected(caster) == JujutsuCharacter.MEGUMI,
					Component.literal("the vessel switch itself failed"));
			helper.assertTrue(NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).size() == 1,
					Component.literal("world anchors must outlive the cast session (D6)"));
			CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, skyAccess = true)
	public void teardownClearsCastStateButKeepsAnchors(GameTestHelper helper) {
		String fixture = "teardownClearsCastStateButKeepsAnchors";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = setupCaster(helper, new BlockPos(1, 1, 1));
		var spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
		helper.runAtTickTime(2, () -> {
			ProjectJjkNailEntity nail = entityNail(level, caster, spirit);
			helper.assertTrue(level.addFreshEntity(nail), Component.literal("owned nail entity was not spawned"));
		});
		helper.runAtTickTime(6, () -> {
			helper.assertTrue(NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).size() == 1,
					Component.literal("premise: one tracked anchor before teardown"));
			NobaraTeardown.onCastStateLost(caster);
			helper.assertTrue(NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).size() == 1,
					Component.literal("teardown must not touch world anchors (D6)"));
			cleanup(helper, caster, spirit);
			helper.succeed();
		});
	}

	private static ProjectJjkNailEntity entityNail(ServerLevel level, ServerPlayer caster,
			net.minecraft.world.entity.Entity target) {
		ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
		if (nail == null) throw new IllegalStateException("projectjjk_nail did not create");
		Vec3 hit = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 from = caster.getEyePosition();
		nail.prepare(caster, from, hit.subtract(from).normalize());
		nail.attachToEntity(target, hit);
		return nail;
	}

	private static ServerPlayer setupCaster(GameTestHelper helper, BlockPos relativeFeet) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(relativeFeet);
		caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				java.util.Set.of(), -90.0f, 0.0f, false);
		CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
		return caster;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer caster,
			net.minecraft.world.entity.Entity spirit) {
		ProjectJjkNailMarks.clearOwner(caster.getUUID());
		NailAnchorRegistry.discardOwned(helper.getLevel(), caster.getUUID());
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, caster);
	}
}
