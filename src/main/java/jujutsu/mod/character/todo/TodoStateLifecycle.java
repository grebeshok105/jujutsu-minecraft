package jujutsu.mod.character.todo;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Everything Todo leaves in the world ends the moment the player stops being able to use it.
 *
 * <p>Todo's state used to outlive the player's right to hold it in two ways, and both had the same
 * root. Death cleared nothing at all — every teardown was keyed on <em>respawn</em>, so between the
 * killing blow and clicking the button a dead player kept a live mark, a resting projectile and a
 * glowing body, for a stretch he controls and can hold open indefinitely. And a marker already in
 * flight was gated only at the throw, so switching vessel inside its flight let it land and create a
 * mark <em>after</em> the leaving-the-vessel teardown had already run.
 *
 * <p>So the teardown is one method with several callers rather than a rule each caller remembers.
 * {@link TodoDefinition#onDeselected} delegates here instead of listing the same calls itself.
 */
public final class TodoStateLifecycle {
	private TodoStateLifecycle() {}

	/** Call once from {@link TodoDefinition#registerServerHooks()}. */
	public static void register() {
		// Death, not respawn. The other teardowns already have their own listeners inside the classes
		// that own each piece of state; this is the trigger none of them had.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				dropEverything(player);
			}
		});
		// TodoSwapMarks and TodoPairSwapRuntime already drop their own state on disconnect. A projectile
		// still in the air belongs to neither of them, so it needs saying here.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				discardMarkersInFlight(server, handler.player.getUUID()));
	}

	/**
	 * Drops every trace of Todo this player owns: the pair selection, the mark and whatever it put in
	 * the world, any marker still in flight, and the momentum window.
	 *
	 * <p>Order matters once: marks are cleared before the in-flight sweep, because clearing a landed
	 * mark discards the projectile that <em>is</em> that mark, and the sweep would otherwise be asked to
	 * discard an entity that is already gone. Both are safe either way; this order just means each
	 * projectile is handled by the path that knows what it was for.
	 */
	public static void dropEverything(ServerPlayer player) {
		TodoPairSwapRuntime.forget(player.getUUID());
		TodoSwapMarks.clear(player.getServer(), player.getUUID());
		discardMarkersInFlight(player.getServer(), player.getUUID());
		player.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
	}

	/**
	 * Removes this owner's thrown markers from every loaded level.
	 *
	 * <p>Swept across all levels rather than the player's own, because the throw and the reason to drop
	 * it can happen in different dimensions and a marker left behind in the old one would be invisible
	 * to a single-level sweep. The owner is matched on {@link TodoSwapMarkerEntity#thrownBy()}, which is
	 * captured at construction — {@code getOwner()} resolves an entity and returns null once that entity
	 * is gone, which is exactly the case this runs in.
	 */
	static void discardMarkersInFlight(MinecraftServer server, UUID owner) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (TodoSwapMarkerEntity marker : level.getEntities(
					EntityTypeTest.forClass(TodoSwapMarkerEntity.class),
					candidate -> owner.equals(candidate.thrownBy()))) {
				marker.discard();
			}
		}
	}
}
