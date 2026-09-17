package jujutsu.mod.character.megumi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The player's active shikigami selection, keyed by player UUID. In-memory by design: the selection
 * resets on relog (recorded limit — see KNOWN_ISSUES); {@link #clearAll()} runs on server stop and
 * {@link #clear(UUID)} on disconnect.
 */
public final class MegumiShikigamiSelection {
	private static final Map<UUID, MegumiShikigami> SELECTED = new ConcurrentHashMap<>();

	private MegumiShikigamiSelection() {}

	public static MegumiShikigami selected(UUID playerId) {
		return SELECTED.getOrDefault(playerId, MegumiShikigami.DOGS);
	}

	/** Advances the selection one step and returns the new selection. */
	public static MegumiShikigami cycle(UUID playerId) {
		MegumiShikigami next = selected(playerId).next();
		SELECTED.put(playerId, next);
		return next;
	}

	/**
	 * Advances the selection to the next entry the caller says is usable, in the same canonical order
	 * {@link #cycle} walks, and returns it.
	 *
	 * <p>The predicate is the caller's: this class knows the roster and the order, not what makes an
	 * entry unavailable. When nothing is usable — not even the current entry — the selection stays
	 * where it is instead of walking onto an entry the player cannot use, and the current selection is
	 * returned, so a cycle that could not move is never mistaken for one that did.
	 */
	public static MegumiShikigami cycleAvailable(UUID playerId, Predicate<MegumiShikigami> selectable) {
		MegumiShikigami current = selected(playerId);
		MegumiShikigami candidate = current;
		for (int step = 0; step < MegumiShikigami.values().length; step++) {
			candidate = candidate.next();
			if (selectable.test(candidate)) {
				SELECTED.put(playerId, candidate);
				return candidate;
			}
		}
		return current;
	}

	/** Sets the selection outright; exists for deselect reset and tests. */
	public static void set(UUID playerId, MegumiShikigami type) {
		if (type == null) {
			SELECTED.remove(playerId);
		} else {
			SELECTED.put(playerId, type);
		}
	}

	public static void clear(UUID playerId) {
		SELECTED.remove(playerId);
	}

	public static void clearAll() {
		SELECTED.clear();
	}
}