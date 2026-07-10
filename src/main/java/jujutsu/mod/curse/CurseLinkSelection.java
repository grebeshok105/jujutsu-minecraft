package jujutsu.mod.curse;

import java.util.List;
import java.util.UUID;

public record CurseLinkSelection(Status status, CurseLink link) {
	public enum Status { NONE, NEEDS_SELECTION, READY, INVALID_SELECTION }

	public static CurseLinkSelection resolve(List<CurseLink> links, UUID selectedId) {
		if (links == null || links.isEmpty()) return new CurseLinkSelection(Status.NONE, null);
		if (links.size() == 1) return new CurseLinkSelection(Status.READY, links.getFirst());
		if (selectedId == null) return new CurseLinkSelection(Status.NEEDS_SELECTION, null);
		for (CurseLink link : links) if (link.id().equals(selectedId)) return new CurseLinkSelection(Status.READY, link);
		return new CurseLinkSelection(Status.INVALID_SELECTION, null);
	}
}
