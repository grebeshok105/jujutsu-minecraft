package jujutsu.mod.character.todo;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable preflight result for the one body a stone swap moves.
 *
 * <p>Both stone casts move exactly one body to the stone's current position — Todo himself for the
 * self-swap, the aimed target for the target-swap — and both refuse to move anything when that one
 * destination is not safe. Mirrors {@link TodoSwapPlan} for the single-destination case, so the
 * "no plan, no move" invariant is testable as pure logic.
 */
public record TodoStonePlan(Vec3 destination) {
	public static Optional<TodoStonePlan> preflight(Vec3 destination) {
		if (destination == null) {
			return Optional.empty();
		}
		return Optional.of(new TodoStonePlan(destination));
	}
}
