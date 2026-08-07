package jujutsu.mod.character;

/**
 * The outcome of one cast attempt, from the player's point of view.
 *
 * <p>Three values exist because {@code boolean} cannot say who already spoke: the shared action-bar
 * fallback must only add its own message when the cast failed and nobody else explained why.
 */
public enum AbilityResult {
	/** The cast happened. */
	SUCCESS,
	/** The cast did not happen, and the runtime already told the player why. */
	HANDLED_FAILURE,
	/** The cast did not happen and nobody said anything — the fallback should speak. */
	UNHANDLED_FAILURE
}
