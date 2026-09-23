package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks embedded cursed-nail marks per owner and target. Marks are the connective tissue of the kit:
 * nails apply them, Hairpin detonates them, and Straw Doll resonates through them.
 */
public final class ProjectJjkNailMarks {
	private static final Map<MarkKey, MarkStack> STACKS = new ConcurrentHashMap<>();

	private ProjectJjkNailMarks() {}

	/** Embeds one more mark for an owner-target pair (capped), refreshing the expiry window. */
	public static int apply(UUID ownerId, UUID targetId, long gameTime) {
		MarkKey key = new MarkKey(ownerId, targetId);
		MarkStack stack = STACKS.computeIfAbsent(key, ignored -> new MarkStack());
		return stack.add(gameTime);
	}

	/** Returns active marks for an owner-target pair at the supplied game time. */
	public static int marks(UUID ownerId, UUID targetId, long gameTime) {
		MarkStack stack = STACKS.get(new MarkKey(ownerId, targetId));
		return stack == null ? 0 : stack.active(gameTime);
	}

	/** Consumes and returns every mark currently held for an owner-target pair. */
	public static int consume(UUID ownerId, UUID targetId) {
		MarkStack stack = STACKS.remove(new MarkKey(ownerId, targetId));
		return stack == null ? 0 : stack.count();
	}

	/** Returns whether any owner still has an active mark on the target. */
	public static boolean anyMarks(UUID targetId, long gameTime) {
		if (targetId == null) {
			return false;
		}
		for (Map.Entry<MarkKey, MarkStack> entry : STACKS.entrySet()) {
			if (targetId.equals(entry.getKey().targetId()) && entry.getValue().active(gameTime) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Clears every mark owned by one caster, leaving other owners' marks untouched. */
	public static void clearOwner(UUID ownerId) {
		if (ownerId != null) {
			STACKS.keySet().removeIf(key -> ownerId.equals(key.ownerId()));
		}
	}

	/** Drops every mark stack. Exists for the dev control surface and GameTests. */
	public static void clearAll() {
		STACKS.clear();
	}

	/**
	 * Target-scoped cleanup retained for entity-removal fixtures. It does not affect marks on other targets.
	 */
	public static void clear(UUID targetId) {
		if (targetId != null) {
			STACKS.keySet().removeIf(key -> targetId.equals(key.targetId()));
		}
	}

	/** Drops fully expired stacks so the map cannot grow without bound. */
	public static void pruneExpired(long gameTime) {
		List<MarkKey> dead = new ArrayList<>();
		for (Map.Entry<MarkKey, MarkStack> entry : STACKS.entrySet()) {
			if (entry.getValue().active(gameTime) <= 0) {
				dead.add(entry.getKey());
			}
		}
		for (MarkKey key : dead) {
			STACKS.remove(key);
		}
	}

	private record MarkKey(UUID ownerId, UUID targetId) {
		private MarkKey {
			Objects.requireNonNull(ownerId, "ownerId");
			Objects.requireNonNull(targetId, "targetId");
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

		private synchronized int count() {
			return count;
		}
	}
}
