package jujutsu.mod.cursedincident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import jujutsu.mod.cursedincident.persist.IncidentSavedData;
import jujutsu.mod.cursedincident.policy.SealPolicy;
import jujutsu.mod.cursedincident.policy.SpawnRollPolicy;
import jujutsu.mod.cursedincident.policy.StagePolicy;
import jujutsu.mod.cursedincident.policy.TemplateRollPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The single server-side API for incident state. Gameplay, commands and the dev
 * bridge all enter through this facade; world mutation is delegated to the bound
 * {@link IncidentWorldSink}.
 */
public final class IncidentControl {
	public record SpawnRequest(BlockPos center, ResourceKey<Level> dimension, String templateId,
			Integer grade, Long seed, IncidentStage startStage, String objectTypeId, SourceKind sourceKind,
			Double radius) {
	}

	public record SealAttempt(boolean ok, int requiredTier, String reason) {
	}

	public record InspectView(UUID id, long seed, String templateId, IncidentStage stage, boolean scarred,
			long ageTicks, long createdGameTime, long lastUpdateGameTime, BlockPos center, double radius,
			String zoneShape, SourceKind sourceKind, UUID objectInstanceId, String objectTypeId,
			Integer objectGrade, BlockPos sourcePos, BlockPos sourceContainer,
			Map<String, Integer> curseSet, String atmosphereId, List<String> localGoals,
			boolean ignoreShelter, boolean sealed, int sealIntegrity, int sealTier, int sealFailures,
			String knowledge, List<SecondaryNode> secondaries, int dependentCenters, List<BlockPos> scars,
			Map<String, Long> workCounters, List<String> transitionLog) {
	}

	public static final class IncidentNotFoundException extends RuntimeException {
		public IncidentNotFoundException(UUID id) {
			super("unknown incident " + id);
		}
	}
	private static final long DEFAULT_GAME_TIME = 0L;
	public static final int MAX_DEPENDENT_CENTERS = TemplateRollPolicy.MAX_DEPENDENT_CENTERS;

	private static Supplier<IncidentSavedData> storeSupplier = IncidentSavedData::new;
	private static IncidentSavedData boundStore = new IncidentSavedData();
	private static IncidentWorldSink worldSink = IncidentWorldSink.NOOP;
	private static DwellProvider dwellProvider = DwellProvider.NONE;
	private static ObjectSpawner objectSpawner;
	private static ServerLevel activeLevel;
	public static void bindStore(Supplier<IncidentSavedData> store) {
		IncidentSavedData resolved = store == null ? null : store.get();
		boundStore = resolved == null ? new IncidentSavedData() : resolved;
		storeSupplier = () -> boundStore;
	}

	private IncidentControl() {
	}

	// ---- wiring ----


	public static void bindWorldSink(IncidentWorldSink sink) {
		worldSink = sink == null ? IncidentWorldSink.NOOP : sink;
	}

	public static void bindDwellProvider(DwellProvider provider) {
		dwellProvider = provider == null ? DwellProvider.NONE : provider;
	}

	public static void bindObjectSpawner(ObjectSpawner spawner) {
		objectSpawner = spawner;
	}

	// ---- lifecycle ----

