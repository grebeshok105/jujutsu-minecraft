package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One owner's shared combat snapshot (issue #107 §5): everything the pack knows about the fight
 * this scan — the candidate pool, who already holds which mark, which targets an ally's committed
 * action points at, who is threatening the owner or a sibling body, and where everyone stands.
 *
 * <p>Rebuilt by {@link MegumiPackCoordinator} every {@link MegumiShikigamiProfile#COORDINATION_SCAN_TICKS}
 * ticks — the entity scan is the expensive part, so the list is allowed to go a few ticks stale;
 * every score still re-checks liveness and line of sight at use time. This is deliberately a
 * snapshot, not a command channel: each body keeps deciding for itself (§5, §9).
 */
public record MegumiCombatContext(
		long gameTime,
		UUID ownerId,
		Vec3 ownerPos,
		List<LivingEntity> candidates,
		Map<UUID, UUID> marks,
		Set<UUID> intentTargets,
		LivingEntity ownerThreat,
		Map<UUID, UUID> allyThreats) {

	/** An empty context for owners with no bodies out — never null downstream. */
	static MegumiCombatContext empty(long gameTime, UUID ownerId, Vec3 ownerPos) {
		return new MegumiCombatContext(gameTime, ownerId, ownerPos, List.of(), Map.of(), Set.of(),
				null, Map.of());
	}

	/** Targets already claimed: marked by a body or pointed at by a committed ally action. */
	public Set<UUID> occupiedOrClaimed() {
		Set<UUID> claimed = new java.util.HashSet<>(intentTargets);
		claimed.addAll(marks.values());
		return claimed;
	}
}
