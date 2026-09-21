package jujutsu.mod.cursedincident.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.IncidentTemplate;
import jujutsu.mod.cursedincident.IncidentTemplates;
import jujutsu.mod.cursedincident.infection.InfectionPolicy;
import jujutsu.mod.cursedincident.infection.ZoneGeometry;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritSpawnRules;
import jujutsu.mod.cursedspirit.CursedSpiritTier;
import jujutsu.mod.registry.JujutsuEntities;

/** Incident-specific spirit placement; natural spawning remains owned by the spirit system. */
public final class IncidentSpawnRuntime {
	private static final String INCIDENT_TAG_PREFIX = "jujutsumod:incident/";
	private static final String NODE_TAG_PREFIX = "jujutsu_incident_node:";

	private IncidentSpawnRuntime() {
	}

	public static boolean trySpawnWave(ServerLevel level, IncidentRecord record) {
		return trySpawnWave(level, record, record == null ? null : record.center, null);
	}

	/** Spawns one spirit in the requested work centre. */
	public static boolean trySpawnWave(ServerLevel level, IncidentRecord record,
			BlockPos center, UUID nodeId) {
		if (level == null || record == null || center == null || record.stage == null
				|| record.sealed || workUnitScarred(record, nodeId)) {
			return false;
		}
		double radius = radiusFor(record, nodeId);
		if (radius < 0.0) {
			return false;
		}
		CursedSpiritTier tier = chooseTier(record);
		BlockPos spawnPos = findSurface(level, record, center, radius, tier.ordinal() + 1);
		if (spawnPos == null || !CursedSpiritSpawnRules.belowLocalCap(level, spawnPos)) {
			return false;
		}
		EntityType<CursedSpiritEntity> type = typeFor(tier);
		CursedSpiritEntity spirit = new CursedSpiritEntity(type, level, tier);
		spirit.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		spirit.setYRot(level.random.nextFloat() * 360.0f);
		spirit.setXRot(0.0f);
		spirit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.COMMAND, null);
		spirit.addTag(incidentTag(record.id));
		if (nodeId != null) {
			spirit.addTag(nodeTag(nodeId));
		}
		if (!level.addFreshEntity(spirit)) {
			return false;
		}
		record.counters.cursesSpawned++;
		return true;
	}

	public static int spawnWave(ServerLevel level, IncidentRecord record, int count) {
		return spawnWave(level, record, record == null ? null : record.center, null, count);
	}

	/** Spawns a bounded wave in one work centre. */
	public static int spawnWave(ServerLevel level, IncidentRecord record,
			BlockPos center, UUID nodeId, int count) {
		int spawned = 0;
		for (int i = 0; i < Math.max(0, count); i++) {
			if (trySpawnWave(level, record, center, nodeId)) {
				spawned++;
			}
		}
		return spawned;
	}

	/** Convenience one-spirit work-centre wave. */
	public static int spawnWave(ServerLevel level, IncidentRecord record,
			BlockPos center, UUID nodeId) {
		return spawnWave(level, record, center, nodeId, 1);
	}


	public static int cleanup(ServerLevel level, UUID incidentId) {
		return cleanupInternal(level, incidentId, null, false);
	}

	/** Removes only spirits owned by the specified parent or secondary work centre. */
	public static int cleanup(ServerLevel level, UUID incidentId, UUID nodeId) {
		return cleanupInternal(level, incidentId, nodeId, true);
	}

	private static int cleanupInternal(ServerLevel level, UUID incidentId, UUID nodeId, boolean exactNode) {
		if (level == null || incidentId == null) {
			return 0;
		}
		String incidentTag = incidentTag(incidentId);
		List<CursedSpiritEntity> spirits = new ArrayList<>();
		for (var entity : level.getAllEntities()) {
			if (entity instanceof CursedSpiritEntity spirit
					&& entity.getTags().contains(incidentTag)
					&& (!exactNode || matchesNode(spirit, nodeId))) {
				spirits.add(spirit);
			}
		}
		int removed = 0;
		for (CursedSpiritEntity spirit : spirits) {
			spirit.discard();
			removed++;
		}
		return removed;
	}

	private static CursedSpiritTier chooseTier(IncidentRecord record) {
		IncidentTemplate template = IncidentTemplates.byId(record.templateId);
		Map<CursedSpiritTier, Integer> table = InfectionPolicy.spawnTableFor(template, record.stage);
		int total = table.values().stream().mapToInt(Integer::intValue).sum();
		if (total <= 0) {
			return CursedSpiritTier.LESSER;
		}
		int roll = (int) Math.floorMod(record.seed ^ record.stage.ordinal() ^ record.counters.cursesSpawned, total);
		for (Map.Entry<CursedSpiritTier, Integer> entry : table.entrySet()) {
			roll -= Math.max(0, entry.getValue());
			if (roll < 0) {
				return entry.getKey();
			}
		}
		return CursedSpiritTier.LESSER;
	}

	private static BlockPos findSurface(ServerLevel level, IncidentRecord record,
			BlockPos center, double radius, int salt) {
		ZoneGeometry.Shape shape = ZoneGeometry.shapeOf(record.params);
		List<BlockPos> samples = ZoneGeometry.sampleBlocks(shape, center,
				Math.max(1.0, radius), net.minecraft.util.RandomSource.create(record.seed ^ salt), 24);
		for (BlockPos sample : samples) {
			if (!level.getChunkSource().hasChunk(sample.getX() >> 4, sample.getZ() >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ());
			BlockPos candidate = new BlockPos(sample.getX(), y, sample.getZ());
			if (ZoneGeometry.contains(shape, center, radius, candidate)) {
				return candidate;
			}
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
		return y > 0 ? new BlockPos(center.getX(), y, center.getZ()) : null;
	}

	private static boolean workUnitScarred(IncidentRecord record, UUID nodeId) {
		if (nodeId == null) {
			return record.scarred;
		}
		for (var node : record.secondaries) {
			if (node != null && nodeId.equals(node.nodeId())) {
				return node.scarred();
			}
		}
		return true;
	}

	private static double radiusFor(IncidentRecord record, UUID nodeId) {
		if (nodeId == null) {
			return Math.max(0.0, record.radius);
		}
		for (var node : record.secondaries) {
			if (node != null && nodeId.equals(node.nodeId())) {
				return Math.max(0.0, node.radius());
			}
		}
		return -1.0;
	}

	public static String incidentTag(UUID incidentId) {
		return INCIDENT_TAG_PREFIX + incidentId;
	}

	public static String nodeTag(UUID nodeId) {
		return NODE_TAG_PREFIX + nodeId;
	}

	private static boolean matchesNode(CursedSpiritEntity spirit, UUID nodeId) {
		if (nodeId == null) {
			return spirit.getTags().stream().noneMatch(tag -> tag.startsWith(NODE_TAG_PREFIX));
		}
		return spirit.getTags().contains(nodeTag(nodeId));
	}

	private static EntityType<CursedSpiritEntity> typeFor(CursedSpiritTier tier) {
		return switch (tier) {
			case GREATER -> JujutsuEntities.GREATER_CURSED_SPIRIT;
			case COMMON -> JujutsuEntities.CURSED_SPIRIT;
			case LESSER -> JujutsuEntities.LESSER_CURSED_SPIRIT;
		};
	}
}
