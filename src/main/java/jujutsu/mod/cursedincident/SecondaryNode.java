package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;

/**
 * A self-sustaining secondary infection node (issue #110 spec §7.3). Once
 * {@code selfSustaining} is true the node keeps escalating even after the parent
 * source is removed — the mechanism that turns dependent infection into an
 * independent one.
 */
public record SecondaryNode(
		UUID id,
		BlockPos center,
		double radius,
		long createdGameTime,
		boolean selfSustaining) {
}
