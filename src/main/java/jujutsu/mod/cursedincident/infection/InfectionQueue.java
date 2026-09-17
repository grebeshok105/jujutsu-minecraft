package jujutsu.mod.cursedincident.infection;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.cursedincident.IncidentRecord;

/** Per-incident FIFO for planned edits; world mutation is always budgeted at drain time. */
public final class InfectionQueue {
	private static final Map<UUID, InfectionQueue> QUEUES = new ConcurrentHashMap<>();
	private final IncidentRecord record;
	private final ArrayDeque<Edit> edits = new ArrayDeque<>();

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
		return edits.size();
	}

	public boolean isEmpty() {
		return edits.isEmpty();
	}

	public void clear() {
		edits.clear();
	}
	public static int boundedBudget(int requested) {
		return Math.min(64, Math.max(0, requested));
	}

	public void enqueue(BlockPos pos, BlockState state) {
		enqueue(pos, state, false);
	}

	public void enqueue(BlockPos pos, BlockState state, boolean destroyWithDrops) {
		if (pos == null || state == null) {
			return;
		}
		edits.addLast(new Edit(pos.immutable(), state, destroyWithDrops));
	}

	/** Applies at most {@code budget} edits and leaves unloaded entries queued. */
	public int drain(ServerLevel level, int budget) {
		if (level == null || budget <= 0) {
			return 0;
		}
		int applied = 0;
		int inspected = 0;
		int maxInspected = Math.max(edits.size(), budget) + 8;
		while (applied < budget && !edits.isEmpty() && inspected++ < maxInspected) {
			Edit edit = edits.removeFirst();
			if (!level.getChunkSource().hasChunk(edit.pos.getX() >> 4, edit.pos.getZ() >> 4)) {
				edits.addLast(edit);
				record.counters.chunkEditsDeferred++;
				continue;
			}
			if (edit.destroyWithDrops) {
				if (level.destroyBlock(edit.pos, true)) {
					applied++;
					record.counters.blocksChanged++;
				}
				continue;
			}
			BlockState current = level.getBlockState(edit.pos);
			if (current.equals(edit.state)) {
				continue;
			}
			if (level.setBlock(edit.pos, edit.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
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
		return QUEUES.computeIfAbsent(record.id, ignored -> new InfectionQueue(record));
	}

	public static void clearRuntimeState() {
		QUEUES.clear();
	}

	private record Edit(BlockPos pos, BlockState state, boolean destroyWithDrops) {
	}
}
