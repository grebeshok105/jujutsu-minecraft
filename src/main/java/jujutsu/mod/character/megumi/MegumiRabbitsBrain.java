package jujutsu.mod.character.megumi;

import java.util.ArrayList;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.MegumiVfxIds;

/**
 * Rabbit Escape's pack logic: the anchor body carries the pack-level tick (anchor loss, lifetime,
 * upkeep) exactly once per tick, while every body bumps hostiles on its own window.
 */
final class MegumiRabbitsBrain {
	/**
	 * The last upkeep window per owner, stamped with the pack it belonged to. The teardown stays
	 * type-agnostic and never touches this map, so a stale entry from a previous pack must be
	 * recognizable as stale: any mark whose token differs from the live pack reads as a fresh
	 * pack starting from its own summon tick.
	 */
	private record UpkeepMark(long summonToken, long gameTime) {}

	private static final Map<UUID, UpkeepMark> LAST_UPKEEP = new ConcurrentHashMap<>();

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiRabbitEntity body, long gameTime) {
		if (body.getUUID().equals(pack.anchorId())) {
			// The sweep is the leak fix the type-agnostic teardown cannot do: any anchor tick
			// drops upkeep marks whose pack is already gone (recall, death, disconnect, ...), so
			// one inert entry per past summoner cannot accumulate for a server lifetime.
			dropUpkeepWithoutPack();
			if (anchorLost(level, pack, body.ownerUuid())) {
				// Type-scoped teardown (issue #107 D1): losing the swarm's anchor must not sweep
				// the owner's other packs — Nue/Toad/Elephant stand beside the rabbits.
				MegumiShikigamiRuntime.teardownType(level.getServer(), body.ownerUuid(),
						MegumiShikigami.RABBITS, MegumiShikigamiRuntime.TeardownReason.DEATH);
				LAST_UPKEEP.remove(body.ownerUuid());
				return;
			}
			if (MegumiRabbitsPolicy.expired(pack.summonedAtGameTime(), gameTime,
					MegumiShikigamiProfile.RABBITS_LIFETIME_TICKS)) {
				expire(level, owner, pack, body);
				return;
			}
			upkeep(level, owner, pack, gameTime);
		}
		bump(level, owner, pack, body, gameTime);
	}

	/**
	 * The upkeep window for this pack: the stored mark, unless it belongs to a previous pack
	 * (different token), in which case the fresh pack starts from its own summon tick. A stale
	 * mark must never top a new swarm up early — or hold it back when the stamp is newer than
	 * the summon. Package-visible for the clock pin test.
	 */
	static long lastUpkeepOrDefault(UUID ownerId, long summonToken, long summonedAtGameTime) {
		UpkeepMark mark = LAST_UPKEEP.get(ownerId);
		return mark != null && mark.summonToken() == summonToken ? mark.gameTime() : summonedAtGameTime;
	}

	static void noteUpkeep(UUID ownerId, long summonToken, long gameTime) {
		LAST_UPKEEP.put(ownerId, new UpkeepMark(summonToken, gameTime));
	}

	/**
	 * Drops every upkeep mark whose owner currently holds no pack. Returns the dropped count, so
	 * the leak fix stays red-proofable without exposing the map. Package-visible for the test.
	 */
	static int dropUpkeepWithoutPack() {
		List<UUID> stale = new ArrayList<>();
		for (UUID ownerId : LAST_UPKEEP.keySet()) {
			if (ownerId == null || MegumiShikigamiRuntime.packs(ownerId).isEmpty()) {
				stale.add(ownerId);
			}
		}
		for (UUID ownerId : stale) {
			LAST_UPKEEP.remove(ownerId);
		}
		return stale.size();
	}

	/**
	 * First-window stagger by spawn order: body {@code 0} may bump at once, body {@code 9} waits
	 * nine ticks, so the ten-strong swarm shoves in a ripple instead of one stacked volley. The
	 * period itself is untouched — every later window still advances by the profile row.
	 * Package-visible for the stagger pin test.
	 */
	static int staggerOffset(int bodyIndex) {
		return Math.floorMod(Math.max(0, bodyIndex), MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS);
	}

	private static boolean anchorLost(ServerLevel level, MegumiShikigamiPack pack, UUID ownerId) {
		return MegumiShikigamiRuntime.livingBodies(level.getServer(), ownerId, pack).stream()
				.noneMatch(body -> body.getUUID().equals(pack.anchorId()));
	}

	/**
	 * Lifetime spent: one pop at the swarm centre, then the expiry teardown — priced by
	 * {@code RABBITS_EXPIRY_COOLDOWN_TICKS}, its own row, so ageing out can be tuned apart from a
	 * manual recall. The sink-out and the cue match the recall-family look.
	 */
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
		MegumiShikigamiRuntime.teardownType(level.getServer(), body.ownerUuid(),
				MegumiShikigami.RABBITS, MegumiShikigamiRuntime.TeardownReason.EXPIRED);
		LAST_UPKEEP.remove(body.ownerUuid());
	}

	/** Replaces fallen bodies at the owner's ring, capped by the batch row; pops a cue per body. */
	private static void upkeep(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			long gameTime) {
		if (owner == null) {
			// The anchor can out-tick a logout: with no owner there is no ring to spawn on and no
			// cue receiver, so the window simply does not advance (the anchor-loss/reconcile pass
			// owns the pack teardown instead).
			return;
		}
		UUID ownerId = owner.getUUID();
		List<MegumiShikigamiEntity> living =
				MegumiShikigamiRuntime.livingBodies(level.getServer(), ownerId, pack);
		long last = lastUpkeepOrDefault(ownerId, pack.summonToken(), pack.summonedAtGameTime());
		if (!MegumiRabbitsPolicy.shouldRespawn(living.size(), MegumiShikigamiProfile.RABBITS_SWARM_SIZE,
				gameTime, last, MegumiShikigamiProfile.RABBITS_RESPAWN_INTERVAL_TICKS)) {
			return;
		}
		noteUpkeep(ownerId, pack.summonToken(), gameTime);
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
	private static void bump(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiRabbitEntity body, long gameTime) {
		if (owner == null) {
			return;
		}
		if (body.nextBumpGameTime() == 0L) {
			// Virgin body: every spawn leaves the window at zero, so without this all ten bodies
			// fire on the same tick and stay aligned, stacking N x 0.35 impulse plus N duplicate
			// sounds and cues onto one victim. Seed the first window by spawn order instead and
			// sit this tick out — the window advances from the seeded value like any other.
			body.postponeBump(gameTime + staggerOffset(pack.bodyIds().indexOf(body.getUUID())));
			return;
		}
		if (!MegumiRabbitsPolicy.bumpReady(body.nextBumpGameTime(), gameTime)) {
			return;
		}
		body.postponeBump(gameTime + MegumiShikigamiProfile.RABBITS_BUMP_PERIOD_TICKS);
		boolean hit = false;
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
			hit = true;
		}
		if (hit) {
			// The existing server-side swing state is synchronized to clients; the imported one-shot
			// attack clip now rides the same pounce/bite-style trigger as the other bodies.
			body.swing(InteractionHand.MAIN_HAND);
		}
	}
}
