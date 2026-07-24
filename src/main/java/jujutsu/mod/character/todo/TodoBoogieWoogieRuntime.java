package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;

/** Server-authoritative first implementation of Todo's Boogie Woogie self-to-target swap. */
public final class TodoBoogieWoogieRuntime {
	private static final List<Vec3> HORIZONTAL_OFFSETS = List.of(
			Vec3.ZERO,
			new Vec3(0.5, 0.0, 0.0), new Vec3(-0.5, 0.0, 0.0),
			new Vec3(0.0, 0.0, 0.5), new Vec3(0.0, 0.0, -0.5),
			new Vec3(1.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0),
			new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0),
			new Vec3(0.7, 0.0, 0.7), new Vec3(0.7, 0.0, -0.7),
			new Vec3(-0.7, 0.0, 0.7), new Vec3(-0.7, 0.0, -0.7)
	);

	private TodoBoogieWoogieRuntime() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.PRIMARY || todo.isSpectator() || !todo.isAlive()
				|| TodoTargetSafety.hasUnsafeTransportState(todo.isPassenger(), todo.isVehicle(), false)
				|| CombatStagger.GLOBAL.isStaggered(todo.getUUID(), todo.level().getGameTime())) {
			return false;
		}
		ServerLevel level = todo.level();
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie attempt player={} range={}", todo.getGameProfile().getName(), TodoProfile.BOOGIE_WOOGIE_RANGE);

		TargetResolver.Result aimed = TargetResolver.resolve(level, todo, TodoProfile.BOOGIE_WOOGIE_RANGE,
				candidate -> isEligibleTarget(todo, candidate));
		if (aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed target");
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
				findSafeDestination(level, todo, targetSnapshot.position(), target),
				findSafeDestination(level, target, todoSnapshot.position(), todo)
		);
		if (plan.isEmpty()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no atomic safe destination");
		}
		if (todo.isRemoved() || target.isRemoved() || !todo.isAlive() || !target.isAlive()
				|| todo.level() != level || target.level() != level) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.invalid_target", "entity changed before commit");
		}

		if (!place(todo, level, plan.get().todoDestination(), todoSnapshot) || !place(target, level, plan.get().targetDestination(), targetSnapshot)) {
			restore(todo, todoSnapshot);
			restore(target, targetSnapshot);
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		restoreMotionAndRotation(todo, todoSnapshot);
		restoreMotionAndRotation(target, targetSnapshot);

		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, JujutsuCharacter.TODO, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		emitSwapFeedback(level, todo, todoSnapshot.position(), targetSnapshot.position());
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie success player={} target={} from={} to={}",
				todo.getGameProfile().getName(), target.getName().getString(), todoSnapshot.position(), plan.get().todoDestination());
		return true;
	}

	private static boolean isEligibleTarget(ServerPlayer todo, LivingEntity target) {
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

	private static Vec3 findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested, Entity otherSwapParticipant) {
		for (int up = 0; up <= TodoProfile.SAFE_POSITION_UPWARD_BLOCKS; up++) {
			for (Vec3 horizontal : HORIZONTAL_OFFSETS) {
				Vec3 candidate = requested.add(horizontal.x, up, horizontal.z);
				if (isSafeDestination(level, entity, otherSwapParticipant, candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private static boolean isSafeDestination(ServerLevel level, LivingEntity entity, Entity otherSwapParticipant, Vec3 candidate) {
		BlockPos destinationBlock = BlockPos.containing(candidate);
		if (!hasFinitePosition(candidate) || !level.isInWorldBounds(destinationBlock)) {
			return false;
		}
		AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(candidate);
		if (!level.getWorldBorder().isWithinBounds(box) || !level.noBlockCollision(entity, box)) {
			return false;
		}
		if (!level.getChunkSource().hasChunk(destinationBlock.getX() >> 4, destinationBlock.getZ() >> 4) || !hasSafeFloor(level, candidate)) {
			return false;
		}
		return level.getEntities(entity, box.inflate(-1.0E-4),
				other -> other instanceof LivingEntity && other != otherSwapParticipant && other != entity
						&& other.isAlive() && !other.isSpectator()).isEmpty();
	}

	private static boolean hasSafeFloor(ServerLevel level, Vec3 candidate) {
		BlockPos floor = BlockPos.containing(candidate.x, candidate.y - 0.05, candidate.z);
		return !level.getBlockState(floor).isAir() && level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP);
	}

	private static boolean place(LivingEntity entity, ServerLevel level, Vec3 destination, Snapshot snapshot) {
		return entity.teleportTo(level, destination.x, destination.y, destination.z, Set.<Relative>of(), snapshot.yaw(), snapshot.pitch(), false);
	}

	private static void restore(LivingEntity entity, Snapshot snapshot) {
		if (entity.level() == snapshot.level()) {
			place(entity, snapshot.level(), snapshot.position(), snapshot);
			restoreMotionAndRotation(entity, snapshot);
		}
	}

	private static void restoreMotionAndRotation(LivingEntity entity, Snapshot snapshot) {
		entity.forceSetRotation(snapshot.yaw(), snapshot.pitch());
		entity.setYHeadRot(snapshot.headYaw());
		entity.setDeltaMovement(snapshot.velocity());
		entity.resetFallDistance();
	}

	private static void emitSwapFeedback(ServerLevel level, ServerPlayer todo, Vec3 todoOrigin, Vec3 targetOrigin) {
		long gameTime = level.getGameTime();
		Vec3 direction = targetOrigin.subtract(todoOrigin);
		JujutsuNetworking.broadcastVfxCue(level, todoOrigin, 64.0,
				new VfxCue(TodoVfxIds.BOOGIE_WOOGIE, todoOrigin, VfxCue.NO_ANCHOR, direction, 1, gameTime, todo.getRandom().nextLong(), direction));
		level.playSound(null, todoOrigin.x, todoOrigin.y, todoOrigin.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.85f, 1.35f);
		level.playSound(null, targetOrigin.x, targetOrigin.y, targetOrigin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.62f, 1.2f);
	}

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

	private record Snapshot(ServerLevel level, Vec3 position, float yaw, float pitch, float headYaw, Vec3 velocity) {
		private static Snapshot capture(LivingEntity entity) {
			return new Snapshot((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot(), entity.getYHeadRot(), entity.getDeltaMovement());
		}
	}
}
