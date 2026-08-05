package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/** Server-authoritative first implementation of Todo's Boogie Woogie self-to-target swap. */
public final class TodoBoogieWoogieRuntime {
	/**
	 * Both policies share one scan (`SafeBodyPlacement`, extracted from this file); they differ only in
	 * the exact-point fallback that SOFT keeps and STRICT refuses.
	 */
	private static final SafeBodyPlacement.Policy SOFT_PLACEMENT = new SafeBodyPlacement.Policy(
			TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS, TodoProfile.SAFE_POSITION_UPWARD_BLOCKS,
			TodoProfile.WORLD_BORDER_MARGIN, true);
	private static final SafeBodyPlacement.Policy STRICT_PLACEMENT = new SafeBodyPlacement.Policy(
			TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS, TodoProfile.SAFE_POSITION_UPWARD_BLOCKS,
			TodoProfile.WORLD_BORDER_MARGIN, false);

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
		// Todo keeps SOFT for his own arrival: that fallback is what makes a mid-air swap feel right, and
		// the risk is his to take. The target never gets it. A body that did not ask to be moved is only
		// ever placed somewhere that passed noBlockCollision, and if there is no such point the whole cast
		// cancels through the preflight below rather than forcing anyone into geometry.
		Optional<TodoSwapPlan> plan = TodoSwapPlan.preflight(
				findSafeDestination(level, todo, targetSnapshot.position(), Strictness.SOFT),
				findSafeDestination(level, target, todoSnapshot.position(), Strictness.STRICT)
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
			rollback("boogie woogie", todo, todo, todoSnapshot, target, targetSnapshot);
			return reject(todo, notify, "message.jujutsumod.todo.boogie.unsafe", "authoritative teleport failed");
		}
		restoreMotionAndRotation(todo, todoSnapshot);
		restoreMotionAndRotation(target, targetSnapshot);

		CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		JujutsuNetworking.sendAbilityCooldown(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS);
		// Past the last `return false` in this method, so the window is only ever opened by a swap that
		// actually happened.
		TodoSwapMomentumRuntime.grant(todo);
		emitSwapImpact(level, todo, todoSnapshot.position(), targetSnapshot.position().subtract(todoSnapshot.position()),
				todoSnapshot.position(), targetSnapshot.position(),
				List.of(new MovedBody(todoSnapshot, plan.get().firstDestination()),
						new MovedBody(targetSnapshot, plan.get().secondDestination())));
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

	/**
	 * SOFT keeps the fallback to the exact requested point; STRICT cancels instead.
	 *
	 * <p>There is deliberately no defaulting overload of {@link #findSafeDestination}. One used to exist and
	 * quietly supplied SOFT, which is how the aimed swap came to place its <em>target</em> — a body that did
	 * not ask to be moved — through a fallback that skips {@code noBlockCollision}. Every caller now says
	 * which it wants at the call site, so choosing the unsafe one is a visible decision rather than an
	 * omission.
	 *
	 * <p>STRICT is not a floor requirement. It is {@code isInWorldDestination} plus {@code noBlockCollision},
	 * so air, water and crawl spaces all remain valid destinations for a third party; what it refuses is a
	 * point inside geometry. The bounding box comes from the entity's own pose, so a large body is held to
	 * its real size, and the world border is tested against that same inflated box.
	 */
	public enum Strictness {
		SOFT,
		STRICT
	}

