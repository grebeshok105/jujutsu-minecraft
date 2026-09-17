package jujutsu.mod.gametest;

import java.util.List;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;
import jujutsu.mod.cursedincident.object.CursedObjectItem;
import jujutsu.mod.cursedincident.object.CursedObjectState;

/** Shared issue #110 fixture helpers; all positions are structure-relative at call sites. */
public final class CursedIncidentTestFixtures {
	private CursedIncidentTestFixtures() {
	}

	public static List<BlockPos> sampledPositions(IncidentRecord record, IncidentStage stage) {
		if (record == null || record.center == null || stage == null) {
			return List.of();
		}
		int count = (int) Math.min(400L,
				Math.max(1L, (long) Math.floor(record.radius * record.radius * record.radius / 8.0)));
		return jujutsu.mod.cursedincident.infection.ZoneGeometry.sampleBlocks(
				jujutsu.mod.cursedincident.infection.ZoneGeometry.shapeOf(record.params),
				record.center, record.radius, net.minecraft.util.RandomSource.create(record.seed ^ stage.ordinal()), count);
	}

	public static IncidentRecord spawn(GameTestHelper helper, BlockPos relativeCenter, IncidentStage stage,
			double radius, long seed, SourceKind sourceKind, String objectType) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(relativeCenter);
		// The facade owns the active world binding; establish it before the initial delta.
		IncidentControl.catchUp(level);
		if (sourceKind == SourceKind.OBJECT) {
			UUID objectId = IncidentControl.spawnObject(level, center, objectType, 3, seed);
			if (objectId == null) {
				return null;
			}
			return IncidentControl.recordsForRuntime().stream()
					.filter(record -> objectId.equals(record.objectInstanceId))
					.findFirst().orElse(null);
		}
		return IncidentControl.spawn(new IncidentControl.SpawnRequest(center, level.dimension(), "blight",
				3, seed, stage, objectType, sourceKind, radius));
	}

	public static IncidentRecord spawnFree(GameTestHelper helper, BlockPos relativeCenter, IncidentStage stage,
			double radius, long seed) {
		return spawn(helper, relativeCenter, stage, radius, seed, SourceKind.FREE, null);
	}

	public static IncidentRecord spawnObject(GameTestHelper helper, BlockPos relativeCenter, IncidentStage stage,
			double radius, long seed, String objectType) {
		return spawn(helper, relativeCenter, stage, radius, seed, SourceKind.OBJECT, objectType);
	}

	public static ItemEntity findCursedObject(GameTestHelper helper, BlockPos relativeCenter, UUID instanceId) {
		BlockPos center = helper.absolutePos(relativeCenter);
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(center).inflate(3.0), item -> item.isAlive())) {
			CursedObjectState state = CursedObjectItem.state(entity.getItem());
			if (state != null && (instanceId == null || state.instanceId().equals(instanceId))) {
				return entity;
			}
		}
		return null;
	}

	public static void layFloor(GameTestHelper helper, int min, int max) {
		for (int x = min; x <= max; x++) {
			for (int z = min; z <= max; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
	}

	public static BlockPos relative(GameTestHelper helper, BlockPos absolute) {
		return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	public static Component diagnostic(String fixture, GameTestHelper helper, String what, Object expected, Object actual) {
		return Component.literal(fixture + " tick=" + helper.getTick() + " " + what + " expected=" + expected + " actual=" + actual);
	}

	public static void cleanup(IncidentRecord record) {
		if (record != null && record.id != null) {
			try {
				IncidentControl.cleanup(record.id);
			} catch (RuntimeException ignored) {
			}
		}
	}
}
