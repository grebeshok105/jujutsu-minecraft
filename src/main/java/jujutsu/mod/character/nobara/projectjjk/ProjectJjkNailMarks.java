package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks embedded "cursed nail" marks per target. Marks are the connective tissue of the whole kit:
 * nails apply them, the Hairpin detonates them, and the Straw Doll resonates through them. Pure logic
 * keyed by target UUID + game time so it can be unit-tested and shared across levels.
 */
public final class ProjectJjkNailMarks {
	private static final Map<UUID, MarkStack> STACKS = new ConcurrentHashMap<>();

	private ProjectJjkNailMarks() {}

	public static int marks(UUID targetId, long gameTime) {
		MarkStack stack = STACKS.get(targetId);
		if (stack == null) {
			return 0;
		}
		return stack.active(gameTime);
	}

	/** Embeds one more nail mark on the target (capped), refreshing the expiry window. */
	public static int apply(UUID targetId, long gameTime) {
		MarkStack stack = STACKS.computeIfAbsent(targetId, id -> new MarkStack());
		return stack.add(gameTime);
	}

	/** Consumes all active marks on the target and returns how many were detonated. */
	public static int consume(UUID targetId, long gameTime) {
		MarkStack stack = STACKS.remove(targetId);
		if (stack == null) {
			return 0;
		}
		return stack.active(gameTime);
	}

	public static void clear(UUID targetId) {
		STACKS.remove(targetId);
	}

	/** Drops fully-expired stacks so the map does not grow without bound. */
	public static void pruneExpired(long gameTime) {
		List<UUID> dead = new ArrayList<>();
		for (Map.Entry<UUID, MarkStack> entry : STACKS.entrySet()) {
			if (entry.getValue().active(gameTime) <= 0) {
				dead.add(entry.getKey());
			}
		}
		for (UUID id : dead) {
			STACKS.remove(id);
		}
	}

	private static final class MarkStack {
		private int count;
		private long lastAppliedGameTime = Long.MIN_VALUE;

		private synchronized int add(long gameTime) {
			int current = active(gameTime);
			count = Math.min(ProjectJjkNobaraProfile.MARK_MAX_PER_TARGET, current + 1);
			lastAppliedGameTime = gameTime;
			return count;
		}

		private synchronized int active(long gameTime) {
			if (lastAppliedGameTime == Long.MIN_VALUE) {
				return 0;
			}
			if (gameTime - lastAppliedGameTime > ProjectJjkNobaraProfile.MARK_DURATION_TICKS) {
				count = 0;
				return 0;
			}
			return count;
		}
	}
}
