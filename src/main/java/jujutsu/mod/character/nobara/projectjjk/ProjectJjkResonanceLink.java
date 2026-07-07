package jujutsu.mod.character.nobara.projectjjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caster resonance link: the straw doll "binds" to a marked target so a later strike can be
 * transmitted remotely through walls and range. Pure state keyed by caster UUID.
 */
public final class ProjectJjkResonanceLink {
	private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

	private ProjectJjkResonanceLink() {}

	public static void bind(UUID casterId, UUID targetId, int targetEntityId, long gameTime) {
		LINKS.put(casterId, new Link(targetId, targetEntityId, gameTime));
	}

	public static Link get(UUID casterId) {
		return LINKS.get(casterId);
	}

	public static boolean isValid(UUID casterId, long gameTime) {
		Link link = LINKS.get(casterId);
		return link != null && gameTime - link.boundAtGameTime() <= ProjectJjkNobaraProfile.MARK_DURATION_TICKS;
	}

	public static void clear(UUID casterId) {
		LINKS.remove(casterId);
	}

	public record Link(UUID targetId, int targetEntityId, long boundAtGameTime) {}
}
