package jujutsu.mod.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ForcedBlackFlash {
	private static final Set<UUID> ENABLED = new HashSet<>();

	private ForcedBlackFlash() {}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENABLED.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ENABLED.clear());
	}

	public static boolean isEnabled(ServerPlayer player) { return ENABLED.contains(player.getUUID()); }
	public static boolean set(ServerPlayer player, boolean enabled) {
		if (enabled) ENABLED.add(player.getUUID()); else ENABLED.remove(player.getUUID());
		return enabled;
	}
}
