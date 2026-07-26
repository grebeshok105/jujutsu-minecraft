package jujutsu.mod.character.todo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

	/**
	 * Puts a mark on a body, glow and all. The one place that does it, because the order below is
	 * load-bearing and two copies of it would drift.
	 *
	 * <p>The previous mark is released <em>before</em> the glow is read. Re-marking the same body would
	 * otherwise see the glow the old mark had applied, conclude it was not ours, and then the old mark's
	 * release would switch it off — leaving the new mark live with no highlight at all.
	 */
	static void markBody(ServerLevel level, UUID owner, LivingEntity struck) {
		clear(level.getServer(), owner);
		// Only claim a glow we switched on, so marking never extinguishes another system's highlight.
		boolean glowApplied = !struck.hasGlowingTag();
		if (glowApplied) {
			struck.setGlowingTag(true);
		}
		mark(level, owner, TodoSwapMark.onEntity(level.dimension(), struck.position(), struck.getId(),
				struck.getUUID(), glowApplied, level.getGameTime() + TodoProfile.MARKER_BODY_MARK_TTL_TICKS));
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

	/**
	 * What a completed swap costs its mark. The single place that decides whether a mark is spent, so a
	 * charge limit or any other price lands here and nowhere in the runtimes.
	 *
	 * <p>A landed mark is an anchor and survives the swap it enabled. A body mark is consumed: following a
	 * living target is worth one trip, not a leash.
	 */
	static void onUsed(MinecraftServer server, UUID owner, TodoSwapMark mark) {
		if (mark.form() == TodoSwapMark.Form.ENTITY) {
			clear(server, owner);
		}
	}

	/** Also the hook for leaving the vessel mid-mark, from the character-selection path. */
	public static void clear(MinecraftServer server, UUID owner) {
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
				continue;
			}
			if (landedMarkerIsGone(level, mark)) {
				clear(level.getServer(), entry.getKey());
				// Expiry is silent, but losing an anchor is not: the owner's route just stopped existing.
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (owner != null) {
					owner.displayClientMessage(Component.translatable("message.jujutsumod.todo.mark.lost"), true);
				}
			}
		}
	}

	/**
	 * A landed mark is only as permanent as the projectile that is the mark.
	 *
	 * <p>The chunk is checked before the entity, and that order is the whole point. {@code TODO_SWAP_MARKER}
	 * is registered with {@code noSave()}, so an unloaded chunk removes the projectile and never brings it
	 * back — while {@link #markedBodyIsGone} deliberately refuses to read an unresolvable entity as dead.
	 * Without this sweep, walking out of render distance and back would leave a working teleport anchor with
	 * no marker anywhere in the world. Absence in a <em>loaded</em> chunk is the end of the mark; absence in
	 * an unloaded one is not, and the same rule covers an explosion, a {@code /kill} and any third-party
	 * cleanup for free.
	 */
	private static boolean landedMarkerIsGone(ServerLevel level, TodoSwapMark mark) {
		if (mark.form() != TodoSwapMark.Form.POSITION) {
			return false;
		}
		BlockPos at = BlockPos.containing(mark.position());
		if (!level.getChunkSource().hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
			return false;
		}
		return level.getEntity(mark.entityId()) == null;
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
