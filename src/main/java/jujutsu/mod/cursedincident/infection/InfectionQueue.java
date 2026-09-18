package jujutsu.mod.cursedincident.infection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;

/** Per-incident FIFO for planned edits; world mutation is always budgeted at drain time. */
public final class InfectionQueue {
	private static final Map<UUID, InfectionQueue> QUEUES = new ConcurrentHashMap<>();
	private final IncidentRecord record;

	public InfectionQueue(IncidentRecord record) {
		if (record == null || record.id == null) {
			throw new IllegalArgumentException("infection queue requires an identified incident");
		}
		this.record = record;
	}

	public IncidentRecord record() {
		return record;
	}

	public int size() {
		return record.pendingEdits.size();
	}

	public boolean isEmpty() {
		return record.pendingEdits.isEmpty();
	}

	public void clear() {
		record.pendingEdits.clear();
	}

	public static int boundedBudget(int requested) {
		return Math.min(64, Math.max(0, requested));
	}

	/** Queues a position for stage-time mapping; the block is resolved when drained. */
	public void enqueue(BlockPos pos, IncidentStage stage) {
		enqueue(pos, stage, false);
	}

	/** Queues a position for stage-time mapping for legacy callers. */
	public void enqueue(BlockPos pos, IncidentStage stage, boolean destroyWithDrops) {
		if (pos == null || stage == null) {
			return;
		}
		record.pendingEdits.add(new IncidentRecord.PendingEdit(pos, null, destroyWithDrops, null, stage));
	}

	/** Queues an already-selected target state for callers that own the mapping. */
	public void enqueue(BlockPos pos, BlockState state) {
		enqueue(pos, state, false);
	}

	public void enqueue(BlockPos pos, BlockState state, boolean destroyWithDrops) {
		if (pos == null || (state == null && !destroyWithDrops)) {
			return;
		}
		record.pendingEdits.add(new IncidentRecord.PendingEdit(pos, state, destroyWithDrops));
	}

	/** Applies at most {@code budget} edits and leaves unloaded entries queued. */
	public int drain(ServerLevel level, int budget) {
		int bounded = boundedBudget(budget);
		if (level == null || bounded <= 0 || record.sealed) {
			return 0;
		}
		int applied = 0;
		int inspected = 0;
		int maxInspected = Math.max(record.pendingEdits.size(), bounded) + 8;
		while (applied < bounded && !record.pendingEdits.isEmpty() && inspected++ < maxInspected) {
			IncidentRecord.PendingEdit edit = record.pendingEdits.remove(0);
			BlockPos pos = edit.pos();
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				// Deferred is counted once at enqueue (InfectionSink.applyStageDelta);
				// requeueing must not inflate the counter on every drain pass.
				record.pendingEdits.add(edit);
				continue;
			}
			BlockState current = level.getBlockState(pos);
			if (edit.destroy() && InfectionPolicy.isContainer(current)) {
				if (level.destroyBlock(pos, true)) {
					applied++;
					record.counters.blocksChanged++;
				}
				continue;
			}
			BlockState target = edit.blockState();
			if (target == null) {
				IncidentStage stage = edit.stage() == null ? record.stage : edit.stage();
				target = InfectionPolicy.mapBlock(current, stage,
						RandomSource.create(record.seed ^ pos.asLong())).orElse(null);
			}
			if (target == null || current.equals(target)) {
				continue;
			}
			if (level.setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
				applied++;
				record.counters.blocksChanged++;
			}
		}
		return applied;
	}

	public static InfectionQueue forIncident(IncidentRecord record) {
		if (record == null || record.id == null) {
			return null;
		}
		return QUEUES.compute(record.id, (ignored, existing) ->
				existing == null || existing.record != record ? new InfectionQueue(record) : existing);
	}

	public static void clearRuntimeState() {
		QUEUES.clear();
	}
}
