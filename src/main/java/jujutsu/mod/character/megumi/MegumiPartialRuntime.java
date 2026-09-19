package jujutsu.mod.character.megumi;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.MegumiTongueStatePayload;
import jujutsu.mod.network.MegumiWingsStatePayload;
import jujutsu.mod.registry.JujutsuEffects;


/**
 * Server-authoritative state machine of Megumi's partial manifestations (issue #108): Nue's wings
 * and Toad's tongue, one at a time, keyed by owner UUID like every other Megumi runtime.
 *
 * <pre>
 * PARTIAL          PARTIAL_RELEASE
 * NUE selection:   wings on ⇄ wings off (toggle)          — no release semantics
 * TOAD selection:  tongue anchored at the aimed surface    — detaches the tongue
 * other selection: refused, and so is a second kit's partial while one is out
 * </pre>
 *
 * <p>Rules the file bends around. The marker effect is the authority: the wings' fall-flying grant
 * (B3's elytra event) and the tongue's both-sides gate read {@code JujutsuEffects}, so the state map
 * mirrors the marker and re-syncs from it every tick — an effect lifted by anything else (milk, a
 * death screen, {@code /effect clear}) ends the partial with it. Nothing else starts the glide:
 * the fall-flying flag's only setter is {@code startFallFlying()}, so the upkeep re-asserts
 * {@code tryToStartFallFlying()} every tick the owner is airborne with the wings out (D2). And every
 * way a partial can end — the player's own key, a shadow move, death, disconnect, respawn, a
 * dimension change, server stop, vessel deselect, the dev fixture reset — lands in the hard teardown
 * path ({@link #teardown}/{@link #endPartial}) that removes the marker AND tells the tongue's client
 * to stop pulling: a client left with {@code active=true} would keep gliding the body on its own
 * authority.
 */
public final class MegumiPartialRuntime {
	private static final Map<UUID, PartialState> PARTIALS = new ConcurrentHashMap<>();
	private static final Map<UUID, MegumiTongueStatePayload> LAST_TONGUE_PAYLOADS = new ConcurrentHashMap<>();
	private static final Map<UUID, WingPayloadProbe> LAST_WING_PAYLOADS = new ConcurrentHashMap<>();
	private static final int TONGUE_SHOOT_TICKS = 4;
	private static final int TONGUE_RETRACT_TICKS = 6;
	private static final int WINGS_MATERIALIZE_TICKS = 8;
	private static final int WINGS_HEARTBEAT_TICKS = 40;

	private MegumiPartialRuntime() {}

	/** One owner's live partial: what kind, since when, and the per-kind upkeep state. */
	static final class PartialState {
		final MegumiPartialProfile.PartialKind kind;
		final long startedGameTime;
		/** Toad only: the point the tongue is stuck to, and the block that point belongs to. */
		Vec3 anchor;
		BlockPos anchorBlock;
		/** Toad only: original shot timestamp and the soft-retract countdown. */
		long tongueShotGameTime;
		int tonguePhase = MegumiTongueStatePayload.SHOOTING;
		int tonguePendingRemoval;
		/** Nue only: whether the owner has been off the ground since the wings came out (D2 landing). */
		boolean airborneSeen;
		int wingPhase = MegumiWingsStatePayload.MATERIALIZING;
		long lastWingHeartbeat;

		private PartialState(MegumiPartialProfile.PartialKind kind, long startedGameTime) {
			this.kind = kind;
			this.startedGameTime = startedGameTime;
			this.lastWingHeartbeat = startedGameTime;
		}
	}

