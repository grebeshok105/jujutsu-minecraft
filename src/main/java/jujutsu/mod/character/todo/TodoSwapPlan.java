package jujutsu.mod.character.todo;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable preflight result: a swap may commit only when both destinations are known-safe.
 *
 * <p>Named for positions rather than participants, because the same rule covers Todo swapping with a
 * target and Todo swapping two bystanders with each other.
 */
public record TodoSwapPlan(Vec3 firstDestination, Vec3 secondDestination) {
	public static Optional<TodoSwapPlan> preflight(Vec3 firstDestination, Vec3 secondDestination) {
		if (firstDestination == null || secondDestination == null) {
			return Optional.empty();
		}
		return Optional.of(new TodoSwapPlan(firstDestination, secondDestination));
	}
}
