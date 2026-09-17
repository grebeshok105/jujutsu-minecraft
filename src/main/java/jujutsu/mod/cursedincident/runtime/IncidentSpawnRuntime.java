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
	private IncidentSpawnRuntime() {
	}

	public static boolean trySpawnWave(ServerLevel level, IncidentRecord record) {
		if (level == null || record == null || record.center == null || record.stage == null
				|| record.scarred || record.sealed) {
			return false;
		}
		CursedSpiritTier tier = chooseTier(record);
		BlockPos spawnPos = findSurface(level, record, tier.ordinal() + 1);
		if (spawnPos == null || !CursedSpiritSpawnRules.belowLocalCap(level, spawnPos)) {
			return false;
		}
		EntityType<CursedSpiritEntity> type = typeFor(tier);
		CursedSpiritEntity spirit = new CursedSpiritEntity(type, level, tier);
		spirit.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		spirit.setYRot(level.random.nextFloat() * 360.0f);
		spirit.setXRot(0.0f);
		spirit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.COMMAND, null);
		spirit.addTag("jujutsumod:incident/" + record.id);
		if (!level.addFreshEntity(spirit)) {
			return false;
		}
		record.counters.cursesSpawned++;
		return true;
	}

	public static int spawnWave(ServerLevel level, IncidentRecord record, int count) {
		int spawned = 0;
		for (int i = 0; i < Math.max(0, count); i++) {
			if (trySpawnWave(level, record)) {
				spawned++;
			}
		}
		return spawned;
	}

	public static int cleanup(ServerLevel level, UUID incidentId) {
		if (level == null || incidentId == null) {
			return 0;
		}
		String tag = "jujutsumod:incident/" + incidentId;
		List<CursedSpiritEntity> spirits = new ArrayList<>(level.getEntitiesOfClass(
				CursedSpiritEntity.class, level.getWorldBorder().getCollisionShape().bounds(), entity -> entity.getTags().contains(tag)));
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

	private static BlockPos findSurface(ServerLevel level, IncidentRecord record, int salt) {
		List<BlockPos> samples = ZoneGeometry.sampleBlocks(ZoneGeometry.shapeOf(record.params), record.center,
				Math.max(1.0, record.radius), net.minecraft.util.RandomSource.create(record.seed ^ salt), 24);
		for (BlockPos sample : samples) {
			if (!level.getChunkSource().hasChunk(sample.getX() >> 4, sample.getZ() >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ());
			BlockPos candidate = new BlockPos(sample.getX(), y, sample.getZ());
			if (ZoneGeometry.contains(ZoneGeometry.shapeOf(record.params), record.center, record.radius, candidate)) {
				return candidate;
			}
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, record.center.getX(), record.center.getZ())
				> 0 ? new BlockPos(record.center.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						record.center.getX(), record.center.getZ()), record.center.getZ()) : null;
	}

	private static EntityType<CursedSpiritEntity> typeFor(CursedSpiritTier tier) {
		return switch (tier) {
			case GREATER -> JujutsuEntities.GREATER_CURSED_SPIRIT;
			case COMMON -> JujutsuEntities.CURSED_SPIRIT;
			case LESSER -> JujutsuEntities.LESSER_CURSED_SPIRIT;
		};
	}
}
