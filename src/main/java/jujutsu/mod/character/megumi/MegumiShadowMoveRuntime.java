package jujutsu.mod.character.megumi;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative state machine of Megumi's Shift+B shadow travel: one technique, three
 * contextual modes (emerge behind a target, free step to an aimed surface, deep submerge while the
 * key is held), one state per player.
 *
 * <pre>
 * tap:  SINK → HIDDEN → EMERGE
 * hold: SINK → SUBMERGED (≤ max, release/re-tap ends early) → EMERGE
 * </pre>
 *
 * <p>Rules the whole file bends around: the exit point is computed against the world as it is at
 * emerge time, never at cast time; a body that cannot be placed anywhere safe resurfaces at its own
 * start point rather than half-travelling; collision never lapses, so the submerged walk can never
 * cross geometry a walking body could not; and every lifecycle event lands in one teardown that
 * restores visibility and leaves no pending teleport behind.
 */
public final class MegumiShadowMoveRuntime {
	private static final Map<UUID, ShadowMove> MOVES = new ConcurrentHashMap<>();

	/** Emerge placement for a body that asked to travel: refuse rather than force a point. */
	private static final SafeBodyPlacement.Policy EXIT_PLACEMENT = new SafeBodyPlacement.Policy(
			MegumiProfile.SAFE_POSITION_HORIZONTAL_RADIUS, MegumiProfile.SAFE_POSITION_UPWARD_BLOCKS,
			MegumiProfile.WORLD_BORDER_MARGIN, false);
	/** Rescue ring around a submerged body whose own position stopped being placeable. */
	private static final SafeBodyPlacement.Policy RESCUE_PLACEMENT = new SafeBodyPlacement.Policy(
			MegumiProfile.EMERGE_SEARCH_RADIUS, MegumiProfile.SAFE_POSITION_UPWARD_BLOCKS,
			MegumiProfile.WORLD_BORDER_MARGIN, false);
	/** Returning to the start point may force it: the body stood exactly there when the move began. */
	private static final SafeBodyPlacement.Policy RETURN_PLACEMENT = new SafeBodyPlacement.Policy(
			MegumiProfile.SAFE_POSITION_HORIZONTAL_RADIUS, MegumiProfile.SAFE_POSITION_UPWARD_BLOCKS,
			MegumiProfile.WORLD_BORDER_MARGIN, true);

	enum Phase {
		SINK,
		HIDDEN,
		SUBMERGED,
		EMERGE
	}

	enum MoveMode {
		BACKSTEP,
		FREE_STEP,
		SUBMERGE
	}

	static final class ShadowMove {
		final MoveMode mode;
		final ResourceKey<Level> dimension;
		final Vec3 startPosition;
		final UUID targetUuid;
		final Vec3 freeStepPoint;
		Phase phase = Phase.SINK;
		int phaseTicksLeft = MegumiProfile.SHADOW_SINK_TICKS;
		int submergedTicks;
		boolean emergeRequested;

		private ShadowMove(MoveMode mode, ResourceKey<Level> dimension, Vec3 startPosition,
				UUID targetUuid, Vec3 freeStepPoint) {
			this.mode = mode;
			this.dimension = dimension;
			this.startPosition = startPosition;
			this.targetUuid = targetUuid;
			this.freeStepPoint = freeStepPoint;
		}
	}

