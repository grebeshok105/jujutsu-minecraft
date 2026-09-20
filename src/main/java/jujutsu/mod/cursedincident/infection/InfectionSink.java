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
import jujutsu.mod.cursedincident.SecondaryNode;
import jujutsu.mod.cursedincident.runtime.IncidentSpawnRuntime;
import jujutsu.mod.cursedincident.runtime.IncidentZoneSync;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;
import jujutsu.mod.network.IncidentZoneStatePayload;
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
	public static final long AMBIENT_INTERVAL_TICKS = 100L;

	/** Per-stage ambient cadence: INITIAL breathes slowly, CATASTROPHIC pulses fast. */
	public static long ambientIntervalTicks(IncidentStage stage) {
		int ordinal = stage == null ? 0 : stage.ordinal();
		return Math.max(40L, AMBIENT_INTERVAL_TICKS + 20L - ordinal * 20L);
	}

	private static final Map<UUID, Set<UUID>> PENDING_SECONDARY_BIRTHS = new HashMap<>();
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
			// hasChunk BEFORE getBlockState — reading the state first would force a chunk
			// load for every radius sample outside the loaded center (review finding).
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				record.counters.chunkEditsDeferred++;
				continue;
			}
			BlockState current = level.getBlockState(pos);
			BlockState target = InfectionPolicy.mapBlock(current, to,
					RandomSource.create(record.seed ^ pos.asLong())).orElse(null);
			boolean destroy = InfectionPolicy.isContainer(current);
			if (target != null || destroy) {
				// The concrete state is durable; drain never needs to re-run a stage mapping.
				queue.enqueue(pos, target, destroy, null);
			}
		}
		// Secondary work centers get their own delta sample at their own center — a stage
		// transition must infect every active hearth, not only the parent's (review P1).
		for (SecondaryNode node : record.secondaries) {
			if (node == null || node.scarred() || node.center() == null) {
				continue;
			}
			for (BlockPos pos : ZoneGeometry.sampleBlocks(shape, node.center(), Math.max(0.0, node.radius()),
					RandomSource.create(record.seed ^ to.ordinal() ^ node.nodeId().getLeastSignificantBits()),
					sampleCount)) {
				if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					record.counters.chunkEditsDeferred++;
					continue;
				}
				BlockState current = level.getBlockState(pos);
				BlockState target = InfectionPolicy.mapBlock(current, to,
						RandomSource.create(record.seed ^ pos.asLong())).orElse(null);
				boolean destroy = InfectionPolicy.isContainer(current);
				if (target != null || destroy) {
					queue.enqueue(pos, target, destroy, node.nodeId());
				}
			}
		}
		record.lastAmbientGameTime = level.getGameTime();
		emitCue(level, record, CursedIncidentVfxIds.STAGE_PULSE, true, to.ordinal() + 1);
		// A stage commit is also the zone's first audible beat: without this the ambient
		// cadence leaves a fresh incident (and `/jujutsu incident demo`) silent for a full
		// interval — up to six seconds at INITIAL.
		emitCue(level, record, CursedIncidentVfxIds.ZONE_AMBIENT, false,
				Math.max(1, to.ordinal()), record.center);
		// Zone-state snapshot on every stage commit (spawn included — the INITIAL→INITIAL
		// delta at spawn is the first broadcast a perceiving client sees).
		IncidentZoneSync.sendZoneState(level, record);
		for (SecondaryNode node : record.secondaries) {
			if (node != null && !node.scarred()) {
				IncidentZoneSync.sendZoneState(level, record, node);
			}
		}
		IncidentSpawnRuntime.spawnWave(level, record, record.center, null, to == IncidentStage.INITIAL ? 1 : 2);
	}

	@Override
	public void tickZone(ServerLevel level, IncidentRecord record, BlockPos center, UUID nodeId, int tickBudget) {
		if (level == null || record == null || center == null || record.sealed
				|| workUnitScarred(record, nodeId)) {
			return;
		}
		long now = level.getGameTime();
		// The shared budget is per SERVER tick, not per level clock — dimensions keep
		// independent gameTime offsets, so keying on now would reset the 64-block cap
		// mid-tick whenever a second dimension's zone runs (review finding).
		long epoch = level.getServer() == null ? now : level.getServer().getTickCount();
		if (budgetTick != epoch) {
			budgetTick = epoch;
			budgetUsed = 0;
		}
		int available = Math.max(0, PER_TICK_BLOCK_BUDGET - budgetUsed);
		int allowance = Math.min(Math.max(0, tickBudget), available);
		InfectionQueue queue = InfectionQueue.forIncident(record);
		budgetUsed += queue.drain(level, allowance, nodeId);
		CenterCadence cadence = nodeId == null ? null : cadenceFor(record, nodeId);
		// Re-arm the short client recipe from a durable server cadence, not every zone tick.
		// Per work center: the parent and every secondary keep their own ambient clock —
		if (due(now, nodeId == null ? record.lastAmbientGameTime : cadence.lastAmbient,
				ambientIntervalTicks(record.stage))) {
			if (nodeId == null) {
				record.lastAmbientGameTime = now;
			} else {
				cadence.lastAmbient = now;
			}
			int intensity = Math.max(1, record.stage == null ? 1 : record.stage.ordinal());
			emitCue(level, record, CursedIncidentVfxIds.ZONE_AMBIENT, false, intensity, center);
			// Heartbeat: re-send this work center's zone state on the ambient cadence so
			// late-joining/late-tracking perceiving players learn the zone without a
			// dedicated join-sync path.
			if (nodeId == null) {
				IncidentZoneSync.sendZoneState(level, record);
			} else {
				for (SecondaryNode node : record.secondaries) {
					if (node != null && nodeId.equals(node.nodeId())) {
						IncidentZoneSync.sendZoneState(level, record, node);
						break;
					}
				}
			}
		}
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
			if (nodeId == null) {
				scanKnownContainers(level);
			}
		}
		// Birth cues fire only for nodes born at runtime (onSecondaryBorn queues them).
		// Persisted nodes are never queued, so a restart replays nothing — the old
		// createdGameTime<now seeding heuristic also swallowed births that landed
		// between cadence passes (review finding). Drained every zone pass, not on a
		// modulo gate: the level clock is per-dimension, so a phase-shifted dimension
		// could sit on a pending cue forever (review finding).
		Set<UUID> pending = PENDING_SECONDARY_BIRTHS.get(record.id);
		if (pending != null && !pending.isEmpty()) {
			for (var node : record.secondaries) {
				if (node != null && pending.remove(node.nodeId())) {
					emitCue(level, record, CursedIncidentVfxIds.SECONDARY_BIRTH, true, 2,
							node.center());
					// The new work center announces itself at its own center — R20.
					IncidentZoneSync.sendZoneState(level, record, node);
				}
			}
			if (pending.isEmpty()) {
				PENDING_SECONDARY_BIRTHS.remove(record.id);
			}
		}
		// The queue drain, counters and cadence anchors above all mutate durable record
		// fields — mark the store dirty or a restart silently loses them (C5/d5).
		IncidentControl.markDirty();
	}

	@Override
	public void onSecondaryBorn(IncidentRecord record, SecondaryNode node) {
		if (record != null && record.id != null && node != null) {
			PENDING_SECONDARY_BIRTHS.computeIfAbsent(record.id, ignored -> new HashSet<>())
					.add(node.nodeId());
		}
	}

	@Override
	public void onRelocated(ServerLevel level, IncidentRecord record, BlockPos oldCenter) {
		if (record != null) {
			// Only edits owed to surviving work centers live on — parent edits die with the
			// old site, and dependent nodes were already stripped by relocate (review P1).
			Set<UUID> survivingNodes = new HashSet<>();
			for (var node : record.secondaries) {
				if (node != null && node.selfSustaining() && !node.scarred()) {
					survivingNodes.add(node.nodeId());
				}
			}
			record.pendingEdits.removeIf(edit -> edit == null || edit.nodeId() == null
					|| !survivingNodes.contains(edit.nodeId()));
			record.lastTopUpGameTime = Long.MIN_VALUE;
			record.lastContainerScanGameTime = Long.MIN_VALUE;
			record.lastCullGameTime = Long.MIN_VALUE;
			record.lastAmbientGameTime = Long.MIN_VALUE;
			// Pending birth cues survive only for nodes that survive the relocate — a
			// self-sustaining node born mid-window still gets its cue, a stripped
			// dependent's cue dies with it.
			Set<UUID> pending = PENDING_SECONDARY_BIRTHS.get(record.id);
			if (pending != null) {
				pending.removeIf(nodeId -> !survivingNodes.contains(nodeId));
				if (pending.isEmpty()) {
					PENDING_SECONDARY_BIRTHS.remove(record.id);
				}
			}
			CENTER_CADENCE.remove(record.id);
			if (level != null && record.id != null) {
				// The parent zone moved: deactivate the old-center key, announce the new
				// center, and drop client entries for stripped dependent nodes.
				IncidentZoneSync.sendInactive(level, record,
						IncidentZoneStatePayload.PARENT_NODE, oldCenter);
				IncidentZoneSync.sendZoneState(level, record);
				for (var node : record.secondaries) {
					if (node != null && !survivingNodes.contains(node.nodeId())) {
						IncidentZoneSync.sendInactive(level, record, node.nodeId(), node.center());
					}
				}
			}
		}
	}

	@Override
	public void onSealed(ServerLevel level, IncidentRecord record) {
		if (record != null && level != null) {
			// Fresh sealing is distinct from later physical degradation bands.
			emitCue(level, record, CursedIncidentVfxIds.SEAL_APPLIED, true, 1);
			IncidentZoneSync.sendZoneState(level, record);
		}
	}

	@Override
	public void onSealDegraded(ServerLevel level, IncidentRecord record, UUID objectId, int bandIndex) {
		if (level != null && record != null && bandIndex >= 1 && bandIndex <= 3) {
			// IncidentControl invokes this only when a durable degradation band is crossed.
			emitCue(level, record, CursedIncidentVfxIds.SEAL_DEGRADE, false, bandIndex);
			IncidentZoneSync.sendZoneState(level, record);
		}
	}

	@Override
	public void onUnsealed(ServerLevel level, IncidentRecord record) {
		// Durable cadence anchors intentionally remain unchanged across a seal.
		if (level != null && record != null) {
			IncidentZoneSync.sendZoneState(level, record);
		}
	}

	@Override
	public void onSealBroken(ServerLevel level, IncidentRecord record) {
		if (level != null && record != null) {
			emitCue(level, record, CursedIncidentVfxIds.SEAL_BREAK, true, 3);
			IncidentZoneSync.sendZoneState(level, record);
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
			// Client zone entries: the parent key dies here; dependent nodes die with it,
			// self-sustaining survivors keep their own keys (R20 child lifecycle).
			IncidentZoneSync.sendInactive(level, record,
					IncidentZoneStatePayload.PARENT_NODE, record.center);
			for (var node : record.secondaries) {
				if (node != null && !node.selfSustaining()) {
					IncidentZoneSync.sendInactive(level, record, node.nodeId(), node.center());
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
			// Same survivor rule as onRelocated: a self-sustaining node born mid-window
			// keeps its pending birth cue; stripped dependents lose theirs.
			Set<UUID> pending = PENDING_SECONDARY_BIRTHS.get(record.id);
			if (pending != null) {
				pending.removeIf(nodeId -> !survivingNodes.contains(nodeId));
				if (pending.isEmpty()) {
					PENDING_SECONDARY_BIRTHS.remove(record.id);
				}
			}
			CENTER_CADENCE.remove(record.id);
			InfectionQueue.remove(record.id);
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
		PENDING_SECONDARY_BIRTHS.clear();
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
							dwellProvider.noteContainer(level, pos, stack);
						}
					}
				}
			}
		}
	}

	private static CenterCadence cadenceFor(IncidentRecord record, UUID nodeId) {
		return CENTER_CADENCE
				.computeIfAbsent(record.id, ignored -> new HashMap<>())
				.computeIfAbsent(nodeId, ignored -> {
					// Fresh cadence inherits the record's durable anchors — starting at
					// MIN_VALUE would fire every due path immediately after each restart,
					// duplicating top-up waves and scans (review P2).
					CenterCadence cadence = new CenterCadence();
					cadence.lastTopUp = record.lastTopUpGameTime;
					cadence.lastContainerScan = record.lastContainerScanGameTime;
					cadence.lastCull = record.lastCullGameTime;
					cadence.lastAmbient = record.lastAmbientGameTime;
					return cadence;
				});
	}

	/**
	 * Re-observes every known container position — the heartbeat that lets a distant chest
	 * relocate its incident even when no zone covers it (issue #110 C6).
	 */
	private void scanKnownContainers(ServerLevel level) {
		for (BlockPos pos : dwellProvider.knownContainerPositions()) {
			if (pos == null) {
				continue;
			}
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof Container container)) {
				continue;
			}
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				var stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					dwellProvider.noteContainer(level, pos, stack);
				}
			}
		}
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
		long lastAmbient = Long.MIN_VALUE;
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
