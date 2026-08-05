package jujutsu.mod.character.todo;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The single owner of Todo's transient server state: the pair-swap selection and the thrown
 * stone. One map, one cleanup path.
 *
 * <p>Runtimes read and write through this class and never keep their own static maps. Every
 * cleanup route — death, respawn, dimension change, vessel change, disconnect, server stop, a
 * stone lost from a loaded chunk — lands in {@link #dropAll} through {@link TodoStateLifecycle},
 * so a new piece of transient state added here is automatically covered by every exit the
 * lifecycle already guards.
 */
public final class TodoTransientState {
	private record State(TodoPendingSelection pairSelection, TodoStoneRef stone) {
		private State withPair(TodoPendingSelection selection) {
			return new State(selection, stone);
		}

		private State withStone(TodoStoneRef ref) {
			return new State(pairSelection, ref);
		}

		private boolean isEmpty() {
			return pairSelection == null && stone == null;
		}
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	public static Optional<TodoPendingSelection> pairSelection(UUID owner) {
		State state = STATES.get(owner);
		return state == null ? Optional.empty() : Optional.ofNullable(state.pairSelection());
	}

	public static void setPairSelection(UUID owner, TodoPendingSelection selection) {
		STATES.compute(owner, (id, state) -> (state == null ? new State(selection, null) : state.withPair(selection)));
	}

	public static void clearPairSelection(UUID owner) {
		STATES.computeIfPresent(owner, (id, state) -> {
			State next = state.withPair(null);
			return next.isEmpty() ? null : next;
		});
	}

	public static Optional<TodoStoneRef> stone(UUID owner) {
		State state = STATES.get(owner);
		return state == null ? Optional.empty() : Optional.ofNullable(state.stone());
	}

	public static void setStone(UUID owner, TodoStoneRef ref) {
		STATES.compute(owner, (id, state) -> (state == null ? new State(null, ref) : state.withStone(ref)));
	}

	/**
	 * Forgets the owner's stone and discards its live entity if the ref still resolves. Resolution
	 * is by UUID inside the ref's dimension only — never by entity id, never across dimensions.
	 */
	public static void clearStone(MinecraftServer server, UUID owner) {
		State state = STATES.get(owner);
		TodoStoneRef ref = state == null ? null : state.stone();
		STATES.computeIfPresent(owner, (id, current) -> {
			State next = current.withStone(null);
			return next.isEmpty() ? null : next;
		});
		if (ref == null || server == null) {
			return;
		}
		ServerLevel level = server.getLevel(ref.dimension());
		if (level == null) {
			return;
		}
		Entity entity = level.getEntity(ref.entityUuid());
		if (entity != null) {
			entity.discard();
		}
	}

	/** The one cleanup everything funnels into: pair selection and stone, nothing else. */
	public static void dropAll(MinecraftServer server, UUID owner) {
		clearPairSelection(owner);
		clearStone(server, owner);
	}

	/** Snapshot of owners with any live transient state; for expiry sweeps. */
	public static Set<UUID> owners() {
		return Set.copyOf(STATES.keySet());
	}

	/** Server-stop teardown: forget everything for everyone, discarding what still resolves. */
	public static void clearAll(MinecraftServer server) {
		for (UUID owner : owners()) {
			dropAll(server, owner);
		}
	}

	private TodoTransientState() {}
}
