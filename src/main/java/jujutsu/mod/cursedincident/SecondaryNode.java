package jujutsu.mod.cursedincident;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/** A self-sustaining secondary infection node. */
public record SecondaryNode(
		UUID id,
		BlockPos center,
		double radius,
		long createdGameTime,
		boolean selfSustaining) {

	public SecondaryNode {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(center, "center");
		if (!Double.isFinite(radius) || radius < 0.0) {
			radius = 0.0;
		}
		center = center.immutable();
	}
}
