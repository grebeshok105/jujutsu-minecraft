package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatTags;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.vfx.MegumiVfxIds;

/** Server-authoritative ambush and restraint loop for the Great Serpent. */
final class MegumiSerpentBrain {
	private static final SafeBodyPlacement.Policy EMERGE_PLACEMENT = new SafeBodyPlacement.Policy(
			0.75, 1, MegumiProfile.WORLD_BORDER_MARGIN, false);
	private static final SafeBodyPlacement.Policy RETURN_PLACEMENT = new SafeBodyPlacement.Policy(
			1.0, 1, MegumiProfile.WORLD_BORDER_MARGIN, false);

	private MegumiSerpentBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiSerpentEntity serpent, long gameTime) {
		switch (serpent.serpentState()) {
			case FOLLOW_READY -> tickReady(level, owner, serpent, gameTime);
			case PREPARE_AMBUSH -> tickPrepare(level, owner, serpent, gameTime);
			case SUBMERGED -> tickSubmerged(level, owner, serpent, gameTime);
			case EMERGE -> tickEmerge(level, owner, serpent, gameTime);
			case BIND -> tickBind(level, owner, serpent, gameTime);
			case RELEASE -> {
				keepPlanted(serpent);
				if (gameTime >= serpent.stateDeadlineGameTime()) {
					serpent.finishRelease(gameTime);
				}
			}
			case RECOVERY -> {
				keepPlanted(serpent);
				if (gameTime >= serpent.stateDeadlineGameTime()) {
					serpent.finishRecovery(gameTime);
				}
			}
		}
	}

	private static void tickReady(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			long gameTime) {
		if (owner == null || !owner.isAlive() || gameTime % MegumiShikigamiProfile.SERPENT_SCAN_TICKS != 0L) {
			return;
		}
		LivingEntity target = resolve(level, serpent.sicTargetUuid());
		if (target == null) {
			return;
		}
		MegumiSerpentPolicy.AmbushFacts facts = ambushFacts(owner, serpent, target);
		if (!MegumiSerpentPolicy.canStart(facts)) {
			return;
		}
		serpent.beginPrepareAmbush(target, gameTime);
		play(level, serpent, SoundEvents.SPIDER_STEP, 0.72f, 0.72f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_AMBUSH,
				target.position(), serpent.getId(), Vec3.ZERO);
	}

	private static void tickPrepare(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			long gameTime) {
		keepPlanted(serpent);
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		boolean targetValid = owner != null && ambushTargetStillValid(owner, serpent, target);
		if (!targetValid) {
			serpent.beginRecovery(gameTime);
			return;
		}
		if (gameTime >= serpent.stateDeadlineGameTime() && serpent.beginSubmerge(gameTime)) {
			// Setting vanilla invisibility is presentation-only: phase remains ACTIVE, collision and
			// hurt handling stay on the base entity path, and reveal re-applies potion invisibility.
			keepSubmerged(serpent);
		}
	}

	private static void tickSubmerged(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			long gameTime) {
		keepSubmerged(serpent);
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		boolean targetValid = owner != null && ambushTargetStillValid(owner, serpent, target);
		if (!targetValid) {
			recoverAtSafeReturn(level, serpent, gameTime);
			return;
		}
		if (gameTime < serpent.stateDeadlineGameTime()) {
			return;
		}
		Vec3 emerge = findEmergeSpot(level, serpent, target);
		if (emerge == null) {
			recoverAtSafeReturn(level, serpent, gameTime);
			return;
		}
		boolean beganEmerge = serpent.beginEmerge(emerge, target, gameTime);
		if (beganEmerge) {
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_AMBUSH,
					emerge, serpent.getId(), Vec3.ZERO);
		}
	}

	private static void tickEmerge(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			long gameTime) {
		keepPlanted(serpent);
		if (gameTime < serpent.stateDeadlineGameTime()) {
			return;
		}
		LivingEntity target = resolve(level, serpent.ambushTargetUuid());
		boolean targetValid = owner != null && ambushTargetStillValid(owner, serpent, target);
		MegumiSerpentPolicy.BindFacts facts = target == null
				? null : bindFacts(owner, serpent, target, false);
		boolean bindFactsAllow = facts != null && MegumiSerpentPolicy.canBind(facts);
		boolean canBind = targetValid && bindFactsAllow;
		if (!targetValid || !canBind) {
			serpent.beginRecovery(gameTime);
			return;
		}
		if (!serpent.beginBind(target, gameTime)) {
			serpent.beginRecovery(gameTime);
			return;
		}
		play(level, serpent, SoundEvents.PHANTOM_BITE, 0.68f, 0.72f);
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_BIND,
				target.position(), serpent.getId(), Vec3.ZERO);
		applyHold(serpent, target);
		if (!HeldVictimRegistry.isHeldBy(serpent, target)) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.HOLD_OWNERSHIP_MISMATCH,
					target, gameTime);
		}
	}

	private static void tickBind(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			long gameTime) {
		keepPlanted(serpent);
		UUID victimUuid = serpent.bindTargetUuid();
		LivingEntity victim = resolveIncludingDead(level, victimUuid);
		if (victim == null) {
			releaseByUuid(serpent, victimUuid);
			release(level, owner, serpent, releaseEventForMissing(level, victimUuid), null, gameTime);
			return;
		}
		if (victim instanceof ServerPlayer player && player.hasDisconnected()) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.VICTIM_DISCONNECT, victim, gameTime);
			return;
		}
		if (!victim.isAlive()) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.VICTIM_DEATH, victim, gameTime);
			return;
		}
		if (owner == null || !owner.isAlive()) {
			MegumiSerpentPolicy.Event event = owner != null
					? MegumiSerpentPolicy.Event.OWNER_DEATH
					: MegumiSerpentPolicy.Event.OWNER_DISCONNECT;
			release(level, owner, serpent, event, victim, gameTime);
			return;
		}
		if (victim.level() != level) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.VICTIM_DIMENSION_CHANGE, victim, gameTime);
			return;
		}
		if (owner.level() != level) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.OWNER_DIMENSION_CHANGE, victim, gameTime);
			return;
		}
		UUID registeredHolder = HeldVictimRegistry.holderUuid(victimUuid);
		if (!serpent.getUUID().equals(registeredHolder)) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.HOLD_OWNERSHIP_MISMATCH,
					victim, gameTime);
			return;
		}
		if (!MegumiSerpentPolicy.canBind(bindFacts(owner, serpent, victim, true))) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.VICTIM_INELIGIBLE, victim, gameTime);
			return;
		}
		if (MegumiSerpentPolicy.bindBroken(serpent.distanceTo(owner), MegumiShikigamiProfile.SERPENT_BIND_LEASH)) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.BIND_LEASH, victim, gameTime);
			return;
		}
		if (gameTime >= serpent.stateDeadlineGameTime()) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.BIND_TIMER, victim, gameTime);
			return;
		}
		applyHold(serpent, victim);
		if (!HeldVictimRegistry.isHeldBy(serpent, victim)) {
			release(level, owner, serpent, MegumiSerpentPolicy.Event.HOLD_OWNERSHIP_MISMATCH,
					victim, gameTime);
			return;
		}
		if (victim instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	private static MegumiSerpentPolicy.AmbushFacts ambushFacts(ServerPlayer owner,
			MegumiSerpentEntity serpent, LivingEntity target) {
		MegumiSerpentPolicy.MarkKind mark = switch (serpent.markKind()) {
			case MANUAL -> MegumiSerpentPolicy.MarkKind.MANUAL;
			case RETALIATION -> MegumiSerpentPolicy.MarkKind.RETALIATION;
			case AUTONOMOUS -> MegumiSerpentPolicy.MarkKind.AUTONOMOUS;
			case null -> MegumiSerpentPolicy.MarkKind.NONE;
		};
		boolean eligible = target != null && target.isAlive() && !target.isRemoved()
				&& target.level() == serpent.level()
				&& MegumiSummonRuntime.isEligibleTarget(owner, target);
		return new MegumiSerpentPolicy.AmbushFacts(mark, eligible,
				target != null && serpent.distanceTo(target) <= MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE,
				target != null && serpent.hasLineOfSight(target),
				target != null && owner.hasLineOfSight(target));
	}

	private static boolean ambushTargetStillValid(ServerPlayer owner, MegumiSerpentEntity serpent,
			LivingEntity target) {
		UUID lockedTarget = serpent.ambushTargetUuid();
		if (target == null || lockedTarget == null || !lockedTarget.equals(serpent.sicTargetUuid())) {
			return false;
		}
		return MegumiSerpentPolicy.canStart(ambushFacts(owner, serpent, target));
	}


	private static MegumiSerpentPolicy.BindFacts bindFacts(ServerPlayer owner,
			MegumiSerpentEntity serpent, LivingEntity target, boolean allowOwnHold) {
		boolean heldByOther = target != null && HoldSupport.isHeld(target)
				&& !(allowOwnHold && HeldVictimRegistry.isHeldBy(serpent, target));
		return new MegumiSerpentPolicy.BindFacts(
				target != null && target.isAlive(),
				target == null || target.isRemoved(),
				target != null && owner != null && MegumiSummonRuntime.isEligibleTarget(owner, target),
				target != null && target.isPassenger(),
				heldByOther,
				CombatTags.isUngrabbable(target),
				target != null && serpent.distanceTo(target) <= MegumiShikigamiProfile.SERPENT_BIND_RANGE,
				target != null && serpent.hasLineOfSight(target));
	}

	private static Vec3 findEmergeSpot(ServerLevel level, MegumiSerpentEntity serpent, LivingEntity target) {
		Vec3 towardSerpent = serpent.position().subtract(target.position());
		for (Vec3 requested : MegumiSerpentPolicy.emergeCandidates(target.position(), towardSerpent,
					MegumiShikigamiProfile.SERPENT_BIND_RANGE * 0.65)) {
			Vec3 placed = SafeBodyPlacement.find(level, serpent, requested, EMERGE_PLACEMENT);
			if (placed != null && safeEmergeFacts(level, serpent, placed)) {
				return placed;
			}
		}
		return null;
	}

	private static boolean safeEmergeFacts(ServerLevel level, MegumiSerpentEntity serpent, Vec3 candidate) {
		boolean finite = candidate != null && Double.isFinite(candidate.x)
				&& Double.isFinite(candidate.y) && Double.isFinite(candidate.z);
		if (!finite) {
			return false;
		}
		BlockPos position = BlockPos.containing(candidate);
		AABB box = serpent.getDimensions(serpent.getPose()).makeBoundingBox(candidate);
		boolean inWorld = level.isInWorldBounds(position)
				&& box.minY >= level.getMinY() && box.maxY <= level.getMaxY();
		boolean loaded = level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4);
		boolean insideBorder = level.getWorldBorder().isWithinBounds(
				box.inflate(MegumiProfile.WORLD_BORDER_MARGIN));
		boolean collisionFree = level.noBlockCollision(serpent, box);
		return MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(
				finite, inWorld, loaded, insideBorder, collisionFree));
	}

	private static void recoverAtSafeReturn(ServerLevel level, MegumiSerpentEntity serpent, long gameTime) {
		Vec3 safeReturn = serpent.safeReturnPosition();
		Vec3 placement = safeReturn == null ? null
				: SafeBodyPlacement.find(level, serpent, safeReturn, RETURN_PLACEMENT);
		if (placement != null && safeEmergeFacts(level, serpent, placement)) {
			serpent.teleportTo(level, placement.x, placement.y, placement.z,
					java.util.Set.of(), serpent.getYRot(), serpent.getXRot(), false);
		}
		serpent.beginRecovery(gameTime);
	}

	private static void release(ServerLevel level, ServerPlayer owner, MegumiSerpentEntity serpent,
			MegumiSerpentPolicy.Event event, LivingEntity victim, long gameTime) {
		UUID victimUuid = serpent.bindTargetUuid();
		if (victim != null) {
			releaseVictim(serpent, victim);
		} else {
			releaseByUuid(serpent, victimUuid);
		}
		if (serpent.beginRelease(event, gameTime) == null) {
			return;
		}
		play(level, serpent, SoundEvents.SPIDER_STEP, 0.76f, 0.68f);
		if (owner != null) {
			Vec3 origin = victim == null ? serpent.position() : victim.position();
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.SERPENT_RELEASE,
					origin, serpent.getId(), Vec3.ZERO);
		}
	}

	private static void releaseVictim(MegumiSerpentEntity serpent, LivingEntity victim) {
		UUID registeredHolder = HeldVictimRegistry.holderUuid(victim.getUUID());
		if (registeredHolder == null || serpent.getUUID().equals(registeredHolder)) {
			HoldSupport.release(victim);
		}
	}

	private static void releaseByUuid(MegumiSerpentEntity serpent, UUID victimUuid) {
		if (victimUuid == null) {
			return;
		}
		UUID registeredHolder = HeldVictimRegistry.holderUuid(victimUuid);
		if (registeredHolder != null && !serpent.getUUID().equals(registeredHolder)) {
			return;
		}
		if (serpent.getServer() != null) {
			for (ServerLevel serverLevel : serpent.getServer().getAllLevels()) {
				if (serverLevel.getEntity(victimUuid) instanceof LivingEntity victim) {
					HoldSupport.release(victim);
					return;
				}
			}
			ServerPlayer remote = serpent.getServer().getPlayerList().getPlayer(victimUuid);
			if (remote != null) {
				HoldSupport.release(remote);
				return;
			}
		}
		HeldVictimRegistry.release(victimUuid);
	}

	private static MegumiSerpentPolicy.Event releaseEventForMissing(ServerLevel level, UUID victimUuid) {
		if (victimUuid != null) {
			for (ServerLevel candidate : level.getServer().getAllLevels()) {
				if (candidate.getEntity(victimUuid) != null) {
					return MegumiSerpentPolicy.Event.VICTIM_DIMENSION_CHANGE;
				}
			}
		}
		return MegumiSerpentPolicy.Event.VICTIM_UNLOAD;
	}

	private static void applyHold(MegumiSerpentEntity serpent, LivingEntity victim) {
		Vec3 look = serpent.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
		if (horizontal.lengthSqr() < 1.0E-8) {
			horizontal = victim.position().subtract(serpent.position()).multiply(1.0, 0.0, 1.0);
		}
		if (horizontal.lengthSqr() < 1.0E-8) {
			horizontal = new Vec3(0.0, 0.0, 1.0);
		} else {
			horizontal = horizontal.normalize();
		}
		Vec3 anchor = serpent.position().add(horizontal.scale(MegumiShikigamiProfile.SERPENT_ANCHOR_OFFSET));
		HoldSupport.applyHold(serpent, victim, anchor, HoldSupport.CollisionPolicy.TOAD,
				MegumiShikigamiProfile.SERPENT_MARKER_TICKS);
	}

	private static void keepPlanted(MegumiSerpentEntity serpent) {
		serpent.getNavigation().stop();
		serpent.setDeltaMovement(Vec3.ZERO);
	}

	private static void keepSubmerged(MegumiSerpentEntity serpent) {
		keepPlanted(serpent);
		serpent.setNoAi(true);
		serpent.setNoGravity(true);
		serpent.setInvisible(true);
	}

	private static void play(ServerLevel level, MegumiSerpentEntity serpent,
			net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, serpent.getX(), serpent.getY(), serpent.getZ(), sound,
				SoundSource.NEUTRAL, volume, pitch);
	}

	private static LivingEntity resolve(ServerLevel level, UUID uuid) {
		return uuid != null && level.getEntity(uuid) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}

	private static LivingEntity resolveIncludingDead(ServerLevel level, UUID uuid) {
		return uuid != null && level.getEntity(uuid) instanceof LivingEntity living
				&& !living.isRemoved() && living.level() == level
				? living : null;
	}
}
