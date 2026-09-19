package jujutsu.mod.cursedincident;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/** A persisted secondary infection work center. */
public record SecondaryNode(
		UUID nodeId,
		BlockPos center,
		double radius,
		long createdGameTime,
		boolean selfSustaining,
		boolean scarred) {

	/** Compatibility constructor for records written before node scars were tracked. */
	public SecondaryNode(UUID nodeId, BlockPos center, double radius, long createdGameTime,
			boolean selfSustaining) {
		this(nodeId, center, radius, createdGameTime, selfSustaining, false);
	}

	public SecondaryNode {
		Objects.requireNonNull(nodeId, "nodeId");
		Objects.requireNonNull(center, "center");
		if (!Double.isFinite(radius) || radius < 0.0) {
			radius = 0.0;
		}
		center = center.immutable();
	}
}