	public static IncidentRecord spawn(SpawnRequest request) {
		if (request == null || request.center() == null) {
			throw new IllegalArgumentException("spawn request and center are required");
		}
		long seed = request.seed() == null ? RandomSource.create().nextLong() : request.seed();
		RandomSource random = RandomSource.create(seed);
		int grade = request.grade() == null
				? 1 + random.nextInt(TemplateRollPolicy.MAX_GRADE)
				: TemplateRollPolicy.clampGrade(request.grade());
		SourceKind sourceKind = request.sourceKind() == null
				? SpawnRollPolicy.rollSourceKind(random)
				: request.sourceKind();
		IncidentTemplate template = request.templateId() == null
				? SpawnRollPolicy.rollTemplate(random, TemplateRollPolicy.escalationSpeedMul(grade))
				: IncidentTemplates.byId(request.templateId());
		if (template == null) {
			template = SpawnRollPolicy.rollTemplate(random, TemplateRollPolicy.escalationSpeedMul(grade));
		}
		IncidentParams params = SpawnRollPolicy.rollParams(random, template, grade);
		if (request.radius() != null && Double.isFinite(request.radius()) && request.radius() >= 0.0) {
			params = new IncidentParams(params.zoneShape(), request.radius(), params.curseWeights(), params.atmosphereId(),
					params.localGoals(), params.escalationSpeedMul(), params.ignoreShelter(), params.secondaryAtCritical(),
					params.dwellTicksRequired());
		}
		IncidentRecord record = new IncidentRecord();
		record.id = UUID.randomUUID();
		record.seed = seed;
		record.createdGameTime = currentGameTime();
		record.lastUpdateGameTime = record.createdGameTime;
		record.dimension = request.dimension() == null ? Level.OVERWORLD : request.dimension();
		record.center = request.center().immutable();
		record.radius = params.baseRadius();
		record.stage = request.startStage() == null ? IncidentStage.INITIAL : request.startStage();
		record.sourceKind = sourceKind;
		record.objectTypeId = sourceKind == SourceKind.OBJECT ? request.objectTypeId() : null;
		record.objectGrade = sourceKind == SourceKind.OBJECT ? grade : null;
		record.sourcePos = sourceKind == SourceKind.OBJECT ? record.center : null;
		record.templateId = template.id();
		record.params = params;
		data().put(record);
		// The INITIAL delta is emitted even for a no-op sink so production and tests share one path.
		worldSink.applyStageDelta(activeLevel, record, IncidentStage.INITIAL, record.stage);
		return record;
	}

	public static UUID spawnObject(ServerLevel level, BlockPos pos, String typeId, int grade, long seed) {
		if (level == null || pos == null || objectSpawner == null) {
			return null;
		}
		activeLevel = level;
		UUID objectId = objectSpawner.spawn(level, pos, typeId, TemplateRollPolicy.clampGrade(grade), seed);
		if (objectId == null) {
			return null;
		}
		IncidentRecord record = spawn(new SpawnRequest(pos, level.dimension(), null, grade, seed,
				IncidentStage.INITIAL, typeId, SourceKind.OBJECT, null));
		record.objectInstanceId = objectId;
		record.sourcePos = pos.immutable();
		data().setDirty();
		return objectId;
	}

	public static InspectView inspect(UUID incidentId) {
		return toView(require(incidentId));
	}

	public static List<InspectView> list() {
		return data().incidents().values().stream()
				.sorted(Comparator.comparing(record -> record.id.toString()))
				.map(IncidentControl::toView)
				.toList();
	}

	/** Runtime seam: returns stable record references while keeping storage ownership in B1. */
	public static List<IncidentRecord> recordsForRuntime() {
		return List.copyOf(data().incidents().values());
	}

	public static IncidentStage setStage(UUID id, IncidentStage target) {
		IncidentRecord record = require(id);
		if (target == null || target.ordinal() <= record.stage.ordinal()) {
			return record.stage;
		}
		long now = currentGameTime();
		IncidentStage previous = record.stage;
		while (record.stage.ordinal() < target.ordinal()) {
			IncidentStage next = record.stage.next();
			worldSink.applyStageDelta(activeLevel, record, record.stage, next);
			record.transitions.add(new IncidentRecord.Transition(record.stage, next, now));
			record.stage = next;
		}
		record.lastUpdateGameTime = now;
		data().setDirty();
		return record.stage;
	}

	/** Adds logical age then executes the same transition engine used by catch-up. */
	public static long advance(UUID id, long ticks) {
		IncidentRecord record = require(id);
		long before = record.ageTicks(currentGameTime());
		long safeTicks = Math.max(0L, ticks);
		long target = Long.MAX_VALUE - before < safeTicks ? Long.MAX_VALUE : before + safeTicks;
		advanceTo(record, target);
		return target;
	}

	/** One transition engine for live ticking, explicit advance and offline catch-up. */
	public static void advanceTo(IncidentRecord record, long targetAgeTicks) {
		if (record == null) {
			return;
		}
		syncDwellCenter(record);
		long now = currentGameTime();
		long before = record.ageTicks(now);
		long target = Math.max(before, targetAgeTicks);
		long naturalAge = Math.max(0L, safeSubtract(now, record.createdGameTime));
		record.bonusAgeTicks = Math.max(0L, safeSubtract(target, naturalAge));
		double speed = record.params == null ? 1.0 : record.params.escalationSpeedMul();
		for (IncidentStage next : StagePolicy.transitionsBetween(record.stage, before, target, speed)) {
			IncidentStage previous = record.stage;
			worldSink.applyStageDelta(activeLevel, record, previous, next);
			record.stage = next;
			record.transitions.add(new IncidentRecord.Transition(previous, next, now));
		}
		record.lastUpdateGameTime = now;
		data().setDirty();
	}

