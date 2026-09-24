package jujutsu.mod.character.megumi;

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
import jujutsu.mod.character.megumi.MegumiSerpentEntity.SerpentState;
import jujutsu.mod.combat.CombatTags;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Great Serpent's behaviour (plan §C): the ambush/restraint state machine
 * {@code FOLLOW → READY → PREPARE_AMBUSH → SUBMERGED → EMERGE → BIND → RELEASE → RECOVERY →
 * FOLLOW}. The serpent never swings — when it has a workable mark it sinks where it stands,
 * waits as a visible sunken coil, teleports to a placement-checked point behind the victim, and
 * pins it for the rest of the pack.
 *
 * <p><strong>Who is ambushed.</strong> The owner's sic mark first, the body's own pick (nearest
 * ambushable candidate of the shared combat context, rescanned every
 * {@code SERPENT_AMBUSH_SCAN_TICKS}) only when there is no mark — a self-picked target never
 * overwrites the owner's order, and the coordinator's autonomy marks arrive through the same
 * sic-mark field as the manual one.
 *
 * <p><strong>How the bind is enforced.</strong> Exactly the toad's hold: the shared
 * {@link HoldSupport} pin every tick (players are teleported to the mouth anchor and GRIPPED,
 * mobs additionally get navigation stopped and Slowness 100), the body itself planted so the
 * anchor cannot walk away. SUBMERGED is a visible sunken coil — Phase.ACTIVE with the sunken
 * action clip, never {@code setInvisible}.
 */
final class MegumiSerpentBrain {
	/** The player marker is refreshed every tick; a short buff is enough and self-heals after death. */
	private static final int GRIP_MARKER_TICKS = 10;
	/** Slowness keeps a bound mob from walking out of the coil between velocity resets. */
	private static final int GRIP_SLOWNESS_TICKS = 10;
	/** The spec's grip slowness is "Slowness 100" — the high amplifier, not a gentle drain. */
	private static final int GRIP_SLOWNESS_AMPLIFIER = 100;
	/** Failure-memory key for every ambush that died before landing a bind (plan §C). */
	private static final String AMBUSH_FAILURE_KEY = "serpent_ambush";
	/**
	 * Placement rules for the surface point: a tight horizontal ring around each rear-arc
	 * request, a couple of upward steps for uneven ground, world-border margin zero (arenas
	 * hug the default border in tests) and never the risky exact-requested fallback — a body
	 * that did not ask to move must not be forced into geometry, and the serpent CHOSE to move
	 * but is still better off re-picking than teleporting into a wall (deviation 4.7's abort).
	 */
	private static final SafeBodyPlacement.Policy EMERGE_PLACEMENT =
			new SafeBodyPlacement.Policy(1.0, 2, 0.0, false);

