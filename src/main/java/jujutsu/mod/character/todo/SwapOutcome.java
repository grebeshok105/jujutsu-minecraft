package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Objects;

/** Immutable result of a node-plan commit. */
public record SwapOutcome(boolean success, List<TodoBoogieWoogieRuntime.MovedBody> moved) {
	public SwapOutcome {
		Objects.requireNonNull(moved, "moved");
		moved = List.copyOf(moved);
	}

	public static SwapOutcome refused() {
		return new SwapOutcome(false, List.of());
	}
}
