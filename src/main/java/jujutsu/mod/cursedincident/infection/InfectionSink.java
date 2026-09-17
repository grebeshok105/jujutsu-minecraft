package jujutsu.mod.cursedincident.infection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.cursedincident.DwellProvider;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.IncidentWorldSink;
import jujutsu.mod.cursedincident.runtime.IncidentSpawnRuntime;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.VfxCues;

/** Applies deterministic infection deltas behind the shared per-tick block budget. */
public final class InfectionSink implements IncidentWorldSink {
	public static final int PER_TICK_BLOCK_BUDGET = 64;
	public static final long CURSE_TOPUP_TICKS = 200L;
	public static final long CONTAINER_SCAN_TICKS = 300L;
	public static final long CULL_TICKS = 400L;

	private static final Map<UUID, Cadence> CADENCE = new HashMap<>();
	private static long budgetTick = Long.MIN_VALUE;
	private static int budgetUsed;
	private final DwellProvider dwellProvider;

	public InfectionSink() {
		this(DwellProvider.NONE);
	}

	public InfectionSink(DwellProvider dwellProvider) {
		this.dwellProvider = dwellProvider == null ? DwellProvider.NONE : dwellProvider;
	}

	@Override
	public void applyStageDelta(ServerLevel level, IncidentRecord record, IncidentStage from, IncidentStage to) {
		if (level == null || record == null || record.center == null || to == null || record.scarred || record.sealed) {
			return;
		}
		InfectionQueue queue = InfectionQueue.forIncident(record);
		int sampleCount = (int) Math.min(400L, Math.max(1L, (long) Math.floor(record.radius * record.radius * record.radius / 8.0)));
		ZoneGeometry.Shape shape = ZoneGeometry.shapeOf(record.params);
		for (BlockPos pos : ZoneGeometry.sampleBlocks(shape, record.center, Math.max(0.0, record.radius),
				RandomSource.create(record.seed ^ to.ordinal()), sampleCount)) {
			// Keep the stage with the position. Both chunk availability and the live block
			// state are resolved by InfectionQueue.drain, so unloaded edits are not lost.
			queue.enqueue(pos, to, true);
		}
		Cadence cadence = CADENCE.computeIfAbsent(record.id, ignored -> new Cadence());
		cadence.lastStage = level.getGameTime();
		emitCue(level, record, CursedIncidentVfxIds.STAGE_PULSE, true, to.ordinal() + 1);
		emitCue(level, record, CursedIncidentVfxIds.ZONE_AMBIENT, false, Math.max(1, to.ordinal()));
		IncidentSpawnRuntime.spawnWave(level, record, to == IncidentStage.INITIAL ? 1 : 2);
	}

	@Override
	public void tickZone(ServerLevel level, IncidentRecord record, int tickBudget) {
		if (level == null || record == null || record.center == null || record.scarred || record.sealed) {
			return;
		}
		long now = level.getGameTime();
		if (budgetTick != now) {
			budgetTick = now;
			budgetUsed = 0;
		}
		int available = Math.max(0, PER_TICK_BLOCK_BUDGET - budgetUsed);
		int allowance = Math.min(Math.max(0, tickBudget), available);
		InfectionQueue queue = InfectionQueue.forIncident(record);
		budgetUsed += queue.drain(level, allowance);
		Cadence cadence = CADENCE.computeIfAbsent(record.id, ignored -> new Cadence());
		if (due(now, cadence.lastTopup, CURSE_TOPUP_TICKS)) {
			cadence.lastTopup = now;
			IncidentSpawnRuntime.trySpawnWave(level, record);
		}
		if (due(now, cadence.lastCull, CULL_TICKS)) {
			cadence.lastCull = now;
			cullAnimals(level, record);
		}
		if (due(now, cadence.lastContainerScan, CONTAINER_SCAN_TICKS)) {
			cadence.lastContainerScan = now;
			scanContainers(level, record);
		}
		if (now % CURSE_TOPUP_TICKS == 0L) {
			for (var node : record.secondaries) {
				if (node != null && cadence.announcedSecondaryBirths.add(node.id())) {
					emitCue(level, record, CursedIncidentVfxIds.SECONDARY_BIRTH, true, 2, node.center());
				}
			}
		}
	}

	@Override
	public void onRelocated(ServerLevel level, IncidentRecord record, BlockPos oldCenter) {
		if (record != null) {
			InfectionQueue queue = InfectionQueue.forIncident(record);
			if (queue != null) {
				queue.clear();
			}
			Cadence cadence = CADENCE.get(record.id);
			if (cadence != null) {
				cadence.resetForRelocation();
			}
		}
	}

	@Override
	public void onSealed(ServerLevel level, IncidentRecord record) {
		if (record != null) {
			InfectionQueue queue = InfectionQueue.forIncident(record);
			if (queue != null) {
				queue.clear();
			}
			if (level != null) {
				emitCue(level, record, CursedIncidentVfxIds.SEAL_DEGRADE, false, 1);
			}
		}
	}

	@Override
	public void onUnsealed(ServerLevel level, IncidentRecord record) {
		if (record != null) {
			CADENCE.computeIfAbsent(record.id, ignored -> new Cadence());
		}
	}

