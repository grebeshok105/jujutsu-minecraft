package jujutsu.mod.character.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import jujutsu.mod.vfx.VfxCues;

/**
 * Boogie Woogie on bystanders, in two forms on one key pair.
 *
 * <p><b>B → B (pair)</b>: the first press marks an aimed participant and takes no cooldown; the second
 * press resolves the pair and commits while Todo stays put. Distance is measured from Todo to each
 * participant and <b>never between the two of them</b> — the whole point of the technique is that the
 * pair can be far apart, so do not "fix" that into a pair-distance limit.
 *
 * <p><b>B → Shift+B (triple)</b>: with a live selection A, casting Shift+B on a second target T runs
 * the triple cyclic swap — Todo to A's position, A to T's position, T to Todo's position. The
 * direction is fixed and test-pinned. Shift+B without a selection refuses; it never degrades into a
 * pair press. The selection survives the transition and is consumed only by a successful commit.
 *
 * <p>Placement runs {@link TodoBoogieWoogieRuntime.Strictness#STRICT}: the last-resort fallback exists so
 * Todo's own mid-air swaps feel good, and that is not a reason to relax safety for someone who did not
 * cast anything. If no clean destination exists the cast cancels with nothing moved.
 *
 * <p>Selection storage is {@link TodoTransientState} — this class keeps no static map — and every
 * lifecycle hook belongs to {@link TodoStateLifecycle}, which drives {@link #serverTick} from its
 * END_SERVER_TICK hook.
 */
