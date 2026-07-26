package jujutsu.mod.character;

import net.minecraft.server.level.ServerPlayer;

/**
 * Everything the server needs to know about one vessel, in one place.
 *
 * <p>The point is what this removes rather than what it adds: shared server code stops asking "which
 * character is this" and starts asking the vessel. Adding a vessel then touches its own definition and
 * the one registry that binds it, and no shared file at all.
 *
 * <p><b>No client types may appear here, ever.</b> A dedicated server loads this interface and every
 * implementation of it; a renderer, a GUI theme or a VFX recipe reachable from here would drag client
 * classes onto a machine that has none. Those live in the client-side definition instead.
 *
 * <p>Only {@link #id()} and {@link #tryCast} are required. The rest describe things most vessels do not
 * do, so they default to doing nothing; a vessel with no attribute modifiers should not have to say so.
 */
public interface CharacterDefinition {
	/** The enum constant this definition speaks for. The registry checks that they agree. */
	JujutsuCharacter id();

	/**
	 * Runs whatever this vessel puts on that input position, or {@code false} if it puts nothing there.
	 *
	 * <p>Selection and the slot cooldown are already checked by {@link CharacterAbilityExecutor}; rules
	 * that belong to one vessel alone, such as Nobara's stagger gate, belong in its router.
	 */
	boolean tryCast(ServerPlayer player, CharacterAbility slot, boolean notify);

	/** Adds this vessel's vanilla attribute modifiers. Called only for the selected vessel. */
	default void applyAttributes(ServerPlayer player) {}

	/**
	 * Removes this vessel's own attribute modifiers. Called for <b>every</b> vessel on a selection
	 * change, including ones that never added any, so it must be safe to call unconditionally.
	 */
	default void removeAttributes(ServerPlayer player) {}

	/** Scales an incoming stagger. The default keeps the requested duration. */
	default int adjustStaggerTicks(int requestedTicks) {
		return requestedTicks;
	}

	/** Whether becoming this vessel for the first time should hand out a starter kit. */
	default boolean grantsStarterKit() {
		return false;
	}

	/** Runs after the player becomes this vessel, once the selection is already stored. */
	default void onSelected(ServerPlayer player) {}

	/**
	 * Runs before the player stops being this vessel. Anything half-finished that a later cast could
	 * consume, or anything left in the world that only this vessel could have used, is dropped here.
	 */
	default void onDeselected(ServerPlayer player) {}

	// No display strings here yet, deliberately. The plan called for name, role and technique keys, but
	// the keys as written are not a set: the roster's three card slots hold different things for each
	// vessel — Nobara passes a full name, a role and a grade, Todo passes a name, a technique and a role
	// — and there is no key for NONE under the vessel id at all. Naming a convention that does not exist
	// would only move the inconsistency somewhere harder to see. They belong to the client definition in
	// the next commit, where the roster can be made honest at the same time.
}