	public static void escalate(UUID id, double multiplier) {
		IncidentRecord record = require(id);
		if (!Double.isFinite(multiplier) || multiplier <= 0.0 || record.params == null) {
			return;
		}
		IncidentParams p = record.params;
		record.params = new IncidentParams(p.zoneShape(), p.baseRadius(), p.curseWeights(), p.atmosphereId(),
				p.localGoals(), p.escalationSpeedMul() * multiplier, p.ignoreShelter(), p.secondaryAtCritical(),
				p.dwellTicksRequired());
		data().setDirty();
	}

	public static SealAttempt seal(UUID id, int sealTier) {
		IncidentRecord record = require(id);
		int grade = record.objectGrade == null ? 3 : TemplateRollPolicy.clampGrade(record.objectGrade);
		int required = Math.max(SealPolicy.requiredTier(grade), TemplateRollPolicy.sealDifficulty(grade));
		int tier = Math.max(0, Math.min(SealPolicy.MAX_TIER, sealTier));
		if (tier < required) {
			record.sealFailures++;
			applySealState(record);
			data().setDirty();
			return new SealAttempt(false, required, "insufficient_tier");
		}
		if (record.sealed) {
			return new SealAttempt(true, required, "already_sealed");
		}
		record.sealed = true;
		record.sealTier = tier;
		record.sealIntegrity = SealPolicy.integrityMax(tier);
		applySealState(record);
		data().setDirty();
		worldSink.onSealed(activeLevel, record);
		return new SealAttempt(true, required, "sealed");
	}

	public static boolean unseal(UUID id) {
		IncidentRecord record = require(id);
		if (!record.sealed) {
			return false;
		}
		record.sealed = false;
		applySealState(record);
		data().setDirty();
		worldSink.onUnsealed(activeLevel, record);
		return true;
	}

	public static int damageSeal(UUID id, int amount) {
		IncidentRecord record = require(id);
		if (!record.sealed || amount <= 0) {
			return Math.max(0, record.sealIntegrity);
		}
		int before = record.sealIntegrity;
		long rollSeed = record.seed ^ ((long) record.sealFailures << 32) ^ before;
		boolean catastrophic = SealPolicy.maybeCatastrophicFail(RandomSource.create(rollSeed), before);
		record.sealIntegrity = catastrophic ? 0 : Math.max(0, before - amount);
		if (catastrophic || record.sealIntegrity == 0) {
			record.sealed = false;
			record.sealFailures++;
			worldSink.onSealBroken(activeLevel, record);
		}
		applySealState(record);
		data().setDirty();
		return record.sealIntegrity;
	}

	public static void relocate(UUID id, BlockPos newCenter) {
		if (newCenter == null) {
			throw new IllegalArgumentException("new center is required");
		}
		IncidentRecord record = require(id);
		BlockPos oldCenter = record.center;
		if (oldCenter != null && !oldCenter.equals(newCenter)) {
			record.scars.add(oldCenter.immutable());
		}
		record.center = newCenter.immutable();
		record.sourcePos = newCenter.immutable();
		record.dwellAnchor = newCenter.immutable();
		record.dwellTicks = 0L;
		// Dependent centres stop at the old site; self-sustaining nodes survive.
		record.secondaries.removeIf(node -> !node.selfSustaining());
		data().setDirty();
		worldSink.onRelocated(activeLevel, record, oldCenter);
	}

	public static SecondaryNode forceSecondary(UUID id, BlockPos pos) {
		IncidentRecord record = require(id);
		for (SecondaryNode existing : record.secondaries) {
			if (!existing.selfSustaining()) {
				return existing;
			}
		}
		BlockPos center = pos == null ? record.center : pos.immutable();
		SecondaryNode node = new SecondaryNode(UUID.randomUUID(), center, Math.max(1.0, record.radius * 0.60),
				currentGameTime(), true);
		record.secondaries.add(node);
		data().setDirty();
		return node;
	}

	public static void identify(UUID id, KnowledgeLevel level) {
		IncidentRecord record = require(id);
		record.knowledge = level == null ? KnowledgeLevel.UNKNOWN : level;
		applySealState(record);
		data().setDirty();
	}

	public static void cleanup(UUID id) {
		IncidentRecord record = require(id);
		record.scarred = true;
		data().setDirty();
	}

