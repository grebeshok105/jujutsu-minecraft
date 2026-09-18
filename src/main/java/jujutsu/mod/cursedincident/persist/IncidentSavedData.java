package jujutsu.mod.cursedincident.persist;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import jujutsu.mod.cursedincident.IncidentRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One overworld-owned durable registry. Corrupt individual records are dropped so a
 * damaged entry cannot make an entire world unopenable (R60).
 */
public final class IncidentSavedData extends SavedData {
	private static final Logger LOGGER = LoggerFactory.getLogger("jujutsumod/incidents");

	public static final Codec<IncidentSavedData> CODEC = Codec.of(
			IncidentSavedData::encode,
			IncidentSavedData::decode,
			"IncidentSavedData");

	/** The storage stem is deliberately underscore-separated for Windows filenames. */
	public static final SavedDataType<IncidentSavedData> TYPE = new SavedDataType<>(
			"jujutsumod_incidents", IncidentSavedData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, IncidentRecord> incidents;
	private long pressure;
	private boolean loading;

	private final class DirtyIncidentMap extends LinkedHashMap<UUID, IncidentRecord> {
		@Override
		public IncidentRecord put(UUID id, IncidentRecord record) {
			IncidentRecord previous = super.put(id, record);
			if (!loading) {
				setDirty();
			}
			return previous;
		}

		@Override
		public IncidentRecord remove(Object id) {
			IncidentRecord previous = super.remove(id);
			if (!loading && previous != null) {
				setDirty();
			}
			return previous;
		}

		@Override
		public void clear() {
			boolean changed = !isEmpty();
			super.clear();
			if (!loading && changed) {
				setDirty();
			}
		}
	}

	public IncidentSavedData() {
		this(Map.of(), 0L);
	}

	public IncidentSavedData(Map<UUID, IncidentRecord> incidents, long pressure) {
		this.incidents = new DirtyIncidentMap();
		loading = true;
		if (incidents != null) {
			for (Map.Entry<UUID, IncidentRecord> entry : incidents.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) {
					this.incidents.put(entry.getKey(), entry.getValue());
				}
			}
		}
		loading = false;
		this.pressure = Math.max(0L, pressure);
	}

	public static IncidentSavedData get(ServerLevel overworld) {
		if (overworld == null) {
			throw new IllegalArgumentException("overworld must not be null");
		}
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Mutable registry view used by the server-side control/runtime seam. */
	public Map<UUID, IncidentRecord> incidents() {
		return incidents;
	}

	public IncidentRecord get(UUID id) {
		return id == null ? null : incidents.get(id);
	}

	public void put(IncidentRecord record) {
		if (record == null || record.id == null) {
			throw new IllegalArgumentException("incident record/id must not be null");
		}
		incidents.put(record.id, record);
		setDirty();
	}

	public IncidentRecord remove(UUID id) {
		IncidentRecord removed = id == null ? null : incidents.remove(id);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	public void clearIncidents() {
		if (!incidents.isEmpty()) {
			incidents.clear();
			setDirty();
		}
	}

	public long pressure() {
		return pressure;
	}

	public void setPressure(long pressure) {
		long next = Math.max(0L, pressure);
		if (this.pressure != next) {
			this.pressure = next;
			setDirty();
		}
	}

	public long addPressure(long delta) {
		long before = pressure;
		if (delta > 0L && Long.MAX_VALUE - pressure < delta) {
			pressure = Long.MAX_VALUE;
		} else if (delta < 0L && pressure < -delta) {
			pressure = 0L;
		} else {
			pressure = Math.max(0L, pressure + delta);
		}
		if (before != pressure) {
			setDirty();
		}
		return pressure;
	}

	private static <T> DataResult<T> encode(IncidentSavedData data, DynamicOps<T> ops, T prefix) {
		RecordBuilder<T> root = ops.mapBuilder();
		RecordBuilder<T> records = ops.mapBuilder();
		for (Map.Entry<UUID, IncidentRecord> entry : data.incidents.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			records.add(entry.getKey().toString(), entry.getValue(), IncidentCodec.CODEC);
		}
		root.add(IncidentNbt.INCIDENTS, records.build(ops.empty()));
		root.add(IncidentNbt.PRESSURE, data.pressure, Codec.LONG);
		return root.build(prefix);
	}

	private static <T> DataResult<Pair<IncidentSavedData, T>> decode(DynamicOps<T> ops, T input) {
		return ops.getMap(input).map(root -> {
			Map<UUID, IncidentRecord> decoded = new LinkedHashMap<>();
			T recordsValue = root.get(IncidentNbt.INCIDENTS);
			if (recordsValue != null) {
				ops.getMap(recordsValue).result().ifPresent(records -> decodeRecords(ops, records, decoded));
			}
			long pressure = readLong(ops, root, IncidentNbt.PRESSURE, 0L);
			return new Pair<>(new IncidentSavedData(decoded, pressure), input);
		});
	}

	private static <T> void decodeRecords(
			DynamicOps<T> ops, MapLike<T> records, Map<UUID, IncidentRecord> target) {
		records.entries().forEach(entry -> {
			String rawId = ops.getStringValue(entry.getFirst()).result().orElse("");
			UUID id;
			try {
				id = UUID.fromString(rawId);
			} catch (IllegalArgumentException ex) {
				LOGGER.warn("Dropping incident with invalid id {}", rawId);
				return;
			}
			IncidentCodec.CODEC.parse(ops, entry.getSecond()).result().ifPresentOrElse(
				record -> {
					if (!id.equals(record.id)) {
						LOGGER.warn("Dropping incident {} with mismatched record id {}", id, record.id);
						return;
					}
					target.put(id, record);
				},
				() -> LOGGER.warn("Dropping corrupt incident record {}", id));
		});
	}

	private static <T> long readLong(DynamicOps<T> ops, MapLike<T> map, String key, long fallback) {
		T value = map.get(key);
		if (value == null) {
			return fallback;
		}
		return Codec.LONG.parse(ops, value).result().orElse(fallback);
	}
}
