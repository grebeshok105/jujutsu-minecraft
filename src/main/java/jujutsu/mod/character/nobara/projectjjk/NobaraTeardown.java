package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drops per-caster Nobara state when the cast session ends: vessel deselect, disconnect, caster
 * death, or server stop.
 *
 * <p>World state is deliberately NOT touched here (design D6): embedded anchors, nail traps and a
 * running hairpin chain belong to the arena, not to the caster's session, so they outlive a
 * disconnect or a death exactly like a placed block would.
 */
public final class NobaraTeardown {
	private NobaraTeardown() {}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onCastStateLost(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onCastStateLost(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				onCastStateLost(player);
			}
		});
	}

	/** Clears every runtime keyed by the caster's UUID. Safe to call for any player. */
	public static void onCastStateLost(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		ProjectJjkNobaraRuntime.clearPlayer(playerId);
		NobaraHammerCombatRuntime.clearPlayer(playerId);
		ProjectJjkStrawDollRuntime.resetCaster(playerId);
		SelfResonanceRuntime.clearCaster(playerId);
	}
}
