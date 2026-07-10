package jujutsu.mod.combat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlackFlashFocus {
	private static final Set<UUID> FOCUSED = ConcurrentHashMap.newKeySet();
	private BlackFlashFocus() {}
	public static boolean hasFocus(UUID entityId) { return FOCUSED.contains(entityId); }
	public static void grant(UUID entityId) { if (entityId != null) FOCUSED.add(entityId); }
	public static void clear(UUID entityId) { FOCUSED.remove(entityId); }
	public static void clearAll() { FOCUSED.clear(); }
}
