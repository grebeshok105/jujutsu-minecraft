package jujutsu.mod.character.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/** Server-authoritative first implementation of Todo's Boogie Woogie self-to-target swap. */
public final class TodoBoogieWoogieRuntime {
	private static final List<Vec3> HORIZONTAL_OFFSETS = buildHorizontalOffsets();

	private TodoBoogieWoogieRuntime() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.PRIMARY) {
			return false;
		}
		// Shared with the feint, so the two casts cannot be told apart by which of them gets refused.
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
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie attempt player={} range={}",
				todo.getGameProfile().getName(), TodoProfile.BOOGIE_WOOGIE_RANGE);

		TargetResolver.Result aimed = TargetResolver.resolve(level, todo, TodoProfile.BOOGIE_WOOGIE_RANGE,
				candidate -> isEligibleTarget(todo, candidate));
		if (aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target",
					"no aimed target mode=" + aimed.mode());
		}
		Entity entity = level.getEntity(aimed.entityId().get());
		if (!(entity instanceof LivingEntity target) || !isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "target no longer eligible or visible");
		}
		if (todo.distanceToSqr(target) > TodoProfile.BOOGIE_WOOGIE_RANGE * TodoProfile.BOOGIE_WOOGIE_RANGE) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.out_of_range", "target moved out of range");
		}

		Snapshot todoSnapshot = Snapshot.capture(todo);
		Snapshot targetSnapshot = Snapshot.capture(target);
		if (todoSnapshot.level() != targetSnapshot.level()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "cross-level target");
		}
		Optional<TodoSwapPlan> plan = TodoSwapPlan.preflight(
				findSafeDestination(level, todo, targetSnapshot.position()),
				findSafeDestination(level, target, todoSnapshot.position())
		);
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no atomic safe destination");
		}
		if (todo.isRemoved() || target.isRemoved() || !todo.isAlive() || !target.isAlive()
				|| todo.level() != level || target.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "entity changed before commit");
		}

		boolean todoPlaced = place(todo, level, plan.get().firstDestination(), todoSnapshot);
		boolean targetPlaced = todoPlaced && place(target, level, plan.get().secondDestination(), targetSnapshot);
		if (!todoPlaced || !targetPlaced) {
			boolean todoRestored = restore(todo, todoSnapshot);
			boolean targetRestored = restore(target, targetSnapshot);
			if (!todoRestored || !targetRestored) {
				JujutsuMod.LOGGER.error(
						"Todo Boogie Woogie rollback incomplete player={} target={} todoRestored={} targetRestored={}",
						todo.getGameProfile().getName(),
						target.getName().getString(),
						todoRestored,
						targetRestored);
			}
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		restoreMotionAndRotation(todo, todoSnapshot);
		restoreMotionAndRotation(target, targetSnapshot);

		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, JujutsuCharacter.TODO, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		emitSwapFeedback(level, todo, todoSnapshot.position(), targetSnapshot.position());
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie success player={} target={} from={} to={}",
				todo.getGameProfile().getName(), target.getName().getString(), todoSnapshot.position(), plan.get().firstDestination());
		return true;
	}

	/** Shared with the pair swap, so a bystander is never held to a laxer standard than a direct target. */
	static boolean isEligibleTarget(ServerPlayer todo, LivingEntity target) {
		boolean leashed = target instanceof Leashable leashable && leashable.isLeashed();
		return target != todo
				&& target.isAlive()
				&& !target.isSpectator()
				&& !target.isRemoved()
				&& !TodoTargetSafety.hasUnsafeTransportState(target.isPassenger(), target.isVehicle(), leashed)
				&& !(target instanceof ArmorStand)
				&& target.level() == todo.level()
				&& hasFinitePosition(target.position());
	}

	/** SOFT keeps the shipped fallback to the exact requested point; STRICT cancels instead. */
	public enum Strictness {
		SOFT,
		STRICT
	}

	private static Vec3 findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested) {
		return findSafeDestination(level, entity, requested, Strictness.SOFT);
	}

	/**
	 * Free-form destination: air / water / crawl / flight are all valid.
	 * Only world bounds, loaded chunks, border, and solid-block intersection are checked.
	 * No floor, no third-party entity occupancy gates.
	 */
	static Vec3 findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested, Strictness strictness) {
		for (int up = 0; up <= TodoProfile.SAFE_POSITION_UPWARD_BLOCKS; up++) {
			for (Vec3 horizontal : HORIZONTAL_OFFSETS) {
				Vec3 candidate = requested.add(horizontal.x, up, horizontal.z);
				if (isPlaceableDestination(level, entity, candidate)) {
					return candidate;
				}
			}
		}
		// Last resort for SOFT only: exact requested point if it is at least in-world and inside the border,
		// which lets mid-air / fluid swaps through. STRICT never forces a point, so the cast cancels instead.
		if (strictness == Strictness.SOFT && isInWorldDestination(level, entity, requested)) {
			return requested;
		}
		return null;
	}

	private static boolean isPlaceableDestination(ServerLevel level, LivingEntity entity, Vec3 candidate) {
		return isInWorldDestination(level, entity, candidate)
				&& level.noBlockCollision(entity, boundingBoxAt(entity, candidate));
	}

	private static boolean isInWorldDestination(ServerLevel level, LivingEntity entity, Vec3 candidate) {
		BlockPos destinationBlock = BlockPos.containing(candidate);
		return hasFinitePosition(candidate)
				&& level.isInWorldBounds(destinationBlock)
				&& level.getChunkSource().hasChunk(destinationBlock.getX() >> 4, destinationBlock.getZ() >> 4)
				&& level.getWorldBorder().isWithinBounds(boundingBoxAt(entity, candidate).inflate(TodoProfile.WORLD_BORDER_MARGIN));
	}

	private static AABB boundingBoxAt(LivingEntity entity, Vec3 candidate) {
		return entity.getDimensions(entity.getPose()).makeBoundingBox(candidate);
	}

	static boolean place(LivingEntity entity, ServerLevel level, Vec3 destination, Snapshot snapshot) {
		return entity.teleportTo(level, destination.x, destination.y, destination.z, Set.<Relative>of(), snapshot.yaw(), snapshot.pitch(), false);
	}

	static boolean restore(LivingEntity entity, Snapshot snapshot) {
		if (entity.level() != snapshot.level()) {
			return false;
		}
		boolean placed = place(entity, snapshot.level(), snapshot.position(), snapshot);
		if (placed) {
			restoreMotionAndRotation(entity, snapshot);
		}
		return placed;
	}

	static void restoreMotionAndRotation(LivingEntity entity, Snapshot snapshot) {
		entity.forceSetRotation(snapshot.yaw(), snapshot.pitch());
		entity.setYHeadRot(snapshot.headYaw());
		entity.setDeltaMovement(snapshot.velocity());
		entity.resetFallDistance();
	}

	private static void emitSwapFeedback(ServerLevel level, ServerPlayer todo, Vec3 todoOrigin, Vec3 targetOrigin) {
		Vec3 pairDelta = targetOrigin.subtract(todoOrigin);
		emitClapPerformance(level, todo, todoOrigin, pairDelta);
		// One absolute endpoint per moved body; only the leading one carries the pair delta the ribbon spans.
		long gameTime = level.getGameTime();
		broadcastSwapEndpoint(level, todo, todoOrigin, pairDelta, gameTime);
		broadcastSwapEndpoint(level, todo, targetOrigin, Vec3.ZERO, gameTime);
		scheduleMoveSound(level, todoOrigin);
		scheduleMoveSound(level, targetOrigin);
	}

	/**
	 * Everything an observer sees and hears of the clap itself, with nothing about the swap in it.
	 * The feint calls exactly this method, which is what makes the two casts share one timing instead
	 * of two implementations that have to be kept in step by hand.
	 */
	static void emitClapPerformance(ServerLevel level, ServerPlayer todo, Vec3 origin, Vec3 aim) {
		// Clap first, so it is never later than the swap it announces.
		level.playSound(null, origin.x, origin.y, origin.z, JujutsuSounds.PROJECTJJK_CLAP, SoundSource.PLAYERS,
				TodoProfile.BOOGIE_WOOGIE_CLAP_VOLUME, TodoProfile.BOOGIE_WOOGIE_CLAP_PITCH);
		// Performance cue: caster-anchored with a zero offset, so it carries no endpoint geometry.
		JujutsuNetworking.broadcastVfxCue(level, origin, TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS,
				new VfxCue(TodoVfxIds.BOOGIE_WOOGIE, origin, todo.getId(), Vec3.ZERO, 1, level.getGameTime(),
						todo.getRandom().nextLong(), aim));
	}

	static void broadcastSwapEndpoint(ServerLevel level, ServerPlayer todo, Vec3 endpoint, Vec3 pairDelta, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, endpoint, TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS,
				new VfxCue(TodoVfxIds.SWAP_ENDPOINT, endpoint, VfxCue.NO_ANCHOR, pairDelta, 1, gameTime, todo.getRandom().nextLong(), pairDelta));
	}

	static void scheduleMoveSound(ServerLevel level, Vec3 origin) {
		long dueAt = level.getGameTime() + TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_DELAY_TICKS;
		PENDING_MOVE_SOUNDS.add(new PendingSound(level.dimension(), origin, dueAt));
	}

	/** Call once from mod init. */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(TodoBoogieWoogieRuntime::tickMoveSounds);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING_MOVE_SOUNDS.clear());
	}

	private static void tickMoveSounds(ServerLevel level) {
		long now = level.getGameTime();
		PENDING_MOVE_SOUNDS.removeIf(pending -> {
			if (!pending.dimension().equals(level.dimension())) {
				return false;
			}
			if (pending.dueAt() > now) {
				return false;
			}
			Vec3 o = pending.origin();
			level.playSound(null, o.x, o.y, o.z, JujutsuSounds.PROJECTJJK_CINEMATIC_WHOOSH, SoundSource.PLAYERS,
					TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_VOLUME, TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_PITCH);
			return true;
		});
	}

	private static final java.util.concurrent.CopyOnWriteArrayList<PendingSound> PENDING_MOVE_SOUNDS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private record PendingSound(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 origin, long dueAt) {}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, String reason) {
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie rejected player={} reason={}", player.getGameProfile().getName(), reason);
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}

	private static boolean hasFinitePosition(Vec3 value) {
		return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}

	private static List<Vec3> buildHorizontalOffsets() {
		double radius = TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS;
		double half = radius * 0.5;
		double diag = radius * 0.7;
		List<Vec3> offsets = new ArrayList<>();
		offsets.add(Vec3.ZERO);
		offsets.add(new Vec3(half, 0.0, 0.0));
		offsets.add(new Vec3(-half, 0.0, 0.0));
		offsets.add(new Vec3(0.0, 0.0, half));
		offsets.add(new Vec3(0.0, 0.0, -half));
		offsets.add(new Vec3(radius, 0.0, 0.0));
		offsets.add(new Vec3(-radius, 0.0, 0.0));
		offsets.add(new Vec3(0.0, 0.0, radius));
		offsets.add(new Vec3(0.0, 0.0, -radius));
		offsets.add(new Vec3(diag, 0.0, diag));
		offsets.add(new Vec3(diag, 0.0, -diag));
		offsets.add(new Vec3(-diag, 0.0, diag));
		offsets.add(new Vec3(-diag, 0.0, -diag));
		return List.copyOf(offsets);
	}

	record Snapshot(ServerLevel level, Vec3 position, float yaw, float pitch, float headYaw, Vec3 velocity) {
		static Snapshot capture(LivingEntity entity) {
			return new Snapshot((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot(), entity.getYHeadRot(), entity.getDeltaMovement());
		}
	}
}
