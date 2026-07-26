package jujutsu.mod.character.todo;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The first participant a caster has marked, held server-side until the second cast.
 *
 * <p>Both the UUID and the network id are kept on purpose: the id is what {@code TargetResolver} hands
 * back and what the level can look up, while the UUID is what proves the entity found under that id is
 * still the same one and not a reused slot.
 */
public record TodoPendingSelection(ResourceKey<Level> dimension, UUID targetUuid, int targetEntityId, long expiresAtGameTime) {
	public boolean isExpired(long gameTime) {
		return gameTime >= expiresAtGameTime;
	}

	public boolean isIn(ResourceKey<Level> dimension) {
		return this.dimension.equals(dimension);
	}

	public boolean identifies(UUID candidateUuid) {
		return targetUuid.equals(candidateUuid);
	}
}