	/** Read-only snapshot of one owner's active partial, for the dev control surface. */
	public record PartialView(String kind, long startedGameTime) {}
	/** Test-visible snapshot of the most recent wing phase send for one owner. */
	public record WingPayloadProbe(boolean active, int phase) {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiPartialRuntime::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				teardown(player.getServer(), player.getUUID());
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				teardown(newPlayer.getServer(), newPlayer.getUUID()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				teardown(player.getServer(), player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PartialState state = PARTIALS.get(handler.player.getUUID());
			if (state != null) {
				endPartial(handler.player, state);
			}
		});
		// The marker effect persists with the player; the PARTIALS map does not. A restart leaves a
		// serialized marker with no runtime state behind it — the wings would keep granting flight and
		// the tongue's client would never hear the stand-down. JOIN reconciles by marker, not by map.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				reconcileOrphanMarkers(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID ownerId : Set.copyOf(PARTIALS.keySet())) {
				teardown(server, ownerId);
			}
			PARTIALS.clear();
			LAST_TONGUE_PAYLOADS.clear();
			LAST_WING_PAYLOADS.clear();
		});
	}

	/** True while any partial manifestation is active for this owner. */
	public static boolean isAnyActive(UUID ownerId) {
		return PARTIALS.containsKey(ownerId);
	}

	/** True while the partial of exactly this shikigami type is active (spec §19 exclusion). */
	public static boolean isActiveForType(UUID ownerId, MegumiShikigami type) {
		PartialState state = PARTIALS.get(ownerId);
		return state != null && state.kind.type() == type;
	}

	/** Live snapshot of the owner's partial state, if one exists. */
	public static Optional<PartialView> partialView(UUID ownerId) {
		PartialState state = PARTIALS.get(ownerId);
		return state == null
				? Optional.empty()
				: Optional.of(new PartialView(state.kind.type().id(), state.startedGameTime));
	}

	/** Press edge of the shared partial key: toggles wings on Nue, starts the tongue on Toad. */
	public static boolean tryPartial(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		MegumiPartialProfile.PartialKind kind =
				MegumiPartialProfile.PartialKind.forSelection(MegumiShikigamiSelection.selected(ownerId));
		if (kind == null) {
			return reject(player, notify, "message.jujutsumod.megumi.partial.wrong_type");
		}
		PartialState active = PARTIALS.get(ownerId);
		if (active != null) {
			if (active.kind != kind) {
				// One partial at a time (R24). The selection cycle refuses while a partial is out, so this
				// arm is reachable only through a forced selection (a command, a dev-lane tool) — and it
				// still refuses rather than swapping the partial under the player.
				return reject(player, notify, "message.jujutsumod.megumi.partial.in_progress");
			}
			if (kind == MegumiPartialProfile.PartialKind.WINGS) {
				endPartial(player, active);
			}
			// Toad: the tongue is already out and the release edge is what detaches it; a repeated press
			// (key repeat, a doubled packet) is the same press, not a second tongue.
			return true;
		}
		if (MegumiShikigamiRuntime.pack(ownerId, kind.type()) != null) {
			// §19 exclusion, one direction: the full body is materialized, so it cannot also be partial.
			// The other direction (a summon refused because this partial is out) lives in the summon path.
			return reject(player, notify, "message.jujutsumod.megumi.partial.materialized",
					Component.translatable(nameKey(kind.type())));
		}
		return switch (kind) {
			case WINGS -> startWings(player, notify);
			case TONGUE -> startTongue(player, notify);
		};
	}

	/** Release edge of the shared partial key: starts the tongue's soft retract window. */
	public static boolean tryPartialRelease(ServerPlayer player) {
		PartialState state = PARTIALS.get(player.getUUID());
		if (state == null || state.kind != MegumiPartialProfile.PartialKind.TONGUE) {
			// The wings have no release semantics, and a release that follows a refused press has nothing
			// to end: both are refusals, in the same way a hold release with no live hold is one.
			return false;
		}
		beginTongueRetract(player, state);
		return true;
	}

	/**
	 * Ends any active partial for this owner. Registered against the same triggers as the packs, and
	 * called by the vessel deselect and the dev fixture reset.
	 */
	public static void teardown(MinecraftServer server, UUID ownerId) {
		PartialState state = PARTIALS.get(ownerId);
		if (state == null) {
			return;
		}
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(ownerId);
		if (player == null) {
			// Offline with no server to look the player up on (a shutdown walk, a fixture step): there is
			// nobody to tell and no body to cleanse, so the record is all that is left to drop.
			PARTIALS.remove(ownerId, state);
			return;
		}
		endPartial(player, state);
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, PartialState> entry : Map.copyOf(PARTIALS).entrySet()) {
			UUID ownerId = entry.getKey();
			PartialState state = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
			if (player == null) {
				// The disconnect hook also fires; this is the same drop one tick sooner.
				PARTIALS.remove(ownerId, state);
				continue;
			}
			if (MegumiShadowMoveRuntime.locksAbilities(player)) {
				// D10: a shadow move swallows every other technique, and a body inside the shadow cannot hold
				// a wing or a tongue. A tick of lag is unobservable and it keeps this file out of the move's
				// own runtime.
				endPartial(player, state);
				continue;
			}
			switch (state.kind) {
				case WINGS -> tickWings(player, state);
				case TONGUE -> tickTongue(player, state);
			}
		}
	}

	private static void tickWings(ServerPlayer player, PartialState state) {
		if (!player.hasEffect(JujutsuEffects.MEGUMI_NUE_WINGS)) {
			// The marker is what the elytra event and the fall-damage veto read, so it is the authority:
			// a marker lifted by anything else takes the partial with it, state map included.
			endPartial(player, state);
			return;
		}
		long now = player.level().getGameTime();
		if (state.wingPhase == MegumiWingsStatePayload.MATERIALIZING
				&& now - state.startedGameTime >= WINGS_MATERIALIZE_TICKS) {
			setWingPhase(player, state, MegumiWingsStatePayload.GROUND_FOLDED);
		}
		if (player.onGround()) {
			if (state.airborneSeen) {
				setWingPhase(player, state, MegumiWingsStatePayload.FOLDING);
				endPartial(player, state);
				return;
			}
			heartbeatWings(player, state, now);
			return;
		}
		state.airborneSeen = true;
		if (state.wingPhase != MegumiWingsStatePayload.FLYING) {
			setWingPhase(player, state, MegumiWingsStatePayload.FLYING);
		}
		heartbeatWings(player, state, now);
		// Nothing else sets the fall-flying flag (D2) — vanilla only ever clears it — so the request is
		// re-asserted every airborne tick. Already-flying is a no-op returning false.
		player.tryToStartFallFlying();
	}

	private static void tickTongue(ServerPlayer player, PartialState state) {
		if (state.tonguePendingRemoval > 0) {
			if (--state.tonguePendingRemoval == 0) {
				endPartial(player, state);
			}
			return;
		}
		if (!player.hasEffect(JujutsuEffects.MEGUMI_TOAD_TONGUE)) {
			// Marker loss is a soft visual exit: leave a retracting tongue on the client for a few ticks.
			beginTongueRetract(player, state);
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (state.tonguePhase == MegumiTongueStatePayload.SHOOTING
				&& now - state.tongueShotGameTime >= TONGUE_SHOOT_TICKS) {
			state.tonguePhase = MegumiTongueStatePayload.ANCHORED;
			sendTongueState(player, state, true);
		}
		// Anchor destroyed (R32) and line blocked (R31) are the two ways the tongue lets go by itself.
		// The anchor check asks the same question the attach clip asked: does this cell still present a
		// collision surface? `isSolid()` is the wrong predicate — fences, panes and slabs collide
		// without being solid, and the resolver's COLLIDER clip happily anchors to them.
		if (level.getBlockState(state.anchorBlock).getCollisionShape(level, state.anchorBlock).isEmpty()
				|| !lineToAnchorIsClear(level, player, state.anchor)) {
			beginTongueRetract(player, state);
		}
	}

	private static boolean startWings(ServerPlayer player, boolean notify) {
		addMarker(player, JujutsuEffects.MEGUMI_NUE_WINGS);
		long now = player.level().getGameTime();
		PartialState state = new PartialState(MegumiPartialProfile.PartialKind.WINGS, now);
		PARTIALS.put(player.getUUID(), state);
		sendWingPhase(player, MegumiWingsStatePayload.MATERIALIZING);
		// State first, cue second: the cue is presentation and may be watched by anything.
		MegumiNueWings.playUnfoldCue(player);
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.partial.wings_out"), true);
		}
		return true;
	}

	private static boolean startTongue(ServerPlayer player, boolean notify) {
		TargetResolver.Result aimed = TargetResolver.resolve(
				player.level(), player, MegumiPartialProfile.TONGUE_RANGE, candidate -> false);
		if (aimed.mode() != TargetResolver.Mode.BLOCK) {
			// No surface on the ray inside the range: the tongue has nothing to stick to. A refused
			// partial starts nothing — no marker, no payload, no state.
			return reject(player, notify, "message.jujutsumod.megumi.partial.no_anchor");
		}
		Vec3 anchor = aimed.point();
		long now = player.level().getGameTime();
		PartialState state = new PartialState(MegumiPartialProfile.PartialKind.TONGUE, now);
		state.anchor = anchor;
		state.tongueShotGameTime = now;
		// The hit point sits ON the face, so the anchored block is found by stepping half a block along the
		// inward normal; `containing(hitPoint)` alone would pick the air cell above a floor hit.
		state.anchorBlock = BlockPos.containing(anchor.subtract(aimed.normal().scale(0.5)));
		addMarker(player, JujutsuEffects.MEGUMI_TOAD_TONGUE);
		PARTIALS.put(player.getUUID(), state);
		sendTongueState(player, state, true);
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.partial.tongue_out"), true);
		}
		return true;
	}

	/** Hard teardown: no retract window is allowed for deselect, death, disconnect, or shadow lock. */
	private static void endPartial(ServerPlayer player, PartialState state) {
		if (!PARTIALS.remove(player.getUUID(), state)) {
			return;
		}
		player.removeEffect(markerFor(state.kind));
		if (state.kind == MegumiPartialProfile.PartialKind.WINGS) {
			setWingPhase(player, state, MegumiWingsStatePayload.FOLDING);
			sendWingInactive(player);
		} else {
			sendTongueState(player, state, false);
			sendWingInactive(player);
		}
	}

	/**
	 * Fail-safe for the one gap teardown cannot cover: a JVM restart serializes the marker effects but
	 * not {@link #PARTIALS}. Any marker found on a joining player without a live state is an orphan —
	 * remove it, and stand the tongue's client down so it does not resume pulling on a dead anchor.
	 */
	private static void reconcileOrphanMarkers(ServerPlayer player) {
		PartialState live = PARTIALS.get(player.getUUID());
		if (live == null || live.kind != MegumiPartialProfile.PartialKind.WINGS) {
			sendWingInactive(player);
		}
		for (MegumiPartialProfile.PartialKind kind : MegumiPartialProfile.PartialKind.values()) {
			if (live != null && live.kind == kind) {
				continue;
			}
			if (player.hasEffect(markerFor(kind))) {
				player.removeEffect(markerFor(kind));
				if (kind == MegumiPartialProfile.PartialKind.TONGUE) {
					sendTongueState(player, null, false);
				}
			}
		}
	}

	private static void addMarker(ServerPlayer player, Holder<MobEffect> marker) {
		// Infinite and hidden: the marker is a gate the server and the client both read, not a buff with a
		// lifetime — every end of a partial removes it, and nothing else ever will.
		player.addEffect(new MobEffectInstance(marker, MobEffectInstance.INFINITE_DURATION, 0,
				false, false, false), player);
	}

	private static Holder<MobEffect> markerFor(MegumiPartialProfile.PartialKind kind) {
		return switch (kind) {
			case WINGS -> JujutsuEffects.MEGUMI_NUE_WINGS;
			case TONGUE -> JujutsuEffects.MEGUMI_TOAD_TONGUE;
		};
	}

	private static void setWingPhase(ServerPlayer player, PartialState state, int phase) {
		if (state.wingPhase == phase) {
			return;
		}
		state.wingPhase = phase;
		state.lastWingHeartbeat = player.level().getGameTime();
		sendWingPhase(player, phase);
	}

	private static void heartbeatWings(ServerPlayer player, PartialState state, long now) {
		if (now - state.lastWingHeartbeat >= WINGS_HEARTBEAT_TICKS) {
			state.lastWingHeartbeat = now;
			sendWingPhase(player, state.wingPhase);
		}
	}

	private static void beginTongueRetract(ServerPlayer player, PartialState state) {
		if (state.tonguePendingRemoval > 0) {
			return;
		}
		state.tonguePhase = MegumiTongueStatePayload.RETRACTING;
		state.tonguePendingRemoval = TONGUE_RETRACT_TICKS;
		sendTongueState(player, state, true);
	}

	/**
	 * Test-visible outgoing snapshot. GameTests use this to assert the production phase transition
	 * without depending on a mock player's absent network channel.
	 */
	public static MegumiTongueStatePayload lastTonguePayload(UUID ownerId) {
		return ownerId == null ? null : LAST_TONGUE_PAYLOADS.get(ownerId);
	}
	/** Test-visible snapshot of the last wing send, including inactive teardown. */
	public static WingPayloadProbe lastWingPayload(UUID ownerId) {
		return ownerId == null ? null : LAST_WING_PAYLOADS.get(ownerId);
	}

	private static void sendWingPhase(ServerPlayer player, int phase) {
		LAST_WING_PAYLOADS.put(player.getUUID(), new WingPayloadProbe(true, phase));
		MegumiWingsSync.send(player, phase);
	}

	private static void sendWingInactive(ServerPlayer player) {
		LAST_WING_PAYLOADS.put(player.getUUID(),
				new WingPayloadProbe(false, MegumiWingsStatePayload.FOLDING));
		MegumiWingsSync.sendInactive(player);
	}

	private static void sendTongueState(ServerPlayer player, PartialState state, boolean active) {
		Vec3 anchor = state == null || state.anchor == null ? Vec3.ZERO : state.anchor;
		int phase = state == null ? MegumiTongueStatePayload.RETRACTING : state.tonguePhase;
		long shotGameTime = state == null ? 0L : state.tongueShotGameTime;
		MegumiTongueStatePayload payload = new MegumiTongueStatePayload(
				player.getUUID(), active, phase, anchor.x, anchor.y, anchor.z, shotGameTime);
		LAST_TONGUE_PAYLOADS.put(player.getUUID(), payload);
		Set<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(player));
		recipients.add(player);
		for (ServerPlayer recipient : recipients) {
			if (recipient.connection != null
					&& ServerPlayNetworking.canSend(recipient, MegumiTongueStatePayload.TYPE)) {
				ServerPlayNetworking.send(recipient, payload);
			}
		}
	}

	/**
	 * The per-tick line-of-sight re-clip (D8/R31). The segment ends on the anchored face, so a clip that
	 * lands within {@link MegumiPartialProfile#TONGUE_LINE_TOLERANCE} of the anchor hit that face; a
	 * nearer hit is a body in the way, and a clean miss means the line runs to the anchor unobstructed.
	 */
	private static boolean lineToAnchorIsClear(ServerLevel level, ServerPlayer player, Vec3 anchor) {
		HitResult hit = level.clip(new ClipContext(player.getEyePosition(), anchor,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return true;
		}
		double tolerance = MegumiPartialProfile.TONGUE_LINE_TOLERANCE;
		return hit.getLocation().distanceToSqr(anchor) < tolerance * tolerance;
	}

	private static String nameKey(MegumiShikigami type) {
		return "jujutsumod.megumi.shikigami." + type.id();
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey, Object... args) {
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey, args), true);
		}
		return false;
	}
}
