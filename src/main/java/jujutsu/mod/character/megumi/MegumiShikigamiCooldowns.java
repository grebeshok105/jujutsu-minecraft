package jujutsu.mod.character.megumi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The roster's per-type cooldown ledger: when each shikigami becomes selectable again, per owner.
 *
 * <p>Separate from {@code CharacterAbilityCooldowns} on purpose. That ledger answers "may this input
 * slot fire", is keyed by slot and gates the <em>summon</em>; this one answers "may this type be
 * selected", is keyed by roster entry and gates the <em>selection</em>. Sharing one map would mean a
 * cooling Nue refused every other slot too, and would make the selector's whole point — moving to a
 * type that is up while a different one recovers — impossible.
 *
 * <p>Writes happen at the same call sites that already charge the shared ability cooldown (recall,
 * death, expiry, the dog pack's own loss), from the fact the site already knows: the type being swept.
 * A deadline is stamped from the roster runtime's own clock, which it refreshes once per server tick —
 * the ledger has no level handle of its own, and reading the clock from a caller's level at one site
 * but stamping from another would be the same counter twice removed. {@link #observe(long)} is the
 * documented test seam for that clock; production feeds it from the server tick.
 */
public final class MegumiShikigamiCooldowns {
	private static final Map<UUID, Map<MegumiShikigami, Long>> READY_AT = new ConcurrentHashMap<>();

	/** The server's game time as of the last tick the roster runtime saw. */
	private static volatile long nowTicks;

	private MegumiShikigamiCooldowns() {}

	/** Records the server clock so {@link #start} can stamp a deadline. Called once per server tick. */
	static void observe(long gameTime) {
		// Monotone: a stale or out-of-order feed must never rewind the clock and shorten a cooldown.
		nowTicks = Math.max(nowTicks, gameTime);
	}

	/**
	 * Charges {@code ticks} of cooldown to one type. Never shortens an entry that is already cooling —
	 * the same non-shortening rule {@code CharacterAbilityCooldowns} applies through
	 * {@code MegumiCooldownPolicy.preservedRemaining}, so a pack that dies while its recall is still
	 * paying keeps the longer of the two.
	 */
	public static void start(UUID owner, MegumiShikigami type, int ticks) {
		if (owner == null || type == null || ticks <= 0) {
			return;
		}
		READY_AT.computeIfAbsent(owner, key -> new ConcurrentHashMap<>())
				.merge(type, nowTicks + ticks, Math::max);
	}

	public static boolean isCooling(UUID owner, MegumiShikigami type, long now) {
		return remainingTicks(owner, type, now) > 0;
	}

	/** Ticks until this type is selectable again, measured against the caller's clock. 0 when it is up. */
	public static int remainingTicks(UUID owner, MegumiShikigami type, long now) {
		if (owner == null || type == null) {
			return 0;
		}
		Map<MegumiShikigami, Long> perType = READY_AT.get(owner);
		if (perType == null) {
			return 0;
		}
		Long readyAt = perType.get(type);
		if (readyAt == null) {
			return 0;
		}
		long remaining = readyAt - now;
		if (remaining <= 0) {
			perType.remove(type, readyAt);
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, remaining);
	}

	/** Drops one owner's whole ledger. Called on disconnect and on vessel deselect. */
	public static void clear(UUID owner) {
		if (owner != null) {
			READY_AT.remove(owner);
		}
	}

	public static void clearAll() {
		READY_AT.clear();
		// The clock shares the rows' lifetime: a new world counts game time from its own epoch, and
		// a stale high-water mark would stamp the next recall hours into the future.
		nowTicks = 0L;
	}
}
