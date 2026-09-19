package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * The authoritative snapshot the server pushes to the owner: which shikigami is active, and what state
 * each roster slot is in.
 *
 * <p><b>Parallel arrays, indexed by the roster's own constant order.</b> {@code stateCodes[i]} is the
 * ordinal of the slot-state constant for the i-th roster entry and {@code cooldownUntilGameTimes[i]}
 * is the game time that entry becomes ready (0 when it is not cooling). The ids, the state names and
 * the roster itself stay out of this file: the shared network package may not name a vessel's types,
 * so the snapshot is deliberately dumb bytes plus two numbers. That keeps {@code CharacterDefinition}
 * the only place that knows what a slot means.
 *
 * <p>{@code gameTime} is the server clock the deadlines were computed against, so the client resolves
 * remaining time against the clock its own level carries rather than trusting arrival latency.
 */
public record ShikigamiStatePayload(String selectedId, byte[] stateCodes, long[] cooldownUntilGameTimes,
		long gameTime) implements CustomPacketPayload {
	/** Every legal roster id is a single short word; the cap is part of the wire format. */
	public static final int MAX_ID_LENGTH = 16;
	/**
	 * Allocation bound for a decoded snapshot, not a roster size: the shared package cannot name the
	 * roster, and a count outside this is a corrupt or hostile buffer rather than a bigger roster.
	 */
	public static final int MAX_SLOTS = 16;
	public static final Type<ShikigamiStatePayload> TYPE = new Type<>(JujutsuMod.id("shikigami_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShikigamiStatePayload> STREAM_CODEC = CustomPacketPayload.codec(
			ShikigamiStatePayload::write, ShikigamiStatePayload::read);

	public ShikigamiStatePayload {
		// The arrays are handed to a codec and later read on the client thread; copying at the boundary
		// keeps the producer's array from being written through after the snapshot was built.
		stateCodes = stateCodes.clone();
		cooldownUntilGameTimes = cooldownUntilGameTimes.clone();
	}

	private static ShikigamiStatePayload read(RegistryFriendlyByteBuf buffer) {
		String selectedId = buffer.readUtf(MAX_ID_LENGTH);
		int codes = slotCount(buffer, "state codes");
		byte[] stateCodes = new byte[codes];
		for (int i = 0; i < codes; i++) {
			stateCodes[i] = buffer.readByte();
		}
		int deadlines = slotCount(buffer, "cooldown deadlines");
		long[] cooldownUntilGameTimes = new long[deadlines];
		for (int i = 0; i < deadlines; i++) {
			cooldownUntilGameTimes[i] = buffer.readLong();
		}
		return new ShikigamiStatePayload(selectedId, stateCodes, cooldownUntilGameTimes, buffer.readLong());
	}

	private static int slotCount(RegistryFriendlyByteBuf buffer, String what) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_SLOTS) {
			throw new IllegalArgumentException("Invalid shikigami " + what + " count: " + count);
		}
		return count;
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(selectedId, MAX_ID_LENGTH);
		buffer.writeVarInt(stateCodes.length);
		for (byte code : stateCodes) {
			buffer.writeByte(code);
		}
		buffer.writeVarInt(cooldownUntilGameTimes.length);
		for (long readyAt : cooldownUntilGameTimes) {
			buffer.writeLong(readyAt);
		}
		buffer.writeLong(gameTime);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