	public static long reseed(UUID id, long newSeed) {
		IncidentRecord record = require(id);
		record.seed = newSeed;
		data().setDirty();
		return newSeed;
	}

	public static void catchUp(ServerLevel overworld) {
		if (overworld == null) {
			return;
		}
		activeLevel = overworld;
		long now = overworld.getGameTime();
		for (IncidentRecord record : recordsForRuntime()) {
			advanceTo(record, record.ageTicks(now));
		}
	}

	public static long cursedPressure() {
		return data().pressure();
	}

	public static long addPressure(long delta) {
		return data().addPressure(delta);
	}

	public static void resetPressure() {
		data().setPressure(0L);
	}

	/** Clears runtime bindings at SERVER_STOPPING; durable state remains in SavedData. */
	public static void clearRuntimeState() {
		activeLevel = null;
		worldSink = IncidentWorldSink.NOOP;
		dwellProvider = DwellProvider.NONE;
		objectSpawner = null;
		boundStore = new IncidentSavedData();
		storeSupplier = () -> boundStore;
	}

	private static IncidentSavedData data() {
		return boundStore;
	}


	private static IncidentRecord require(UUID id) {
		IncidentRecord record = data().get(id);
		if (record == null) {
			throw new IncidentNotFoundException(id);
		}
		return record;
	}

	private static long currentGameTime() {
		return activeLevel == null ? DEFAULT_GAME_TIME : activeLevel.getGameTime();
	}

	private static void applySealState(IncidentRecord record) {
		if (record.sourceKind == SourceKind.OBJECT && record.objectInstanceId != null) {
			dwellProvider.applySealState(record.objectInstanceId, record.sealed, record.sealTier,
					record.sealIntegrity, record.knowledge);
		}
	}

	private static void syncDwellCenter(IncidentRecord record) {
		if (record.sourceKind != SourceKind.OBJECT || record.objectInstanceId == null
				|| record.sealed || dwellProvider == DwellProvider.NONE
				|| dwellProvider.isSealed(record.objectInstanceId)) {
			return;
		}
		BlockPos dwell = dwellProvider.dwellCenterOf(record.objectInstanceId);
		if (dwell == null || dwell.equals(record.center)) {
			return;
		}
		BlockPos oldCenter = record.center;
		if (oldCenter != null) {
			record.scars.add(oldCenter.immutable());
		}
		record.center = dwell.immutable();
		record.sourcePos = dwell.immutable();
		record.dwellAnchor = dwell.immutable();
		record.dwellTicks = 0L;
		record.secondaries.removeIf(node -> !node.selfSustaining());
		worldSink.onRelocated(activeLevel, record, oldCenter);
		data().setDirty();
	}

	private static long safeSubtract(long left, long right) {
		try {
			return Math.subtractExact(left, right);
		} catch (ArithmeticException overflow) {
			return left >= right ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}

	private static InspectView toView(IncidentRecord record) {
		IncidentParams params = record.params == null ? new IncidentParams(
				"sphere", 0.0, Map.of(), "", List.of(), 1.0, false, false, 24_000L) : record.params;
		Map<String, Long> counters = new LinkedHashMap<>();
		counters.put("blocks_changed", record.counters.blocksChanged);
		counters.put("curses_spawned", record.counters.cursesSpawned);
		counters.put("animals_culled", record.counters.animalsCulled);
		counters.put("chunk_edits_deferred", record.counters.chunkEditsDeferred);
		List<String> transitionLog = record.transitions.stream()
				.map(value -> value.from().wireName() + "->" + value.to().wireName() + "@" + value.gameTime())
				.toList();
		return new InspectView(record.id, record.seed, record.templateId, record.stage, record.scarred,
				record.ageTicks(currentGameTime()), record.createdGameTime, record.lastUpdateGameTime,
				record.center, record.radius, params.zoneShape(), record.sourceKind, record.objectInstanceId,
				record.objectTypeId, record.objectGrade, record.sourcePos, record.sourceContainer,
				Map.copyOf(params.curseWeights()), params.atmosphereId(), List.copyOf(params.localGoals()),
				params.ignoreShelter(), record.sealed, record.sealIntegrity, record.sealTier, record.sealFailures,
				record.knowledge.wireName(), List.copyOf(record.secondaries), record.dependentCenterCount(),
				List.copyOf(record.scars), Map.copyOf(counters), transitionLog);
	}
}
