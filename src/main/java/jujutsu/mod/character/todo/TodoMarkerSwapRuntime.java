package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.network.JujutsuNetworking;

/**
 * Boogie Woogie onto a thrown mark. This is not a separate ability: the primary cast falls back here when
 * nothing eligible is under the crosshair, so the natural priority holds — an enemy in the crosshair is
 * what you meant, and the mark is what you set up in advance.
 *
 * <p>Both mark forms are STRICT on placement. The last-resort fallback exists for Todo's own snap swaps;
 * a mark swap is planned and telegraphed, so there is no reason to force a point for it.
 *
 * <p>A mark is consumed by the swap it enables. It is not a reusable anchor.
 */
public final class TodoMarkerSwapRuntime {
	private TodoMarkerSwapRuntime() {}

	static boolean hasMark(ServerPlayer todo, ServerLevel level) {
		return TodoSwapMarks.active(level.getServer(), todo.getUUID(), level) != null;
	}

	static boolean swapWithMark(ServerPlayer todo, ServerLevel level, boolean notify) {
		TodoSwapMark mark = TodoSwapMarks.active(level.getServer(), todo.getUUID(), level);
		if (mark == null) {
			return false;
		}
		LivingEntity marked = mark.form() == TodoSwapMark.Form.ENTITY ? resolveMarked(todo, level, mark) : null;
		if (mark.form() == TodoSwapMark.Form.ENTITY && marked == null) {
			TodoSwapMarks.clear(level.getServer(), todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "marked body is gone");
		}
		Vec3 destination = mark.destination(marked == null ? null : marked.position());
		if (todo.position().distanceToSqr(destination) > TodoProfile.MARKER_SWAP_RANGE * TodoProfile.MARKER_SWAP_RANGE) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.out_of_range", "mark is out of reach");
		}
		return marked == null
				? swapWithPosition(todo, level, destination, notify)
				: swapWithMarkedBody(todo, level, marked, notify);
	}

	/** One body moves, so atomicity is trivial: either Todo's destination is safe or nothing happens. */
	private static boolean swapWithPosition(ServerPlayer todo, ServerLevel level, Vec3 destination, boolean notify) {
		Vec3 safe = TodoBoogieWoogieRuntime.findSafeDestination(level, todo, destination,
				TodoBoogieWoogieRuntime.Strictness.STRICT);
		if (safe == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no strict destination at the mark");
		}
		TodoBoogieWoogieRuntime.Snapshot snapshot = TodoBoogieWoogieRuntime.Snapshot.capture(todo);
		if (!TodoBoogieWoogieRuntime.place(todo, level, safe, snapshot)) {
			TodoBoogieWoogieRuntime.restore(todo, snapshot);
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(todo, snapshot);
		// One body between two points: the ribbon still spans the throw, but only Todo landed anywhere.
		finish(todo, level, snapshot.position(), safe, List.of(new TodoBoogieWoogieRuntime.MovedBody(snapshot, safe)));
		return true;
	}

	/** Two bodies move, so it runs the ordinary atomic plan with no fallback for either side. */
	private static boolean swapWithMarkedBody(ServerPlayer todo, ServerLevel level, LivingEntity marked, boolean notify) {
		TodoBoogieWoogieRuntime.Snapshot todoSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(todo);
		TodoBoogieWoogieRuntime.Snapshot markedSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(marked);
		Optional<TodoSwapPlan> plan = TodoSwapPlan.preflight(
				TodoBoogieWoogieRuntime.findSafeDestination(level, todo, markedSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT),
				TodoBoogieWoogieRuntime.findSafeDestination(level, marked, todoSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT)
		);
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no strict destination for the marked pair");
		}
		boolean todoPlaced = TodoBoogieWoogieRuntime.place(todo, level, plan.get().firstDestination(), todoSnapshot);
		boolean markedPlaced = todoPlaced && TodoBoogieWoogieRuntime.place(marked, level, plan.get().secondDestination(), markedSnapshot);
		if (!todoPlaced || !markedPlaced) {
			boolean todoRestored = TodoBoogieWoogieRuntime.restore(todo, todoSnapshot);
			boolean markedRestored = TodoBoogieWoogieRuntime.restore(marked, markedSnapshot);
			if (!todoRestored || !markedRestored) {
				JujutsuMod.LOGGER.error(
						"Todo marker swap rollback incomplete player={} marked={} todoRestored={} markedRestored={}",
						todo.getGameProfile().getName(), marked.getName().getString(), todoRestored, markedRestored);
			}
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(todo, todoSnapshot);
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(marked, markedSnapshot);
		finish(todo, level, todoSnapshot.position(), markedSnapshot.position(),
				List.of(new TodoBoogieWoogieRuntime.MovedBody(todoSnapshot, plan.get().firstDestination()),
						new TodoBoogieWoogieRuntime.MovedBody(markedSnapshot, plan.get().secondDestination())));
		return true;
	}

	/** Consumes the mark, takes the ordinary swap cooldown, and presents an ordinary swap. */
	private static void finish(ServerPlayer todo, ServerLevel level, Vec3 todoOrigin, Vec3 markOrigin,
			List<TodoBoogieWoogieRuntime.MovedBody> moved) {
		TodoSwapMarks.clear(level.getServer(), todo.getUUID());
		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.PRIMARY,
				TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		TodoBoogieWoogieRuntime.emitSwapImpact(level, todo, todoOrigin, markOrigin.subtract(todoOrigin),
				todoOrigin, markOrigin, moved);
		JujutsuMod.LOGGER.debug("Todo marker swap success player={} from={} to={}",
				todo.getGameProfile().getName(), todoOrigin, markOrigin);
	}

	/**
	 * The id finds it, the UUID proves it is the same body, and {@code isEligibleTarget} proves it is still
	 * safe to move — a marked body can be mounted, leashed or boarded during the mark's ten seconds, and
	 * teleporting it then is exactly what {@code TodoTargetSafety} exists to prevent.
	 *
	 * <p>Line of sight is deliberately <em>not</em> required. A thrown mark's value includes reaching a
	 * spot the caster can no longer see, which is why this is the one swap path that does not check it.
	 */
	private static LivingEntity resolveMarked(ServerPlayer todo, ServerLevel level, TodoSwapMark mark) {
		Entity entity = level.getEntity(mark.entityId());
		if (!(entity instanceof LivingEntity marked) || !marked.getUUID().equals(mark.entityUuid())
				|| marked.isRemoved() || !marked.isAlive() || marked.isSpectator()
				|| !TodoBoogieWoogieRuntime.isEligibleTarget(todo, marked)) {
			return null;
		}
		return marked;
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo marker swap rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
