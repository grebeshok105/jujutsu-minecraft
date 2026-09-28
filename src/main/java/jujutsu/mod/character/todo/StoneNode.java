package jujutsu.mod.character.todo;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A live Todo stone whose transient owner reference must still resolve at commit time. */
public record StoneNode(TodoStoneEntity stone) implements SwapNode {
	public StoneNode {
		Objects.requireNonNull(stone, "stone");
	}

	@Override
	public Vec3 position() {
		return stone.position();
	}

	@Override
	public ServerLevel level() {
		return (ServerLevel) stone.level();
	}

	@Override
	public boolean stillValid(ServerPlayer caster) {
		if (caster == null || stone.isRemoved() || stone.level() != caster.level()) {
			return false;
		}
		return TodoTransientState.stone(caster.getUUID()).map(ref -> ref.entityUuid().equals(stone.getUUID())
				&& ref.dimension().equals(level().dimension())).orElse(false);
	}

	@Override
	public TodoBoogieWoogieRuntime.Snapshot snapshotOrNull() {
		return null;
	}
}
