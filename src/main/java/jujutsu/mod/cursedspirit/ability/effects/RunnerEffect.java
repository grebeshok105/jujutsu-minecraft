package jujutsu.mod.cursedspirit.ability.effects;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritProfile;
import jujutsu.mod.cursedspirit.CursedSpiritTierStats;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Grab-runner (Block 3, Step 6): the spirit snatches a player, carries them on a chaotic
 * run for ~4 s, and lets go. No damage at any point — the threat is displacement.
 *
 * <p>The pin reuses the one shared hold mechanic (C6): every tick calls
 * {@link HoldSupport#applyHold} with a short marker refresh, so the shared
 * {@code GRIPPED} marker never lapses (a lapsed tick lets the client fight the pin and
 * the victim "breaks out"). This file never re-implements the pin.
 *
 * <p>Exactly three actions are denied to a carried victim — attacking, block breaking,
 * and block <em>placement</em> (only when the held stack is a {@link BlockItem}, read
 * from the callback's hand). Doors, buttons, chests, crafting, item use, eating and slot
 * changes stay allowed: a wider right-click ban would be a design change, not a hold.
 *
 * <p>Routing safety is pure geometry, decided before the step is taken: lava ahead turns
 * the run, a drop of more than {@link #MAX_SAFE_DROP} turns the run, a wall collision
 * re-orients. The course itself wanders on a fixed steer period.
 */
public final class RunnerEffect {
	/** Marker refresh per carry tick: the marker must never lapse, so this stays small. */
	public static final int HOLD_MARKER_TICKS = 10;
	/** Contact telegraph duration after the spirit reaches the victim. */
	public static final int WINDUP_TICKS = 2;
	/** Course wander period. BALANCE-adjacent. */
	public static final int STEER_PERIOD_TICKS = 15;
	/** Look-ahead for lava and drops. BALANCE-adjacent. */
	public static final double ROUTE_PROBE_BLOCKS = 3.0;
	/** A fall deeper than this ahead turns the run. */
	public static final double MAX_SAFE_DROP = 10.0;
	/** Yaw kick on wall collision or unsafe route. */
	public static final float TURN_DEGREES = 120.0f;
	/** Forward hand offset in blocks. */
	public static final double CARRY_FORWARD = 0.6;
	/** Upward hand offset in blocks. */
	public static final double CARRY_UP = 1.2;

	public enum Phase {
		APPROACH,
		WINDUP,
		CONTACT,
		CARRY
	}

	/** Victims currently carried by any runner: the deny-predicates read exactly this. */
	private static final Set<UUID> CARRIED = ConcurrentHashMap.newKeySet();
	/** One runner may own the approach intent for a victim at a time. */
	private static final Map<UUID, UUID> APPROACHING = new ConcurrentHashMap<>();
	private static final Map<UUID, Phase> PHASES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> PHASE_TICKS = new ConcurrentHashMap<>();
	/**
	 * The exact player instance a run resolved — keyed by spirit UUID. A respawn or a
	 * disconnect/reconnect produces a NEW ServerPlayer with the same UUID; identity, not
	 * the UUID, is what keeps a run bound to the body it grabbed.
	 */
	private static final Map<UUID, ServerPlayer> RESOLVED = new ConcurrentHashMap<>();

	private RunnerEffect() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				isRunnerVictim(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
				isRunnerVictim(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
				isRunnerVictim(player) && isBlockPlacement(player.getItemInHand(hand))
						? InteractionResult.FAIL
						: InteractionResult.PASS);
		// JVM state must not leak into a later server/world.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CARRIED.clear();
			APPROACHING.clear();
			PHASES.clear();
			PHASE_TICKS.clear();
			RESOLVED.clear();
			HeldVictimRegistry.clear();
		});
	}

	public static boolean isRunnerVictim(Player player) {
		return player != null && CARRIED.contains(player.getUUID());
	}

	/** Placement = held stack is a block item. Empty hands and items are not placement. */
	public static boolean isBlockPlacement(ItemStack held) {
		return held != null && !held.isEmpty() && held.getItem() instanceof BlockItem;
	}

	/** Carried-victim count, for GameTests. */
	public static int carriedCount() {
		return CARRIED.size();
	}

	/** Current server phase, or {@code null} when no runner window owns the spirit. */
	public static Phase phaseOf(CursedSpiritEntity spirit) {
		return spirit == null ? null : PHASES.get(spirit.getUUID());
	}

	/**
	 * Contact gate expressed in hitbox-edge distance. The tier reach is still consulted through the
	 * shared attack policy (2.0/2.5/3.0); the runner's tighter 2.2-block edge cap prevents a tier's
	 * larger melee reach from becoming a distant grab.
	 */
	public static boolean inContactRange(CursedSpiritEntity spirit, ServerPlayer victim) {
		if (spirit == null || victim == null) {
			return false;
		}
		// Horizontal edge distance: a victim one slab/ledge up must still be grabbable, and the
		// 3D centre distance would tax that height difference twice. The vertical band keeps the
		// gate honest — a victim two blocks overhead is out of arm's reach.
		double dx = spirit.getX() - victim.getX();
		double dz = spirit.getZ() - victim.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = Math.abs(spirit.getY() - victim.getY());
		if (dy > 2.0) {
			return false;
		}
		CursedSpiritTierStats stats = CursedSpiritProfile.of(spirit.tier());
		boolean tierReach = CursedSpiritAttackPolicy.inReach(horizontal,
				spirit.getBbWidth(), victim.getBbWidth(), stats);
		double edgeDistance = horizontal - spirit.getBbWidth() * 0.5 - victim.getBbWidth() * 0.5;
		return tierReach && edgeDistance <= CursedSpiritAbilityProfile.RUNNER_CONTACT_RANGE;
	}

	public static boolean start(CursedSpiritEntity spirit, ServerPlayer victim, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		// Issue #80: the grab is control — never starts on a non-perceiving player.
		if (!CursePerception.mayTouch(spirit, victim)) {
			return false;
		}
		// One victim, one holder (issue #90): the shared GRIPPED marker is a single flag, so a
		// second holder's release would strip the deny-state the first still owns. Refuse both
		// already-held victims and duplicate approach intents.
		if (isRunnerVictim(victim) || HoldSupport.isHeld(victim)
				|| APPROACHING.containsKey(victim.getUUID())) {
			return false;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.GRAB_RUNNER, now + params.durationTicks(), params,
				victim.getUUID(), now)) {
			return false;
		}
		APPROACHING.put(victim.getUUID(), spirit.getUUID());
		setPhase(spirit, Phase.APPROACH);
		return true;
	}

	public static void tick(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain brain, CursedSpiritAbilityBrain.EffectState state, long now) {
		if (!(level.getEntity(state.targetUuid()) instanceof ServerPlayer victim)
				|| !victim.isAlive() || victim.hasDisconnected()) {
			end(spirit, state.targetUuid(), brain);
			return;
		}
		// A respawn or reconnect produces a new ServerPlayer with the same UUID — the run
		// belongs to the body it grabbed, not the name. First resolve binds the instance;
		// any later instance with the same UUID is a different body and ends the run.
		ServerPlayer bound = RESOLVED.putIfAbsent(spirit.getUUID(), victim);
		if (bound != null && bound != victim) {
			end(spirit, state.targetUuid(), brain);
			return;
		}
		// Issue #80: a victim that stops perceiving mid-run is released instead of being held by a
		// curse that no longer exists for them.
		if (!CursePerception.mayTouch(spirit, victim)) {
			end(spirit, state.targetUuid(), brain);
			return;
		}
		Phase phase = PHASES.getOrDefault(spirit.getUUID(), Phase.APPROACH);
		switch (phase) {
			case APPROACH -> {
				if (inContactRange(spirit, victim)) {
					setPhase(spirit, Phase.WINDUP);
					spirit.getNavigation().stop();
					spirit.getLookControl().setLookAt(victim, 30.0f, 30.0f);
					level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
				} else {
					approach(spirit, victim, state.params());
				}
			}
			case WINDUP -> {
				spirit.getNavigation().stop();
				spirit.getLookControl().setLookAt(victim, 30.0f, 30.0f);
				int elapsed = PHASE_TICKS.merge(spirit.getUUID(), 1, Integer::sum);
				if (elapsed >= WINDUP_TICKS) {
					setPhase(spirit, Phase.CONTACT);
				}
			}
			case CONTACT -> {
				// The victim may move or a wall may appear during the telegraph. This is the sole
				// authoritative gate; a miss ends cleanly without CARRIED, GRIPPED, or teleport.
				// A riding victim is refused the same way the toad's grab refuses one — pinning a
				// passenger fights the mount's own rideTick and displaces an unrelated vehicle.
				if (!inContactRange(spirit, victim) || !spirit.hasLineOfSight(victim)
						|| victim.isPassenger()) {
					end(spirit, victim.getUUID(), brain);
				} else {
					commitCarry(spirit, level, victim, now);
				}
			}
			case CARRY -> {
				HoldSupport.applyHold(spirit, victim, carryAnchor(spirit),
						HoldSupport.CollisionPolicy.RUNNER, HOLD_MARKER_TICKS);
				steer(spirit, level, state, now);
			}
		}
	}

	private static void approach(CursedSpiritEntity spirit, ServerPlayer victim,
			CursedSpiritAbilityParams params) {
		spirit.getLookControl().setLookAt(victim, 30.0f, 30.0f);
		double speed = clampSpeed(params.speed());
		if (spirit.getNavigation().isDone() && !inContactRange(spirit, victim)) {
			// Pathfinding calls ~3 blocks "close enough" and ends the path, but the contact gate
			// wants the victim inside arm's reach. Drive the last stretch through MoveControl —
			// the vanilla input path (travel reads the wanted position every tick) — instead of
			// a raw delta push, which the mob input model cancels out.
			spirit.getMoveControl().setWantedPosition(victim.getX(), victim.getY(), victim.getZ(),
					speed);
			return;
		}
		spirit.getNavigation().moveTo(victim, speed);
	}

	private static void commitCarry(CursedSpiritEntity spirit, ServerLevel level,
			ServerPlayer victim, long now) {
		dropHands(victim);
		CARRIED.add(victim.getUUID());
		APPROACHING.remove(victim.getUUID(), spirit.getUUID());
		setPhase(spirit, Phase.CARRY);
		Vec3 contactPoint = carryAnchor(spirit);
		HoldSupport.applyHold(spirit, victim, contactPoint,
				HoldSupport.CollisionPolicy.RUNNER, HOLD_MARKER_TICKS);
		JujutsuNetworking.broadcastVfxCue(level, contactPoint,
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.anchored(CursedSpiritVfxIds.RUNNER, contactPoint, spirit.getId(),
						spirit.position(), 1, now, spirit.getRandom().nextLong()),
				CursePerception::perceives);
	}

	/** Cancels every run owned by a removed spirit. Bodies released, markers lifted. */
	public static void cancelAllFor(CursedSpiritEntity spirit, CursedSpiritAbilityBrain brain) {
		CursedSpiritAbilityBrain.EffectState state =
				brain.window(CursedSpiritAbilityId.GRAB_RUNNER);
		if (state != null && state.targetUuid() != null) {
			end(spirit, state.targetUuid(), brain);
		}
	}

	/** Releases the carry set entry and the shared pin. Idempotent: safe on expiry paths. */
	public static void end(CursedSpiritEntity spirit, UUID victimUuid,
			CursedSpiritAbilityBrain brain) {
		brain.forceEnd(CursedSpiritAbilityId.GRAB_RUNNER);
		UUID spiritUuid = spirit == null ? null : spirit.getUUID();
		if (spiritUuid != null) {
			RESOLVED.remove(spiritUuid);
			PHASES.remove(spiritUuid);
			PHASE_TICKS.remove(spiritUuid);
		}
		if (victimUuid == null) {
			return;
		}
		CARRIED.remove(victimUuid);
		if (spiritUuid != null) {
			APPROACHING.remove(victimUuid, spiritUuid);
		} else {
			APPROACHING.remove(victimUuid);
		}
		HeldVictimRegistry.release(victimUuid);
		if (spirit.level() instanceof ServerLevel level) {
			// Resolve server-wide, not just in this level: a victim who changed dimension or
			// unloaded before the release is invisible to level.getEntity but still wears
			// GRIPPED — the marker would linger on the destination player for its refresh
			// window and keep the client smoothing/deny gates alive.
			if (level.getEntity(victimUuid) instanceof ServerPlayer victim) {
				HoldSupport.release(victim);
			} else if (level.getServer().getPlayerList().getPlayer(victimUuid) instanceof ServerPlayer remote) {
				remote.removeEffect(jujutsu.mod.registry.JujutsuEffects.GRIPPED);
			}
			// Close the client attack pose only when this run reached the telegraph.
			level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_RELEASE);
		}
	}

	private static void setPhase(CursedSpiritEntity spirit, Phase phase) {
		PHASES.put(spirit.getUUID(), phase);
		PHASE_TICKS.put(spirit.getUUID(), 0);
	}

	private static Vec3 carryAnchor(CursedSpiritEntity spirit) {
		Vec3 forward = Vec3.directionFromRotation(0.0f, spirit.getYRot());
		return spirit.position().add(forward.scale(CARRY_FORWARD)).add(0.0, CARRY_UP, 0.0);
	}

	/**
	 * Drops both hands at the victim's feet, inventory untouched, empty hands dropping nothing.
	 * Main hand first, then off hand — the order the GameTest counts on.
	 */
	static void dropHands(ServerPlayer victim) {
		ItemStack main = victim.getMainHandItem();
		if (!main.isEmpty()) {
			victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			victim.drop(main, true);
		}
		ItemStack off = victim.getOffhandItem();
		if (!off.isEmpty()) {
			victim.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			victim.drop(off, true);
		}
	}

	private static void steer(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain.EffectState state, long now) {
		long age = now - state.startedGameTime();
		float yaw = spirit.getYRot();
		if (age % STEER_PERIOD_TICKS == 0) {
			yaw += spirit.getRandom().nextFloat() * 90.0f - 45.0f;
		}
		if (spirit.horizontalCollision || !routeSafe(spirit, level, yaw)) {
			yaw += TURN_DEGREES;
		}
		spirit.setYRot(yaw);
		Vec3 forward = Vec3.directionFromRotation(0.0f, yaw);
		BlockPos ahead = BlockPos.containing(spirit.position().add(forward.scale(2.0)));
		spirit.getNavigation().moveTo(ahead.getX() + 0.5, ahead.getY(), ahead.getZ() + 0.5,
				clampSpeed(state.params().speed()));
	}

	private static double clampSpeed(double speed) {
		return Math.min(2.0, Math.max(0.5, speed));
	}

	/**
	 * Pure-geometry route probe: lava in the ahead column or a drop deeper than
	 * {@link #MAX_SAFE_DROP} fails the course. No pathfinding, no block reads beyond the two probe
	 * columns.
	 */
	static boolean routeSafe(CursedSpiritEntity spirit, ServerLevel level, float yaw) {
		Vec3 forward = Vec3.directionFromRotation(0.0f, yaw);
		Vec3 probe = spirit.position().add(forward.scale(ROUTE_PROBE_BLOCKS));
		BlockPos at = BlockPos.containing(probe);
		if (level.getFluidState(at).is(FluidTags.LAVA)
				|| level.getFluidState(at.above()).is(FluidTags.LAVA)) {
			return false;
		}
		int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).getY();
		return spirit.position().y - ground <= MAX_SAFE_DROP;
	}
}