	private MegumiSerpentBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiSerpentEntity serpent, long gameTime) {
		serpent.tickState();
		switch (serpent.state()) {
			case FOLLOW, READY -> tickSeeking(level, owner, serpent, gameTime);
			case PREPARE_AMBUSH -> tickPrepare(level, owner, serpent, gameTime);
			case SUBMERGED -> tickSubmerged(level, owner, serpent, gameTime);
			case EMERGE -> tickEmerge(level, owner, serpent, gameTime);
			case BIND -> tickBind(level, owner, serpent, gameTime);
			case RELEASE -> tickRelease(serpent);
			case RECOVERY -> tickRecovery(serpent);
		}
	}

	// --- FOLLOW / READY: vanilla follow + read marks --------------------------------------------

	/**
	 * The marked target is worked on every tick it stays valid; without one the body looks for its
	 * own on the scan cadence. READY is just "a mark is being read" — both names share the loop.
	 */
	private static void tickSeeking(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		serpent.setNoAi(false);
		serpent.setPresentationAction(0);
		LivingEntity marked = resolve(level, serpent.sicTargetUuid());
		if (marked == null) {
			if (serpent.state() != SerpentState.FOLLOW) {
				serpent.setState(SerpentState.FOLLOW);
			}
			if (owner == null || !serpent.attackReady(gameTime)
					|| !MegumiSerpentPolicy.ambushDue(gameTime, serpent.nextAmbushScanGameTime())) {
				return;
			}
			serpent.markAmbushScan(gameTime + MegumiShikigamiProfile.SERPENT_AMBUSH_SCAN_TICKS);
			LivingEntity pick = nearestAmbushable(level, owner, serpent);
			if (pick != null) {
				beginAmbush(level, owner, serpent, pick, gameTime);
			}
			return;
		}
		if (serpent.state() != SerpentState.READY) {
			serpent.setState(SerpentState.READY);
		}
		if (!serpent.attackReady(gameTime)
				|| !MegumiSerpentPolicy.canAmbush(ambushFacts(level, owner, serpent, marked))) {
			return;
		}
		beginAmbush(level, owner, serpent, marked, gameTime);
	}

	/**
	 * The body's own pick: the nearest ambushable candidate of the shared combat context. Soft
	 * coordination (issue #107 §3): a target no ally already marks or works is preferred — the
	 * serpent spreads the pack across the crowd instead of doubling up. Falls back to the nearest
	 * overall when everything in reach is claimed.
	 */
	private static LivingEntity nearestAmbushable(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent) {
		MegumiCombatContext context = MegumiPackCoordinator.contextFor(owner, level);
		Set<UUID> claimed = context.occupiedOrClaimed();
		LivingEntity nearest = null;
		LivingEntity nearestFree = null;
		double best = Double.MAX_VALUE;
		double bestFree = Double.MAX_VALUE;
		for (LivingEntity candidate : context.candidates()) {
			if (candidate == serpent || candidate.isSpectator()
					|| !MegumiSerpentPolicy.canAmbush(ambushFacts(level, owner, serpent, candidate))) {
				continue;
			}
			double distance = serpent.distanceToSqr(candidate);
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

	/**
	 * The telegraph: the submerge clip runs while the surface point is validated — all the way
	 * back to RECOVERY when no rear-arc point can be placement-checked (the wall trick from
	 * {@code serpentNeverNoclips} dies here, before the body ever sinks).
	 */
	private static void beginAmbush(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, LivingEntity target, long gameTime) {
		Vec3 point = findEmergePoint(level, serpent, target);
		if (point == null) {
			abortAmbush(level, serpent, gameTime);
			return;
		}
		serpent.beginAmbush(target, point);
		serpent.setState(SerpentState.PREPARE_AMBUSH);
		serpent.setNoAi(true);
		serpent.getNavigation().stop();
		serpent.setDeltaMovement(Vec3.ZERO);
		serpent.setPresentationAction(MegumiSerpentEntity.ACTION_SUBMERGE);
	}

	/**
	 * The first placement-checked point of the rear arc, or null when every request and its
	 * fallback ring is unusable — the contract orders the candidates, {@link SafeBodyPlacement}
	 * rules each one.
	 */
	private static Vec3 findEmergePoint(ServerLevel level, MegumiSerpentEntity serpent,
			LivingEntity target) {
		for (Vec3 requested : MegumiSerpentPolicy.emergeCandidates(target.position(),
				target.yBodyRot, MegumiShikigamiProfile.SERPENT_EMERGE_REAR_OFFSET)) {
			Vec3 found = SafeBodyPlacement.find(level, serpent, requested, EMERGE_PLACEMENT);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	// --- committed states -----------------------------------------------------------------------

	private static void tickPrepare(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		if (target == null
				|| !MegumiSerpentPolicy.ambushStillHolds(ambushFacts(level, owner, serpent, target))) {
			abortAmbush(level, serpent, gameTime);
			return;
		}
		if (serpent.stateTicks() >= MegumiShikigamiProfile.SERPENT_PREPARE_TICKS) {
			serpent.setState(SerpentState.SUBMERGED);
			serpent.setDeltaMovement(Vec3.ZERO);
			serpent.setPresentationAction(MegumiSerpentEntity.ACTION_SUBMERGED);
		}
	}

	private static void tickSubmerged(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		serpent.getNavigation().stop();
		serpent.setDeltaMovement(Vec3.ZERO);
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		if (target == null
				|| !MegumiSerpentPolicy.ambushStillHolds(ambushFacts(level, owner, serpent, target))
				|| serpent.stateTicks() >= MegumiShikigamiProfile.SERPENT_SUBMERGED_MAX_TICKS) {
			// Surfacing empty-handed: the window closed or the mark escaped while the coil waited.
			abortAmbush(level, serpent, gameTime);
			return;
		}
		Vec3 point = serpent.emergePoint();
		if (point == null
				|| !SafeBodyPlacement.isPlaceable(level, serpent, point, EMERGE_PLACEMENT.borderMargin())) {
			serpent.markEmergePointInvalid();
			if (serpent.emergePointInvalidTicks() >= MegumiSerpentPolicy.SERPENT_EMERGE_ABORT_TICKS) {
				// A wall over the surface spot: better to stay home than to noclip into it.
				abortAmbush(level, serpent, gameTime);
			}
			return;
		}
		serpent.clearEmergePointInvalid();
		if (serpent.stateTicks() >= MegumiSerpentPolicy.SERPENT_SUBMERGED_DWELL_TICKS) {
			serpent.setState(SerpentState.EMERGE);
			serpent.setPresentationAction(MegumiSerpentEntity.ACTION_EMERGE);
		}
	}

	private static void tickEmerge(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		if (serpent.stateTicks() == 1) {
			// The point is re-validated on the exact emerge tick (deviation 10): the placement the
			// PREPARE tick checked can be gone by the time the body actually travels.
			LivingEntity target = resolve(level, serpent.ambushTargetUuid());
			Vec3 point = serpent.emergePoint();
			if (target == null || point == null
					|| !MegumiSerpentPolicy.ambushStillHolds(ambushFacts(level, owner, serpent, target))
					|| !SafeBodyPlacement.isPlaceable(level, serpent, point, EMERGE_PLACEMENT.borderMargin())) {
				abortAmbush(level, serpent, gameTime);
				return;
			}
			float yaw = MegumiShadowMovePolicy.faceYawDegrees(point, target.position());
			serpent.teleportTo(level, point.x, point.y, point.z, Set.of(), yaw, 0.0f, false);
			serpent.getNavigation().stop();
			serpent.setDeltaMovement(Vec3.ZERO);
			level.playSound(null, serpent.getX(), serpent.getY(), serpent.getZ(), SoundEvents.RAVAGER_ROAR,
					SoundSource.NEUTRAL, 0.8f, 1.0f);
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_EMERGE,
					point, serpent.getId(), Vec3.ZERO);
			return;
		}
		if (serpent.stateTicks() >= MegumiShikigamiProfile.SERPENT_EMERGE_TICKS) {
			commitBind(level, owner, serpent, gameTime);
		}
	}

	/**
	 * The rise is over: the coil closes on the ambush target — re-checked like the toad's commit
	 * tick, because the whole dive is long enough for the victim to die, be grabbed by another
	 * holder, mount something, or leave the owner's side.
	 */
	private static void commitBind(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		boolean valid = target != null && owner != null
				&& MegumiSerpentPolicy.canAmbush(ambushFacts(level, owner, serpent, target));
		if (!valid) {
			abortAmbush(level, serpent, gameTime);
			return;
		}
		int bindTicks = MegumiSerpentPolicy.bindTicksFor(target.getMaxHealth(), hitboxVolume(target),
				target instanceof Player);
		serpent.beginBind(target, gameTime + bindTicks);
		serpent.setState(SerpentState.BIND);
		serpent.setPresentationAction(MegumiSerpentEntity.ACTION_BIND);
		// The coil closes atomically with the state: isBinding() must imply a live registry
		// pair — a one-tick gap where BIND is set but the victim is not yet held reads as a
		// broken pin to anything polling the hold (and lets the victim get one free move).
		HoldSupport.applyHold(serpent, target,
				MegumiSerpentPolicy.mouthAnchor(serpent.position(), serpent.getLookAngle()),
				HoldSupport.CollisionPolicy.SERPENT, GRIP_MARKER_TICKS);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_SPLASH,
				SoundSource.NEUTRAL, 0.9f, 1.0f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_BIND,
				target.position(), target.getId(), new Vec3(0.0, target.getBbHeight() * 0.5, 0.0));
	}

	/** Every bound tick: plant the body, pin the victim to the mouth anchor, watch for the exit. */
	private static void tickBind(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, long gameTime) {
		// resolveHeld (not resolve) keeps a dead-but-still-present victim reachable so its
		// marker can be lifted explicitly — a respawned player would otherwise re-enter the
		// level still GRIPPED (issue #90).
		LivingEntity victim = resolveHeld(level, serpent.bindVictimUuid());
		double ownerLeash = owner != null && owner.level() == level ? serpent.distanceTo(owner)
				: Double.MAX_VALUE;
		MegumiSerpentPolicy.SerpentAction action = MegumiSerpentPolicy.bindAction(
				new MegumiSerpentPolicy.BindFacts(
						victim != null,
						victim == null || victim.isAlive(),
						victim instanceof ServerPlayer player && player.hasDisconnected(),
						victim != null && victim.level() == level,
						gameTime >= serpent.bindEndGameTime(),
						ownerLeash,
						victim == null ? 0.0 : serpent.distanceTo(victim)));
		if (action == MegumiSerpentPolicy.SerpentAction.ABORT) {
			// Death, disconnect, despawn or a dimension change is a release, not a toss: the
			// unwrap is for a live exit. The entity's own release path drops the registry pair
			// by UUID (a victim this level cannot resolve still unmarks) and strips GRIPPED on
			// whichever side of the dimension line the victim ended up — dead-but-present
			// included, so a respawned player never re-enters the level still GRIPPED (issue #90).
			serpent.releaseBindVictim();
			serpent.setState(SerpentState.RECOVERY);
			serpent.setPresentationAction(0);
			return;
		}
		if (action == MegumiSerpentPolicy.SerpentAction.RELEASE) {
			releaseVictim(level, owner, serpent, victim, gameTime);
			return;
		}
		// The body coils in place: a walking serpent would drag both the anchor and the victim.
		serpent.getNavigation().stop();
		serpent.setDeltaMovement(Vec3.ZERO);
		Vec3 anchor = MegumiSerpentPolicy.mouthAnchor(serpent.position(), serpent.getLookAngle());
		HoldSupport.applyHold(serpent, victim, anchor, HoldSupport.CollisionPolicy.SERPENT,
				GRIP_MARKER_TICKS);
		if (victim instanceof Mob mob) {
			// A mob is server-driven and its own AI keeps pushing between our ticks: stop the
			// navigation and drain what is left of its walking speed. No setNoAi — the design keeps
			// a bound mob able to aim and attack its owner.
			mob.getNavigation().stop();
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GRIP_SLOWNESS_TICKS,
					GRIP_SLOWNESS_AMPLIFIER, false, false, false), serpent);
		}
	}

	/** The live exit: the victim is tossed away from its owner, released, and the unwrap plays. */
	private static void releaseVictim(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, LivingEntity victim, long gameTime) {
		Vec3 origin = owner != null ? owner.position() : serpent.position();
		victim.setDeltaMovement(MegumiSerpentPolicy.tossVelocity(origin, serpent.position()));
		victim.hurtMarked = true;
		HoldSupport.release(victim);
		if (!(victim instanceof Player)) {
			// Strip only the tail of our own grip refresh: a slowness that predates the bind or
			// came from somewhere else (different amplifier, or longer than one refresh window)
			// belongs to its owner and survives the toss.
			MobEffectInstance active = victim.getEffect(MobEffects.SLOWNESS);
			if (active != null && active.getAmplifier() == GRIP_SLOWNESS_AMPLIFIER
					&& active.getDuration() <= GRIP_SLOWNESS_TICKS) {
				victim.removeEffect(MobEffects.SLOWNESS);
			}
		}
		serpent.clearBind();
		serpent.setState(SerpentState.RELEASE);
		serpent.setPresentationAction(MegumiSerpentEntity.ACTION_RELEASE);
		serpent.markAttackUsed(gameTime, MegumiShikigamiProfile.SERPENT_BIND_COOLDOWN_TICKS);
		level.playSound(null, serpent.getX(), serpent.getY(), serpent.getZ(), SoundEvents.WITHER_BREAK_BLOCK,
				SoundSource.NEUTRAL, 0.5f, 1.0f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_RELEASE,
				victim.position(), victim.getId(), new Vec3(0.0, victim.getBbHeight() * 0.5, 0.0));
	}

	private static void tickRelease(MegumiSerpentEntity serpent) {
		serpent.setDeltaMovement(Vec3.ZERO);
		if (serpent.stateTicks() >= MegumiSerpentPolicy.SERPENT_RELEASE_TICKS) {
			serpent.setState(SerpentState.RECOVERY);
			serpent.setPresentationAction(0);
		}
	}

	private static void tickRecovery(MegumiSerpentEntity serpent) {
		serpent.setNoAi(false);
		if (serpent.stateTicks() >= MegumiSerpentPolicy.SERPENT_RECOVERY_TICKS) {
			serpent.setState(SerpentState.FOLLOW);
		}
	}

	/** A dead ambush: drop the commitment, pay the short retry pause, remember the failure. */
	private static void abortAmbush(ServerLevel level, MegumiSerpentEntity serpent, long gameTime) {
		serpent.clearAmbush();
		serpent.setState(SerpentState.RECOVERY);
		serpent.setNoAi(false);
		serpent.setPresentationAction(0);
		serpent.markAttackUsed(gameTime, MegumiShikigamiProfile.SERPENT_AMBUSH_SCAN_TICKS);
		MegumiFailureMemory.recordFailure(serpent.getUUID(), AMBUSH_FAILURE_KEY, gameTime);
	}

	// --- facts + small shared helpers -----------------------------------------------------------

	private static MegumiSerpentPolicy.AmbushFacts ambushFacts(ServerLevel level, ServerPlayer owner,
			MegumiSerpentEntity serpent, LivingEntity target) {
		return new MegumiSerpentPolicy.AmbushFacts(
				MegumiHostilityPolicy.isHostile(owner, target),
				MegumiShikigamiFriendlyFire.isOwnSideOnly(owner, target),
				MegumiShikigamiFriendlyFire.isProtected(owner, target),
				owner != null && MegumiSummonRuntime.isEligibleTarget(owner, target),
				HoldSupport.isHeld(target),
				CombatTags.isUngrabbable(target),
				target.isPassenger(),
				isAirborne(level, target),
				serpent.distanceTo(target));
	}

	/**
	 * Deviation 4.7's airborne refusal: a target off the ground (dive-carried, mid-knockback,
	 * flying) has no ankles to bite. Swimming counts as grounded — the coil works in water.
	 *
	 * <p>{@code onGround()} alone cannot decide this: the flag is only recomputed inside travel(),
	 * which a NoAI body never runs — a body standing planted on the floor reports
	 * {@code onGround()==false} from spawn to despawn. The probe therefore asks the world whether
	 * anything actually supports the hitbox: no collision within half a block below means air.
	 */
	private static boolean isAirborne(ServerLevel level, LivingEntity target) {
		return !target.onGround() && !target.isInWater() && !target.isInLava()
				&& level.noCollision(target, target.getBoundingBox().move(0.0, -0.51, 0.0));
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
