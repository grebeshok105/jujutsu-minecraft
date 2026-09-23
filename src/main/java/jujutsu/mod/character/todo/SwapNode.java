package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A server-side participant in a Todo swap plan. */
public sealed interface SwapNode permits BodyNode, StoneNode {
	Vec3 position();

	ServerLevel level();

	/** Cheap liveness check used before the full policy revalidation. */
	boolean stillValid(ServerPlayer caster);

	/** Body snapshot for rollback/feedback; stone nodes deliberately return null. */
	TodoBoogieWoogieRuntime.Snapshot snapshotOrNull();
}
