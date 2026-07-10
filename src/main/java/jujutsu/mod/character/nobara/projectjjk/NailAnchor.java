package jujutsu.mod.character.nobara.projectjjk;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record NailAnchor(
		Kind kind,
		UUID stableId,
		int cachedEntityId,
		BlockPos blockPos,
		ResourceLocation dimension,
		Direction face,
		String blockStateSignature,
		ResourceLocation runtimeType,
		Vec3 localOffset,
		Vec3 localForward
) {
	public enum Kind { NONE, ENTITY, BLOCK, RUNTIME_OBJECT }

	public NailAnchor {
		kind = Objects.requireNonNull(kind, "kind");
		localOffset = localOffset == null ? Vec3.ZERO : localOffset;
		localForward = localForward == null ? new Vec3(0.0, 0.0, 1.0) : localForward;
	}

	public static NailAnchor none() {
		return new NailAnchor(Kind.NONE, null, -1, null, null, null, "", null, Vec3.ZERO, new Vec3(0.0, 0.0, 1.0));
	}

	public static NailAnchor entity(UUID targetId, int entityId, Vec3 localOffset, Vec3 localForward) {
		return new NailAnchor(Kind.ENTITY, Objects.requireNonNull(targetId), entityId, null, null, null, "", null, localOffset, localForward);
	}

	public static NailAnchor block(BlockPos pos, ResourceLocation dimension, Direction face, String stateSignature, Vec3 localOffset, Vec3 localForward) {
		return new NailAnchor(Kind.BLOCK, null, -1, Objects.requireNonNull(pos).immutable(), Objects.requireNonNull(dimension), Objects.requireNonNull(face), Objects.requireNonNull(stateSignature), null, localOffset, localForward);
	}

	public static NailAnchor runtime(ResourceLocation type, UUID objectId, Vec3 localOffset, Vec3 localForward) {
		return new NailAnchor(Kind.RUNTIME_OBJECT, Objects.requireNonNull(objectId), -1, null, null, null, "", Objects.requireNonNull(type), localOffset, localForward);
	}

	public NailAnchor withCachedEntityId(int entityId) {
		return new NailAnchor(kind, stableId, entityId, blockPos, dimension, face, blockStateSignature, runtimeType, localOffset, localForward);
	}
}