public final class TodoPairSwapRuntime {
	private TodoPairSwapRuntime() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability == CharacterAbility.SECONDARY) {
			return pairCast(todo, notify);
		}
		if (ability == CharacterAbility.SECONDARY_SNEAK) {
			return tripleCast(todo, notify);
		}
		return false;
	}

	private static boolean pairCast(ServerPlayer todo, boolean notify) {
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
		TodoPendingSelection pending = liveSelection(todo, level);
		LivingEntity aimed = resolveAimed(todo, level);
		if (pending == null) {
			return mark(todo, level, aimed, notify);
		}
		return commit(todo, level, pending, aimed, notify);
	}

	/**
	 * The live selection for this caster in this level, or {@code null}. A selection whose clock has run
	 * out or that belongs to another dimension is dropped first — a stale mark must never be consumed by
	 * a cast that comes after the moment the player could reasonably act on it.
	 */
	private static TodoPendingSelection liveSelection(ServerPlayer todo, ServerLevel level) {
		TodoPendingSelection pending = TodoTransientState.pairSelection(todo.getUUID()).orElse(null);
		if (pending != null && (pending.isExpired(level.getGameTime()) || !pending.isIn(level.dimension()))) {
			TodoTransientState.clearPairSelection(todo.getUUID());
			pending = null;
		}
		return pending;
	}

	private static boolean mark(ServerPlayer todo, ServerLevel level, LivingEntity aimed, boolean notify) {
		if (aimed == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed first participant");
		}
		TodoTransientState.setPairSelection(todo.getUUID(), new TodoPendingSelection(level.dimension(), aimed.getUUID(),
				aimed.getId(), level.getGameTime() + TodoProfile.PAIR_SELECTION_TTL_TICKS));
		// Caster-only, and it is the only feedback: nobody else may learn who has been marked.
		JujutsuNetworking.sendVfxCue(todo,
				VfxCues.anchored(TodoVfxIds.PAIR_MARK, aimed.position(), aimed.getId(), aimed.position(), 1,
						level.getGameTime(), todo.getRandom().nextLong()));
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
			TodoTransientState.clearPairSelection(todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "marked participant is gone");
		}
		if (first == aimed) {
			// Aiming back at the mark is the deliberate cancel, and it costs nothing.
			TodoTransientState.clearPairSelection(todo.getUUID());
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
			TodoTransientState.clearPairSelection(todo.getUUID());
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
			TodoBoogieWoogieRuntime.rollback("pair swap", todo, first, firstSnapshot, aimed, secondSnapshot);
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(first, firstSnapshot);
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(aimed, secondSnapshot);

		TodoTransientState.clearPairSelection(todo.getUUID());
		CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.SECONDARY,
				TodoProfile.PAIR_SWAP_COOLDOWN_TICKS);
		emitPairFeedback(level, todo, firstSnapshot, plan.get().firstDestination(), secondSnapshot, plan.get().secondDestination());
		JujutsuMod.LOGGER.debug("Todo pair swap success caster={} first={} second={}",
				todo.getGameProfile().getName(), first.getName().getString(), aimed.getName().getString());
		return true;
	}

	/**
	 * The triple cycle: Todo → A's position, A → T's position, T → Todo's position.
	 *
	 * <p>Every refusal keeps the selection, so a failed or mis-aimed Shift+B never costs the setup the
	 * player already paid for with a B press. The selection is consumed only by a successful commit.
	 */
	private static boolean tripleCast(ServerPlayer todo, boolean notify) {
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
		TodoPendingSelection pending = liveSelection(todo, level);
		if (pending == null) {
			// Shift+B never silently degrades into B: without a first participant there is no cycle.
			return reject(todo, notify, "message.jujutsumod.todo.triple.no_first",
					"triple requires a live first selection");
		}
		LivingEntity first = resolveMarked(level, pending);
		if (first == null) {
			TodoTransientState.clearPairSelection(todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "marked participant is gone");
		}
		LivingEntity aimed = resolveAimed(todo, level);
		if (aimed == null) {
			// A miss must not cost the setup, so the selection survives.
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed third participant");
		}
		if (aimed == first) {
			// The cycle needs a third body; the marked one cannot play both parts. The selection stays.
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target",
					"the aimed body is the marked one");
		}
		if (!TodoBoogieWoogieRuntime.isEligibleTarget(todo, first)) {
			// Recoverable: the selection stays, because a boat ride or a leash can end.
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "marked participant became ineligible");
		}
		if (!inReach(todo, first) || !inReach(todo, aimed)) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.out_of_range", "a participant is out of Todo's reach");
		}
		if (!todo.hasLineOfSight(first) || !todo.hasLineOfSight(aimed)) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "a participant is not visible");
		}

		TodoBoogieWoogieRuntime.Snapshot todoSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(todo);
		TodoBoogieWoogieRuntime.Snapshot firstSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(first);
		TodoBoogieWoogieRuntime.Snapshot thirdSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(aimed);
		if (todoSnapshot.level() != firstSnapshot.level() || todoSnapshot.level() != thirdSnapshot.level()) {
			TodoTransientState.clearPairSelection(todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "participants are not in one level");
		}
		// Cycle direction, fixed and test-pinned: each body lands where the next one stood. All three
		// destinations are STRICT — the cycle moves Todo too, and his own mid-air fallback stays his
		// private luxury for the aimed swap, not a policy for a three-body commitment.
		Optional<TodoTripleSwapPlan> plan = TodoTripleSwapPlan.preflight(
				TodoBoogieWoogieRuntime.findSafeDestination(level, todo, firstSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT),
				TodoBoogieWoogieRuntime.findSafeDestination(level, first, thirdSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT),
				TodoBoogieWoogieRuntime.findSafeDestination(level, aimed, todoSnapshot.position(), TodoBoogieWoogieRuntime.Strictness.STRICT)
		);
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.triple.unsafe", "no strict safe destination for the cycle");
		}
		if (todo.isRemoved() || first.isRemoved() || aimed.isRemoved()
				|| !todo.isAlive() || !first.isAlive() || !aimed.isAlive()
				|| todo.level() != level || first.level() != level || aimed.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "participant changed before commit");
		}

		List<PlacedBody> placed = new ArrayList<>(3);
		boolean todoPlaced = TodoBoogieWoogieRuntime.place(todo, level, plan.get().todoDestination(), todoSnapshot);
		if (todoPlaced) {
			placed.add(new PlacedBody(todo, todoSnapshot));
		}
		boolean firstPlaced = todoPlaced && TodoBoogieWoogieRuntime.place(first, level, plan.get().aDestination(), firstSnapshot);
		if (firstPlaced) {
			placed.add(new PlacedBody(first, firstSnapshot));
		}
		boolean thirdPlaced = firstPlaced && TodoBoogieWoogieRuntime.place(aimed, level, plan.get().tDestination(), thirdSnapshot);
		if (thirdPlaced) {
			placed.add(new PlacedBody(aimed, thirdSnapshot));
		}
		if (!todoPlaced || !firstPlaced || !thirdPlaced) {
			rollbackTriple(todo, placed, plan.get());
			return reject(todo, notify, "message.jujutsumod.todo.triple.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(todo, todoSnapshot);
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(first, firstSnapshot);
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(aimed, thirdSnapshot);

		TodoTransientState.clearPairSelection(todo.getUUID());
		CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY_SNEAK, TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.SECONDARY_SNEAK,
				TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS);
		emitTripleFeedback(level, todo, todoSnapshot, plan.get(), firstSnapshot, thirdSnapshot);
		JujutsuMod.LOGGER.debug("Todo triple swap success caster={} first={} third={}",
				todo.getGameProfile().getName(), first.getName().getString(), aimed.getName().getString());
		return true;
	}

	/** One body a commit has already placed; a rollback restores these in reverse placement order. */
	private record PlacedBody(LivingEntity entity, TodoBoogieWoogieRuntime.Snapshot snapshot) {}

	/**
	 * Rolls a failed triple commit back to the snapshots, last placed first.
	 *
	 * <p>The cycle moves three bodies, so a failure after the first placement must not strand anyone on
	 * a destination the cycle never completed — the reverse-order restore makes every body end where it
	 * stood before the cast, and the error log is the evidence that the failure happened and was rolled
	 * back. A partial cycle without that log line is a bug this method exists to make impossible.
	 */
	private static void rollbackTriple(ServerPlayer caster, List<PlacedBody> placed, TodoTripleSwapPlan plan) {
		for (int i = placed.size() - 1; i >= 0; i--) {
			PlacedBody body = placed.get(i);
			if (!TodoBoogieWoogieRuntime.restore(body.entity(), body.snapshot())) {
				JujutsuMod.LOGGER.error("Todo triple swap rollback incomplete caster={} body={} snapshot={}",
						caster.getGameProfile().getName(), body.entity().getName().getString(),
						body.snapshot().position());
			}
		}
		JujutsuMod.LOGGER.error("Todo triple swap commit failed caster={} moved={} destinations todo={} a={} t={}",
				caster.getGameProfile().getName(), placed.size(),
				plan.todoDestination(), plan.aDestination(), plan.tDestination());
	}

	/**
	 * The cycle's own presentation: three TRIPLE_SWAP edges, one per step of the cycle with the flow in
	 * the direction, plus the ordinary afterimage and arrival for each body that actually moved. No clap
	 * and no endpoint ribbon: the pair swap reads as one clap because Todo stays put, but a cycle that
	 * moves Todo too has its own language and does not pretend to be a bystander pair.
	 */
	private static void emitTripleFeedback(ServerLevel level, ServerPlayer todo,
			TodoBoogieWoogieRuntime.Snapshot todoSnapshot, TodoTripleSwapPlan plan,
			TodoBoogieWoogieRuntime.Snapshot firstSnapshot, TodoBoogieWoogieRuntime.Snapshot thirdSnapshot) {
		long gameTime = level.getGameTime();
		RandomSource random = todo.getRandom();
		emitCycleEdge(level, todo, todoSnapshot.position(), firstSnapshot.position(), gameTime, random);
		emitCycleEdge(level, todo, firstSnapshot.position(), thirdSnapshot.position(), gameTime, random);
		emitCycleEdge(level, todo, thirdSnapshot.position(), todoSnapshot.position(), gameTime, random);
		TodoBoogieWoogieRuntime.broadcastAfterimage(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(todoSnapshot, plan.todoDestination()), gameTime);
		TodoBoogieWoogieRuntime.broadcastArrival(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(todoSnapshot, plan.todoDestination()), gameTime);
		TodoBoogieWoogieRuntime.broadcastAfterimage(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(firstSnapshot, plan.aDestination()), gameTime);
		TodoBoogieWoogieRuntime.broadcastArrival(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(firstSnapshot, plan.aDestination()), gameTime);
		TodoBoogieWoogieRuntime.broadcastAfterimage(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(thirdSnapshot, plan.tDestination()), gameTime);
		TodoBoogieWoogieRuntime.broadcastArrival(level, todo,
				new TodoBoogieWoogieRuntime.MovedBody(thirdSnapshot, plan.tDestination()), gameTime);
	}

	/**
	 * One edge of the triple cycle, world-fixed at the edge's start. The direction carries the flow —
	 * {@link VfxCue} normalizes it, so the magnitude cannot survive there — and {@code anchorOffset.x}
	 * carries the edge length in blocks instead.
	 */
	private static void emitCycleEdge(ServerLevel level, ServerPlayer todo, Vec3 from, Vec3 to,
			long gameTime, RandomSource random) {
		JujutsuNetworking.broadcastVfxCue(level, from, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(TodoVfxIds.TRIPLE_SWAP, from, 1, gameTime, random.nextLong(),
						to.subtract(from)));
	}

	/**
	 * The clap is Todo's, the endpoints are the two bodies that actually moved. Reusing the ordinary swap
	 * cues on purpose: from a distance a pair swap and a normal one look the same, which is free deception.
	 */
	private static void emitPairFeedback(ServerLevel level, ServerPlayer todo,
			TodoBoogieWoogieRuntime.Snapshot first, Vec3 firstDestination,
			TodoBoogieWoogieRuntime.Snapshot second, Vec3 secondDestination) {
		// The clap is his and keeps his aim; the geometry belongs to the two bodies that actually moved.
		TodoBoogieWoogieRuntime.emitSwapImpact(level, todo, todo.position(), todo.getLookAngle(),
				first.position(), second.position(),
				List.of(new TodoBoogieWoogieRuntime.MovedBody(first, firstDestination),
						new TodoBoogieWoogieRuntime.MovedBody(second, secondDestination)));
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
	 * Per-server-tick expiry and pulse sweep, driven by {@link TodoStateLifecycle}'s END_SERVER_TICK
	 * hook — this class registers no Fabric hooks of its own.
	 *
	 * <p>A selection is dropped when its clock runs out (silent: the TTL is the caster's own deadline)
	 * or when the marked body is verifiably gone (loud: the caster's plan just changed). A body that
	 * cannot be found is deliberately left alone — an unloaded chunk is not a death, and the commit path
	 * verifies liveness anyway.
	 *
	 * <p>While a selection lives, PAIR_MARK is re-emitted on the marked body every
	 * {@link TodoProfile#PAIR_MARK_PULSE_TICKS} at intensity 0 — the silent trap-boundary pulse pattern
	 * — so the first chosen body stays readable without a HUD framework. The initial mark (intensity 1)
	 * is emitted by {@link #mark} at selection time. The pulse is caster-only like the mark: nobody else
	 * may learn who has been marked.
	 */
	public static void serverTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (UUID ownerId : TodoTransientState.owners()) {
			TodoPendingSelection pending = TodoTransientState.pairSelection(ownerId).orElse(null);
			if (pending == null) {
				continue;
			}
			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null) {
				continue;
			}
			long now = level.getGameTime();
			boolean expired = pending.isExpired(now);
			Entity marked = level.getEntity(pending.targetEntityId());
			boolean died = marked instanceof LivingEntity living
					&& pending.identifies(living.getUUID())
					&& (living.isRemoved() || !living.isAlive() || living.isSpectator());
			if (expired || died) {
				TodoTransientState.clearPairSelection(ownerId);
				if (died) {
					// Expiry is silent; a lost participant is not, because the caster's plan just changed.
					ServerPlayer caster = server.getPlayerList().getPlayer(ownerId);
					if (caster != null) {
						caster.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.lost"), true);
					}
				}
				continue;
			}
			if (!(marked instanceof LivingEntity markedBody) || !pending.identifies(markedBody.getUUID())) {
				continue;
			}
			// Megumi's trap-boundary pulse: strictly periodic from placement, so the first beat lands a
			// full period in and never double-fires with the intensity-1 mark that opened the selection.
			long age = now - (pending.expiresAtGameTime() - TodoProfile.PAIR_SELECTION_TTL_TICKS);
			if (age > 0 && age % TodoProfile.PAIR_MARK_PULSE_TICKS == 0) {
				ServerPlayer caster = server.getPlayerList().getPlayer(ownerId);
				if (caster != null) {
					JujutsuNetworking.sendVfxCue(caster, VfxCues.anchoredSilentRepeat(TodoVfxIds.PAIR_MARK,
							markedBody.position(), markedBody.getId(), markedBody.position(), now,
							caster.getRandom().nextLong()));
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
