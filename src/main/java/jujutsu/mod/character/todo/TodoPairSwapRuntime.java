package jujutsu.mod.character.todo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Boogie Woogie on two bystanders: Todo claps and two other bodies trade places while he stays put.
 *
 * <p>Two casts. The first marks a participant and takes no cooldown; the second resolves the pair and
 * commits. Distance is measured from Todo to each participant and <b>never between the two of them</b> —
 * the whole point of the technique is that the pair can be far apart, so do not "fix" that into a
 * pair-distance limit.
 *
 * <p>Placement runs {@link TodoBoogieWoogieRuntime.Strictness#STRICT}: the last-resort fallback exists so
 * Todo's own mid-air swaps feel good, and that is not a reason to relax safety for someone who did not
 * cast anything. If no clean destination exists the cast cancels with nothing moved.
 */
public final class TodoPairSwapRuntime {
	private static final Map<UUID, TodoPendingSelection> PENDING = new ConcurrentHashMap<>();

	private TodoPairSwapRuntime() {}

	/** Call once from mod init. */
	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TodoPairSwapRuntime::tickSelections);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> forget(newPlayer.getUUID()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	/** Drops a caster's mark. Also the hook for leaving the vessel mid-setup. */
	public static void forget(UUID casterId) {
		PENDING.remove(casterId);
	}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.SECONDARY) {
			return false;
		}
		switch (TodoSwapGates.evaluate(todo)) {
			case UNAVAILABLE -> {
				return false;
			}
			case HANDS_FULL -> {
				return reject(todo, notify, "message.jujutsumod.todo.boogie.hands_full", "item in main or off hand");
			}
			case ALLOWED -> {
			}
		}
		ServerLevel level = todo.level();
		TodoPendingSelection pending = PENDING.get(todo.getUUID());
		if (pending != null && (pending.isExpired(level.getGameTime()) || !pending.isIn(level.dimension()))) {
			PENDING.remove(todo.getUUID());
			pending = null;
		}
		LivingEntity aimed = resolveAimed(todo, level);
		if (pending == null) {
			return mark(todo, level, aimed, notify);
		}
		return commit(todo, level, pending, aimed, notify);
	}

	private static boolean mark(ServerPlayer todo, ServerLevel level, LivingEntity aimed, boolean notify) {
		if (aimed == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed first participant");
		}
		PENDING.put(todo.getUUID(), new TodoPendingSelection(level.dimension(), aimed.getUUID(), aimed.getId(),
				level.getGameTime() + TodoProfile.PAIR_SELECTION_TTL_TICKS));
		// Caster-only, and it is the only feedback: nobody else may learn who has been marked.
		JujutsuNetworking.sendVfxCue(todo, new VfxCue(TodoVfxIds.PAIR_MARK, aimed.position(), aimed.getId(), Vec3.ZERO, 1,
				level.getGameTime(), todo.getRandom().nextLong(), Vec3.ZERO));
		if (notify) {
			todo.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.marked", aimed.getDisplayName()), true);
		}
		JujutsuMod.LOGGER.debug("Todo pair swap marked player={} target={}",
				todo.getGameProfile().getName(), aimed.getName().getString());
		// Marking is free: the cooldown belongs to the swap, not to lining one up.
		return true;
	}

	private static boolean commit(ServerPlayer todo, ServerLevel level, TodoPendingSelection pending, LivingEntity aimed, boolean notify) {
		if (aimed == null) {
			// A miss must not cost the setup, so the mark survives.
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed second participant");
		}
		LivingEntity first = resolveMarked(level, pending);
		if (first == null) {
			PENDING.remove(todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "marked participant is gone");
		}
		if (first == aimed) {
			// Aiming back at the mark is the deliberate cancel, and it costs nothing.
			PENDING.remove(todo.getUUID());
			if (notify) {
				todo.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.cancelled"), true);
			}
			return true;
		}
		if (!TodoBoogieWoogieRuntime.isEligibleTarget(todo, first)) {
			// Recoverable: the mark stays, because a boat ride or a leash can end.
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "marked participant became ineligible");
		}
		if (!inReach(todo, first) || !inReach(todo, aimed)) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.out_of_range", "a participant is out of Todo's reach");
		}
		if (!todo.hasLineOfSight(first) || !todo.hasLineOfSight(aimed)) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "a participant is not visible");
		}

		TodoBoogieWoogieRuntime.Snapshot firstSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(first);
		TodoBoogieWoogieRuntime.Snapshot secondSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(aimed);
		if (firstSnapshot.level() != secondSnapshot.level() || firstSnapshot.level() != level) {
			PENDING.remove(todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "participants are not in one level");
		}
		Optional<TodoSwapPlan> plan = TodoSwapPlan.preflight(
				TodoBoogieWoogieRuntime.findSafeDestination(level, first, secondSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT),
				TodoBoogieWoogieRuntime.findSafeDestination(level, aimed, firstSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT)
		);
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no strict safe destination for a bystander");
		}
		if (first.isRemoved() || aimed.isRemoved() || !first.isAlive() || !aimed.isAlive()
				|| first.level() != level || aimed.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "participant changed before commit");
		}

		boolean firstPlaced = TodoBoogieWoogieRuntime.place(first, level, plan.get().firstDestination(), firstSnapshot);
		boolean secondPlaced = firstPlaced && TodoBoogieWoogieRuntime.place(aimed, level, plan.get().secondDestination(), secondSnapshot);
		if (!firstPlaced || !secondPlaced) {
			boolean firstRestored = TodoBoogieWoogieRuntime.restore(first, firstSnapshot);
			boolean secondRestored = TodoBoogieWoogieRuntime.restore(aimed, secondSnapshot);
			if (!firstRestored || !secondRestored) {
				JujutsuMod.LOGGER.error(
						"Todo pair swap rollback incomplete caster={} first={} second={} firstRestored={} secondRestored={}",
						todo.getGameProfile().getName(),
						first.getName().getString(),
						aimed.getName().getString(),
						firstRestored,
						secondRestored);
			}
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(first, firstSnapshot);
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(aimed, secondSnapshot);

		PENDING.remove(todo.getUUID());
		CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.SECONDARY,
				TodoProfile.PAIR_SWAP_COOLDOWN_TICKS);
		emitPairFeedback(level, todo, firstSnapshot.position(), secondSnapshot.position());
		JujutsuMod.LOGGER.debug("Todo pair swap success caster={} first={} second={}",
				todo.getGameProfile().getName(), first.getName().getString(), aimed.getName().getString());
		return true;
	}

	/**
	 * The clap is Todo's, the endpoints are the two bodies that actually moved. Reusing the ordinary swap
	 * cues on purpose: from a distance a pair swap and a normal one look the same, which is free deception.
	 */
	private static void emitPairFeedback(ServerLevel level, ServerPlayer todo, Vec3 firstOrigin, Vec3 secondOrigin) {
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, todo.position(), todo.getLookAngle());
		long gameTime = level.getGameTime();
		Vec3 pairDelta = secondOrigin.subtract(firstOrigin);
		TodoBoogieWoogieRuntime.broadcastSwapEndpoint(level, todo, firstOrigin, pairDelta, gameTime);
		TodoBoogieWoogieRuntime.broadcastSwapEndpoint(level, todo, secondOrigin, Vec3.ZERO, gameTime);
		TodoBoogieWoogieRuntime.scheduleMoveSound(level, firstOrigin);
		TodoBoogieWoogieRuntime.scheduleMoveSound(level, secondOrigin);
	}

	private static LivingEntity resolveAimed(ServerPlayer todo, ServerLevel level) {
		TargetResolver.Result aimed = TargetResolver.resolve(level, todo, TodoProfile.BOOGIE_WOOGIE_RANGE,
				candidate -> TodoBoogieWoogieRuntime.isEligibleTarget(todo, candidate));
		if (aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()) {
			return null;
		}
		Entity entity = level.getEntity(aimed.entityId().get());
		if (!(entity instanceof LivingEntity target) || !TodoBoogieWoogieRuntime.isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)) {
			return null;
		}
		return target;
	}

	/** The id finds it; the UUID proves the id was not recycled onto some other entity. */
	private static LivingEntity resolveMarked(ServerLevel level, TodoPendingSelection pending) {
		Entity entity = level.getEntity(pending.targetEntityId());
		if (!(entity instanceof LivingEntity marked) || !pending.identifies(marked.getUUID())
				|| marked.isRemoved() || !marked.isAlive() || marked.isSpectator()) {
			return null;
		}
		return marked;
	}

	private static boolean inReach(ServerPlayer todo, LivingEntity participant) {
		return todo.distanceToSqr(participant) <= TodoProfile.BOOGIE_WOOGIE_RANGE * TodoProfile.BOOGIE_WOOGIE_RANGE;
	}

	/**
	 * Expiry sweep. A mark whose entity cannot be found is deliberately left alone rather than reported
	 * lost — an unloaded chunk is not a death, and the commit path verifies liveness anyway.
	 */
	private static void tickSelections(ServerLevel level) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Map.Entry<UUID, TodoPendingSelection> entry : PENDING.entrySet()) {
			TodoPendingSelection pending = entry.getValue();
			if (!pending.isIn(level.dimension())) {
				continue;
			}
			boolean expired = pending.isExpired(now);
			Entity marked = level.getEntity(pending.targetEntityId());
			boolean died = marked instanceof LivingEntity living
					&& pending.identifies(living.getUUID())
					&& (living.isRemoved() || !living.isAlive() || living.isSpectator());
			if (!expired && !died) {
				continue;
			}
			PENDING.remove(entry.getKey());
			if (died) {
				// Expiry is silent; a lost participant is not, because the caster's plan just changed.
				ServerPlayer caster = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (caster != null) {
					caster.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.lost"), true);
				}
			}
		}
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo pair swap rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
