package jujutsu.mod.client.character.nobara;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

/**
 * Client-side ESP aggregator for Nobara's embedded nails.
 *
 * <p>Every 2 ticks scans the level for embedded nails owned by the local player, groups them
 * by target entity, and produces an immutable snapshot. Registration via {@link #register()}
 * must happen once during client initialization.
 */
public final class NobaraEspState {
	private static final int REFRESH_INTERVAL_TICKS = 2;

	private static Map<Integer, TargetEsp> snapshot = Map.of();
	private static int tickCounter;

	/**
	 * Immutable data for one target entity that has at least one embedded nail owned by the local
	 * Nobara player.
	 *
	 * @param targetId          the target entity's network id
	 * @param nailCount         how many embedded nails this target has
	 * @param nailDepths        depth values, sorted descending
	 * @param leaderNailEntityId the nail entity with the smallest entity id among this group
	 */
	public record TargetEsp(int targetId, int nailCount, List<Integer> nailDepths, int leaderNailEntityId) {}

	/**
	 * Pure data for {@link #aggregate(List)}, avoiding any Minecraft references.
	 *
	 * @param nailEntityId   the nail entity's id
	 * @param targetEntityId the target entity's network id
	 * @param depth          the nail's embed depth (1-3)
	 * @param embedded       whether the nail is currently embedded
	 * @param ownedByLocal   whether the nail is owned by the local player
	 * @param targetAlive    whether the target is alive and valid
	 */
	public record NailView(int nailEntityId, int targetEntityId, int depth, boolean embedded, boolean ownedByLocal, boolean targetAlive) {}

	private NobaraEspState() {}

	/**
	 * Pure aggregation: filters valid nail views, groups by target, computes leader and sorted
	 * depths. Testable without a running Minecraft instance.
	 *
	 * @param nails input nail views
	 * @return an unmodifiable map from target entity id to its {@link TargetEsp}
	 */
	public static Map<Integer, TargetEsp> aggregate(List<NailView> nails) {
		Map<Integer, List<NailView>> byTarget = new HashMap<>();
		for (NailView nail : nails) {
			if (!nail.embedded() || !nail.ownedByLocal() || !nail.targetAlive() || nail.targetEntityId() < 0) {
				continue;
			}
			byTarget.computeIfAbsent(nail.targetEntityId(), k -> new ArrayList<>()).add(nail);
		}

		Map<Integer, TargetEsp> result = new HashMap<>();
		for (Map.Entry<Integer, List<NailView>> entry : byTarget.entrySet()) {
			int targetId = entry.getKey();
			List<NailView> group = entry.getValue();
			int leaderId = Integer.MAX_VALUE;
			List<Integer> depths = new ArrayList<>(group.size());
			for (NailView nail : group) {
				if (nail.nailEntityId() < leaderId) {
					leaderId = nail.nailEntityId();
				}
				depths.add(nail.depth());
			}
			depths.sort(Collections.reverseOrder());
			result.put(targetId, new TargetEsp(targetId, group.size(), List.copyOf(depths), leaderId));
		}
		return Collections.unmodifiableMap(result);
	}

	/**
	 * Returns the current snapshot. Updated every 2 ticks when the local player is Nobara.
	 */
	public static Map<Integer, TargetEsp> snapshot() {
		return snapshot;
	}

	/**
	 * Registers the client tick handler that refreshes the snapshot.
	 * Call once from {@code registerClientHooks()}.
	 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++tickCounter % REFRESH_INTERVAL_TICKS != 0) {
				return;
			}
			tickCounter = 0;
			refresh(client);
		});
	}

	private static void refresh(Minecraft client) {
		if (client.player == null || client.level == null) {
			snapshot = Map.of();
			return;
		}
		UUID localId = client.player.getUUID();
		if (ClientCharacterSelectionManager.characterOrNone(localId) != JujutsuCharacter.NOBARA) {
			snapshot = Map.of();
			return;
		}

		List<NailView> nails = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ProjectJjkNailEntity nail)) {
				continue;
			}
			if (!nail.isEmbedded()) {
				continue;
			}
			Optional<UUID> ownerUuid = nail.clientOwnerUuid();
			if (ownerUuid.isEmpty() || !ownerUuid.get().equals(localId)) {
				continue;
			}
			int targetId = nail.embeddedTargetEntityId();
			if (targetId < 0) {
				continue;
			}

			Entity target = client.level.getEntity(targetId);
			boolean targetAlive = target instanceof LivingEntity living && living.isAlive()
					&& target.getId() != client.player.getId();

			nails.add(new NailView(
					nail.getId(),
					targetId,
					nail.embedDepthLevel(),
					true,
					true,
					targetAlive
			));
		}
		snapshot = aggregate(nails);
	}
}
