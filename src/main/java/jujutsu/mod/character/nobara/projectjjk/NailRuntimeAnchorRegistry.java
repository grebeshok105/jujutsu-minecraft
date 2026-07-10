package jujutsu.mod.character.nobara.projectjjk;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class NailRuntimeAnchorRegistry {
	public static final NailRuntimeAnchorRegistry GLOBAL = new NailRuntimeAnchorRegistry();
	private final Map<ResourceLocation, Resolver> resolvers = new ConcurrentHashMap<>();

	public void register(ResourceLocation type, Resolver resolver) {
		if (resolvers.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(resolver)) != null) {
			throw new IllegalStateException("Duplicate nail runtime anchor resolver: " + type);
		}
	}

	public Result resolve(NailAnchor anchor, Vec3 lastKnownPosition) {
		if (anchor.kind() != NailAnchor.Kind.RUNTIME_OBJECT || anchor.runtimeType() == null || anchor.stableId() == null) {
			return Result.invalid(lastKnownPosition);
		}
		Resolver resolver = resolvers.get(anchor.runtimeType());
		return resolver == null ? Result.invalid(lastKnownPosition) : resolver.resolve(anchor.stableId(), lastKnownPosition);
	}

	@FunctionalInterface
	public interface Resolver {
		Result resolve(UUID objectId, Vec3 lastKnownPosition);
	}

	public record Result(NailAnchorResolution resolution, Vec3 position, Vec3 forward) {
		public Result {
			resolution = Objects.requireNonNull(resolution);
			position = position == null ? Vec3.ZERO : position;
			forward = forward == null ? new Vec3(0.0, 0.0, 1.0) : forward;
		}

		public static Result resolved(Vec3 position, Vec3 forward) {
			return new Result(NailAnchorResolution.RESOLVED, position, forward);
		}

		public static Result unavailable(Vec3 lastKnownPosition) {
			return new Result(NailAnchorResolution.TEMPORARILY_UNAVAILABLE, lastKnownPosition, Vec3.ZERO);
		}

		public static Result removed(Vec3 lastKnownPosition) {
			return new Result(NailAnchorResolution.CONFIRMED_REMOVED, lastKnownPosition, Vec3.ZERO);
		}

		public static Result invalid(Vec3 lastKnownPosition) {
			return new Result(NailAnchorResolution.INVALID, lastKnownPosition, Vec3.ZERO);
		}
	}
}
