package jujutsu.mod.curse;

import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record CurseLink(UUID id, UUID sourceId, ResourceLocation techniqueId, Set<UUID> participants, long createdAt) {
	public CurseLink {
		if (id == null || sourceId == null || techniqueId == null || participants == null || participants.isEmpty()) throw new IllegalArgumentException("Invalid curse link");
		participants = Set.copyOf(participants);
	}
}
