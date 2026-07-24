package jujutsu.mod.character.todo;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Immutable preflight result: a swap may commit only when both destinations are known-safe. */
public record TodoSwapPlan(Vec3 todoDestination, Vec3 targetDestination) {
	public static Optional<TodoSwapPlan> preflight(Vec3 todoDestination, Vec3 targetDestination) {
		if (todoDestination == null || targetDestination == null) {
			return Optional.empty();
		}
		return Optional.of(new TodoSwapPlan(todoDestination, targetDestination));
	}
}
