package jujutsu.mod.cursedincident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jujutsu.mod.cursedincident.persist.IncidentSavedData;
import jujutsu.mod.cursedincident.policy.SealPolicy;
import jujutsu.mod.cursedincident.policy.SpawnRollPolicy;
import jujutsu.mod.cursedincident.policy.StagePolicy;
import jujutsu.mod.cursedincident.policy.TemplateRollPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
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
			Double radius, Long dwellTicksRequired) {
		/** Backward-compatible form without the dwell override. */
		public SpawnRequest(BlockPos center, ResourceKey<Level> dimension, String templateId,
				Integer grade, Long seed, IncidentStage startStage, String objectTypeId,
				SourceKind sourceKind, Double radius) {
			this(center, dimension, templateId, grade, seed, startStage, objectTypeId, sourceKind,
					radius, null);
		}
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

	private static IncidentSavedData boundStore = new IncidentSavedData();
	private static final Map<UUID, UUID> INCIDENT_BY_OBJECT = new ConcurrentHashMap<>();
	private static IncidentWorldSink worldSink = IncidentWorldSink.NOOP;
	private static DwellProvider dwellProvider = DwellProvider.NONE;
	private static ObjectSpawner objectSpawner;
	private static ServerLevel activeLevel;
	private static MinecraftServer server;
	public static void bindServer(MinecraftServer value) {
		server = value;
	}

	public static void bindStore(Supplier<IncidentSavedData> store) {
		IncidentSavedData resolved = store == null ? null : store.get();
		boundStore = resolved == null ? new IncidentSavedData() : resolved;
		rebuildObjectIndex();
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
		if (request.dwellTicksRequired() != null && request.dwellTicksRequired() >= 0L) {
			params = new IncidentParams(params.zoneShape(), params.baseRadius(), params.curseWeights(), params.atmosphereId(),
					params.localGoals(), params.escalationSpeedMul(), params.ignoreShelter(), params.secondaryAtCritical(),
					request.dwellTicksRequired());
		}
		IncidentRecord record = new IncidentRecord();
		record.id = UUID.randomUUID();
		record.seed = seed;
		record.dimension = request.dimension() == null ? Level.OVERWORLD : request.dimension();
		record.createdGameTime = currentGameTime(record);
		record.lastUpdateGameTime = record.createdGameTime;
		record.center = request.center().immutable();
		record.radius = params.baseRadius();
		record.stage = request.startStage() == null ? IncidentStage.INITIAL : request.startStage();
		record.sourceKind = sourceKind;
		record.objectTypeId = sourceKind == SourceKind.OBJECT
				&& request.objectTypeId() != null && !request.objectTypeId().isBlank()
						? request.objectTypeId()
						: null;
		record.objectGrade = sourceKind == SourceKind.OBJECT ? grade : null;
		record.sourcePos = sourceKind == SourceKind.OBJECT ? record.center : null;
		record.templateId = template.id();
		record.params = params;
		if (sourceKind == SourceKind.OBJECT && objectSpawner != null) {
			ServerLevel level = levelFor(record);
			if (level != null) {
				record.objectInstanceId = objectSpawner.spawn(level, record.center, record.objectTypeId, grade, seed);
			}
		}
		data().put(record);
		if (record.objectInstanceId != null) {
			INCIDENT_BY_OBJECT.put(record.objectInstanceId, record.id);
		}
		// The INITIAL delta is emitted even for a no-op sink so production and tests share one path.
		worldSink.applyStageDelta(levelFor(record), record, IncidentStage.INITIAL, record.stage);
		return record;
	}

	public static UUID spawnObject(ServerLevel level, BlockPos pos, String typeId, int grade, long seed) {
		if (level == null || pos == null || objectSpawner == null) {
			return null;
		}
		activeLevel = level;
		IncidentRecord record = spawn(new SpawnRequest(pos, level.dimension(), null, grade, seed,
				IncidentStage.INITIAL, typeId, SourceKind.OBJECT, null));
		if (record.objectInstanceId == null) {
			data().remove(record.id);
			return null;
		}
		return record.objectInstanceId;
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

	/** The incident whose source is this object instance, or null (R43/R44 dwell lookup). */
	public static IncidentRecord recordForObject(UUID objectInstanceId) {
		if (objectInstanceId == null) {
			return null;
		}
		UUID incidentId = INCIDENT_BY_OBJECT.get(objectInstanceId);
		if (incidentId != null) {
			return data().get(incidentId);
		}
		// Records whose objectInstanceId was assigned post-spawn (tests, legacy loads)
		// bypass the index — fall back to a scan and heal the index.
		for (IncidentRecord record : data().incidents().values()) {
			if (objectInstanceId.equals(record.objectInstanceId)) {
				INCIDENT_BY_OBJECT.put(objectInstanceId, record.id);
				return record;
			}
		}
		return null;
	}

	public static IncidentStage setStage(UUID id, IncidentStage target) {
		IncidentRecord record = require(id);
		if (target == null || target.ordinal() <= record.stage.ordinal()) {
			return record.stage;
		}
		long now = currentGameTime(record);
		while (record.stage.ordinal() < target.ordinal()) {
			applyTransition(record, record.stage.next(), now);
		}
		record.lastUpdateGameTime = now;
		data().setDirty();
		return record.stage;
	}

	/** Adds logical age then executes the same transition engine used by catch-up. */
	public static long advance(UUID id, long ticks) {
		IncidentRecord record = require(id);
		long before = record.ageTicks(currentGameTime(record));
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
		long now = currentGameTime(record);
		long before = record.ageTicks(now);
		long target = Math.max(before, targetAgeTicks);
		long naturalAge = Math.max(0L, safeSubtract(now, record.createdGameTime));
		record.bonusAgeTicks = Math.max(0L, safeSubtract(target, naturalAge));
		double speed = record.params == null ? 1.0 : record.params.escalationSpeedMul();
		for (IncidentStage next : StagePolicy.transitionsBetween(record.stage, before, target, speed)) {
			applyTransition(record, next, now);
		}
		record.lastUpdateGameTime = now;
		data().setDirty();
	}

	private static void applyTransition(IncidentRecord record, IncidentStage next, long now) {
		IncidentStage previous = record.stage;
		worldSink.applyStageDelta(levelFor(record), record, previous, next);
		record.stage = next;
		record.transitions.add(new IncidentRecord.Transition(previous, next, now));
		if (next == IncidentStage.CRITICAL && record.params != null
				&& record.params.secondaryAtCritical()
				&& record.dependentCenterCount() < MAX_DEPENDENT_CENTERS) {
			record.secondaries.add(new SecondaryNode(UUID.randomUUID(),
					record.center == null ? BlockPos.ZERO : record.center,
					Math.max(1.0, record.radius * 0.60), now, true));
		}
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
		worldSink.onSealed(levelFor(record), record);
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
		worldSink.onUnsealed(levelFor(record), record);
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
			worldSink.onSealBroken(levelFor(record), record);
		}
		applySealState(record);
		data().setDirty();
		return record.sealIntegrity;
	}

	/** Reconciles the durable record with the authoritative physical-object component. */
	public static void syncSealFromComponent(UUID objectInstanceId) {
		if (objectInstanceId == null || dwellProvider == DwellProvider.NONE) {
			return;
		}
		IncidentRecord record = recordForObject(objectInstanceId);
		if (record == null || record.sourceKind != SourceKind.OBJECT) {
			return;
		}
		boolean componentSealed = dwellProvider.isSealed(objectInstanceId);
		if (record.sealed == componentSealed) {
			return;
		}
		record.sealed = componentSealed;
		data().setDirty();
		if (componentSealed) {
			worldSink.onSealed(levelFor(record), record);
		} else {
			worldSink.onSealBroken(levelFor(record), record);
		}
	}

	/** Marks the incident scarred when its destructible physical source disappears. */
	public static void onSourceDestroyed(UUID objectInstanceId) {
		IncidentRecord record = recordForObject(objectInstanceId);
		if (record != null) {
			cleanup(record.id);
		}
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
		worldSink.onRelocated(levelFor(record), record, oldCenter);
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
				currentGameTime(record), true);
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
		if (record.objectInstanceId != null) {
			INCIDENT_BY_OBJECT.remove(record.objectInstanceId, record.id);
		}
		worldSink.onCleanup(levelFor(record), record);
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
		server = null;
		worldSink = IncidentWorldSink.NOOP;
		// The object spawner and dwell tracker are process-wide seams; their own lifecycle hook
		// clears per-world state, so retaining the bindings keeps a second server start functional.
		INCIDENT_BY_OBJECT.clear();
		boundStore = new IncidentSavedData();
		activeLevel = null;
	}

	private static void rebuildObjectIndex() {
		INCIDENT_BY_OBJECT.clear();
		for (IncidentRecord record : boundStore.incidents().values()) {
			if (record != null && record.id != null && record.objectInstanceId != null) {
				INCIDENT_BY_OBJECT.put(record.objectInstanceId, record.id);
			}
		}
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

	private static long currentGameTime(IncidentRecord record) {
		ServerLevel level = levelFor(record);
		return level == null ? DEFAULT_GAME_TIME : level.getGameTime();
	}

	private static long currentGameTime() {
		return currentGameTime(null);
	}

	private static ServerLevel levelFor(IncidentRecord record) {
		if (server != null) {
			return server.getLevel(record == null || record.dimension == null ? Level.OVERWORLD : record.dimension);
		}
		return activeLevel;
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
		BlockPos container = dwellProvider.containerOf(record.objectInstanceId);
		BlockPos nextContainer = container == null ? null : container.immutable();
		boolean containerChanged = record.sourceContainer == null
				? nextContainer != null
				: !record.sourceContainer.equals(nextContainer);
		record.sourceContainer = nextContainer;
		if (dwell == null || dwell.equals(record.center)) {
			if (containerChanged) {
				data().setDirty();
			}
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
		worldSink.onRelocated(levelFor(record), record, oldCenter);
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
				record.ageTicks(currentGameTime(record)), record.createdGameTime, record.lastUpdateGameTime,
				record.center, record.radius, params.zoneShape(), record.sourceKind, record.objectInstanceId,
				record.objectTypeId, record.objectGrade, record.sourcePos, record.sourceContainer,
				Map.copyOf(params.curseWeights()), params.atmosphereId(), List.copyOf(params.localGoals()),
				params.ignoreShelter(), record.sealed, record.sealIntegrity, record.sealTier, record.sealFailures,
				record.knowledge.wireName(), List.copyOf(record.secondaries), record.dependentCenterCount(),
				List.copyOf(record.scars), Map.copyOf(counters), transitionLog);
	}
}
