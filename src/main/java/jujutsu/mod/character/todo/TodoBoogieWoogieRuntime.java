package jujutsu.mod.character.todo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/** Server-authoritative helpers and the aimed cast for Todo's unified swap-node pipeline. */
public final class TodoBoogieWoogieRuntime {
	/** The caster may accept an exact point fallback; bystanders never may. */
	public enum Strictness {
		SOFT,
		STRICT
	}

	private static final SafeBodyPlacement.Policy SOFT_PLACEMENT = new SafeBodyPlacement.Policy(
			TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS, TodoProfile.SAFE_POSITION_UPWARD_BLOCKS,
			TodoProfile.WORLD_BORDER_MARGIN, true);
	private static final SafeBodyPlacement.Policy STRICT_PLACEMENT = new SafeBodyPlacement.Policy(
			TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS, TodoProfile.SAFE_POSITION_UPWARD_BLOCKS,
			TodoProfile.WORLD_BORDER_MARGIN, false);
	private static final CopyOnWriteArrayList<PendingSound> PENDING_SOUNDS = new CopyOnWriteArrayList<>();

	private TodoBoogieWoogieRuntime() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		if (ability != CharacterAbility.PRIMARY) {
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
		Vec3 origin = todo.position();
		int intensity = 1 + TodoSwapHooks.beatOf(todo);
		// The clap is emitted before selection/plan failure so a refused swap remains observer-indistinguishable
		// from a valid clap-only cast.
		emitClapPerformance(level, todo, origin, todo.getLookAngle(), TodoVfxIds.BOOGIE_WOOGIE, intensity);
		BodyNode target = SwapNodes.aimed(todo, TodoProfile.BOOGIE_WOOGIE_RANGE).orElse(null);
		if (target == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.no_target", "no aimed target");
		}
		Vec3 targetPosition = target.position();
		BodyNode self = SwapNodes.self(todo);
		SwapPlan plan = SwapNodes.planExchange(self, target).orElse(null);
		if (plan == null) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "no atomic safe destination");
		}
		SwapOutcome outcome = SwapCommit.commit(todo, plan);
		if (!outcome.success()) {
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative commit refused");
		}
		TodoSwapHooks.fireAfterCommit(todo, SwapKind.AIMED, outcome, false);
		TodoCooldownPolicy.arm(todo, SwapKind.AIMED.slot(), SwapKind.AIMED.baseCooldownTicks());
		if (SwapKind.AIMED.grantsMomentum()) {
			TodoSwapMomentumRuntime.grant(todo);
		}
		emitSwapFeedback(level, todo, origin, targetPosition, origin, targetPosition, outcome.moved());
		JujutsuMod.LOGGER.debug("Todo Boogie Woogie success player={} target={}",
				todo.getGameProfile().getName(), target.body().getName().getString());
		return true;
	}

	/** Shared by every Todo resolver and by commit-time revalidation. */
	static boolean isEligibleTarget(ServerPlayer todo, LivingEntity target) {
		boolean leashed = target instanceof Leashable leashable && leashable.isLeashed();
		return target != todo
				&& target.isAlive()
				&& !target.isSpectator()
				&& !target.isRemoved()
				&& !TodoTargetSafety.hasUnsafeTransportState(target.isPassenger(), target.isVehicle(), leashed)
				&& !(target instanceof ArmorStand)
				&& target.level() == todo.level()
				&& hasFinitePosition(target.position())
				&& !jujutsu.mod.combat.CombatTags.isBoogieWoogieImmune(target);
	}

	/** Safe destination scan with an explicit policy; no implicit SOFT overload exists. */
	static Vec3 findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested, Strictness strictness) {
		return SafeBodyPlacement.find(level, entity, requested,
				strictness == Strictness.SOFT ? SOFT_PLACEMENT : STRICT_PLACEMENT);
	}

	/** Restores the non-position state that an absolute teleport resets. */
	static void restoreMotionAndRotation(LivingEntity entity, Snapshot snapshot) {
		entity.forceSetRotation(snapshot.yaw(), snapshot.pitch());
		entity.setYHeadRot(snapshot.headYaw());
		entity.setDeltaMovement(snapshot.velocity());
		entity.resetFallDistance();
		entity.hurtMarked = true;
	}

	/** Everything an observer sees and hears of a completed swap except its already-emitted clap. */
	static void emitSwapFeedback(ServerLevel level, ServerPlayer todo, Vec3 clapOrigin, Vec3 clapAim,
			Vec3 ribbonFrom, Vec3 ribbonTo, List<MovedBody> moved) {
		long gameTime = level.getGameTime();
		int intensity = 1 + TodoSwapHooks.beatOf(todo);
		broadcastSwapEndpoint(level, todo, ribbonFrom, ribbonTo.subtract(ribbonFrom), gameTime, intensity);
		broadcastSwapEndpoint(level, todo, ribbonTo, Vec3.ZERO, gameTime, intensity);
		for (MovedBody body : moved) {
			broadcastAfterimage(level, todo, body, gameTime, intensity);
			broadcastArrival(level, todo, body, gameTime, intensity);
		}
		scheduleDisplacementWhoosh(level, ribbonFrom);
		scheduleDisplacementWhoosh(level, ribbonTo);
		scheduleLandingReport(level, arrivalMidpoint(moved, ribbonTo));
	}

	/** Compatibility helper for callers that want the complete normal clap + impact presentation. */
	static void emitSwapImpact(ServerLevel level, ServerPlayer todo, Vec3 clapOrigin, Vec3 clapAim,
			Vec3 ribbonFrom, Vec3 ribbonTo, List<MovedBody> moved) {
		emitClapPerformance(level, todo, clapOrigin, clapAim, TodoVfxIds.BOOGIE_WOOGIE,
				1 + TodoSwapHooks.beatOf(todo));
		emitSwapFeedback(level, todo, clapOrigin, clapAim, ribbonFrom, ribbonTo, moved);
	}

	static void broadcastAfterimage(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime) {
		broadcastAfterimage(level, todo, body, gameTime, 1 + TodoSwapHooks.beatOf(todo));
	}

	static void broadcastAfterimage(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime, int intensity) {
		Snapshot snapshot = body.snapshot();
		JujutsuNetworking.broadcastVfxCue(level, snapshot.position(), TodoProfile.VFX_DELIVERY_RADIUS,
				new VfxCue(TodoVfxIds.SWAP_AFTERIMAGE, snapshot.position(), VfxCue.NO_ANCHOR,
						new Vec3(snapshot.bbWidth(), snapshot.bbHeight(), snapshot.yaw()), intensity, gameTime,
						todo.getRandom().nextLong(), body.destination().subtract(snapshot.position())));
	}

	static void broadcastArrival(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime) {
		broadcastArrival(level, todo, body, gameTime, 1 + TodoSwapHooks.beatOf(todo));
	}

	static void broadcastArrival(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime, int intensity) {
		Snapshot snapshot = body.snapshot();
		Vec3 velocity = snapshot.velocity();
		JujutsuNetworking.broadcastVfxCue(level, body.destination(), TodoProfile.VFX_DELIVERY_RADIUS,
				new VfxCue(TodoVfxIds.SWAP_ARRIVAL, body.destination(), VfxCue.NO_ANCHOR,
						new Vec3(velocity.length(), snapshot.bbWidth(), snapshot.bbHeight()), intensity, gameTime,
						todo.getRandom().nextLong(), velocity));
	}

	static void broadcastSwapEndpoint(ServerLevel level, ServerPlayer todo, Vec3 endpoint, Vec3 pairDelta,
			long gameTime) {
		broadcastSwapEndpoint(level, todo, endpoint, pairDelta, gameTime, 1 + TodoSwapHooks.beatOf(todo));
	}

	static void broadcastSwapEndpoint(ServerLevel level, ServerPlayer todo, Vec3 endpoint, Vec3 pairDelta,
			long gameTime, int intensity) {
		JujutsuNetworking.broadcastVfxCue(level, endpoint, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(TodoVfxIds.SWAP_ENDPOINT, endpoint, intensity, gameTime,
						todo.getRandom().nextLong(), pairDelta));
	}

	/** One clap path shared by the real swap and Fake Clap; only the cue id/hook differs. */
	static void emitClapPerformance(ServerLevel level, ServerPlayer todo, Vec3 origin, Vec3 aim) {
		emitClapPerformance(level, todo, origin, aim, TodoVfxIds.BOOGIE_WOOGIE, 1 + TodoSwapHooks.beatOf(todo));
	}

	static void emitClapPerformance(ServerLevel level, ServerPlayer todo, Vec3 origin, Vec3 aim,
			 net.minecraft.resources.ResourceLocation cueId, int intensity) {
		level.playSound(null, origin.x, origin.y, origin.z, JujutsuSounds.PROJECTJJK_CLAP, SoundSource.PLAYERS,
				TodoProfile.BOOGIE_WOOGIE_CLAP_VOLUME, TodoProfile.BOOGIE_WOOGIE_CLAP_PITCH);
		JujutsuNetworking.broadcastVfxCue(level, origin, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredDirected(cueId, origin, todo.getId(), origin, intensity,
						level.getGameTime(), todo.getRandom().nextLong(), aim));
	}

	/** One tick behind the clap, preserving the real-vs-feint audio silhouette. */
	static void scheduleDisplacementWhoosh(ServerLevel level, Vec3 origin) {
		schedule(level, origin, JujutsuSounds.PROJECTJJK_CINEMATIC_WHOOSH,
				TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_VOLUME, TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_PITCH,
				TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_DELAY_TICKS);
	}

	private static Vec3 arrivalMidpoint(List<MovedBody> moved, Vec3 fallback) {
		if (moved.isEmpty()) {
			return fallback;
		}
		Vec3 sum = Vec3.ZERO;
		for (MovedBody body : moved) {
			sum = sum.add(body.destination());
		}
		return sum.scale(1.0 / moved.size());
	}

	private static void scheduleLandingReport(ServerLevel level, Vec3 origin) {
		schedule(level, origin, JujutsuSounds.PROJECTJJK_AEC_BOOM,
				TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_VOLUME, TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_PITCH,
				TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_DELAY_TICKS);
	}

	private static void schedule(ServerLevel level, Vec3 origin, SoundEvent sound, float volume, float pitch,
			int delayTicks) {
		PENDING_SOUNDS.add(new PendingSound(level.dimension(), origin, level.getGameTime() + delayTicks, sound, volume, pitch));
	}

	/** Registers delayed swap sounds once from TodoDefinition. */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(
				TodoBoogieWoogieRuntime::tickPendingSounds);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING_SOUNDS.clear());
	}

	private static void tickPendingSounds(ServerLevel level) {
		long now = level.getGameTime();
		PENDING_SOUNDS.removeIf(pending -> {
			if (!pending.dimension().equals(level.dimension()) || pending.dueAt() > now) {
				return false;
			}
			Vec3 origin = pending.origin();
			level.playSound(null, origin.x, origin.y, origin.z, pending.sound(), SoundSource.PLAYERS,
					pending.volume(), pending.pitch());
			return true;
		});
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

	/** Everything about a body before a swap, used for rollback and observer-facing afterimages. */
	public record Snapshot(ServerLevel level, Vec3 position, float yaw, float pitch, float headYaw, Vec3 velocity,
			float bbWidth, float bbHeight) {
		public static Snapshot capture(LivingEntity entity) {
			return new Snapshot((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot(),
					entity.getYHeadRot(), entity.getDeltaMovement(), entity.getBbWidth(), entity.getBbHeight());
		}
	}

	/** Observer-facing record of a body that reached its planned destination. */
	public record MovedBody(Snapshot snapshot, Vec3 destination) {}

	private record PendingSound(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
			Vec3 origin, long dueAt, SoundEvent sound, float volume, float pitch) {}
}
