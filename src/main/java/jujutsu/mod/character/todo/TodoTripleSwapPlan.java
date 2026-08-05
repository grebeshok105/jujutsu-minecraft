package jujutsu.mod.character.todo;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable preflight result of the triple cyclic swap: a commit may run only when all three
 * destinations are known-safe.
 *
 * <p>Named for roles rather than participants, because the cycle moves Todo himself: {@code todo}
 * lands where the first participant (A) stood, A lands where the crosshair target (T) stood, and T
 * lands where Todo stood. The direction is fixed and test-pinned — the cycle never reverses.
 */
public record TodoTripleSwapPlan(Vec3 todoDestination, Vec3 aDestination, Vec3 tDestination) {
	public static Optional<TodoTripleSwapPlan> preflight(Vec3 todoDest, Vec3 aDest, Vec3 tDest) {
		if (todoDest == null || aDest == null || tDest == null) {
			return Optional.empty();
		}
		return Optional.of(new TodoTripleSwapPlan(todoDest, aDest, tDest));
	}
}
