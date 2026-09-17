package jujutsu.mod.client.character.megumi.selector;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;
import jujutsu.mod.network.ShikigamiStatePayload;

/**
 * The client's read model of Megumi's roster: what the server last said, plus the one optimistic edit
 * a click makes before the server answers.
 *
 * <p>The server is authoritative and this is a cache, so both halves are cheap to reason about. A
 * snapshot replaces every field at once ({@link #apply}); an optimistic {@link #markSelected} only
 * moves the active marker, because the click's other consequences — a cooldown starting, a pack
 * being swept — are decisions only the server may make, and the next push corrects the cache anyway.
 * A click the server refuses simply never lands here.
 *
 * <p>Lives under the vessel's client package rather than beside the strip: it names the roster enum,
 * and the shared client packages may not. Its receiver and its disconnect clear are registered by the
 * vessel's client definition, next to the rest of the vessel's wiring.
 *
 * <p>Fields are written on the client thread and read on the render path, which are the same thread;
 * the arrays are still replaced wholesale rather than mutated so a half-updated snapshot can never be
 * read.
 */
public final class ClientMegumiShikigamiState {
	private static final int SLOTS = MegumiShikigami.values().length;
	/** All slots, nothing cooling: what the cache holds before the first snapshot arrives. */
	private static final byte[] NO_STATE = readyCodes();

	private static volatile MegumiShikigami selected = MegumiShikigami.DOGS;
	private static volatile byte[] stateCodes = NO_STATE;
	private static volatile long[] cooldownUntilGameTimes = new long[SLOTS];

	private ClientMegumiShikigamiState() {}

	/** Replaces the cache with the server's snapshot. */
	public static void apply(ShikigamiStatePayload payload) {
		selected = byIdOrDefault(payload.selectedId());
		stateCodes = payload.stateCodes().clone();
		cooldownUntilGameTimes = payload.cooldownUntilGameTimes().clone();
	}

	/** Optimistic edit for a click the client just sent; the next push is authoritative. */
	public static void markSelected(MegumiShikigami type) {
		if (type != null) {
			selected = type;
		}
	}

	public static MegumiShikigami selected() {
		return selected;
	}

	public static MegumiShikigamiSlotState stateOf(MegumiShikigami type) {
		int index = type.ordinal();
		byte[] codes = stateCodes;
		if (index >= codes.length) {
			return MegumiShikigamiSlotState.READY;
		}
		int code = codes[index];
		MegumiShikigamiSlotState[] states = MegumiShikigamiSlotState.values();
		return code >= 0 && code < states.length ? states[code] : MegumiShikigamiSlotState.READY;
	}

	/** Ticks left before this type is selectable again, against the client's own game clock. */
	public static int cooldownRemainingTicks(MegumiShikigami type) {
		int index = type.ordinal();
		long[] until = cooldownUntilGameTimes;
		if (index >= until.length || until[index] <= 0L) {
			return 0;
		}
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return 0;
		}
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, until[index] - level.getGameTime()));
	}

	/** Drops everything the server told us; wired to disconnect, so no world's markers survive into the next. */
	public static void clear() {
		selected = MegumiShikigami.DOGS;
		stateCodes = NO_STATE;
		cooldownUntilGameTimes = new long[SLOTS];
	}

	private static MegumiShikigami byIdOrDefault(String id) {
		if (id != null) {
			for (MegumiShikigami type : MegumiShikigami.values()) {
				if (type.id().equals(id)) {
					return type;
				}
			}
		}
		return MegumiShikigami.DOGS;
	}

	private static byte[] readyCodes() {
		byte[] codes = new byte[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			codes[i] = (byte) MegumiShikigamiSlotState.READY.ordinal();
		}
		return codes;
	}
}
