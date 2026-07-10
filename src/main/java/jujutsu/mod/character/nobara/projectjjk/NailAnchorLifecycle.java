package jujutsu.mod.character.nobara.projectjjk;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;

public final class NailAnchorLifecycle {
	private static final Set<UUID> CONFIRMED_REMOVED = ConcurrentHashMap.newKeySet();

	private NailAnchorLifecycle() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> confirmRemoved(entity.getUUID()));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK && reason != Entity.RemovalReason.CHANGED_DIMENSION) {
				confirmRemoved(entity.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CONFIRMED_REMOVED.clear());
	}

	public static void confirmRemoved(UUID id) {
		if (id != null) {
			CONFIRMED_REMOVED.add(id);
		}
	}

	public static boolean isConfirmedRemoved(UUID id) {
		return id != null && CONFIRMED_REMOVED.contains(id);
	}

	public static void observeLoaded(UUID id) {
		if (id != null) {
			CONFIRMED_REMOVED.remove(id);
		}
	}
}
