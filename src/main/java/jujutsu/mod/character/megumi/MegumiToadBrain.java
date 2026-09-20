package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.CombatTags;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Toad's sic behaviour (issue #79): a short windup, then the tongue commits and the target is
 * <em>held</em> — 3 to 5 seconds pinned in front of the body, then thrown away from its owner. The
 * tongue no longer deals damage: the value of the move is control, not a hit.
 *
 * <p><strong>Who is grabbed.</strong> The owner's sic mark first, the body's own pick (nearest
 * eligible body in reach, rescanned every {@code GRAB_SCAN_INTERVAL_TICKS}) only when there is no
 * mark — a self-picked target never overwrites the owner's order.
 *
 * <p><strong>How a hold is enforced.</strong> Mobs are held by stopping their navigation and
 * zeroing their velocity every tick (their AI would otherwise walk them out of the grip), players
 * by the shared {@link HoldSupport} pin: the client owns its own movement, so the server teleports
 * them to the anchor and the {@code GRIPPED} marker stops the client from fighting it. Both cases
 * keep the body itself planted, or the anchor would walk away with it.
 */
final class MegumiToadBrain {
	/** How often an unmarked body looks for something to grab. */
	private static final int GRAB_SCAN_INTERVAL_TICKS = 20;
	/** The player marker is refreshed every tick; a short buff is enough and self-heals after death. */
	private static final int GRIP_MARKER_TICKS = 10;
	/** Slowness keeps a held mob from walking out of the grip between velocity resets. */
	private static final int GRIP_SLOWNESS_TICKS = 10;
	/** The spec's grip slowness is "Slowness 100" — the high amplifier, not a gentle drain. */
	private static final int GRIP_SLOWNESS_AMPLIFIER = 100;
	/** How long the client keeps drawing the toss after it happened. */
	private static final int THROW_FLASH_TICKS = 10;

