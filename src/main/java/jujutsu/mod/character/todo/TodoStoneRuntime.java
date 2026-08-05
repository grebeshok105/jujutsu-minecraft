package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative stone kit: V throws, V with a live stone self-swaps, Shift+V swaps an aimed
 * target with the stone.
 *
 * <p>One stone at a time and only while it flies. A live stone means V never throws a second one — it
 * becomes the self-swap — so the throw cooldown stays tiny (anti-double-click only) while the
 * self-swap carries the real price. The stone's state lives in {@link TodoTransientState}; this class
 * reads and writes it and keeps no static map of its own.
 *
 * <p>Both swap casts run the strict placement policy: the body that did not ask to be moved — or Todo
 * himself at a stone that may sit anywhere — is only ever placed somewhere that passed
 * {@code noBlockCollision}, and if no such point exists the whole cast cancels with nothing moved.
 */
public final class TodoStoneRuntime {
	private TodoStoneRuntime() {}

	/**
	 * Expiry sweep, driven by {@link TodoStateLifecycle} from END_SERVER_TICK.
	 *
	 * <p>The stone expires on its own clock when its chunk ticks; this sweep is the backstop for stones
	 * that stopped ticking. The type is {@code noSave()}, so an unloaded chunk discards the entity
	 * outright — "missing" and "lost from a loaded chunk" are the same fact here — and either way the
	 * ref is stale and must be cleared. The vanish cue rides inside
	 * {@link TodoTransientState#clearStone}, emitted only when the entity is still alive to be seen.
	 */
	public static void serverTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (UUID owner : TodoTransientState.owners()) {
			Optional<TodoStoneRef> ref = TodoTransientState.stone(owner);
			if (ref.isEmpty()) {
				continue;
			}
			ServerLevel level = server.getLevel(ref.get().dimension());
			if (level == null) {
				TodoTransientState.clearStone(server, owner);
				continue;
			}
			boolean expired = level.getGameTime() >= ref.get().thrownAtGameTime() + TodoProfile.STONE_LIFETIME_TICKS;
			boolean lost = !(level.getEntity(ref.get().entityUuid()) instanceof TodoStoneEntity);
			if (expired || lost) {
				TodoTransientState.clearStone(server, owner);
			}
		}
	}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		return switch (ability) {
			case TERTIARY -> tertiary(todo, notify);
			case TERTIARY_SNEAK -> targetSwap(todo, notify);
			default -> false;
		};
	}

	private static boolean tertiary(ServerPlayer todo, boolean notify) {
		if (TodoSwapGates.casterStateBlocked(todo)) {
			// Silent, like every UNAVAILABLE refusal: caster state is not worth an actionbar line.
			return false;
		}
		Optional<TodoStoneRef> ref = TodoTransientState.stone(todo.getUUID());
		if (shouldThrow(ref)) {
			return throwStone(todo, notify);
		}
		return selfSwap(todo, notify, ref.get());
	}

	/** Pure single-stone policy: a live stone means V never throws a second one. */
	static boolean shouldThrow(Optional<TodoStoneRef> stone) {
		return stone.isEmpty();
	}

	/**
	 * Pure stone eligibility shared by both swap casts: the stone must be present, in the caster's
	 * level, and within swap range.
	 */
	static boolean stoneEligibleForSwap(boolean stonePresent, boolean sameDimension, boolean withinRange) {
		return stonePresent && sameDimension && withinRange;
	}

	private static boolean throwStone(ServerPlayer todo, boolean notify) {
		ServerLevel level = todo.level();
		Vec3 launchPosition = todo.getEyePosition();
		Vec3 velocity = todo.getLookAngle().scale(TodoProfile.STONE_SPEED_BLOCKS_PER_TICK);
		TodoStoneEntity stone = new TodoStoneEntity(JujutsuEntities.TODO_STONE, level);
		stone.launch(todo, launchPosition, velocity);
		level.addFreshEntity(stone);
		TodoTransientState.setStone(todo.getUUID(),
				new TodoStoneRef(stone.getUUID(), level.dimension(), level.getGameTime()));
		// Anchored at the caster so the flick reads as a hand gesture; the direction lets the recipe
		// throw the particles along the same line the stone takes.
		JujutsuNetworking.broadcastVfxCue(level, launchPosition, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredDirected(TodoVfxIds.STONE_THROW, launchPosition, todo.getId(), todo.position(),
						1, level.getGameTime(), todo.getRandom().nextLong(), todo.getLookAngle()));
		CharacterAbilityCooldowns.start(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_THROW_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_THROW_COOLDOWN_TICKS);
		JujutsuMod.LOGGER.debug("Todo stone thrown player={} pos={} velocity={}",
				todo.getGameProfile().getName(), launchPosition, velocity);
		return true;
	}

	private static boolean selfSwap(ServerPlayer todo, boolean notify, TodoStoneRef ref) {
		ServerLevel level = todo.level();
		TodoStoneEntity stone = resolveStone(level, ref);
		if (!stoneEligibleForSwap(stone != null, ref.dimension().equals(level.dimension()),
				withinSwapRange(todo, stone))) {
			if (stone == null) {
				// The ref is stale across dimensions or the entity is gone: both mean the stone is lost.
				TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			}
			return reject(todo, notify,
					stone == null ? "message.jujutsumod.todo.stone.gone" : "message.jujutsumod.todo.stone.out_of_range",
					"stone not usable for the self-swap");
		}
		TodoBoogieWoogieRuntime.Snapshot todoSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(todo);
		Optional<TodoStonePlan> plan = TodoStonePlan.preflight(TodoBoogieWoogieRuntime.findSafeDestination(
				level, todo, stone.position(), TodoBoogieWoogieRuntime.Strictness.STRICT));
		if (plan.isEmpty()) {
			// Todo's own arrival is STRICT here on purpose: the stone can sit anywhere, and only a point
			// that passed noBlockCollision is good enough for him to land on.
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "no strict safe destination at the stone");
		}
		if (stone.isRemoved() || stone.level() != level) {
			TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.stone.gone", "stone changed before commit");
		}
		Vec3 stonePosition = stone.position();
		// The displaced body's center, as the design contract promises twice: a feet-level respawn
		// would hug the floor the body stood on and die on the first slab lip or farmland edge.
		Vec3 stoneDestination = todoSnapshot.position().add(0.0, todoSnapshot.bbHeight() / 2.0, 0.0);
		boolean todoPlaced = TodoBoogieWoogieRuntime.place(todo, level, plan.get().destination(), todoSnapshot);
		if (!todoPlaced) {
			TodoBoogieWoogieRuntime.rollback("stone self swap", todo, todo, todoSnapshot, null, null);
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(todo, todoSnapshot);
		stone.snapTo(level, stoneDestination);
		// A swap Todo made with his own body earns the same momentum window as the aimed swap.
		TodoSwapMomentumRuntime.grant(todo);
		CharacterAbilityCooldowns.start(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS);
		TodoBoogieWoogieRuntime.emitSwapImpact(level, todo, todoSnapshot.position(),
				stonePosition.subtract(todoSnapshot.position()), todoSnapshot.position(), stonePosition,
				List.of(new TodoBoogieWoogieRuntime.MovedBody(todoSnapshot, plan.get().destination())));
		JujutsuMod.LOGGER.debug("Todo stone self-swap success player={} from={} to={}",
				todo.getGameProfile().getName(), todoSnapshot.position(), stonePosition);
		return true;
	}

	private static boolean targetSwap(ServerPlayer todo, boolean notify) {
		if (TodoSwapGates.casterStateBlocked(todo)) {
			return false;
		}
		Optional<TodoStoneRef> ref = TodoTransientState.stone(todo.getUUID());
		if (ref.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.no_stone", "no live stone to swap with");
		}
		ServerLevel level = todo.level();
		TodoStoneEntity stone = resolveStone(level, ref.get());
		if (!stoneEligibleForSwap(stone != null, ref.get().dimension().equals(level.dimension()),
				withinSwapRange(todo, stone))) {
			if (stone == null) {
				TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			}
			return reject(todo, notify,
					stone == null ? "message.jujutsumod.todo.stone.gone" : "message.jujutsumod.todo.stone.out_of_range",
					"stone not usable for the target swap");
		}
		TargetResolver.Result aimed = TargetResolver.resolve(level, todo, TodoProfile.STONE_TARGET_RANGE,
				candidate -> TodoBoogieWoogieRuntime.isEligibleTarget(todo, candidate));
		if (aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.no_target", "no aimed target for the target swap");
		}
		Entity entity = level.getEntity(aimed.entityId().get());
		if (!(entity instanceof LivingEntity target) || !TodoBoogieWoogieRuntime.isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.invalid_target", "target no longer eligible or visible");
		}
		if (todo.distanceToSqr(target) > TodoProfile.STONE_TARGET_RANGE * TodoProfile.STONE_TARGET_RANGE) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.invalid_target", "target moved out of range");
		}
		TodoBoogieWoogieRuntime.Snapshot targetSnapshot = TodoBoogieWoogieRuntime.Snapshot.capture(target);
		if (targetSnapshot.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.invalid_target", "cross-level target");
		}
		Optional<TodoStonePlan> plan = TodoStonePlan.preflight(TodoBoogieWoogieRuntime.findSafeDestination(
				level, target, stone.position(), TodoBoogieWoogieRuntime.Strictness.STRICT));
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "no strict safe destination for the target at the stone");
		}
		if (target.isRemoved() || !target.isAlive() || target.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.invalid_target", "target changed before commit");
		}
		if (stone.isRemoved() || stone.level() != level) {
			TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			return reject(todo, notify, "message.jujutsumod.todo.stone.gone", "stone changed before commit");
		}
		Vec3 stonePosition = stone.position();
		Vec3 stoneDestination = targetSnapshot.position().add(0.0, targetSnapshot.bbHeight() / 2.0, 0.0);
		boolean targetPlaced = TodoBoogieWoogieRuntime.place(target, level, plan.get().destination(), targetSnapshot);
		if (!targetPlaced) {
			TodoBoogieWoogieRuntime.rollback("stone target swap", todo, target, targetSnapshot, null, null);
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "authoritative teleport failed");
		}
		TodoBoogieWoogieRuntime.restoreMotionAndRotation(target, targetSnapshot);
		stone.snapTo(level, stoneDestination);
		// Todo stays where he is, and moving a bystander buys him nothing: no momentum for this cast.
		CharacterAbilityCooldowns.start(todo, CharacterAbility.TERTIARY_SNEAK, TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.TERTIARY_SNEAK, TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS);
		TodoBoogieWoogieRuntime.emitSwapImpact(level, todo, todo.position(), todo.getLookAngle(),
				targetSnapshot.position(), stonePosition,
				List.of(new TodoBoogieWoogieRuntime.MovedBody(targetSnapshot, plan.get().destination())));
		JujutsuMod.LOGGER.debug("Todo stone target-swap success player={} target={} stoneFrom={}",
				todo.getGameProfile().getName(), target.getName().getString(), stonePosition);
		return true;
	}

	/**
	 * The ref finds the stone by UUID inside the ref's own dimension — never by entity id, never across
	 * levels. A null answer means the ref is stale and the caller clears it.
	 */
	private static TodoStoneEntity resolveStone(ServerLevel level, TodoStoneRef ref) {
		if (!ref.dimension().equals(level.dimension())) {
			return null;
		}
		return level.getEntity(ref.entityUuid()) instanceof TodoStoneEntity stone ? stone : null;
	}

	private static boolean withinSwapRange(ServerPlayer todo, TodoStoneEntity stone) {
		return stone != null && withinSwapRange(todo.distanceToSqr(stone));
	}

	/** The numeric boundary itself, kept pure so the inclusive-at-range contract stays testable. */
	static boolean withinSwapRange(double distanceSqr) {
		return distanceSqr <= TodoProfile.STONE_SWAP_RANGE * TodoProfile.STONE_SWAP_RANGE;
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo stone rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
