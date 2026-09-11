package jujutsu.mod.character.megumi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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