	private MegumiToadBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiToadEntity toad, long gameTime) {
		if (toad.isHolding()) {
			tickHold(level, owner, toad, gameTime);
			return;
		}
		LivingEntity target = pickTarget(level, owner, toad, gameTime);
		if (target == null) {
			toad.clearGrabIntent();
			return;
		}
		if (MegumiToadPolicy.strikeTickReached(toad.actionTicks())) {
			commitGrab(level, owner, toad, target, gameTime);
			return;
		}
		if (toad.actionTicks() > 0) {
			// Mid-windup: the intent is already recorded and re-checked on the commit tick.
			return;
		}
		// Owner LoS gates only the owner's own ORDER (an owner cannot sic what it cannot see).
		// A self-picked target answers to the body's own senses instead: nearestGrabbable already
		// required the toad's LoS, and the commit below re-checks it — an owner standing in a
		// cellar must not blind its toad (issue #90). The same holds for an AUTONOMOUS mark the
		// coordinator wrote: it is the pack's pick, not the owner's sighted order — treating it
		// as owner-ordered let a blind owner veto the body's own mark (CI flake 35362569362).
		boolean ownerOrdered = toad.hasManualSicTarget()
				&& toad.sicTargetUuid() != null
				&& toad.sicTargetUuid().equals(target.getUUID());
		if (owner == null || !toad.attackReady(gameTime)
				|| !MegumiToadPolicy.canGrab(toad.distanceTo(target))
				|| (ownerOrdered && !owner.hasLineOfSight(target))) {
			return;
		}
		toad.beginGrabIntent(target);
		toad.beginAction(MegumiShikigamiProfile.TOAD_GRAB_WINDUP_TICKS);
		level.playSound(null, toad.getX(), toad.getY(), toad.getZ(), SoundEvents.FROG_TONGUE,
				SoundSource.NEUTRAL, 0.9f, 1.0f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TOAD_TONGUE,
				toad.position(), toad.getId(), Vec3.ZERO);
	}

	/**
	 * The mark the body is working on: the owner's sic, else the target of the windup already
	 * running, else a fresh self-pick (throttled — the scan walks the level).
	 */
	private static LivingEntity pickTarget(ServerLevel level, ServerPlayer owner,
			MegumiToadEntity toad, long gameTime) {
		LivingEntity marked = resolve(level, toad.sicTargetUuid());
		if (marked != null) {
			return marked;
		}
		LivingEntity intent = resolve(level, toad.grabIntentUuid());
		if (intent != null) {
			return intent;
		}
		if (owner == null || gameTime < toad.nextGrabScanGameTime()) {
			return null;
		}
		toad.markGrabScan(gameTime + GRAB_SCAN_INTERVAL_TICKS);
		return nearestGrabbable(level, owner, toad);
	}

	private static LivingEntity nearestGrabbable(ServerLevel level, ServerPlayer owner,
			MegumiToadEntity toad) {
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
				toad.getBoundingBox().inflate(MegumiShikigamiProfile.TOAD_GRAB_RANGE),
				candidate -> candidate != toad
						&& candidate.isAlive()
						&& !candidate.isRemoved()
						&& !candidate.isSpectator()
						&& !CombatTags.isUngrabbable(candidate)
						&& !candidate.isPassenger()
						&& !HoldSupport.isHeld(candidate)
						&& MegumiSummonRuntime.isEligibleTarget(owner, candidate)
						&& MegumiToadPolicy.canGrab(toad.distanceTo(candidate))
						&& toad.hasLineOfSight(candidate));
		// Soft coordination (issue #107 §3): a target no ally already marks or works is preferred —
		// the toad spreads the pack across the crowd instead of doubling up. Falls back to the
		// nearest overall when everything in reach is claimed.
		Set<UUID> claimed = owner == null ? Set.of()
				: MegumiPackCoordinator.contextFor(owner, level).occupiedOrClaimed();
		LivingEntity nearest = null;
		LivingEntity nearestFree = null;
		double best = Double.MAX_VALUE;
		double bestFree = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = toad.distanceToSqr(candidate);
			if (distance < best) {
				best = distance;
				nearest = candidate;
			}
			if (!claimed.contains(candidate.getUUID()) && distance < bestFree) {
				bestFree = distance;
				nearestFree = candidate;
			}
		}
		return nearestFree != null ? nearestFree : nearest;
	}

	/** The tongue has landed: nothing is damaged, the hold starts (R1). */
	private static void commitGrab(ServerLevel level, ServerPlayer owner, MegumiToadEntity toad,
			LivingEntity target, long gameTime) {
		// Re-check eligibility at the commit tick: six ticks of windup are enough for the target
		// to die, be grabbed by another holder, mount something, or leave the owner's side.
		boolean valid = target.isAlive()
				&& !target.isRemoved()
				// A passenger escapes the pin: the vehicle re-asserts the rider's position in
				// rideTick. Refusing is the honest gate — a teleport mid-ride is a lie either way.
				&& !target.isPassenger()
				// One victim, one holder: the shared GRIPPED marker is a single flag, so a second
				// holder's release would strip the deny-state the first still owns (issue #90).
				&& !HoldSupport.isHeld(target)
				&& !CombatTags.isUngrabbable(target)
				&& (owner == null || MegumiSummonRuntime.isEligibleTarget(owner, target))
				&& MegumiToadPolicy.canGrab(toad.distanceTo(target))
				// The tongue is the body's: the toad's own LoS is the commit gate (issue #90 —
				// a sic ordered while the owner saw the target must not land through a wall).
				&& toad.hasLineOfSight(target);
		if (valid) {
			int holdTicks = MegumiToadPolicy.holdTicksFor(target.getMaxHealth(), hitboxVolume(target),
					target instanceof Player);
			toad.beginGrab(target, gameTime + holdTicks);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FROG_EAT,
					SoundSource.NEUTRAL, 0.9f, 1.0f);
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TOAD_TONGUE,
					target.position(), target.getId(), new Vec3(0.0, target.getBbHeight() * 0.5, 0.0));
		}
		toad.clearGrabIntent();
		toad.markAttackUsed(gameTime, MegumiShikigamiProfile.TOAD_GRAB_COOLDOWN_TICKS);
	}

	/** Every held tick: plant the body, pin the victim to the anchor, watch for the exit. */
	private static void tickHold(ServerLevel level, ServerPlayer owner, MegumiToadEntity toad,
			long gameTime) {
		// resolveHeld (not resolve) keeps a dead-but-still-present victim reachable so its
		// marker can be lifted explicitly — a respawned player would otherwise re-enter the
		// level still GRIPPED (issue #90).
		LivingEntity victim = resolveHeld(level, toad.grabbedUuid());
		if (victim == null) {
			// The victim left the level entirely (unload, dimension change, despawn): the
			// registry pair must still drop, or the UUID stays marked held forever and the
			// same entity can never be grabbed again after it returns.
			HeldVictimRegistry.release(toad.grabbedUuid());
			toad.clearGrab();
			return;
		}
		// Death or disconnect is a release, not a throw: the toss is for a live exit. Without
		// this guard a corpse is re-pinned for the death window and a respawned player is
		// dragged back to the anchor (issue #90).
		if (!victim.isAlive()
				|| (victim instanceof ServerPlayer player && player.hasDisconnected())) {
			HoldSupport.release(victim);
			toad.clearGrab();
			return;
		}
		double leash = owner == null ? 0.0 : toad.distanceTo(owner);
		if (gameTime >= toad.grabEndGameTime()
				|| (owner != null && MegumiToadPolicy.bindBroken(leash, MegumiShikigamiProfile.TOAD_GRAB_BIND_RANGE))) {
			throwVictim(level, owner, toad, victim, gameTime);
			return;
		}
		// The body plants its feet: a walking toad would drag both the anchor and the victim.
		toad.getNavigation().stop();
		toad.setDeltaMovement(Vec3.ZERO);
		Vec3 anchor = MegumiToadPolicy.anchor(toad.position(), toad.getLookAngle(),
				MegumiShikigamiProfile.TOAD_GRIP_OFFSET);
		// HoldSupport keeps the historic Toad fallback to the body's feet when the hand anchor
		// intersects a solid block; unlike the runner, this policy is intentionally one-step.
		HoldSupport.applyHold(toad, victim, anchor, HoldSupport.CollisionPolicy.TOAD, GRIP_MARKER_TICKS);
		if (victim instanceof Mob mob) {
			// A mob is server-driven and its own AI keeps pushing between our ticks: stop the
			// navigation and drain what is left of its walking speed. No setNoAi — the design keeps
			// a held mob able to aim and attack its owner (R14).
			mob.getNavigation().stop();
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GRIP_SLOWNESS_TICKS,
					GRIP_SLOWNESS_AMPLIFIER, false, false, false), owner);
		}
	}

	/** The hold is over: the victim is tossed away from its owner, staggered, and released. */
	private static void throwVictim(ServerLevel level, ServerPlayer owner, MegumiToadEntity toad,
			LivingEntity victim, long gameTime) {
		Vec3 origin = owner != null ? owner.position() : toad.position();
		// Stagger first: its velocity scale would eat the toss applied below, and the toss is
		// exactly what the victim carries into the next tick.
		CombatStagger.GLOBAL.apply(victim, gameTime, MegumiShikigamiProfile.TOAD_THROW_STAGGER_TICKS);
		victim.setDeltaMovement(MegumiToadPolicy.throwVelocity(origin, toad.position(),
				MegumiShikigamiProfile.TOAD_THROW_SPEED, MegumiShikigamiProfile.TOAD_THROW_LIFT));
		victim.hurtMarked = true;
		// Both kinds carry the marker through the hold (the pin is written for mobs too), so both
		// drop it here — otherwise a thrown mob keeps a foreign HARMFUL icon for the marker's rest.
		HoldSupport.release(victim);
		if (!(victim instanceof Player)) {
			// Strip only the tail of our own grip refresh: a slowness that predates the grab or
			// came from somewhere else (different amplifier, or longer than one refresh window)
			// belongs to its owner and survives the throw.
			MobEffectInstance active = victim.getEffect(MobEffects.SLOWNESS);
			if (active != null && active.getAmplifier() == GRIP_SLOWNESS_AMPLIFIER
					&& active.getDuration() <= GRIP_SLOWNESS_TICKS) {
				victim.removeEffect(MobEffects.SLOWNESS);
			}
		}
		toad.markThrown(victim, gameTime + THROW_FLASH_TICKS);
		toad.clearGrab();
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.FROG_LONG_JUMP,
				SoundSource.NEUTRAL, 0.8f, 1.1f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TOAD_TONGUE,
				victim.position(), victim.getId(), new Vec3(0.0, victim.getBbHeight() * 0.5, 0.0));
	}

	private static double hitboxVolume(LivingEntity entity) {
		return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}

	/** Like {@link #resolve} but keeps a dead victim reachable so its marker can be lifted. */
	private static LivingEntity resolveHeld(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& !living.isRemoved() && living.level() == level
				? living : null;
	}
}
