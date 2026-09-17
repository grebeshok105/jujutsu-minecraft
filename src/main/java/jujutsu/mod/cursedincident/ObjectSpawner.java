package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Mints a physical cursed object into the world (issue #110). Implemented by the object
 * system (Block 2's {@code ObjectSpawnerImpl}); the core calls it when an incident's
 * source kind is OBJECT.
 *
 * @return the object instance id (the {@code CursedObjectState.instanceId}), or null if
 *         the type refused minting (e.g. a unique type at its instance cap).
 */
public interface ObjectSpawner {

	UUID spawn(ServerLevel level, BlockPos pos, String typeId, int grade, long seed);
}
