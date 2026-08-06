package jujutsu.mod.character.todo;

/**
 * How the aimed Boogie Woogie commit delivers a body to its destination. The production
 * implementation is the vanilla authoritative teleport; tests may substitute a failing
 * backend to exercise the partial-commit rollback path, which no deterministic world state
 * can reach (Entity#teleportTo only fails for removed entities — re-checked synchronously
 * right before the commit). Rollback and restore NEVER route through this seam.
 */
@FunctionalInterface
public interface SwapCommitTeleport {
	boolean teleport(net.minecraft.world.entity.LivingEntity body, net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 destination, float yaw, float pitch);
}