	/**
	 * Free-form destination: air / water / crawl / flight are all valid.
	 * Only world bounds, loaded chunks, border, and solid-block intersection are checked.
	 * No floor, no third-party entity occupancy gates. The scan itself lives in
	 * {@link SafeBodyPlacement}; this wrapper only translates the swap's strictness vocabulary.
	 */
	static Vec3 findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested, Strictness strictness) {
		return SafeBodyPlacement.find(level, entity, requested,
				strictness == Strictness.SOFT ? SOFT_PLACEMENT : STRICT_PLACEMENT);
	}

	static boolean place(LivingEntity entity, ServerLevel level, Vec3 destination, Snapshot snapshot) {
		return entity.teleportTo(level, destination.x, destination.y, destination.z, Set.<Relative>of(), snapshot.yaw(), snapshot.pitch(), false);
	}

	/**
	 * Restores the participants of a failed commit, and reports a restore that itself failed.
	 *
	 * <p>Four commit paths roll back and they used to hand-copy this: two restores, a two-flag check, an
	 * error log. The single-body marker swap copied only the restore and discarded its result, so the one
	 * route with no second participant was also the one where a failed rollback was invisible. Best-effort
	 * rollback is an accepted design; an unreported one is not, because that log line is the only evidence
	 * that a body ended up somewhere neither the plan nor the snapshot describes.
	 *
	 * @param route what to call this cast in the log; the four are told apart by nothing else
	 * @param second the other participant, or {@code null} when the cast moved only one body
	 */
	static void rollback(String route, ServerPlayer caster, LivingEntity first, Snapshot firstSnapshot,
			LivingEntity second, Snapshot secondSnapshot) {
		boolean firstRestored = restore(first, firstSnapshot);
		boolean secondRestored = second == null || restore(second, secondSnapshot);
		if (firstRestored && secondRestored) {
			return;
		}
		JujutsuMod.LOGGER.error(
				"Todo {} rollback incomplete caster={} first={} firstRestored={} second={} secondRestored={}",
				route,
				caster.getGameProfile().getName(),
				first.getName().getString(),
				firstRestored,
				second == null ? "-" : second.getName().getString(),
				secondRestored);
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
		// `place` teleports absolutely with no Relative flags, so the transition carries Vec3.ZERO and the
		// client is told its velocity is nothing -- the line above would otherwise be a server-side fiction
		// for a player, who owns his own movement. `hurtMarked` makes ServerEntity#sendChanges emit
		// ClientboundSetEntityMotionPacket through broadcastAndSend, which reaches the trackers and, for a
		// ServerPlayer, that player's own connection. Same primitive CombatStagger already uses, and putting
		// it here rather than at the call sites fixes every swap path at once, rollback included.
		entity.hurtMarked = true;
	}

	/** One body that changed places. Everything the impact sequence needs about it is in its pre-swap snapshot. */
	record MovedBody(Snapshot snapshot, Vec3 destination) {}

	/**
	 * Everything an observer sees and hears of a completed swap.
	 *
	 * <p>One method for all four routes — the aimed swap, both marker swaps and the pair swap. They used to
	 * hand-copy the same five calls, which is a shape that drifts, and the copy that drifts is always the
	 * one nobody plays often enough to notice.
	 *
	 * <p>{@code ribbonFrom}/{@code ribbonTo} are the two ends of the technique's geometry, which is not the
	 * same set as the bodies that moved: a swap onto a landed mark moves one body between two points.
	 */
	static void emitSwapImpact(ServerLevel level, ServerPlayer todo, Vec3 clapOrigin, Vec3 clapAim,
			Vec3 ribbonFrom, Vec3 ribbonTo, List<MovedBody> moved) {
		emitClapPerformance(level, todo, clapOrigin, clapAim);
		// One absolute endpoint per end of the geometry; only the leading one carries the delta the ribbon spans.
		long gameTime = level.getGameTime();
		broadcastSwapEndpoint(level, todo, ribbonFrom, ribbonTo.subtract(ribbonFrom), gameTime);
		broadcastSwapEndpoint(level, todo, ribbonTo, Vec3.ZERO, gameTime);
		for (MovedBody body : moved) {
			broadcastAfterimage(level, todo, body, gameTime);
			broadcastArrival(level, todo, body, gameTime);
		}
		scheduleDisplacementWhoosh(level, ribbonFrom);
		scheduleDisplacementWhoosh(level, ribbonTo);
		scheduleLandingReport(level, arrivalMidpoint(moved, ribbonTo));
	}

	/** The residue at the vacated spot, sized and turned like the body that stood there. */
	static void broadcastAfterimage(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime) {
		Snapshot snapshot = body.snapshot();
		JujutsuNetworking.broadcastVfxCue(level, snapshot.position(), TodoProfile.VFX_DELIVERY_RADIUS,
				new VfxCue(TodoVfxIds.SWAP_AFTERIMAGE, snapshot.position(), VfxCue.NO_ANCHOR,
						new Vec3(snapshot.bbWidth(), snapshot.bbHeight(), snapshot.yaw()), 1, gameTime,
						todo.getRandom().nextLong(), body.destination().subtract(snapshot.position())));
	}

	/** The landing. Speed rides in the offset because a cue normalizes its direction. */
	static void broadcastArrival(ServerLevel level, ServerPlayer todo, MovedBody body, long gameTime) {
		Snapshot snapshot = body.snapshot();
		Vec3 velocity = snapshot.velocity();
		JujutsuNetworking.broadcastVfxCue(level, body.destination(), TodoProfile.VFX_DELIVERY_RADIUS,
				new VfxCue(TodoVfxIds.SWAP_ARRIVAL, body.destination(), VfxCue.NO_ANCHOR,
						new Vec3(velocity.length(), snapshot.bbWidth(), snapshot.bbHeight()), 1, gameTime,
						todo.getRandom().nextLong(), velocity));
	}

	/** Where the swap finished, as one point: the average of every destination a body actually reached. */
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
		JujutsuNetworking.broadcastVfxCue(level, origin, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredDirected(TodoVfxIds.BOOGIE_WOOGIE, origin, todo.getId(), origin, 1,
						level.getGameTime(), todo.getRandom().nextLong(), aim));
	}

	static void broadcastSwapEndpoint(ServerLevel level, ServerPlayer todo, Vec3 endpoint, Vec3 pairDelta, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, endpoint, TodoProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(TodoVfxIds.SWAP_ENDPOINT, endpoint, 1, gameTime,
						todo.getRandom().nextLong(), pairDelta));
	}

	/** Short tear of air where a body used to be. */
	private static void scheduleDisplacementWhoosh(ServerLevel level, Vec3 origin) {
		schedule(level, origin, JujutsuSounds.PROJECTJJK_CINEMATIC_WHOOSH,
				TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_VOLUME, TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_PITCH,
				TodoProfile.BOOGIE_WOOGIE_MOVE_SOUND_DELAY_TICKS);
	}

	/** The low report that says the displacement landed. */
	private static void scheduleLandingReport(ServerLevel level, Vec3 origin) {
		schedule(level, origin, JujutsuSounds.PROJECTJJK_AEC_BOOM,
				TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_VOLUME, TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_PITCH,
				TodoProfile.BOOGIE_WOOGIE_IMPACT_SOUND_DELAY_TICKS);
	}

	private static void schedule(ServerLevel level, Vec3 origin, SoundEvent sound, float volume, float pitch, int delayTicks) {
		PENDING_SOUNDS.add(new PendingSound(level.dimension(), origin, level.getGameTime() + delayTicks, sound, volume, pitch));
	}

	/** Call once from mod init. */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(TodoBoogieWoogieRuntime::tickPendingSounds);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING_SOUNDS.clear());
	}

	private static void tickPendingSounds(ServerLevel level) {
		long now = level.getGameTime();
		PENDING_SOUNDS.removeIf(pending -> {
			if (!pending.dimension().equals(level.dimension())) {
				return false;
			}
			if (pending.dueAt() > now) {
				return false;
			}
			Vec3 o = pending.origin();
			level.playSound(null, o.x, o.y, o.z, pending.sound(), SoundSource.PLAYERS, pending.volume(), pending.pitch());
			return true;
		});
	}

	private static final java.util.concurrent.CopyOnWriteArrayList<PendingSound> PENDING_SOUNDS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private record PendingSound(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 origin,
			long dueAt, SoundEvent sound, float volume, float pitch) {}

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


	/**
	 * Everything about a body from before it was moved. The bounding box travels with it because the
	 * afterimage is drawn where the body <em>used</em> to be, by which time the live entity is standing
	 * somewhere else, possibly in a different pose.
	 */
	record Snapshot(ServerLevel level, Vec3 position, float yaw, float pitch, float headYaw, Vec3 velocity,
			float bbWidth, float bbHeight) {
		static Snapshot capture(LivingEntity entity) {
			return new Snapshot((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot(),
					entity.getYHeadRot(), entity.getDeltaMovement(), entity.getBbWidth(), entity.getBbHeight());
		}
	}
}
