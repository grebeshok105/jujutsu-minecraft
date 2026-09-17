package jujutsu.mod.character.megumi;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-(owner, shikigami type) summon cooldowns (issue #107 coexistence): with several packs allowed
 * at once, a shared slot cooldown would let recalling Nue block summoning Toad. Each type therefore
 * carries its own "summonable again at" timestamp; the Divine Dogs ride the same map under
 * {@link MegumiShikigami#DOGS}.
 */
public final class MegumiSummonCooldowns {
	private static final Map<UUID, EnumMap<MegumiShikigami, Long>> UNTIL_GAME_TIME =
			new ConcurrentHashMap<>();

	private MegumiSummonCooldowns() {}

	public static boolean onCooldown(UUID ownerId, MegumiShikigami type, long gameTime) {
		EnumMap<MegumiShikigami, Long> perType = UNTIL_GAME_TIME.get(ownerId);
		if (perType == null) {
			return false;
		}
		Long until = perType.get(type);
		return until != null && gameTime < until;
	}

	public static void start(UUID ownerId, MegumiShikigami type, long untilGameTime) {
		UNTIL_GAME_TIME.computeIfAbsent(ownerId, key -> new EnumMap<>(MegumiShikigami.class))
				.put(type, untilGameTime);
	}

	/** Remaining ticks for the dev control surface; zero when the type is summonable. */
	public static long remainingTicks(UUID ownerId, MegumiShikigami type, long gameTime) {
		EnumMap<MegumiShikigami, Long> perType = UNTIL_GAME_TIME.get(ownerId);
		if (perType == null) {
			return 0;
		}
		Long until = perType.get(type);
		return until == null ? 0 : Math.max(0, until - gameTime);
	}

	public static void clear(UUID ownerId) {
		UNTIL_GAME_TIME.remove(ownerId);
	}

	public static void clearAll() {
		UNTIL_GAME_TIME.clear();
	}
}
