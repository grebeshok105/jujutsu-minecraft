package jujutsu.mod.cursedspirit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;

/**
 * Brain start rules and NBT round-trip (Block 3, Step 2). Entity-free: params come
 * straight from the profile, windows from explicit ticks.
 *
 * <p>Red-proofs: allow a second start per tick → cap red; drop the movement check →
 * mutex red; restore windows on load → transient-reset red; lose a cooldown on load →
 * cooldown-survival red.
 */
final class CursedSpiritAbilityBrainTest {
	private static final List<CursedSpiritAbilityId> TRIO = List.of(CursedSpiritAbilityId.DASH,
			CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.ARMOR);

	private static CursedSpiritAbilityBrain pinned() {
		CursedSpiritAbilityBrain brain = new CursedSpiritAbilityBrain();
		brain.forcePoolForTest(TRIO);
		return brain;
	}

	private static CursedSpiritAbilityParams dashParams() {
		return CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.DASH, CursedSpiritGrade.GRADE_5);
	}

	@Test
	void atMostOneNewStartPerTick() {
		CursedSpiritAbilityBrain brain = pinned();
		assertTrue(brain.tryStart(CursedSpiritAbilityId.DASH, 15, dashParams(), null, 1));
		assertFalse(brain.tryStart(CursedSpiritAbilityId.REGEN, 121,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.REGEN, CursedSpiritGrade.GRADE_5),
				null, 1));
		assertTrue(brain.tryStart(CursedSpiritAbilityId.REGEN, 122,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.REGEN, CursedSpiritGrade.GRADE_5),
				null, 2));
	}

	@Test
	void movementGroupIsMutuallyExclusive() {
		CursedSpiritAbilityBrain brain = pinned();
		brain.forcePoolForTest(List.of(CursedSpiritAbilityId.DASH,
				CursedSpiritAbilityId.GROUND_SLAM, CursedSpiritAbilityId.ARMOR));
		assertTrue(brain.tryStart(CursedSpiritAbilityId.DASH, 15, dashParams(), null, 1));
		assertTrue(brain.movementOwned(5));
		assertFalse(brain.tryStart(CursedSpiritAbilityId.GROUND_SLAM, 41,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.GROUND_SLAM,
						CursedSpiritGrade.GRADE_5),
				null, 2));
		brain.forceEnd(CursedSpiritAbilityId.DASH);
		assertFalse(brain.movementOwned(5));
	}

	@Test
	void clipGateBlocksClipPlayersWhileFearCasts() {
		CursedSpiritAbilityBrain brain = pinned();
		brain.forcePoolForTest(List.of(CursedSpiritAbilityId.FEAR,
				CursedSpiritAbilityId.ACID_SPIT, CursedSpiritAbilityId.ARMOR));
		assertTrue(brain.tryStart(CursedSpiritAbilityId.FEAR, 21,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.FEAR, CursedSpiritGrade.GRADE_5),
				null, 1));
		assertTrue(brain.attackClipOccupied(5));
		assertFalse(brain.tryStart(CursedSpiritAbilityId.ACID_SPIT, 11,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.ACID_SPIT,
						CursedSpiritGrade.GRADE_5),
				null, 2));
	}

	@Test
	void cooldownSurvivesWhileWindowsResetOnLoad() {
		CursedSpiritAbilityBrain brain = pinned();
		assertTrue(brain.tryStart(CursedSpiritAbilityId.DASH, 15, dashParams(), null, 1));
		MapValueOutput output = new MapValueOutput();
		brain.saveTo(output);

		CursedSpiritAbilityBrain loaded = pinned();
		assertTrue(loaded.loadFrom(new MapValueInput(output.strings, output.longs)));
		assertEquals(TRIO, loaded.pool());
		assertFalse(loaded.isActive(CursedSpiritAbilityId.DASH, 5));
		assertFalse(loaded.ready(CursedSpiritAbilityId.DASH, 5));
		assertTrue(loaded.ready(CursedSpiritAbilityId.DASH, 201));
	}

	@Test
	void invalidPoolFailsLoadAndKeepsCooldowns() {
		MapValueOutput output = new MapValueOutput();
		output.putString(CursedSpiritAbilityBrain.ABILITIES_TAG, "dash,banana");
		output.putLong(CursedSpiritAbilityBrain.READY_AT_PREFIX + "dash", 500L);
		CursedSpiritAbilityBrain brain = new CursedSpiritAbilityBrain();
		assertFalse(brain.loadFrom(new MapValueInput(output.strings, output.longs)));
		assertTrue(brain.pool().isEmpty());
		assertFalse(brain.ready(CursedSpiritAbilityId.DASH, 5));
	}

	@Test
	void forcedPoolRejectsAnythingButATrio() {
		CursedSpiritAbilityBrain brain = new CursedSpiritAbilityBrain();
		assertThrows(IllegalArgumentException.class,
				() -> brain.forcePoolForTest(List.of(CursedSpiritAbilityId.DASH)));
	}

	@Test
	void unpooledIdNeverStarts() {
		CursedSpiritAbilityBrain brain = pinned();
		assertFalse(brain.tryStart(CursedSpiritAbilityId.FEAR, 21,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.FEAR, CursedSpiritGrade.GRADE_5),
				null, 1));
	}

	/** In-memory {@link ValueOutput}: the brain only writes flat string/long keys. */
	static final class MapValueOutput implements ValueOutput {
		final Map<String, String> strings = new HashMap<>();
		final Map<String, Long> longs = new HashMap<>();

		@Override
		public <T> void store(String key, Codec<T> codec, T value) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public <T> void storeNullable(String key, Codec<T> codec, T value) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public <T> void store(MapCodec<T> codec, T value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void putBoolean(String key, boolean value) {
			longs.put(key, value ? 1L : 0L);
		}

		@Override
		public void putByte(String key, byte value) {
			longs.put(key, (long) value);
		}

		@Override
		public void putShort(String key, short value) {
			longs.put(key, (long) value);
		}

		@Override
		public void putInt(String key, int value) {
			longs.put(key, (long) value);
		}

		@Override
		public void putLong(String key, long value) {
			longs.put(key, value);
		}

		@Override
		public void putFloat(String key, float value) {
			longs.put(key, (long) value);
		}

		@Override
		public void putDouble(String key, double value) {
			longs.put(key, (long) value);
		}

		@Override
		public void putString(String key, String value) {
			strings.put(key, value);
		}

		@Override
		public void putIntArray(String key, int[] value) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public ValueOutput child(String key) {
			return this;
		}

		@Override
		public ValueOutputList childrenList(String key) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public <T> TypedOutputList<T> list(String key, Codec<T> codec) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public void discard(String key) {
			strings.remove(key);
			longs.remove(key);
		}

		@Override
		public boolean isEmpty() {
			return strings.isEmpty() && longs.isEmpty();
		}
	}

	/** In-memory {@link ValueInput} reading the maps above. */
	static final class MapValueInput implements ValueInput {
		private final Map<String, String> strings;
		private final Map<String, Long> longs;

		MapValueInput(Map<String, String> strings, Map<String, Long> longs) {
			this.strings = strings;
			this.longs = longs;
		}

		@Override
		public <T> Optional<T> read(String key, Codec<T> codec) {
			return Optional.empty();
		}

		@Override
		public <T> Optional<T> read(MapCodec<T> codec) {
			return Optional.empty();
		}

		@Override
		public Optional<ValueInput> child(String key) {
			return Optional.empty();
		}

		@Override
		public ValueInput childOrEmpty(String key) {
			return this;
		}

		@Override
		public Optional<ValueInputList> childrenList(String key) {
			return Optional.empty();
		}

		@Override
		public ValueInputList childrenListOrEmpty(String key) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public <T> Optional<TypedInputList<T>> list(String key, Codec<T> codec) {
			return Optional.empty();
		}

		@Override
		public <T> TypedInputList<T> listOrEmpty(String key, Codec<T> codec) {
			throw new UnsupportedOperationException(key);
		}

		@Override
		public boolean getBooleanOr(String key, boolean fallback) {
			return longs.getOrDefault(key, fallback ? 1L : 0L) != 0L;
		}

		@Override
		public byte getByteOr(String key, byte fallback) {
			return (byte) (long) longs.getOrDefault(key, (long) fallback);
		}

		@Override
		public int getShortOr(String key, short fallback) {
			return longs.getOrDefault(key, (long) fallback).intValue();
		}

		@Override
		public Optional<Integer> getInt(String key) {
			return Optional.ofNullable(longs.get(key)).map(Long::intValue);
		}

		@Override
		public int getIntOr(String key, int fallback) {
			return longs.getOrDefault(key, (long) fallback).intValue();
		}

		@Override
		public long getLongOr(String key, long fallback) {
			return longs.getOrDefault(key, fallback);
		}

		@Override
		public Optional<Long> getLong(String key) {
			return Optional.ofNullable(longs.get(key));
		}

		@Override
		public float getFloatOr(String key, float fallback) {
			return longs.getOrDefault(key, (long) fallback).floatValue();
		}

		@Override
		public double getDoubleOr(String key, double fallback) {
			return longs.getOrDefault(key, (long) fallback).doubleValue();
		}

		@Override
		public Optional<String> getString(String key) {
			return Optional.ofNullable(strings.get(key));
		}

		@Override
		public String getStringOr(String key, String fallback) {
			return strings.getOrDefault(key, fallback);
		}

		@Override
		public Optional<int[]> getIntArray(String key) {
			return Optional.empty();
		}

		@Override
		public HolderLookup.Provider lookup() {
			throw new UnsupportedOperationException();
		}
	}
}
