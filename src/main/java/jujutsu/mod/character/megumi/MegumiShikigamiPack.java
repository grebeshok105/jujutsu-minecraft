package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Identity record for one active shikigami pack: which type, where, which bodies, the summon
 * token that pins this particular summoning and the game time it happened.
 *
 * @param anchorId the keystone body — for single-body packs it IS the body; for the rabbit swarm it
 *                 is the marked rabbit whose death dispels the swarm
 */
public record MegumiShikigamiPack(
		MegumiShikigami type,
		ResourceKey<Level> dimension,
		UUID anchorId,
		List<UUID> bodyIds,
		long summonToken,
		long summonedAtGameTime) {

	/** Whether {@code bodyId} belongs to this pack: token and dimension must match, id must be one of the bodies. */
	public boolean contains(UUID bodyId, long token, ResourceKey<Level> dim) {
		return token == summonToken && dim.equals(dimension) && bodyIds.contains(bodyId);
	}
}