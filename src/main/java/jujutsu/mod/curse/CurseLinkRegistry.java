package jujutsu.mod.curse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class CurseLinkRegistry {
	public static final CurseLinkRegistry GLOBAL = new CurseLinkRegistry();
	private final Map<UUID, CurseLink> links = new ConcurrentHashMap<>();

	public CurseLink createLink(UUID source, ResourceLocation techniqueId, Set<UUID> participants, long gameTime) {
		CurseLink link = new CurseLink(UUID.randomUUID(), source, techniqueId, participants, gameTime);
		links.put(link.id(), link);
		return link;
	}

	public CurseLink get(UUID id) { return id == null ? null : links.get(id); }
	public boolean removeLink(UUID id) { return id != null && links.remove(id) != null; }
	public int removeLinksOwnedBy(UUID source) {
		int before = links.size();
		links.entrySet().removeIf(entry -> entry.getValue().sourceId().equals(source));
		return before - links.size();
	}
	public List<CurseLink> linksForParticipant(UUID participant) {
		List<CurseLink> result = new ArrayList<>();
		for (CurseLink link : links.values()) if (link.participants().contains(participant)) result.add(link);
		result.sort(Comparator.comparingLong(CurseLink::createdAt).thenComparing(CurseLink::id));
		return List.copyOf(result);
	}
	public void clear() { links.clear(); }
}
