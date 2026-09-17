package jujutsu.mod.cursedincident.infection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.cursedincident.DwellProvider;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.IncidentTemplate;
import jujutsu.mod.cursedincident.IncidentTemplates;
import jujutsu.mod.cursedincident.IncidentWorldSink;
import jujutsu.mod.cursedincident.runtime.IncidentSpawnRuntime;
import jujutsu.mod.cursedincident.runtime.IncidentRuntime;
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
		if (level == null || record == null || record.center == null || to == null || record.scarred) {
			return;
		}
		InfectionQueue queue = InfectionQueue.forIncident(record);
		int sampleCount = (int) Math.min(400L, Math.max(1L, (long) Math.floor(record.radius * record.radius * record.radius / 8.0)));
		ZoneGeometry.Shape shape = ZoneGeometry.shapeOf(record.params);
		for (BlockPos pos : ZoneGeometry.sampleBlocks(shape, record.center, Math.max(0.0, record.radius),
				RandomSource.create(record.seed ^ to.ordinal()), sampleCount)) {
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				record.counters.chunkEditsDeferred++;
				continue;
			}
			BlockState current = level.getBlockState(pos);
			if (InfectionPolicy.isContainer(current)) {
				queue.enqueue(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), true);
				continue;
			}
			InfectionPolicy.mapBlock(current, to, RandomSource.create(record.seed ^ pos.asLong()))
					.ifPresent(mapped -> queue.enqueue(pos, mapped));
		}
		Cadence cadence = CADENCE.computeIfAbsent(record.id, ignored -> new Cadence());
		cadence.lastStage = level.getGameTime();
		emitCue(level, record, CursedIncidentVfxIds.STAGE_PULSE, true, to.ordinal() + 1);
		emitCue(level, record, CursedIncidentVfxIds.ZONE_AMBIENT, false, Math.max(1, to.ordinal()));
		IncidentSpawnRuntime.spawnWave(level, record, to == IncidentStage.INITIAL ? 1 : 2);
	}

	@Override
	public void tickZone(ServerLevel level, IncidentRecord record, int tickBudget) {
		if (level == null || record == null || record.center == null || record.scarred) {
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
		if (record.sealed) {
			return;
		}
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
		if (!record.secondaries.isEmpty() && now % CURSE_TOPUP_TICKS == 0L) {
			emitCue(level, record, CursedIncidentVfxIds.SECONDARY_BIRTH, true, 2);
		}
	}

	@Override
	public void onRelocated(ServerLevel level, IncidentRecord record, BlockPos oldCenter) {
		if (record != null) {
			InfectionQueue queue = InfectionQueue.forIncident(record);
			queue.clear();
			CADENCE.remove(record.id);
		}
	}

	@Override
	public void onSealed(ServerLevel level, IncidentRecord record) {
		if (record != null) {
			InfectionQueue.forIncident(record).clear();
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
		int bound = Math.max(1, (int) Math.ceil(record.radius));
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -bound; dx <= bound; dx++) {
			for (int dy = -bound; dy <= bound; dy++) {
				for (int dz = -bound; dz <= bound; dz++) {
					BlockPos pos = record.center.offset(dx, dy, dz);
					if (!ZoneGeometry.contains(ZoneGeometry.shapeOf(record.params), record.center, record.radius, pos)
							|| !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
					BlockEntity blockEntity = level.getBlockEntity(pos);
					if (!(blockEntity instanceof Container container)) continue;
					for (int slot = 0; slot < container.getContainerSize(); slot++) {
						var stack = container.getItem(slot);
						if (!stack.isEmpty()) dwellProvider.noteContainer(pos, stack.copy());
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
		var cue = VfxCues.worldFixed(id, record.center.getCenter(), intensity, level.getGameTime(), record.seed ^ id.hashCode());
		if (physical) {
			JujutsuNetworking.broadcastVfxCue(level, record.center.getCenter(), CursedIncidentVfxIds.VFX_DELIVERY_RADIUS, cue,
					player -> true);
		} else {
			JujutsuNetworking.broadcastVfxCue(level, record.center.getCenter(), CursedIncidentVfxIds.VFX_DELIVERY_RADIUS, cue,
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

		CadenceProbe snapshot() {
			return new CadenceProbe(lastStage, lastTopup, lastContainerScan, lastCull);
		}
	}
}