	private MegumiShadowMoveRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiShadowMoveRuntime::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MegumiShadowMoveRuntime::allowDamage);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				MOVES.containsKey(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				teardown(player.getServer(), player.getUUID());
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				teardown(newPlayer.getServer(), newPlayer.getUUID()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				teardown(player.getServer(), player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				teardown(server, handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID ownerId : Set.copyOf(MOVES.keySet())) {
				teardown(server, ownerId);
			}
			MOVES.clear();
		});
	}

	/** Any active phase locks every other ability and vanilla melee; the router asks this first. */
	public static boolean locksAbilities(ServerPlayer player) {
		return MOVES.containsKey(player.getUUID());
	}

	/**
	 * A slot cast arriving while a move is active: a repeat tap (or the hold release) asks to emerge
	 * early, everything else is swallowed with the bound message so no vessel fallback line leaks out.
	 */
	public static boolean handleWhileActive(ServerPlayer player, CharacterAbility ability, boolean notify) {
		if (ability == CharacterAbility.SECONDARY_SNEAK || ability == CharacterAbility.SECONDARY_SNEAK_RELEASE) {
			return tryRelease(player);
		}
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shadow.bound"), true);
		}
		return true;
	}

	public static boolean tryTap(ServerPlayer player, boolean notify) {
		ServerLevel level = player.level();
		TargetResolver.Result aimed = TargetResolver.resolve(
				level, player, MegumiProfile.SHADOW_STEP_TARGET_RANGE,
				candidate -> MegumiSummonRuntime.isEligibleTarget(player, candidate));
		if (aimed.mode() == TargetResolver.Mode.ENTITY && aimed.entityId().isPresent()) {
			Entity resolved = level.getEntity(aimed.entityId().get());
			if (resolved instanceof LivingEntity target
					&& MegumiSummonRuntime.isEligibleTarget(player, target)
					&& player.hasLineOfSight(target)) {
				begin(player, new ShadowMove(MoveMode.BACKSTEP, level.dimension(), player.position(),
						target.getUUID(), null));
				return true;
			}
		}
		TargetResolver.Result surface = TargetResolver.resolve(
				level, player, MegumiProfile.SHADOW_STEP_RANGE, candidate -> false);
		if (surface.mode() == TargetResolver.Mode.BLOCK) {
			// Half a block out of the face keeps the requested point outside the surface it names; the
			// placement scan owns the rest (it may still resolve slightly above, onto a ledge).
			Vec3 requested = surface.point().add(surface.normal().scale(0.5));
			if (SafeBodyPlacement.find(level, player, requested, EXIT_PLACEMENT) == null) {
				return reject(player, notify, "message.jujutsumod.megumi.shadow.no_safe_exit");
			}
			begin(player, new ShadowMove(MoveMode.FREE_STEP, level.dimension(), player.position(),
					null, requested));
			return true;
		}
		return reject(player, notify, "message.jujutsumod.megumi.shadow.no_path");
	}

	public static boolean tryHoldStart(ServerPlayer player, boolean notify) {
		if (MOVES.containsKey(player.getUUID())) {
			return true;
		}
		// The hold slot carries no cooldown of its own; the gesture shares the tap's key, and the router
		// is the one place that can say so (the executor only folds slots that mean the same action).
		if (!CharacterAbilityCooldowns.isReady(player, CharacterAbility.SECONDARY_SNEAK)) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.character.action.cooldown"), true);
			}
			return true;
		}
		begin(player, new ShadowMove(MoveMode.SUBMERGE, player.level().dimension(), player.position(),
				null, null));
		return true;
	}

	/** Quietly tolerates a release with no state: the hold it belonged to may have been refused. */
	public static boolean tryRelease(ServerPlayer player) {
		ShadowMove move = MOVES.get(player.getUUID());
		if (move == null) {
			return true;
		}
		move.emergeRequested = true;
		return true;
	}

	private static void begin(ServerPlayer player, ShadowMove move) {
		MOVES.put(player.getUUID(), move);
		ServerLevel level = player.level();
		// The sink anchors the body: shadow does not run. Submerge keeps only the sink itself slowed —
		// once under, the walk is free.
		int gripTicks = move.mode == MoveMode.SUBMERGE
				? MegumiProfile.SHADOW_SINK_TICKS
				: MegumiProfile.SHADOW_SINK_TICKS + MegumiProfile.SHADOW_HIDDEN_TICKS;
		player.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SHADOW_GRIP, gripTicks, 0, true, false, false), player);
		broadcastAnchoredCue(level, player, MegumiVfxIds.SHADOW_DIVE, player.position());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_GOO_FOLEY,
				SoundSource.PLAYERS, 0.85f, 0.62f);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		ShadowMove move = MOVES.get(player.getUUID());
		if (move == null) {
			return true;
		}
		return switch (move.phase) {
			case SINK -> {
				// The interruptible entry window: the hit lands and the shadow rejects him on the spot.
				cancelSink(player, move);
				yield true;
			}
			// Fully under: ordinary attacks cannot reach a body that is not there. Sources that bypass
			// invulnerability (the void, /kill) still land, and their death event tears the state down.
			case HIDDEN, SUBMERGED -> source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
			case EMERGE -> true;
		};
	}

	private static void cancelSink(ServerPlayer player, ShadowMove move) {
		if (MOVES.remove(player.getUUID(), move)) {
			ServerLevel level = player.level();
			broadcastAnchoredCue(level, player, MegumiVfxIds.SHADOW_EMERGE, player.position());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_IMPLODE,
					SoundSource.PLAYERS, 0.6f, 1.1f);
			// No teleport happened and no cooldown starts: the technique never came off.
		}
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, ShadowMove> entry : Map.copyOf(MOVES).entrySet()) {
			UUID ownerId = entry.getKey();
			ShadowMove move = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
			if (player == null || player.level().dimension() != move.dimension) {
				// The dimension-change and disconnect hooks also fire; this is the same teardown one tick sooner.
				teardown(server, ownerId);
				continue;
			}
			switch (move.phase) {
				case SINK -> tickSink(player, move);
				case HIDDEN -> tickHidden(player, move);
				case SUBMERGED -> tickSubmerged(player, move);
				case EMERGE -> tickEmerge(player, move);
			}
		}
	}

	private static void tickSink(ServerPlayer player, ShadowMove move) {
		if (--move.phaseTicksLeft > 0) {
			return;
		}
		if (move.mode == MoveMode.SUBMERGE) {
			move.phase = Phase.SUBMERGED;
			move.submergedTicks = 0;
		} else {
			move.phase = Phase.HIDDEN;
			move.phaseTicksLeft = MegumiProfile.SHADOW_HIDDEN_TICKS;
		}
		hide(player);
		if (move.emergeRequested && move.phase == Phase.SUBMERGED) {
			beginEmerge(player, move);
		}
	}

	private static void tickHidden(ServerPlayer player, ShadowMove move) {
		if (--move.phaseTicksLeft > 0) {
			return;
		}
		beginEmerge(player, move);
	}

	private static void tickSubmerged(ServerPlayer player, ShadowMove move) {
		move.submergedTicks++;
		if (move.emergeRequested || move.submergedTicks >= MegumiProfile.SUBMERGE_MAX_TICKS) {
			beginEmerge(player, move);
			return;
		}
		if (move.submergedTicks % MegumiProfile.SHADOW_RIPPLE_PERIOD_TICKS == 0) {
			broadcastAnchoredCue(player.level(), player, MegumiVfxIds.SHADOW_RIPPLE, player.position());
		}
	}

	/** Resolves the exit against the live world, moves the body, and opens the readable exit beat. */
	private static void beginEmerge(ServerPlayer player, ShadowMove move) {
		ServerLevel level = player.level();
		Exit exit = resolveExit(level, player, move);
		if (!exit.position().equals(player.position())) {
			player.teleportTo(level, exit.position().x, exit.position().y, exit.position().z,
					Set.<Relative>of(), exit.yaw(), player.getXRot(), false);
		} else {
			player.forceSetRotation(exit.yaw(), player.getXRot());
		}
		player.setYHeadRot(exit.yaw());
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.hurtMarked = true;
		reveal(player);
		move.phase = Phase.EMERGE;
		move.phaseTicksLeft = MegumiProfile.SHADOW_EMERGE_TICKS;
		MegumiSummonRuntime.startCooldownIfLonger(player, CharacterAbility.SECONDARY_SNEAK,
				MegumiShadowMovePolicy.cooldownTicks(move.mode == MoveMode.SUBMERGE));
		broadcastAnchoredCue(level, player, MegumiVfxIds.SHADOW_EMERGE, exit.position());
		level.playSound(null, exit.position().x, exit.position().y, exit.position().z,
				JujutsuSounds.PROJECTJJK_WHOOSH_HIT, SoundSource.PLAYERS, 0.9f, 0.78f);
	}

	private static void tickEmerge(ServerPlayer player, ShadowMove move) {
		if (--move.phaseTicksLeft <= 0) {
			MOVES.remove(player.getUUID(), move);
		}
	}

	private record Exit(Vec3 position, float yaw) {}

	private static Exit resolveExit(ServerLevel level, ServerPlayer player, ShadowMove move) {
		return switch (move.mode) {
			case BACKSTEP -> resolveBackstepExit(level, player, move);
			case FREE_STEP -> resolveFreeStepExit(level, player, move);
			case SUBMERGE -> resolveSubmergeExit(level, player, move);
		};
	}

	private static Exit resolveBackstepExit(ServerLevel level, ServerPlayer player, ShadowMove move) {
		Entity entity = level.getEntity(move.targetUuid);
		if (entity instanceof LivingEntity target && MegumiShadowMovePolicy.backstepTargetStillHolds(
				target.isAlive(), target.isRemoved(), target.level() == level,
				target.position().distanceToSqr(move.startPosition))) {
			for (float arc : MegumiShadowMovePolicy.REAR_ARC_DEGREES) {
				Vec3 requested = MegumiShadowMovePolicy.behindPoint(
						target.position(), target.yBodyRot, arc, MegumiProfile.BACKSTEP_DISTANCE);
				Vec3 placed = SafeBodyPlacement.find(level, player, requested, EXIT_PLACEMENT);
				if (placed != null) {
					return new Exit(placed, MegumiShadowMovePolicy.faceYawDegrees(placed, target.position()));
				}
			}
		}
		return returnExit(level, player, move);
	}

	private static Exit resolveFreeStepExit(ServerLevel level, ServerPlayer player, ShadowMove move) {
		// Re-validated against the world as it is now, not as it was at cast time.
		Vec3 placed = SafeBodyPlacement.find(level, player, move.freeStepPoint, EXIT_PLACEMENT);
		return placed != null ? new Exit(placed, player.getYRot()) : returnExit(level, player, move);
	}

	private static Exit resolveSubmergeExit(ServerLevel level, ServerPlayer player, ShadowMove move) {
		if (SafeBodyPlacement.isPlaceable(level, player, player.position(), MegumiProfile.WORLD_BORDER_MARGIN)) {
			return new Exit(player.position(), player.getYRot());
		}
		Vec3 rescued = SafeBodyPlacement.find(level, player, player.position(), RESCUE_PLACEMENT);
		return rescued != null ? new Exit(rescued, player.getYRot()) : returnExit(level, player, move);
	}

	/** The no-exit fallback: resurface where the move began. That point held a body seconds ago. */
	private static Exit returnExit(ServerLevel level, ServerPlayer player, ShadowMove move) {
		Vec3 back = SafeBodyPlacement.find(level, player, move.startPosition, RETURN_PLACEMENT);
		return new Exit(back != null ? back : move.startPosition, player.getYRot());
	}

	private static void hide(ServerPlayer player) {
		player.setInvisible(true);
	}

	private static void reveal(ServerPlayer player) {
		player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
	}

	static void teardown(MinecraftServer server, UUID ownerId) {
		if (server == null) {
			return;
		}
		ShadowMove move = MOVES.remove(ownerId);
		if (move == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
		if (player != null) {
			reveal(player);
		}
	}

	private static void broadcastAnchoredCue(ServerLevel level, ServerPlayer player, ResourceLocation effectId, Vec3 origin) {
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchored(effectId, origin, player.getId(), player.position(), 1,
						level.getGameTime(), player.getRandom().nextLong()));
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey) {
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
