package jujutsu.mod.character.todo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One live thrown mark per Todo, and the single owner of its lifetime.
 *
 * <p>The two mark forms leave different traces in the world — a resting projectile, or a glow on a body —
 * and every way a mark can end funnels through {@link #release}, so neither trace can be orphaned.
 */
public final class TodoSwapMarks {
	private static final Map<UUID, TodoSwapMark> MARKS = new ConcurrentHashMap<>();

	private TodoSwapMarks() {}

	/** Call once from mod init. */
	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TodoSwapMarks::tickMarks);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(server, handler.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clear(newPlayer.getServer(), newPlayer.getUUID()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				clear(player.getServer(), player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID owner : Map.copyOf(MARKS).keySet()) {
				clear(server, owner);
			}
			MARKS.clear();
		});
	}

	/** Replaces any previous mark, so a Todo can never be holding two at once. */
	static void mark(ServerLevel level, UUID owner, TodoSwapMark mark) {
		clear(level.getServer(), owner);
		MARKS.put(owner, mark);
	}

	/** The owner's usable mark, or null when there is none, it has expired, or it is in another dimension. */
	static TodoSwapMark active(MinecraftServer server, UUID owner, ServerLevel level) {
		TodoSwapMark mark = MARKS.get(owner);
		if (mark == null) {
			return null;
		}
		if (!mark.isIn(level.dimension()) || mark.isExpired(level.getGameTime())) {
			clear(server, owner);
			return null;
		}
		return mark;
	}

	static void clear(MinecraftServer server, UUID owner) {
		TodoSwapMark previous = MARKS.remove(owner);
		if (previous != null) {
			release(server, previous);
		}
	}

	/** Undoes what the mark put into the world: the resting projectile, or a glow that was ours. */
	private static void release(MinecraftServer server, TodoSwapMark mark) {
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(mark.dimension());
		if (level == null) {
			return;
		}
		Entity entity = level.getEntity(mark.entityId());
		if (entity == null) {
			return;
		}
		switch (mark.form()) {
			case POSITION -> entity.discard();
			case ENTITY -> {
				if (mark.glowApplied() && entity.getUUID().equals(mark.entityUuid())) {
					entity.setGlowingTag(false);
				}
			}
		}
	}

	private static void tickMarks(ServerLevel level) {
		if (MARKS.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Map.Entry<UUID, TodoSwapMark> entry : MARKS.entrySet()) {
			TodoSwapMark mark = entry.getValue();
			if (!mark.isIn(level.dimension())) {
				continue;
			}
			if (mark.isExpired(now) || markedBodyIsGone(level, mark)) {
				clear(level.getServer(), entry.getKey());
			}
		}
	}

	/**
	 * Only an entity mark can lose its body this way. An unresolvable entity is not treated as gone — an
	 * unloaded chunk is not a death, and a position mark stays valid regardless of its projectile.
	 */
	private static boolean markedBodyIsGone(ServerLevel level, TodoSwapMark mark) {
		if (mark.form() != TodoSwapMark.Form.ENTITY) {
			return false;
		}
		Entity entity = level.getEntity(mark.entityId());
		return entity instanceof LivingEntity living
				&& living.getUUID().equals(mark.entityUuid())
				&& (living.isRemoved() || !living.isAlive() || living.isSpectator());
	}
}
