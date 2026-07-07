package jujutsu.mod.character.nobara.projectjjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative cursed-energy pool for Nobara's Straw Doll kit. Every ability draws from
 * the same reservoir, which regenerates each tick. Pure logic (no MC types) so it stays unit-testable.
 */
public final class ProjectJjkCursedEnergy {
	private static final Map<UUID, Float> POOLS = new ConcurrentHashMap<>();

	private ProjectJjkCursedEnergy() {}

	public static float get(UUID playerId) {
		return POOLS.getOrDefault(playerId, ProjectJjkNobaraProfile.CE_MAX);
	}

	public static boolean has(UUID playerId, float cost) {
		return get(playerId) + 1.0e-3f >= cost;
	}

	/** Attempts to spend {@code cost}. Returns true and deducts when affordable, false otherwise. */
	public static boolean spend(UUID playerId, float cost) {
		float current = get(playerId);
		if (current + 1.0e-3f < cost) {
			return false;
		}
		POOLS.put(playerId, clamp(current - cost));
		return true;
	}

	public static void regenerate(UUID playerId, float amount) {
		float current = get(playerId);
		if (current >= ProjectJjkNobaraProfile.CE_MAX) {
			return;
		}
		POOLS.put(playerId, clamp(current + amount));
	}

	public static void reset(UUID playerId) {
		POOLS.put(playerId, ProjectJjkNobaraProfile.CE_MAX);
	}

	public static void clear(UUID playerId) {
		POOLS.remove(playerId);
	}

	private static float clamp(float value) {
		if (value < 0.0f) {
			return 0.0f;
		}
		return Math.min(ProjectJjkNobaraProfile.CE_MAX, value);
	}
}
