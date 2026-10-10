package jujutsu.mod.character.todo;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCues;

/** Server-authoritative V/Shift+V stone casts using the same node transaction as body swaps. */
public final class TodoStoneRuntime {
	private TodoStoneRuntime() {}

	/** Backstop sweep for stones whose entity tick stopped in an unloaded chunk. */
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
			return false;
		}
		Optional<TodoStoneRef> ref = TodoTransientState.stone(todo.getUUID());
		return shouldThrow(ref) ? throwStone(todo) : selfSwap(todo, notify, ref.get());
	}

	static boolean shouldThrow(Optional<TodoStoneRef> stone) {
		return stone.isEmpty();
	}

	static boolean stoneEligibleForSwap(boolean stonePresent, boolean sameDimension, boolean withinRange) {
		return stonePresent && sameDimension && withinRange;
	}

	private static boolean throwStone(ServerPlayer todo) {
		ServerLevel level = todo.level();
		Vec3 launchPosition = todo.getEyePosition();
		Vec3 velocity = todo.getLookAngle().scale(TodoProfile.STONE_SPEED_BLOCKS_PER_TICK);
		TodoStoneEntity stone = new TodoStoneEntity(JujutsuEntities.TODO_STONE, level);
		stone.launch(todo, launchPosition, velocity);
		level.addFreshEntity(stone);
		TodoTransientState.setStone(todo.getUUID(), new TodoStoneRef(stone.getUUID(), level.dimension(), level.getGameTime()));
		JujutsuNetworking.broadcastVfxCue(level, launchPosition, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredDirected(TodoVfxIds.STONE_THROW, launchPosition, todo.getId(), todo.position(),
						1 + TodoSwapHooks.beatOf(todo), level.getGameTime(), todo.getRandom().nextLong(), todo.getLookAngle()));
		CharacterAbilityCooldowns.start(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_THROW_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.TERTIARY, TodoProfile.STONE_THROW_COOLDOWN_TICKS);
		return true;
	}

	private static boolean selfSwap(ServerPlayer todo, boolean notify, TodoStoneRef ref) {
		ServerLevel level = todo.level();
		Vec3 clapOrigin = todo.position();
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, clapOrigin, todo.getLookAngle(),
				TodoVfxIds.BOOGIE_WOOGIE, 1 + TodoSwapHooks.beatOf(todo));
		StoneNode stone = SwapNodes.stone(todo).orElse(null);
		if (stone == null) {
			if (level.getEntity(ref.entityUuid()) == null || !ref.dimension().equals(level.dimension())) {
				TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			}
			return reject(todo, notify,
					stonePresent(level, ref) ? "message.jujutsumod.todo.stone.out_of_range" : "message.jujutsumod.todo.stone.gone",
					"stone not usable for self-swap");
		}
		Vec3 stonePosition = stone.position();
		SwapPlan plan = SwapNodes.planExchange(
				new BodyNode(todo, TodoBoogieWoogieRuntime.Strictness.STRICT), stone).orElse(null);
		if (plan == null) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "no strict safe destination at stone");
		}
		SwapOutcome outcome = SwapCommit.commit(todo, plan);
		if (!outcome.success()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "authoritative commit refused");
		}
		TodoSwapHooks.fireAfterCommit(todo, SwapKind.STONE_SELF, outcome, false);
		TodoCooldownPolicy.arm(todo, SwapKind.STONE_SELF.slot(), SwapKind.STONE_SELF.baseCooldownTicks());
		TodoSwapMomentumRuntime.grant(todo);
		TodoBoogieWoogieRuntime.emitSwapFeedback(level, todo, clapOrigin, stonePosition.subtract(clapOrigin),
				clapOrigin, stonePosition, outcome.moved());
		return true;
	}

	private static boolean targetSwap(ServerPlayer todo, boolean notify) {
		if (TodoSwapGates.casterStateBlocked(todo)) {
			return false;
		}
		ServerLevel level = todo.level();
		Vec3 clapOrigin = todo.position();
		TodoBoogieWoogieRuntime.emitClapPerformance(level, todo, clapOrigin, todo.getLookAngle(),
				TodoVfxIds.BOOGIE_WOOGIE, 1 + TodoSwapHooks.beatOf(todo));
		Optional<TodoStoneRef> ref = TodoTransientState.stone(todo.getUUID());
		if (ref.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.no_stone", "no live stone to swap with");
		}
		StoneNode stone = SwapNodes.stone(todo).orElse(null);
		if (stone == null) {
			if (level.getEntity(ref.get().entityUuid()) == null || !ref.get().dimension().equals(level.dimension())) {
				TodoTransientState.clearStone(level.getServer(), todo.getUUID());
			}
			return reject(todo, notify,
					stonePresent(level, ref.get()) ? "message.jujutsumod.todo.stone.out_of_range" : "message.jujutsumod.todo.stone.gone",
					"stone not usable for target swap");
		}
		BodyNode target = SwapNodes.aimed(todo, TodoProfile.STONE_TARGET_RANGE).orElse(null);
		if (target == null) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.no_target", "no aimed target");
		}
		Vec3 targetPosition = target.position();
		Vec3 stonePosition = stone.position();
		SwapPlan plan = SwapNodes.planExchange(target, stone).orElse(null);
		if (plan == null) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "no strict safe destination at stone");
		}
		SwapOutcome outcome = SwapCommit.commit(todo, plan);
		if (!outcome.success()) {
			return reject(todo, notify, "message.jujutsumod.todo.stone.unsafe", "authoritative commit refused");
		}
		TodoSwapHooks.fireAfterCommit(todo, SwapKind.STONE_TARGET, outcome, false);
		TodoCooldownPolicy.arm(todo, SwapKind.STONE_TARGET.slot(), SwapKind.STONE_TARGET.baseCooldownTicks());
		TodoBoogieWoogieRuntime.emitSwapFeedback(level, todo, clapOrigin, todo.getLookAngle(),
				targetPosition, stonePosition, outcome.moved());
		return true;
	}

	private static boolean stonePresent(ServerLevel level, TodoStoneRef ref) {
		return ref != null && ref.dimension().equals(level.dimension())
				&& level.getEntity(ref.entityUuid()) instanceof TodoStoneEntity stone && !stone.isRemoved();
	}

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
