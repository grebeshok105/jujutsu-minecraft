package jujutsu.mod.character.todo;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A living body with the placement strictness required for its destination. */
public record BodyNode(LivingEntity body, TodoBoogieWoogieRuntime.Strictness strictness) implements SwapNode {
	public BodyNode {
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(strictness, "strictness");
	}

	@Override
	public Vec3 position() {
		return body.position();
	}

	@Override
	public ServerLevel level() {
		return (ServerLevel) body.level();
	}

	@Override
	public boolean stillValid(ServerPlayer caster) {
		return caster != null && body.isAlive() && !body.isRemoved() && body.level() == caster.level();
	}

	@Override
	public TodoBoogieWoogieRuntime.Snapshot snapshotOrNull() {
		return TodoBoogieWoogieRuntime.Snapshot.capture(body);
	}
}
