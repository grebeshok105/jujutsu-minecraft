package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;

/** Server-thread index of currently loaded, non-trap embedded nails grouped by level and owner. */
public final class EmbeddedNailRegistry {
	private static final Map<ServerLevel, Map<UUID, LinkedHashMap<UUID, ProjectJjkNailEntity>>> BY_LEVEL = new HashMap<>();

	private EmbeddedNailRegistry() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BY_LEVEL.keySet().removeIf(level -> level.getServer() == server));
	}

	public static boolean track(ServerLevel level, ProjectJjkNailEntity nail) {
		UUID ownerId = nail.ownerUuid();
		if (ownerId == null || !isLiveLoadedNail(level, ownerId, nail)) {
			return false;
		}
		LinkedHashMap<UUID, ProjectJjkNailEntity> owned = BY_LEVEL
				.computeIfAbsent(level, ignored -> new HashMap<>())
				.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
		owned.putIfAbsent(nail.getUUID(), nail);
		prune(level, ownerId, owned);
		while (owned.size() > ProjectJjkNobaraProfile.MAX_EMBEDDED_NAILS_PER_OWNER) {
			Iterator<ProjectJjkNailEntity> oldest = owned.values().iterator();
			if (!oldest.hasNext()) {
				break;
			}
			ProjectJjkNailEntity expired = oldest.next();
			oldest.remove();
			expired.discard();
		}
		removeEmpty(level, ownerId, owned);
		return !nail.isRemoved();
	}

	public static void untrack(ServerLevel level, ProjectJjkNailEntity nail) {
		UUID ownerId = nail.ownerUuid();
		if (ownerId == null) {
			return;
		}
		Map<UUID, LinkedHashMap<UUID, ProjectJjkNailEntity>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return;
		}
		LinkedHashMap<UUID, ProjectJjkNailEntity> owned = byOwner.get(ownerId);
		if (owned == null) {
			return;
		}
		owned.remove(nail.getUUID());
		removeEmpty(level, ownerId, owned);
	}

	public static List<ProjectJjkNailEntity> loadedOwnedNails(ServerLevel level, UUID ownerId) {
		Map<UUID, LinkedHashMap<UUID, ProjectJjkNailEntity>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return List.of();
		}
		LinkedHashMap<UUID, ProjectJjkNailEntity> owned = byOwner.get(ownerId);
		if (owned == null) {
			return List.of();
		}
		prune(level, ownerId, owned);
		List<ProjectJjkNailEntity> result = List.copyOf(owned.values());
		removeEmpty(level, ownerId, owned);
		return result;
	}

	private static void prune(ServerLevel level, UUID ownerId, LinkedHashMap<UUID, ProjectJjkNailEntity> owned) {
		owned.entrySet().removeIf(entry -> !isLiveLoadedNail(level, ownerId, entry.getValue()));
	}

	private static boolean isLiveLoadedNail(ServerLevel level, UUID ownerId, ProjectJjkNailEntity nail) {
		return nail != null
				&& !nail.isRemoved()
				&& nail.level() == level
				&& nail.isEmbedded()
				&& !nail.isTrapNail()
				&& nail.isOwnedBy(ownerId)
				&& level.getEntity(nail.getUUID()) == nail;
	}

	private static void removeEmpty(ServerLevel level, UUID ownerId, LinkedHashMap<UUID, ProjectJjkNailEntity> owned) {
		if (!owned.isEmpty()) {
			return;
		}
		Map<UUID, LinkedHashMap<UUID, ProjectJjkNailEntity>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return;
		}
		byOwner.remove(ownerId, owned);
		if (byOwner.isEmpty()) {
			BY_LEVEL.remove(level, byOwner);
		}
	}
}