	@Override
	public void onSealBroken(ServerLevel level, IncidentRecord record) {
		if (level != null && record != null) {
			emitCue(level, record, CursedIncidentVfxIds.SEAL_BREAK, true, 3);
		}
	}

	@Override
	public void onCleanup(ServerLevel level, IncidentRecord record) {
		if (record == null) {
			return;
		}
		if (level != null && record.id != null) {
			IncidentSpawnRuntime.cleanup(level, record.id);
		}
		InfectionQueue queue = InfectionQueue.forIncident(record);
		if (queue != null) {
			queue.clear();
		}
		if (record.id != null) {
			CADENCE.remove(record.id);
		}
		if (record.objectInstanceId != null) {
			ObjectDwellTracker.forget(record.objectInstanceId);
		}
	}

	public static Map<UUID, CadenceProbe> cadenceProbeForTest() {
		Map<UUID, CadenceProbe> out = new HashMap<>();
		CADENCE.forEach((id, cadence) -> out.put(id, cadence.snapshot()));
		return Map.copyOf(out);
	}

	public static CadenceProbe cadenceProbeForTest(UUID id) {
		Cadence cadence = CADENCE.get(id);
		return cadence == null ? new CadenceProbe(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE) : cadence.snapshot();
	}

	public static void clearRuntimeState() {
		CADENCE.clear();
		budgetTick = Long.MIN_VALUE;
		budgetUsed = 0;
	}

	private void cullAnimals(ServerLevel level, IncidentRecord record) {
		float chance = InfectionPolicy.cullAnimalChance(record.stage);
		if (chance <= 0.0f) return;
		RandomSource random = RandomSource.create(record.seed ^ level.getGameTime());
		AABB area = new AABB(record.center).inflate(record.radius);
		for (Animal animal : level.getEntitiesOfClass(Animal.class, area, entity -> !entity.isDeadOrDying())) {
			if (random.nextFloat() < chance) {
				boolean wasAlive = animal.isAlive();
				animal.hurt(JujutsuDamageSources.cursedZone(level, null), Float.MAX_VALUE);
				if (wasAlive && animal.isDeadOrDying()) {
					record.counters.animalsCulled++;
				}
			}
		}
	}

	private void scanContainers(ServerLevel level, IncidentRecord record) {
		int minX = (int) Math.floor(record.center.getX() - record.radius);
		int maxX = (int) Math.ceil(record.center.getX() + record.radius);
		int minZ = (int) Math.floor(record.center.getZ() - record.radius);
		int maxZ = (int) Math.ceil(record.center.getZ() + record.radius);
		int minChunkX = minX >> 4;
		int maxChunkX = maxX >> 4;
		int minChunkZ = minZ >> 4;
		int maxChunkZ = maxZ >> 4;
		ZoneGeometry.Shape shape = ZoneGeometry.shapeOf(record.params);
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					BlockPos pos = blockEntity.getBlockPos();
					if (!ZoneGeometry.contains(shape, record.center, record.radius, pos)
							|| !(blockEntity instanceof Container container)) {
						continue;
					}
					for (int slot = 0; slot < container.getContainerSize(); slot++) {
						var stack = container.getItem(slot);
						if (!stack.isEmpty()) {
							// Keep the live stack reference; ObjectDwellTracker resolves it
							// again from this container before every write-through.
							dwellProvider.noteContainer(pos, stack);
						}
					}
				}
			}
		}
	}

	private static boolean due(long now, long last, long cadence) {
		return last == Long.MIN_VALUE || now - last >= cadence;
	}

	private static void emitCue(ServerLevel level, IncidentRecord record, net.minecraft.resources.ResourceLocation id,
			boolean physical, int intensity) {
		emitCue(level, record, id, physical, intensity, record.center);
	}

	private static void emitCue(ServerLevel level, IncidentRecord record, net.minecraft.resources.ResourceLocation id,
			boolean physical, int intensity, BlockPos center) {
		if (center == null) {
			return;
		}
		var origin = center.getCenter();
		var cue = VfxCues.worldFixed(id, origin, intensity, level.getGameTime(), record.seed ^ id.hashCode());
		if (physical) {
			JujutsuNetworking.broadcastVfxCue(level, origin, CursedIncidentVfxIds.VFX_DELIVERY_RADIUS, cue,
					player -> true);
		} else {
			JujutsuNetworking.broadcastVfxCue(level, origin, CursedIncidentVfxIds.VFX_DELIVERY_RADIUS, cue,
					CursePerception::perceives);
		}
	}

	public record CadenceProbe(long lastStage, long lastTopup, long lastContainerScan, long lastCull) {
	}

	private static final class Cadence {
		long lastStage = Long.MIN_VALUE;
		long lastTopup = Long.MIN_VALUE;
		long lastContainerScan = Long.MIN_VALUE;
		long lastCull = Long.MIN_VALUE;
		final Set<UUID> announcedSecondaryBirths = new HashSet<>();

		void resetForRelocation() {
			lastStage = Long.MIN_VALUE;
			lastTopup = Long.MIN_VALUE;
			lastContainerScan = Long.MIN_VALUE;
			lastCull = Long.MIN_VALUE;
		}

		CadenceProbe snapshot() {
			return new CadenceProbe(lastStage, lastTopup, lastContainerScan, lastCull);
		}
	}
}
