package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * @return the spawned instance (uuid + resolved type id), or {@code null} if the type
 *         refused minting (e.g. a unique type at its instance cap).
 */
public interface ObjectSpawner {

	/** Result of a successful mint: the instance id and the type actually used. */
	record Spawned(UUID uuid, String typeId) {
	}

	Spawned spawn(ServerLevel level, BlockPos pos, String typeId, int grade, long seed);
}
