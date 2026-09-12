package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Rabbit Escape's pack logic: the anchor body carries the pack-level tick (anchor loss, lifetime,
 * upkeep) exactly once per tick, while every body bumps hostiles on its own window.
 */
final class MegumiRabbitsBrain {
	private static final Map<UUID, Long> LAST_UPKEEP = new ConcurrentHashMap<>();

	private MegumiRabbitsBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiRabbitEntity body, long gameTime) {
		if (body.getUUID().equals(pack.anchorId())) {
			if (anchorLost(level, pack, body.ownerUuid())) {
				MegumiShikigamiRuntime.teardown(level.getServer(), body.ownerUuid(),
						MegumiShikigamiRuntime.TeardownReason.DEATH);
				return;
			}
			if (MegumiRabbitsPolicy.expired(pack.summonedAtGameTime(), gameTime,
					MegumiShikigamiProfile.RABBITS_LIFETIME_TICKS)) {
				expire(level, owner, pack, body);
				return;
			}
			upkeep(level, owner, pack, gameTime);
		}
		bump(level, owner, body, gameTime);
	}

	private static boolean anchorLost(ServerLevel level, MegumiShikigamiPack pack, UUID ownerId) {
		return MegumiShikigamiRuntime.livingBodies(level.getServer(), ownerId, pack).stream()
				.noneMatch(body -> body.getUUID().equals(pack.anchorId()));
	}

	/** Lifetime spent: one pop at the swarm centre, then the recall-family teardown. */
	private static void expire(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiRabbitEntity body) {
		List<MegumiShikigamiEntity> living =
				MegumiShikigamiRuntime.livingBodies(level.getServer(), body.ownerUuid(), pack);
		Vec3 centre = body.position();
		if (!living.isEmpty()) {
			double x = 0.0;
			double y = 0.0;
			double z = 0.0;
			for (MegumiShikigamiEntity other : living) {
				x += other.getX();
				y += other.getY();
				z += other.getZ();
			}
			centre = new Vec3(x / living.size(), y / living.size(), z / living.size());
		}
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.RABBITS_POP,
				centre, body.getId(), Vec3.ZERO);
		MegumiShikigamiRuntime.teardown(level.getServer(), body.ownerUuid(),
				MegumiShikigamiRuntime.TeardownReason.RECALL);
		if (owner != null) {
			MegumiSummonRuntime.startCooldownIfLonger(owner, CharacterAbility.PRIMARY,
					MegumiShikigamiProfile.RABBITS_EXPIRY_COOLDOWN_TICKS);
		}
	}

	/** Replaces fallen bodies at the owner's ring, capped by the batch row; pops a cue per body. */
	private static void upkeep(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			long gameTime) {
		if (owner == null) {
			return;
		}
		UUID ownerId = owner.getUUID();
		List<MegumiShikigamiEntity> living =
				MegumiShikigamiRuntime.livingBodies(level.getServer(), ownerId, pack);
		long last = LAST_UPKEEP.getOrDefault(ownerId, pack.summonedAtGameTime());
		if (!MegumiRabbitsPolicy.shouldRespawn(living.size(), MegumiShikigamiProfile.RABBITS_SWARM_SIZE,
				gameTime, last, MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS)) {
			return;
		}
		LAST_UPKEEP.put(ownerId, gameTime);
		int batch = MegumiRabbitsPolicy.respawnBatch(living.size(),
				MegumiShikigamiProfile.RABBITS_SWARM_SIZE, MegumiShikigamiProfile.RABBITS_RESPAWN_BATCH);
		if (batch <= 0) {
			return;
		}
		List<Vec3> spots = MegumiShikigamiSpawnPlacement.ring(level, owner.position(), batch,
				MegumiShikigamiProfile.RABBITS_SPAWN_RADIUS,
				JujutsuEntities.MEGUMI_RABBIT.getDimensions());
		for (Vec3 spot : spots) {
			MegumiRabbitEntity rabbit = new MegumiRabbitEntity(JujutsuEntities.MEGUMI_RABBIT, level);
			rabbit.setPos(spot);
			rabbit.setYRot(owner.getYRot());
			rabbit.setTame(true, false);
			rabbit.setOwner(owner);
			rabbit.configureSummon(ownerId, pack.summonToken());
			if (!level.addFreshEntity(rabbit)) {
				rabbit.discard();
				continue;
			}
			MegumiShikigamiRuntime.registerExtraBody(ownerId, rabbit);
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.RABBITS_POP,
					spot, rabbit.getId(), Vec3.ZERO);
		}
	}

	/**
	 * The swarm's only hostile interaction: eligible hostiles inside the bump radius take a shove
	 * away from the body plus slowness. The window always advances, victims or not, so an idle
	 * body scans once per period instead of every tick.
	 */
	private static void bump(ServerLevel level, ServerPlayer owner, MegumiRabbitEntity body,
			long gameTime) {
		if (owner == null
				|| !MegumiRabbitsPolicy.bumpReady(body.nextBumpGameTime(), gameTime)) {
			return;
		}
		body.postponeBump(gameTime + MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				body.getBoundingBox().inflate(MegumiShikigamiProfile.RABBITS_BUMP_RADIUS),
				candidate -> candidate.isAlive() && !candidate.isRemoved())) {
			if (!MegumiSummonRuntime.isEligibleTarget(owner, target)
					|| MegumiShikigamiFriendlyFire.isProtected(owner, target)) {
				continue;
			}
			target.setDeltaMovement(target.getDeltaMovement()
					.add(MegumiRabbitsPolicy.bumpImpulse(body.position(), target.position())));
			target.hurtMarked = true;
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
					MegumiShikigamiProfile.RABBITS_BUMP_SLOWNESS_TICKS, 0, true, false, true), owner);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.RABBIT_HURT,
					SoundSource.NEUTRAL, 0.7f, 1.15f);
			MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.RABBITS_POP,
					body.position(), body.getId(), Vec3.ZERO);
		}
	}
}
