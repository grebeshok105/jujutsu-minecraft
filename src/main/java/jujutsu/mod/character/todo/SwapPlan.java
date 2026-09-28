package jujutsu.mod.character.todo;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable all-or-nothing list of node destinations. */
public record SwapPlan(List<SwapMove> moves) {
	public SwapPlan {
		Objects.requireNonNull(moves, "moves");
		moves = List.copyOf(moves);
	}

	public static Optional<SwapPlan> preflight(List<SwapMove> moves) {
		if (moves == null || moves.isEmpty() || moves.stream().anyMatch(move -> move == null || move.destination() == null)) {
			return Optional.empty();
		}
		return Optional.of(new SwapPlan(moves));
	}

	public static Optional<SwapPlan> preflight(SwapMove... moves) {
		return moves == null ? Optional.empty() : preflight(Arrays.asList(moves));
	}
}
