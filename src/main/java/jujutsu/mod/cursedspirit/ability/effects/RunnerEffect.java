package jujutsu.mod.cursedspirit.ability.effects;

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
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
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
	/** Course wander period. BALANCE-adjacent. */
	public static final int STEER_PERIOD_TICKS = 15;
	/** Look-ahead for lava and drops. BALANCE-adjacent. */
	public static final double ROUTE_PROBE_BLOCKS = 3.0;
	/** A fall deeper than this ahead turns the run. */
	public static final double MAX_SAFE_DROP = 10.0;
	/** Yaw kick on wall collision or unsafe route. */
	public static final float TURN_DEGREES = 120.0f;

	/** Victims currently carried by any runner: the deny-predicates read exactly this. */
	private static final Set<UUID> CARRIED = ConcurrentHashMap.newKeySet();

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
		// Same convention as every static runtime in the repo: the deny-set is JVM state,
		// so a server stop inside a carry must not leak it into the next world — a stale
		// entry would deny attack/break/place to that UUID for the rest of the session.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CARRIED.clear());
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

	public static boolean start(CursedSpiritEntity spirit, ServerPlayer victim, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		// Issue #80: the grab is control — never starts on a non-perceiving player.
		if (!CursePerception.mayTouch(spirit, victim)) {
			return false;
		}
		// One victim, one holder (issue #90): the shared GRIPPED marker is a single flag, so a
		// second holder's release would strip the deny-state the first still owns. Refuse the
		// start on a victim already carried by another runner or held by a toad.
		if (isRunnerVictim(victim) || HoldSupport.isHeld(victim)) {
			return false;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.GRAB_RUNNER, now + params.durationTicks(), params,
				victim.getUUID(), now)) {
			return false;
		}
		dropHands(victim);
		CARRIED.add(victim.getUUID());
		HoldSupport.applyHold(victim, carryAnchor(spirit), HOLD_MARKER_TICKS);
		ServerLevel level = (ServerLevel) spirit.level();
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
		JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.anchored(CursedSpiritVfxIds.RUNNER, spirit.position(), spirit.getId(),
						spirit.position(), 1, now, spirit.getRandom().nextLong()),
				CursePerception::perceives);
		return true;
	}

	/**
	 * Carry tick: refresh the shared pin every tick (no lapsed marker, ever), steer the
	 * run, end on timeout/victim loss. The run deals no damage on any path.
	 */
	public static void tick(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain brain, CursedSpiritAbilityBrain.EffectState state, long now) {
		if (!(level.getEntity(state.targetUuid()) instanceof ServerPlayer victim)
				|| !victim.isAlive() || victim.hasDisconnected()) {
			end(spirit, state.targetUuid(), brain);
			return;
		}
		// Issue #80: a victim that stops perceiving mid-carry (vessel switch) is released
		// instead of being held by a curse that no longer exists for them.
		if (!CursePerception.mayTouch(spirit, victim)) {
			end(spirit, state.targetUuid(), brain);
			return;
		}
		HoldSupport.applyHold(victim, carryAnchor(spirit), HOLD_MARKER_TICKS);
		steer(spirit, level, state, now);
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
		if (victimUuid == null) {
			return;
		}
		CARRIED.remove(victimUuid);
		if (spirit.level() instanceof ServerLevel level
				&& level.getEntity(victimUuid) instanceof ServerPlayer victim) {
			HoldSupport.release(victim);
		}
	}

	private static Vec3 carryAnchor(CursedSpiritEntity spirit) {
		return spirit.position();
	}

	/**
	 * Drops both hands at the victim's feet, inventory untouched, empty hands dropping
	 * nothing. Main hand first, then off hand — the order the GameTest counts on.
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
	 * {@link #MAX_SAFE_DROP} fails the course. No pathfinding, no block reads beyond the
	 * two probe columns.
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
