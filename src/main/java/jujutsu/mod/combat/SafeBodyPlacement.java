package jujutsu.mod.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared search for a point where a living body can be placed without being forced into geometry.
 *
 * <p>Extracted from Todo's Boogie Woogie destination scan once Megumi's shadow move became its second
 * production consumer; the semantics are exactly the swap's. A placeable point is <b>free-form</b>:
 * air, water and crawl spaces are all valid, because the check is in-world + chunk-loaded + inside the
 * world border + {@code noBlockCollision} against the body's own posed bounding box. There is
 * deliberately no floor requirement and no third-party entity-occupancy gate — those decisions belong
 * to callers, and so far every caller has declined them.
 *
 * <p>The scan visits the requested point first, then a fixed horizontal ring, repeated over a few
 * one-block upward steps. Callers that would rather force the exact requested point than cancel opt
 * into {@link Policy#exactRequestedFallback()}; that fallback skips {@code noBlockCollision} (it still
 * refuses out-of-world points), so it is only for a body that chose the risk for itself — never for
 * one that did not ask to be moved.
 */
public final class SafeBodyPlacement {
	private SafeBodyPlacement() {}

	/**
	 * One caller's placement rules, with the candidate ring precomputed so a per-cast search allocates
	 * nothing. Declare policies {@code static final}.
	 */
	public static final class Policy {
		private final int upwardBlocks;
		private final double borderMargin;
		private final boolean exactRequestedFallback;
		private final List<Vec3> horizontalOffsets;

		public Policy(double horizontalRadius, int upwardBlocks, double borderMargin, boolean exactRequestedFallback) {
			this.upwardBlocks = upwardBlocks;
			this.borderMargin = borderMargin;
			this.exactRequestedFallback = exactRequestedFallback;
			this.horizontalOffsets = buildHorizontalOffsets(horizontalRadius);
		}

		public int upwardBlocks() {
			return upwardBlocks;
		}

		public double borderMargin() {
			return borderMargin;
		}

		public boolean exactRequestedFallback() {
			return exactRequestedFallback;
		}

		/** Candidate order is part of the contract: centre first, then near ring, then far ring. */
		public List<Vec3> horizontalOffsets() {
			return horizontalOffsets;
		}
	}

	/** Returns a placeable point near {@code requested}, or {@code null} when the policy says cancel. */
	public static Vec3 find(ServerLevel level, LivingEntity body, Vec3 requested, Policy policy) {
		for (int up = 0; up <= policy.upwardBlocks(); up++) {
			for (Vec3 horizontal : policy.horizontalOffsets()) {
				Vec3 candidate = requested.add(horizontal.x, up, horizontal.z);
				if (isPlaceable(level, body, candidate, policy.borderMargin())) {
					return candidate;
				}
			}
		}
		if (policy.exactRequestedFallback() && isInWorld(level, body, requested, policy.borderMargin())) {
			return requested;
		}
		return null;
	}

	/** In-world plus {@code noBlockCollision}: the full standard a body that did not ask to move gets. */
	public static boolean isPlaceable(ServerLevel level, LivingEntity body, Vec3 candidate, double borderMargin) {
		return isInWorld(level, body, candidate, borderMargin)
				&& level.noBlockCollision(body, boundingBoxAt(body, candidate));
	}

	private static boolean isInWorld(ServerLevel level, LivingEntity body, Vec3 candidate, double borderMargin) {
		BlockPos destinationBlock = BlockPos.containing(candidate);
		return hasFinitePosition(candidate)
				&& level.isInWorldBounds(destinationBlock)
				&& level.getChunkSource().hasChunk(destinationBlock.getX() >> 4, destinationBlock.getZ() >> 4)
				&& level.getWorldBorder().isWithinBounds(boundingBoxAt(body, candidate).inflate(borderMargin));
	}

	private static AABB boundingBoxAt(LivingEntity body, Vec3 candidate) {
		return body.getDimensions(body.getPose()).makeBoundingBox(candidate);
	}

	private static boolean hasFinitePosition(Vec3 value) {
		return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}

	private static List<Vec3> buildHorizontalOffsets(double radius) {
		double half = radius * 0.5;
		double diag = radius * 0.7;
		List<Vec3> offsets = new ArrayList<>();
		offsets.add(Vec3.ZERO);
		offsets.add(new Vec3(half, 0.0, 0.0));
		offsets.add(new Vec3(-half, 0.0, 0.0));
		offsets.add(new Vec3(0.0, 0.0, half));
		offsets.add(new Vec3(0.0, 0.0, -half));
		offsets.add(new Vec3(radius, 0.0, 0.0));
		offsets.add(new Vec3(-radius, 0.0, 0.0));
		offsets.add(new Vec3(0.0, 0.0, radius));
		offsets.add(new Vec3(0.0, 0.0, -radius));
		offsets.add(new Vec3(diag, 0.0, diag));
		offsets.add(new Vec3(diag, 0.0, -diag));
		offsets.add(new Vec3(-diag, 0.0, diag));
		offsets.add(new Vec3(-diag, 0.0, -diag));
		return List.copyOf(offsets);
	}
}
