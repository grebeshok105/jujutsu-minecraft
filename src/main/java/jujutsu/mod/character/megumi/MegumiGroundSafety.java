package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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
	private static final List<Integer> VERTICAL_OFFSETS = List.of(0, 1, -1, 2, -2, 3, -3);
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
		return firstSafe(leashCandidates(ownerPosition), candidate -> {
			boolean entityCollision = !level.getEntities(
					dog, dimensions.makeBoundingBox(candidate), entity -> !entity.isSpectator()).isEmpty();
			return isSafe(level, candidate, dimensions, entityCollision);
		});
	}

	private static List<Vec3> leashCandidates(Vec3 ownerPosition) {
		List<Vec3> candidates = new ArrayList<>(LEASH_OFFSETS.size() * VERTICAL_OFFSETS.size());
		for (HorizontalOffset offset : LEASH_OFFSETS) {
			Vec3 desired = ownerPosition.add(offset.x(), 0.0, offset.z());
			int baseY = BlockPos.containing(desired).getY();
			for (int verticalOffset : VERTICAL_OFFSETS) {
				candidates.add(new Vec3(desired.x, baseY + verticalOffset, desired.z));
			}
		}
		return List.copyOf(candidates);
	}

	static List<Integer> verticalOffsets() {
		return VERTICAL_OFFSETS;
	}

	static Optional<Vec3> firstSafe(List<Vec3> candidates, Predicate<Vec3> safe) {
		return candidates.stream().filter(safe).findFirst();
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
		return isSafe(level, candidate, dimensions, false);
	}

	private static boolean isSafe(
			ServerLevel level, Vec3 candidate, EntityDimensions dimensions, boolean entityCollision) {
		BlockPos feet = BlockPos.containing(candidate);
		BlockPos floor = feet.below();
		if (!level.isLoaded(feet) || !level.isLoaded(floor)) {
			return accepts(new SafetyFacts(false, false, false, false, entityCollision));
		}
		BlockState floorState = level.getBlockState(floor);
		boolean sturdyFloor = floorState.isFaceSturdy(level, floor, Direction.UP);
		boolean hazard = isHazard(level, floor);
		AABB bounds = dimensions.makeBoundingBox(candidate);
		boolean collisionFree = level.noCollision(bounds);
		boolean loaded = true;
		for (BlockPos occupied : BlockPos.betweenClosed(bounds)) {
			if (!level.isLoaded(occupied)) {
				loaded = false;
			} else if (isHazard(level, occupied)) {
				hazard = true;
			}
		}
		return accepts(new SafetyFacts(loaded, sturdyFloor, hazard, collisionFree, entityCollision));
	}

	static boolean accepts(SafetyFacts facts) {
		return facts.loaded()
				&& facts.sturdyFloor()
				&& !facts.hazard()
				&& facts.collisionFree()
				&& !facts.entityCollision();
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

	record SafetyFacts(
			boolean loaded,
			boolean sturdyFloor,
			boolean hazard,
			boolean collisionFree,
			boolean entityCollision
	) {}
}
