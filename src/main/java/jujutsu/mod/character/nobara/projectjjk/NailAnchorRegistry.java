package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Server-thread index of every embedded Nobara nail, including trap anchors.
 *
 * <p>The entity remains the physical source of truth. Entries are only a fast, owner-scoped view and
 * every query validates the corresponding live entity before exposing it.
 */
public final class NailAnchorRegistry {
	public enum NailOrigin {
		LAUNCHED,
		TRAP_CORNER,
		TRAP_IMPACT
	}

	public record Entry(UUID nailId, UUID ownerId, NailAnchor anchor, int depth, NailOrigin origin, UUID targetId) {}

	private static final Map<ServerLevel, Map<UUID, LinkedHashMap<UUID, Entry>>> BY_LEVEL = new HashMap<>();

	private NailAnchorRegistry() {}

	/** Registers lifecycle cleanup for the per-server level index. */
	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
				BY_LEVEL.keySet().removeIf(level -> level.getServer() == server));
	}

	/**
	 * Adds an embedded nail to its owner's insertion-ordered index. Returns false for an ineligible
	 * entity (no owner, wrong level, removed, or not embedded).
	 */
	public static boolean track(ServerLevel level, ProjectJjkNailEntity nail) {
		if (!isLiveLoadedNail(level, nail, nail == null ? null : nail.ownerUuid())) {
			return false;
		}
		UUID ownerId = nail.ownerUuid();
		LinkedHashMap<UUID, Entry> owned = BY_LEVEL
				.computeIfAbsent(level, ignored -> new HashMap<>())
				.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
		owned.put(nail.getUUID(), nail.anchorRecord());
		pruneStale(level, ownerId, owned);
		while (owned.size() > ProjectJjkNobaraProfile.MAX_EMBEDDED_NAILS_PER_OWNER) {
			Iterator<Entry> oldest = owned.values().iterator();
			if (!oldest.hasNext()) {
				break;
			}
			Entry expired = oldest.next();
			oldest.remove();
			Entity entity = level.getEntity(expired.nailId());
			if (entity instanceof ProjectJjkNailEntity nailEntity && !nailEntity.isRemoved()) {
				nailEntity.discard();
			}
		}
		removeEmpty(level, ownerId, owned);
		return !nail.isRemoved();
	}

	/** Removes a nail by id, regardless of which owner bucket currently contains it. */
	public static void untrack(ServerLevel level, UUID nailId) {
		if (level == null || nailId == null) {
			return;
		}
		Map<UUID, LinkedHashMap<UUID, Entry>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return;
		}
		Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Entry>>> owners = byOwner.entrySet().iterator();
		while (owners.hasNext()) {
			Map.Entry<UUID, LinkedHashMap<UUID, Entry>> owner = owners.next();
			owner.getValue().remove(nailId);
			if (owner.getValue().isEmpty()) {
				owners.remove();
			}
		}
		if (byOwner.isEmpty()) {
			BY_LEVEL.remove(level);
		}
	}

	/** Updates the indexed depth without changing insertion order. */
	public static void updateDepth(ServerLevel level, UUID nailId, int depth) {
		if (level == null || nailId == null) {
			return;
		}
		Map<UUID, LinkedHashMap<UUID, Entry>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return;
		}
		for (LinkedHashMap<UUID, Entry> owned : byOwner.values()) {
			Entry entry = owned.get(nailId);
			if (entry != null) {
				owned.put(nailId, new Entry(entry.nailId(), entry.ownerId(), entry.anchor(), Math.clamp(depth, 1, 3), entry.origin(), entry.targetId()));
				return;
			}
		}
	}

	/** Returns validated anchors in insertion order, dropping stale records. */
	public static List<Entry> ownedAnchors(ServerLevel level, UUID ownerId) {
		if (level == null || ownerId == null) {
			return List.of();
		}
		Map<UUID, LinkedHashMap<UUID, Entry>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return List.of();
		}
		LinkedHashMap<UUID, Entry> owned = byOwner.get(ownerId);
		if (owned == null) {
			return List.of();
		}
		List<Entry> result = validatedEntries(level, ownerId, owned);
		removeEmpty(level, ownerId, owned);
		return result;
	}

	/** Returns validated anchors belonging to an owner and attached to the requested target. */
	public static List<Entry> anchorsOnTarget(ServerLevel level, UUID ownerId, UUID targetId) {
		if (targetId == null) {
			return List.of();
		}
		return ownedAnchors(level, ownerId).stream()
				.filter(entry -> targetId.equals(entry.targetId()))
				.toList();
	}

	/** Deep anchoring is a derived predicate: at least one live target anchor is exactly depth three. */
	public static boolean isDeeplyAnchored(ServerLevel level, UUID ownerId, UUID targetId) {
		return anchorsOnTarget(level, ownerId, targetId).stream().anyMatch(entry -> entry.depth() == 3);
	}

	/** Discards every live embedded nail owned by {@code ownerId} in one loaded level. */
	public static int discardOwned(ServerLevel level, UUID ownerId) {
		int discarded = 0;
		for (Entry entry : ownedAnchors(level, ownerId)) {
			Entity entity = level.getEntity(entry.nailId());
			if (entity instanceof ProjectJjkNailEntity nail && !nail.isRemoved()) {
				nail.discard();
				discarded++;
			}
		}
		return discarded;
	}

	/** Discards one owner's anchors across all loaded dimensions without forcing chunk loads. */
	public static int discardOwned(MinecraftServer server, UUID ownerId) {
		if (server == null || ownerId == null) {
			return 0;
		}
		int discarded = 0;
		for (ServerLevel level : server.getAllLevels()) {
			discarded += discardOwned(level, ownerId);
		}
		return discarded;
	}

	/** Discards owned anchors across all loaded dimensions without forcing chunk loads. */
	public static int discardOwned(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		int discarded = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID ownerId : List.copyOf(ownerIds(level))) {
				discarded += discardOwned(level, ownerId);
			}
		}
		return discarded;
	}

	/** Drops every indexed entry. Physical entities are intentionally left in the world. */
	public static void clearAll() {
		BY_LEVEL.clear();
	}

	private static List<UUID> ownerIds(ServerLevel level) {
		Map<UUID, LinkedHashMap<UUID, Entry>> byOwner = BY_LEVEL.get(level);
		return byOwner == null ? List.of() : new ArrayList<>(byOwner.keySet());
	}

	private static List<Entry> validatedEntries(ServerLevel level, UUID ownerId, LinkedHashMap<UUID, Entry> owned) {
		List<Entry> result = new ArrayList<>(owned.size());
		Iterator<Map.Entry<UUID, Entry>> entries = owned.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Entry> indexed = entries.next();
			Entity entity = level.getEntity(indexed.getKey());
			if (!(entity instanceof ProjectJjkNailEntity nail)
					|| !isLiveLoadedNail(level, nail, ownerId)) {
				entries.remove();
				continue;
			}
			Entry current = nail.anchorRecord();
			indexed.setValue(current);
			result.add(current);
		}
		return result;
	}

	private static void pruneStale(ServerLevel level, UUID ownerId, LinkedHashMap<UUID, Entry> owned) {
		Iterator<Map.Entry<UUID, Entry>> entries = owned.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Entry> indexed = entries.next();
			Entity entity = level.getEntity(indexed.getKey());
			if (!(entity instanceof ProjectJjkNailEntity nail)
					|| !isLiveLoadedNail(level, nail, ownerId)) {
				entries.remove();
			}
		}
	}

	private static boolean isLiveLoadedNail(ServerLevel level, ProjectJjkNailEntity nail, UUID ownerId) {
		return level != null
				&& nail != null
				&& ownerId != null
				&& !nail.isRemoved()
				&& nail.level() == level
				&& nail.isEmbedded()
				&& nail.isOwnedBy(ownerId)
				&& level.getEntity(nail.getUUID()) == nail;
	}

	private static void removeEmpty(ServerLevel level, UUID ownerId, LinkedHashMap<UUID, Entry> owned) {
		if (!owned.isEmpty()) {
			return;
		}
		Map<UUID, LinkedHashMap<UUID, Entry>> byOwner = BY_LEVEL.get(level);
		if (byOwner == null) {
			return;
		}
		byOwner.remove(ownerId, owned);
		if (byOwner.isEmpty()) {
			BY_LEVEL.remove(level, byOwner);
		}
	}
}
