package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

/** Floor-supported placement policy shared by initial spawn and later leash recovery. */
final class MegumiGroundSafety {
	private static final int[] VERTICAL_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
	private static final List<HorizontalOffset> LEASH_OFFSETS = buildLeashOffsets(
			(int) MegumiProfile.LEASH_SAFE_SEARCH_RADIUS);

	private MegumiGroundSafety() {}

	static Optional<SpawnPair> findSummonPair(
			ServerLevel level, Vec3 ownerPosition, Vec3 look, EntityDimensions dimensions) {
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		forward = forward.lengthSqr() > 1.0E-8 ? forward.normalize() : new Vec3(0.0, 0.0, 1.0);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		for (List<Vec3> desired : List.of(
				List.of(ownerPosition.add(right.scale(1.5)), ownerPosition.add(right.scale(-1.5))),
				List.of(ownerPosition.add(forward.scale(1.5)), ownerPosition.add(forward.scale(-1.5))))) {
			Optional<Vec3> first = findVertical(level, desired.get(0), dimensions);
			Optional<Vec3> second = findVertical(level, desired.get(1), dimensions);
			if (first.isPresent() && second.isPresent()
					&& !dimensions.makeBoundingBox(first.get()).intersects(dimensions.makeBoundingBox(second.get()))) {
				return Optional.of(new SpawnPair(first.get(), second.get()));
			}
		}
		return Optional.empty();
	}

	static Optional<Vec3> findVertical(ServerLevel level, Vec3 desired, EntityDimensions dimensions) {
		int baseY = BlockPos.containing(desired).getY();
		for (int offset : VERTICAL_OFFSETS) {
			Vec3 candidate = new Vec3(desired.x, baseY + offset, desired.z);
			if (isSafe(level, candidate, dimensions)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	static Optional<Vec3> findLeashPosition(
			ServerLevel level, Vec3 ownerPosition, MegumiDivineDogEntity dog) {
		EntityDimensions dimensions = dog.getDimensions(Pose.STANDING);
		for (HorizontalOffset offset : LEASH_OFFSETS) {
			Vec3 desired = ownerPosition.add(offset.x(), 0.0, offset.z());
			int baseY = BlockPos.containing(desired).getY();
			for (int verticalOffset : VERTICAL_OFFSETS) {
				Vec3 candidate = new Vec3(desired.x, baseY + verticalOffset, desired.z);
				if (isSafe(level, candidate, dimensions)
						&& level.getEntities(dog, dimensions.makeBoundingBox(candidate), entity -> !entity.isSpectator()).isEmpty()) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	static List<HorizontalOffset> buildLeashOffsets(int radius) {
		List<HorizontalOffset> offsets = new ArrayList<>();
		offsets.add(new HorizontalOffset(0, 0));
		Comparator<HorizontalOffset> order = Comparator
				.comparingInt(HorizontalOffset::distanceSquared)
				.thenComparingInt(HorizontalOffset::x)
				.thenComparingInt(HorizontalOffset::z);
		for (int ring = 1; ring <= radius; ring++) {
			List<HorizontalOffset> ringOffsets = new ArrayList<>();
			for (int x = -ring; x <= ring; x++) {
				for (int z = -ring; z <= ring; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) == ring) {
						ringOffsets.add(new HorizontalOffset(x, z));
					}
				}
			}
			ringOffsets.sort(order);
			offsets.addAll(ringOffsets);
		}
		return List.copyOf(offsets);
	}

	static boolean isSafe(ServerLevel level, Vec3 candidate, EntityDimensions dimensions) {
		BlockPos feet = BlockPos.containing(candidate);
		BlockPos floor = feet.below();
		if (!level.isLoaded(feet) || !level.isLoaded(floor)) {
			return false;
		}
		BlockState floorState = level.getBlockState(floor);
		if (!floorState.isFaceSturdy(level, floor, Direction.UP) || isHazard(level, floor)) {
			return false;
		}
		AABB bounds = dimensions.makeBoundingBox(candidate);
		if (!level.noCollision(bounds)) {
			return false;
		}
		for (BlockPos occupied : BlockPos.betweenClosed(bounds)) {
			if (!level.isLoaded(occupied) || isHazard(level, occupied)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isHazard(ServerLevel level, BlockPos position) {
		BlockState state = level.getBlockState(position);
		return state.is(BlockTags.FIRE)
				|| state.is(BlockTags.CAMPFIRES)
				|| level.getFluidState(position).is(FluidTags.LAVA);
	}

	record SpawnPair(Vec3 white, Vec3 black) {}

	record HorizontalOffset(int x, int z) {
		int distanceSquared() {
			return x * x + z * z;
		}
	}
}
