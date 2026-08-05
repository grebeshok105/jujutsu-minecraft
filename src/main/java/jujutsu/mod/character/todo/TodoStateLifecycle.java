package jujutsu.mod.character.todo;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Everything Todo leaves in the world ends the moment the player stops being able to use it.
 *
 * <p>Todo's transient state used to outlive the player's right to hold it: death cleared nothing at
 * all — every teardown was keyed on <em>respawn</em>, so between the killing blow and clicking the
 * button a dead player kept his state for a stretch he controls and can hold open indefinitely. And
 * each piece of state registered its own listeners, so one more piece meant one more place to forget
 * a cleanup path.
 *
 * <p>So the teardown is one method with several callers rather than a rule each caller remembers.
 * {@link TodoDefinition#onDeselected} delegates here instead of listing the same calls itself.
 *
 * <p>This class is the only registrar of Todo's lifecycle hooks. Every cleanup route — death,
 * respawn, dimension change, disconnect, server stop, and the per-tick expiry sweep — funnels into
 * {@link TodoTransientState}, the single owner of Todo's transient server state, so a piece of state
 * added there is automatically covered by every exit guarded here.
 */
public final class TodoStateLifecycle {
	private TodoStateLifecycle() {}

	/** Call once from {@link TodoDefinition#registerServerHooks()}. */
	public static void register() {
		// Death, not respawn. Every teardown Todo had was keyed on AFTER_RESPAWN, so between the
		// killing blow and clicking the button — a stretch the player controls and can hold open
		// indefinitely — his state survived. Respawn stays wired as the same teardown: it costs
		// nothing, and it keeps the guarantee even if a future exit path misses the death event.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				dropEverything(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> dropEverything(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				dropEverything(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> dropEverything(handler.player));
		// The stone's entity and the pair selection both live in TodoTransientState; the server owns
		// their expiry, so the sweep is server-level rather than one handler per level.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TodoTransientState.clearAll(server));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TodoPairSwapRuntime.serverTick(server);
			TodoStoneRuntime.serverTick(server);
		});
	}

	/**
	 * Drops every trace of Todo this player owns: the pair selection, the stone (discarding its live
	 * entity), and the momentum window. {@link TodoTransientState#dropAll} is the only path transient
	 * state exits through, so every cleanup hook and the vessel change land here.
	 */
	public static void dropEverything(ServerPlayer player) {
		TodoTransientState.dropAll(player.getServer(), player.getUUID());
		player.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
	}
}
