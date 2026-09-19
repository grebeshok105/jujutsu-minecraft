package jujutsu.mod.cursedincident.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jujutsu.mod.cursedincident.IncidentParams;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.KnowledgeLevel;
import jujutsu.mod.cursedincident.SecondaryNode;
import jujutsu.mod.cursedincident.SourceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


/** Codec for the complete mutable incident record. Unknown enum strings use safe defaults. */
public final class IncidentCodec {
	private static final Codec<Map<String, Integer>> INT_MAP_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.INT);
	private static final Codec<ResourceKey<Level>> DIMENSION_CODEC = ResourceLocation.CODEC.xmap(
			location -> ResourceKey.create(Registries.DIMENSION, location), ResourceKey::location);

	private static final Codec<IncidentParams> PARAMS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf(IncidentNbt.ZONE_SHAPE, "sphere").forGetter(IncidentParams::zoneShape),
			Codec.DOUBLE.optionalFieldOf(IncidentNbt.BASE_RADIUS, 0.0).forGetter(IncidentParams::baseRadius),
			INT_MAP_CODEC.optionalFieldOf(IncidentNbt.CURSE_WEIGHTS, Map.of()).forGetter(IncidentParams::curseWeights),
			Codec.STRING.optionalFieldOf(IncidentNbt.ATMOSPHERE, "").forGetter(IncidentParams::atmosphereId),
			Codec.STRING.listOf().optionalFieldOf(IncidentNbt.LOCAL_GOALS, List.of()).forGetter(IncidentParams::localGoals),
			Codec.DOUBLE.optionalFieldOf(IncidentNbt.ESCALATION_SPEED, 1.0).forGetter(IncidentParams::escalationSpeedMul),
			Codec.BOOL.optionalFieldOf(IncidentNbt.IGNORE_SHELTER, false).forGetter(IncidentParams::ignoreShelter),
			Codec.BOOL.optionalFieldOf(IncidentNbt.SECONDARY_AT_CRITICAL, false).forGetter(IncidentParams::secondaryAtCritical),
			Codec.LONG.optionalFieldOf(IncidentNbt.DWELL_TICKS_REQUIRED, 24_000L).forGetter(IncidentParams::dwellTicksRequired)
	).apply(instance, IncidentParams::new));

	private static final Codec<SecondaryNode> SECONDARY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.STRING_CODEC.fieldOf(IncidentNbt.ID).forGetter(SecondaryNode::nodeId),
			BlockPos.CODEC.fieldOf(IncidentNbt.CENTER).forGetter(SecondaryNode::center),
			Codec.DOUBLE.fieldOf(IncidentNbt.RADIUS).forGetter(SecondaryNode::radius),
			Codec.LONG.fieldOf(IncidentNbt.CREATED).forGetter(SecondaryNode::createdGameTime),
			Codec.BOOL.fieldOf("self_sustaining").forGetter(SecondaryNode::selfSustaining),
			Codec.BOOL.optionalFieldOf(IncidentNbt.SCARRED, false).forGetter(SecondaryNode::scarred)
	).apply(instance, SecondaryNode::new));
	private static final Codec<IncidentRecord.PendingDelta> PENDING_DELTA_CODEC =
			RecordCodecBuilder.create(instance -> instance.group(
					Codec.STRING.optionalFieldOf(IncidentNbt.FROM, "initial")
							.forGetter(value -> value.from().wireName()),
					Codec.STRING.optionalFieldOf(IncidentNbt.TO, "initial")
							.forGetter(value -> value.to().wireName())
			).apply(instance, (from, to) -> new IncidentRecord.PendingDelta(
					IncidentStage.byNameOrDefault(from), IncidentStage.byNameOrDefault(to))));

	private static final Codec<IncidentRecord.PendingEdit> PENDING_EDIT_CODEC =
			RecordCodecBuilder.create(instance -> instance.group(
					BlockPos.CODEC.fieldOf(IncidentNbt.POS).forGetter(IncidentRecord.PendingEdit::pos),
					BlockState.CODEC.optionalFieldOf(IncidentNbt.BLOCK_STATE)
							.forGetter(value -> Optional.ofNullable(value.blockState())),
					Codec.BOOL.optionalFieldOf(IncidentNbt.DESTROY, false)
							.forGetter(IncidentRecord.PendingEdit::destroy),
					UUIDUtil.STRING_CODEC.optionalFieldOf(IncidentNbt.NODE_ID)
							.forGetter(value -> Optional.ofNullable(value.nodeId())),
					Codec.STRING.optionalFieldOf(IncidentNbt.PENDING_STAGE)
							.forGetter(value -> Optional.ofNullable(value.stage()).map(IncidentStage::wireName))
			).apply(instance, (pos, state, destroy, nodeId, stage) -> new IncidentRecord.PendingEdit(
					pos, state.orElse(null), destroy, nodeId.orElse(null),
					stage.map(IncidentStage::byNameOrDefault).orElse(null))));


	private static final Codec<IncidentRecord.Transition> TRANSITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf(IncidentNbt.FROM, "initial").forGetter(value -> value.from().wireName()),
			Codec.STRING.optionalFieldOf(IncidentNbt.TO, "initial").forGetter(value -> value.to().wireName()),
			Codec.LONG.optionalFieldOf(IncidentNbt.GAME_TIME, 0L).forGetter(IncidentRecord.Transition::gameTime)
	).apply(instance, (from, to, gameTime) -> new IncidentRecord.Transition(
			IncidentStage.byNameOrDefault(from), IncidentStage.byNameOrDefault(to), gameTime)));

	private record SealValues(boolean sealed, int integrity, int tier, int failures) {
		private static final Codec<SealValues> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf(IncidentNbt.SEALED, false).forGetter(SealValues::sealed),
				Codec.INT.optionalFieldOf(IncidentNbt.INTEGRITY, 0).forGetter(SealValues::integrity),
				Codec.INT.optionalFieldOf(IncidentNbt.TIER, 0).forGetter(SealValues::tier),
				Codec.INT.optionalFieldOf(IncidentNbt.FAILURES, 0).forGetter(SealValues::failures)
		).apply(instance, SealValues::new));
	}

	private record DwellValues(long ticks, BlockPos anchor) {
		private static final Codec<DwellValues> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.optionalFieldOf(IncidentNbt.TICKS, 0L).forGetter(DwellValues::ticks),
				BlockPos.CODEC.optionalFieldOf(IncidentNbt.ANCHOR, BlockPos.ZERO).forGetter(value ->
						value.anchor() == null ? BlockPos.ZERO : value.anchor())
		).apply(instance, DwellValues::new));
	}

	private record CounterValues(long blocksChanged, long cursesSpawned, long animalsCulled, long chunkEditsDeferred) {
		private static final Codec<CounterValues> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.optionalFieldOf(IncidentNbt.BLOCKS_CHANGED, 0L).forGetter(CounterValues::blocksChanged),
				Codec.LONG.optionalFieldOf(IncidentNbt.CURSES_SPAWNED, 0L).forGetter(CounterValues::cursesSpawned),
				Codec.LONG.optionalFieldOf(IncidentNbt.ANIMALS_CULLED, 0L).forGetter(CounterValues::animalsCulled),
				Codec.LONG.optionalFieldOf(IncidentNbt.CHUNK_EDITS_DEFERRED, 0L).forGetter(CounterValues::chunkEditsDeferred)
		).apply(instance, CounterValues::new));
	}

	public static final Codec<IncidentRecord> CODEC = Codec.of(
			IncidentCodec::encode,
			IncidentCodec::decode,
			"IncidentRecord");

	private IncidentCodec() {
	}

	private static <T> DataResult<T> encode(IncidentRecord record, DynamicOps<T> ops, T prefix) {
		if (record == null || !IncidentNbt.validId(record.id)) {
			return DataResult.error(() -> "incident record has no id");
		}
		RecordBuilder<T> builder = ops.mapBuilder();
		builder.add(IncidentNbt.ID, record.id, UUIDUtil.STRING_CODEC);
		builder.add(IncidentNbt.SEED, record.seed, Codec.LONG);
		builder.add(IncidentNbt.CREATED, record.createdGameTime, Codec.LONG);
		builder.add(IncidentNbt.LAST_UPDATE, record.lastUpdateGameTime, Codec.LONG);
			builder.add(IncidentNbt.LAST_PROCESSED_AGE, Math.max(0L, record.lastProcessedAgeTicks), Codec.LONG);

		builder.add(IncidentNbt.BONUS_AGE, record.bonusAgeTicks, Codec.LONG);
		builder.add(IncidentNbt.DIM, record.dimension == null ? Level.OVERWORLD : record.dimension, DIMENSION_CODEC);
		builder.add(IncidentNbt.CENTER, record.center == null ? BlockPos.ZERO : record.center, BlockPos.CODEC);
		builder.add(IncidentNbt.RADIUS, record.radius, Codec.DOUBLE);
		builder.add(IncidentNbt.STAGE, (record.stage == null ? IncidentStage.INITIAL : record.stage).wireName(), Codec.STRING);
		builder.add(IncidentNbt.SCARRED, record.scarred, Codec.BOOL);
		builder.add(IncidentNbt.SOURCE_KIND,
				(record.sourceKind == null ? SourceKind.FREE : record.sourceKind).wireName(), Codec.STRING);
		addOptional(builder, ops, IncidentNbt.OBJECT_ID, record.objectInstanceId, UUIDUtil.STRING_CODEC);
		addOptional(builder, ops, IncidentNbt.OBJECT_TYPE, record.objectTypeId, Codec.STRING);
		addOptional(builder, ops, IncidentNbt.OBJECT_GRADE, record.objectGrade, Codec.INT);
		addOptional(builder, ops, IncidentNbt.SOURCE_POS, record.sourcePos, BlockPos.CODEC);
		addOptional(builder, ops, IncidentNbt.SOURCE_CONTAINER, record.sourceContainer, BlockPos.CODEC);
		builder.add(IncidentNbt.TEMPLATE, record.templateId == null ? "" : record.templateId, Codec.STRING);
		builder.add(IncidentNbt.PARAMS, record.params == null ? defaultParams() : record.params, PARAMS_CODEC);
			builder.add(IncidentNbt.PENDING_DELTAS, List.copyOf(record.pendingDeltas), PENDING_DELTA_CODEC.listOf());
			builder.add(IncidentNbt.PENDING_EDITS, List.copyOf(record.pendingEdits), PENDING_EDIT_CODEC.listOf());

		builder.add(IncidentNbt.SECONDARIES, List.copyOf(record.secondaries), SECONDARY_CODEC.listOf());
		builder.add(IncidentNbt.TRANSITIONS, List.copyOf(record.transitions), TRANSITION_CODEC.listOf());
		builder.add(IncidentNbt.SCARS, List.copyOf(record.scars), BlockPos.CODEC.listOf());
		builder.add(IncidentNbt.SEAL,
				new SealValues(record.sealed, Math.max(0, record.sealIntegrity), Math.max(0, record.sealTier),
						Math.max(0, record.sealFailures)), SealValues.CODEC);
		builder.add(IncidentNbt.KNOWLEDGE,
				(record.knowledge == null ? KnowledgeLevel.UNKNOWN : record.knowledge).wireName(), Codec.STRING);
		builder.add(IncidentNbt.DWELL,
				new DwellValues(Math.max(0L, record.dwellTicks), record.dwellAnchor), DwellValues.CODEC);
		builder.add(IncidentNbt.COUNTERS,
				new CounterValues(record.counters.blocksChanged, record.counters.cursesSpawned,
						record.counters.animalsCulled, record.counters.chunkEditsDeferred), CounterValues.CODEC);
			builder.add(IncidentNbt.LAST_TOP_UP_GAME_TIME, record.lastTopUpGameTime, Codec.LONG);
			builder.add(IncidentNbt.LAST_CONTAINER_SCAN_GAME_TIME, record.lastContainerScanGameTime, Codec.LONG);
			builder.add(IncidentNbt.LAST_CULL_GAME_TIME, record.lastCullGameTime, Codec.LONG);
			builder.add(IncidentNbt.LAST_AMBIENT_GAME_TIME, record.lastAmbientGameTime, Codec.LONG);

		return builder.build(prefix);
	}

	private static <T, V> void addOptional(
			RecordBuilder<T> builder, DynamicOps<T> ops, String key, V value, Codec<V> codec) {
		if (value != null) {
			builder.add(key, value, codec);
		}
	}

	private static <T> DataResult<Pair<IncidentRecord, T>> decode(DynamicOps<T> ops, T input) {
		return ops.getMap(input)
				.flatMap(map -> decodeMap(ops, map))
				.map(record -> Pair.of(record, input));
	}

	private static <T> DataResult<IncidentRecord> decodeMap(DynamicOps<T> ops, MapLike<T> map) {
		T idValue = map.get(IncidentNbt.ID);
		if (idValue == null) {
			return DataResult.error(() -> "incident record is missing id");
		}
		DataResult<UUID> idResult = UUIDUtil.STRING_CODEC.parse(ops, idValue);
		if (idResult.result().isEmpty()) {
			return DataResult.error(() -> "incident record has invalid id");
		}
		IncidentRecord record = new IncidentRecord();
		record.id = idResult.result().orElseThrow();
		record.seed = read(ops, map, IncidentNbt.SEED, Codec.LONG, 0L);
		record.createdGameTime = read(ops, map, IncidentNbt.CREATED, Codec.LONG, 0L);
		record.lastUpdateGameTime = read(ops, map, IncidentNbt.LAST_UPDATE, Codec.LONG, record.createdGameTime);
		record.bonusAgeTicks = Math.max(0L, read(ops, map, IncidentNbt.BONUS_AGE, Codec.LONG, 0L));
		record.lastProcessedAgeTicks = Math.max(0L,
				read(ops, map, IncidentNbt.LAST_PROCESSED_AGE, Codec.LONG, 0L));

		record.dimension = read(ops, map, IncidentNbt.DIM, DIMENSION_CODEC, Level.OVERWORLD);
		record.center = read(ops, map, IncidentNbt.CENTER, BlockPos.CODEC, BlockPos.ZERO);
		record.radius = Math.max(0.0, read(ops, map, IncidentNbt.RADIUS, Codec.DOUBLE, 0.0));
		String stage = read(ops, map, IncidentNbt.STAGE, Codec.STRING, "initial");
		record.stage = IncidentStage.byNameOrDefault(stage);
		record.scarred = read(ops, map, IncidentNbt.SCARRED, Codec.BOOL, false);
		record.sourceKind = SourceKind.byNameOrDefault(read(ops, map, IncidentNbt.SOURCE_KIND, Codec.STRING, "free"));
		record.objectInstanceId = readOptional(ops, map, IncidentNbt.OBJECT_ID, UUIDUtil.STRING_CODEC);
		record.objectTypeId = readOptional(ops, map, IncidentNbt.OBJECT_TYPE, Codec.STRING);
		record.objectGrade = readOptional(ops, map, IncidentNbt.OBJECT_GRADE, Codec.INT);
		record.sourcePos = readOptional(ops, map, IncidentNbt.SOURCE_POS, BlockPos.CODEC);
		record.sourceContainer = readOptional(ops, map, IncidentNbt.SOURCE_CONTAINER, BlockPos.CODEC);
		record.templateId = read(ops, map, IncidentNbt.TEMPLATE, Codec.STRING, "");
		record.params = read(ops, map, IncidentNbt.PARAMS, PARAMS_CODEC, defaultParams());
		record.secondaries.addAll(readList(ops, map, IncidentNbt.SECONDARIES, SECONDARY_CODEC));
		record.transitions.addAll(readList(ops, map, IncidentNbt.TRANSITIONS, TRANSITION_CODEC));
		record.pendingDeltas.addAll(readList(ops, map, IncidentNbt.PENDING_DELTAS, PENDING_DELTA_CODEC));
		record.pendingEdits.addAll(readList(ops, map, IncidentNbt.PENDING_EDITS, PENDING_EDIT_CODEC));

		record.scars.addAll(readList(ops, map, IncidentNbt.SCARS, BlockPos.CODEC));
		SealValues seal = read(ops, map, IncidentNbt.SEAL, SealValues.CODEC, new SealValues(false, 0, 0, 0));
		record.sealed = seal.sealed();
		record.sealIntegrity = Math.max(0, seal.integrity());
		record.sealTier = Math.max(0, seal.tier());
		record.sealFailures = Math.max(0, seal.failures());
		record.knowledge = KnowledgeLevel.byNameOrDefault(read(ops, map, IncidentNbt.KNOWLEDGE, Codec.STRING, "unknown"));
		DwellValues dwell = read(ops, map, IncidentNbt.DWELL, DwellValues.CODEC, new DwellValues(0L, null));
		record.dwellTicks = Math.max(0L, dwell.ticks());
		record.dwellAnchor = dwell.anchor();
		CounterValues counters = read(ops, map, IncidentNbt.COUNTERS, CounterValues.CODEC, new CounterValues(0, 0, 0, 0));
		record.counters.blocksChanged = Math.max(0L, counters.blocksChanged());
		record.counters.cursesSpawned = Math.max(0L, counters.cursesSpawned());
		record.counters.animalsCulled = Math.max(0L, counters.animalsCulled());
		record.counters.chunkEditsDeferred = Math.max(0L, counters.chunkEditsDeferred());
		record.lastTopUpGameTime = read(ops, map, IncidentNbt.LAST_TOP_UP_GAME_TIME, Codec.LONG, Long.MIN_VALUE);
		record.lastContainerScanGameTime = read(ops, map, IncidentNbt.LAST_CONTAINER_SCAN_GAME_TIME, Codec.LONG, Long.MIN_VALUE);
		record.lastCullGameTime = read(ops, map, IncidentNbt.LAST_CULL_GAME_TIME, Codec.LONG, Long.MIN_VALUE);
		record.lastAmbientGameTime = read(ops, map, IncidentNbt.LAST_AMBIENT_GAME_TIME, Codec.LONG, Long.MIN_VALUE);

		return DataResult.success(record);
	}

	private static <T, V> V read(DynamicOps<T> ops, MapLike<T> map, String key, Codec<V> codec, V fallback) {
		T value = map.get(key);
		if (value == null) {
			return fallback;
		}
		return codec.parse(ops, value).result().orElse(fallback);
	}

	private static <T, V> V readOptional(DynamicOps<T> ops, MapLike<T> map, String key, Codec<V> codec) {
		T value = map.get(key);
		return value == null ? null : codec.parse(ops, value).result().orElse(null);
	}

	private static <T, V> List<V> readList(DynamicOps<T> ops, MapLike<T> map, String key, Codec<V> codec) {
		T value = map.get(key);
		if (value == null) {
			return List.of();
		}
		return codec.listOf().parse(ops, value).result().orElse(List.of());
	}

	private static IncidentParams defaultParams() {
		return new IncidentParams("sphere", 0.0, Map.of(), "", List.of(), 1.0, false, false, 24_000L);
	}
}
