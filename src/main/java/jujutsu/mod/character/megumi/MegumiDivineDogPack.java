package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Identity of one two-body summon. Entity UUID plus token prevents an older pack joining a newer one. */
public record MegumiDivineDogPack(
		ResourceKey<Level> dimension,
		UUID whiteId,
		UUID blackId,
		long summonToken,
		long summonedAtGameTime
) {
	public List<UUID> dogIds() {
		return List.of(whiteId, blackId);
	}

	public boolean contains(UUID dogId, long token, ResourceKey<Level> dogDimension) {
		return summonToken == token
				&& dimension.equals(dogDimension)
				&& (whiteId.equals(dogId) || blackId.equals(dogId));
	}

	public boolean wasSummonedAt(long gameTime) {
		return summonedAtGameTime == gameTime;
	}
}
