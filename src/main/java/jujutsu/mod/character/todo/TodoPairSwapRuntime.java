package jujutsu.mod.character.todo;

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
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCues;

/** B pair and Shift+B triple casts, sharing the node→plan→commit transaction. */
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
		if (!gate(todo, notify)) {
			return false;
		}
		ServerLevel level = todo.level();
		Vec3 clapOrigin = todo.position();
		emitClap(level, todo, clapOrigin);
		TodoPendingSelection pending = liveSelection(todo, level);
		BodyNode aimed = SwapNodes.aimed(todo, TodoProfile.BOOGIE_WOOGIE_RANGE).orElse(null);
		if (pending == null) {
			return mark(todo, level, aimed, notify);
		}
		return commitPair(todo, level, pending, aimed, clapOrigin, notify);
	}

	private static boolean gate(ServerPlayer todo, boolean notify) {
		return switch (TodoSwapGates.evaluate(todo)) {
			case UNAVAILABLE -> false;
			case HANDS_FULL -> reject(todo, notify, "message.jujutsumod.todo.boogie.hands_full", "item in main or off hand");
			case ALLOWED -> true;
		};
	}

	private static void emitClap(ServerLevel level, ServerPlayer todo, Vec3 origin) {
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, origin, todo.getLookAngle(),
				TodoVfxIds.BOOGIE_WOOGIE, 1 + TodoSwapHooks.beatOf(todo));
	}

	private static TodoPendingSelection liveSelection(ServerPlayer todo, ServerLevel level) {
		TodoPendingSelection pending = TodoTransientState.pairSelection(todo.getUUID()).orElse(null);
		if (pending != null && (pending.isExpired(level.getGameTime()) || !pending.isIn(level.dimension()))) {
			TodoTransientState.clearPairSelection(todo.getUUID());
			return null;
		}
		return pending;
	}

	private static boolean mark(ServerPlayer todo, ServerLevel level, BodyNode aimed, boolean notify) {
		if (aimed == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed first participant");
		}
		LivingEntity target = aimed.body();
		TodoTransientState.setPairSelection(todo.getUUID(), new TodoPendingSelection(level.dimension(), target.getUUID(),
				target.getId(), level.getGameTime() + TodoProfile.PAIR_SELECTION_TTL_TICKS));
		JujutsuNetworking.sendVfxCue(todo,
				VfxCues.anchored(TodoVfxIds.PAIR_MARK, target.position(), target.getId(), target.position(), 1,
						level.getGameTime(), todo.getRandom().nextLong()));
		if (notify) {
			todo.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.marked", target.getDisplayName()), true);
		}
		return true;
	}

	private static boolean commitPair(ServerPlayer todo, ServerLevel level, TodoPendingSelection pending,
			BodyNode aimed, Vec3 clapOrigin, boolean notify) {
		if (aimed == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed second participant");
		}
		BodyNode first = resolveMarkedRaw(todo, level, pending);
		if (first == null) {
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "marked participant is gone");
		}
		if (first.body() == aimed.body()) {
			TodoTransientState.clearPairSelection(todo.getUUID());
			if (notify) {
				todo.displayClientMessage(Component.translatable("message.jujutsumod.todo.pair.cancelled"), true);
			}
			return true;
		}
		SwapPlan plan = SwapNodes.planExchange(first, aimed).orElse(null);
		if (plan == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no strict safe destination for bystanders");
		}
		Vec3 firstPosition = first.position();
		Vec3 secondPosition = aimed.position();
		SwapOutcome outcome = SwapCommit.commit(todo, plan);
		if (!outcome.success()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative commit refused");
		}
		TodoTransientState.clearPairSelection(todo.getUUID());
		TodoSwapHooks.fireAfterCommit(todo, SwapKind.PAIR, outcome, false);
		TodoCooldownPolicy.arm(todo, SwapKind.PAIR.slot(), SwapKind.PAIR.baseCooldownTicks());
		TodoBoogieWoogieRuntime.emitSwapFeedback(level, todo, clapOrigin, todo.getLookAngle(),
				firstPosition, secondPosition, outcome.moved());
		return true;
	}

	private static boolean tripleCast(ServerPlayer todo, boolean notify) {
		if (!gate(todo, notify)) {
			return false;
		}
		ServerLevel level = todo.level();
		Vec3 clapOrigin = todo.position();
		emitClap(level, todo, clapOrigin);
		TodoPendingSelection pending = liveSelection(todo, level);
		if (pending == null) {
			return reject(todo, notify, "message.jujutsumod.todo.triple.no_first", "triple requires a live first selection");
		}
		BodyNode first = resolveMarkedRaw(todo, level, pending);
		if (first == null) {
			return reject(todo, notify, "message.jujutsumod.todo.pair.lost", "marked participant is gone");
		}
		BodyNode aimed = SwapNodes.aimed(todo, TodoProfile.BOOGIE_WOOGIE_RANGE).orElse(null);
		if (aimed == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed third participant");
		}
		if (aimed.body() == first.body()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "the aimed body is the marked one");
		}
		BodyNode self = SwapNodes.self(todo);
		SwapPlan plan = SwapNodes.planCycle(self, first, aimed).orElse(null);
		if (plan == null) {
			return reject(todo, notify, "message.jujutsumod.todo.triple.unsafe", "no strict safe destination for cycle");
		}
		Vec3 todoPosition = self.position();
		Vec3 firstPosition = first.position();
		Vec3 thirdPosition = aimed.position();
		SwapOutcome outcome = SwapCommit.commit(todo, plan);
		if (!outcome.success()) {
			return reject(todo, notify, "message.jujutsumod.todo.triple.unsafe", "authoritative commit refused");
		}
		TodoTransientState.clearPairSelection(todo.getUUID());
		TodoSwapHooks.fireAfterCommit(todo, SwapKind.TRIPLE, outcome, false);
		TodoCooldownPolicy.arm(todo, SwapKind.TRIPLE.slot(), SwapKind.TRIPLE.baseCooldownTicks());
		emitTripleFeedback(level, todo, todoPosition, firstPosition, thirdPosition, outcome);
		return true;
	}

	private static BodyNode resolveMarkedRaw(ServerPlayer todo, ServerLevel level, TodoPendingSelection pending) {
		Entity entity = level.getEntity(pending.targetEntityId());
		if (!(entity instanceof LivingEntity marked) || !pending.identifies(marked.getUUID())
				|| marked.isRemoved() || !marked.isAlive() || marked.isSpectator()) {
			return null;
		}
		if (!TodoBoogieWoogieRuntime.isEligibleTarget(todo, marked)) {
			return null;
		}
		return new BodyNode(marked, TodoBoogieWoogieRuntime.Strictness.STRICT);
	}

	private static void emitTripleFeedback(ServerLevel level, ServerPlayer todo, Vec3 todoPosition,
			Vec3 firstPosition, Vec3 thirdPosition, SwapOutcome outcome) {
		long gameTime = level.getGameTime();
		RandomSource random = todo.getRandom();
		int intensity = 1 + TodoSwapHooks.beatOf(todo);
		emitCycleEdge(level, todo, todoPosition, firstPosition, gameTime, random, intensity);
		emitCycleEdge(level, todo, firstPosition, thirdPosition, gameTime, random, intensity);
		emitCycleEdge(level, todo, thirdPosition, todoPosition, gameTime, random, intensity);
		for (TodoBoogieWoogieRuntime.MovedBody body : outcome.moved()) {
			TodoBoogieWoogieRuntime.broadcastAfterimage(level, todo, body, gameTime, intensity);
			TodoBoogieWoogieRuntime.broadcastArrival(level, todo, body, gameTime, intensity);
		}
		TodoBoogieWoogieRuntime.scheduleDisplacementWhoosh(level, todoPosition);
		TodoBoogieWoogieRuntime.scheduleDisplacementWhoosh(level, thirdPosition);
	}

	private static void emitCycleEdge(ServerLevel level, ServerPlayer todo, Vec3 from, Vec3 to, long gameTime,
			RandomSource random, int intensity) {
		JujutsuNetworking.broadcastVfxCue(level, from, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(TodoVfxIds.TRIPLE_SWAP, from, intensity, gameTime, random.nextLong(),
						to.subtract(from)));
	}

	/** Pair selection expiry and caster-only pulse sweep. */
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
			Entity marked = level.getEntity(pending.targetEntityId());
			boolean died = marked instanceof LivingEntity living && pending.identifies(living.getUUID())
					&& (living.isRemoved() || !living.isAlive() || living.isSpectator());
			if (pending.isExpired(now) || died) {
				TodoTransientState.clearPairSelection(ownerId);
				if (died) {
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
			long age = now - (pending.expiresAtGameTime() - TodoProfile.PAIR_SELECTION_TTL_TICKS);
			if (age > 0 && age % TodoProfile.PAIR_MARK_PULSE_TICKS == 0) {
				ServerPlayer caster = server.getPlayerList().getPlayer(ownerId);
				if (caster != null) {
					JujutsuNetworking.sendVfxCue(caster, VfxCues.anchoredSilentRepeat(TodoVfxIds.PAIR_MARK,
							markedBody.position(), markedBody.getId(), markedBody.position(), now, caster.getRandom().nextLong()));
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
