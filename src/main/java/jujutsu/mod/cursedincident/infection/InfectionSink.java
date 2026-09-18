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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.cursedincident.DwellProvider;
import jujutsu.mod.cursedincident.IncidentControl;
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

	private static final Map<UUID, Set<UUID>> ANNOUNCED_SECONDARY_BIRTHS = new HashMap<>();
	private static final Map<UUID, Map<UUID, CenterCadence>> CENTER_CADENCE = new HashMap<>();
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
		int sampleCount = (int) Math.min(400L,
				Math.max(1L, (long) Math.floor(record.radius * record.radius * record.radius / 8.0)));
		ZoneGeometry.Shape shape = ZoneGeometry.shapeOf(record.params);
		for (BlockPos pos : ZoneGeometry.sampleBlocks(shape, record.center, Math.max(0.0, record.radius),
				RandomSource.create(record.seed ^ to.ordinal()), sampleCount)) {
			BlockState current = level.getBlockState(pos);
			BlockState target = InfectionPolicy.mapBlock(current, to,
					RandomSource.create(record.seed ^ pos.asLong())).orElse(null);
			boolean destroy = InfectionPolicy.isContainer(current);
			if (target != null || destroy) {
				// The concrete state is durable; drain never needs to re-run a stage mapping.
				queue.enqueue(pos, target, destroy, null);
			}
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				record.counters.chunkEditsDeferred++;
			}
		}
		record.lastAmbientGameTime = level.getGameTime();
		emitCue(level, record, CursedIncidentVfxIds.STAGE_PULSE, true, to.ordinal() + 1);
		emitCue(level, record, CursedIncidentVfxIds.ZONE_AMBIENT, false, Math.max(1, to.ordinal()));
		IncidentSpawnRuntime.spawnWave(level, record, record.center, null, to == IncidentStage.INITIAL ? 1 : 2);
	}

	@Override
	public void tickZone(ServerLevel level, IncidentRecord record, BlockPos center, UUID nodeId, int tickBudget) {
		if (level == null || record == null || center == null || record.sealed
				|| workUnitScarred(record, nodeId)) {
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
		budgetUsed += queue.drain(level, allowance, nodeId);
		CenterCadence cadence = nodeId == null ? null : cadenceFor(record, nodeId);
		if (due(now, nodeId == null ? record.lastTopUpGameTime : cadence.lastTopUp, CURSE_TOPUP_TICKS)) {
			if (nodeId == null) {
				record.lastTopUpGameTime = now;
			} else {
				cadence.lastTopUp = now;
			}
			IncidentSpawnRuntime.trySpawnWave(level, record, center, nodeId);
		}
		if (due(now, nodeId == null ? record.lastCullGameTime : cadence.lastCull, CULL_TICKS)) {
			if (nodeId == null) {
				record.lastCullGameTime = now;
			} else {
				cadence.lastCull = now;
			}
			cullAnimals(level, record, center, radiusFor(record, nodeId));
		}
		if (due(now, nodeId == null ? record.lastContainerScanGameTime : cadence.lastContainerScan,
				CONTAINER_SCAN_TICKS)) {
			if (nodeId == null) {
				record.lastContainerScanGameTime = now;
			} else {
				cadence.lastContainerScan = now;
			}
			scanContainers(level, record, center, nodeId, radiusFor(record, nodeId));
		}
		if (now % CURSE_TOPUP_TICKS == 0L) {
			Set<UUID> announced = ANNOUNCED_SECONDARY_BIRTHS.computeIfAbsent(record.id, ignored -> new HashSet<>());
			for (var node : record.secondaries) {
				if (node != null && announced.add(node.nodeId())) {
					emitCue(level, record, CursedIncidentVfxIds.SECONDARY_BIRTH, true, 2, node.center());
				}
			}
		}
	}

	@Override
	public void onRelocated(ServerLevel level, IncidentRecord record, BlockPos oldCenter) {
		if (record != null) {
			record.pendingEdits.clear();
			record.lastTopUpGameTime = Long.MIN_VALUE;
			record.lastContainerScanGameTime = Long.MIN_VALUE;
			record.lastCullGameTime = Long.MIN_VALUE;
			record.lastAmbientGameTime = Long.MIN_VALUE;
			ANNOUNCED_SECONDARY_BIRTHS.remove(record.id);
			CENTER_CADENCE.remove(record.id);
		}
	}

	@Override
	public void onSealed(ServerLevel level, IncidentRecord record) {
		if (record != null && level != null) {
			// Sealing freezes progression but does not discard committed durable edits.
			emitCue(level, record, CursedIncidentVfxIds.SEAL_DEGRADE, false, 1);
		}
	}

	@Override
	public void onUnsealed(ServerLevel level, IncidentRecord record) {
		// Durable cadence anchors intentionally remain unchanged across a seal.
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
			IncidentSpawnRuntime.cleanup(level, record.id, null);
			for (var node : record.secondaries) {
				if (node != null && !node.selfSustaining()) {
					IncidentSpawnRuntime.cleanup(level, record.id, node.nodeId());
				}
			}
		}
		Set<UUID> survivingNodes = new HashSet<>();
		for (var node : record.secondaries) {
			if (node != null && node.selfSustaining() && !node.scarred()) {
				survivingNodes.add(node.nodeId());
			}
		}
		record.pendingEdits.removeIf(edit -> edit == null || edit.nodeId() == null
				|| !survivingNodes.contains(edit.nodeId()));
		if (record.id != null) {
			ANNOUNCED_SECONDARY_BIRTHS.remove(record.id);
			CENTER_CADENCE.remove(record.id);
		}
		if (record.objectInstanceId != null) {
			ObjectDwellTracker.forgetEverywhere(record.objectInstanceId);
		}
	}

	public static Map<UUID, CadenceProbe> cadenceProbeForTest() {
		Map<UUID, CadenceProbe> out = new HashMap<>();
		for (IncidentRecord record : IncidentControl.recordsForRuntime()) {
			if (record != null && record.id != null) {
				out.put(record.id, cadenceProbeForTest(record));
			}
		}
		return Map.copyOf(out);
	}

	public static CadenceProbe cadenceProbeForTest(UUID id) {
		if (id != null) {
			for (IncidentRecord record : IncidentControl.recordsForRuntime()) {
				if (record != null && id.equals(record.id)) {
					return cadenceProbeForTest(record);
				}
			}
		}
		return new CadenceProbe(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
	}

	private static CadenceProbe cadenceProbeForTest(IncidentRecord record) {
		return new CadenceProbe(record.lastUpdateGameTime, record.lastTopUpGameTime,
				record.lastContainerScanGameTime, record.lastCullGameTime);
	}

	public static void clearRuntimeState() {
		ANNOUNCED_SECONDARY_BIRTHS.clear();
		CENTER_CADENCE.clear();
		budgetTick = Long.MIN_VALUE;
		budgetUsed = 0;
	}

	private void cullAnimals(ServerLevel level, IncidentRecord record, BlockPos center, double radius) {
		float chance = InfectionPolicy.cullAnimalChance(record.stage);
		if (chance <= 0.0f || center == null || radius < 0.0) return;
		RandomSource random = RandomSource.create(record.seed ^ level.getGameTime() ^ center.asLong());
		AABB area = new AABB(center).inflate(radius);
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

	private void scanContainers(ServerLevel level, IncidentRecord record, BlockPos center,
			UUID nodeId, double radius) {
		int minX = (int) Math.floor(center.getX() - radius);
		int maxX = (int) Math.ceil(center.getX() + radius);
		int minZ = (int) Math.floor(center.getZ() - radius);
		int maxZ = (int) Math.ceil(center.getZ() + radius);
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
					if (!ZoneGeometry.contains(shape, center, radius, pos)
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


	private static CenterCadence cadenceFor(IncidentRecord record, UUID nodeId) {
		return CENTER_CADENCE
				.computeIfAbsent(record.id, ignored -> new HashMap<>())
				.computeIfAbsent(nodeId, ignored -> new CenterCadence());
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

	private static final class CenterCadence {
		long lastTopUp = Long.MIN_VALUE;
		long lastContainerScan = Long.MIN_VALUE;
		long lastCull = Long.MIN_VALUE;
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
}
