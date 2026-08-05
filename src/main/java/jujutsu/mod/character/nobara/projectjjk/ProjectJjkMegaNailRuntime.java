package jujutsu.mod.character.nobara.projectjjk;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative runtime for Nobara's Mega Nail (B): converges every embedded nail on the
 * aimed target into one delayed piercing strike that passes through the target.
 *
 * <p>Owns its own tick cycle via {@link ServerTickEvents#END_SERVER_TICK}, independent of the
 * Hairpin chain scheduler in {@link ProjectJjkRitualRuntime}.
 */
public final class ProjectJjkMegaNailRuntime {
	/** Boxed so the VFX radius contract test can read the delivery radius from bytecode field accesses. */
	private static final Double VFX_DELIVERY_RADIUS = 64.0;
	private static final Map<UUID, PendingStrike> PENDING = new ConcurrentHashMap<>();

	private ProjectJjkMegaNailRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkMegaNailRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	// -- Public API -----------------------------------------------------------------------------

	/**
	 * Attempts to start a Mega Nail strike on the aimed living target.
	 *
	 * <p>All owned embedded nails on the target are atomically consumed and a delayed strike is
	 * scheduled. Returns {@code false} when there is no valid target or no nails, letting the
	 * caller produce the fallback toast.
	 */
	public static boolean start(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		TargetResolver.Result result = TargetResolver.resolve(level, caster, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE);
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return false;
		}
		Entity entity = level.getEntity(result.entityId().get());
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return false;
		}
		// Collect embedded nails on this target
		List<ProjectJjkNailEntity> nails = level.getEntitiesOfClass(ProjectJjkNailEntity.class,
				target.getBoundingBox().inflate(2.0), nail ->
					nail.isEmbedded() && nail.isOwnedBy(caster.getUUID())
							&& target.getUUID().equals(nail.anchor().stableId()));
		if (nails.isEmpty()) {
			return false;
		}
		// Snapshot weight, count, and converge point
		float weight = 0.0f;
		int count = nails.size();
		Vec3 converge = Vec3.ZERO;
		for (ProjectJjkNailEntity nail : nails) {
			weight += nail.depthDamageMultiplier();
			converge = converge.add(nail.position());
		}
		converge = converge.scale(1.0 / count);
		// Direction: caster → target centre
		Vec3 direction = safeDirection(target.position().subtract(caster.position()));
		// Atomically discard nails and consume marks
		for (ProjectJjkNailEntity nail : nails) {
			Vec3 at = nail.position();
			broadcast(level, at, NobaraVfxIds.ENLARGE, 1, at, gameTime);
			nail.discard();
		}
		ProjectJjkNailMarks.consume(target.getUUID(), gameTime);
		// Caster presentation cue
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_DELIVERY_RADIUS,
				cue(level, NobaraVfxIds.CASTER_ACTION, NobaraVfxIds.CASTER_MEGA_NAIL,
						caster.position(), gameTime, caster));
		// Schedule the delayed strike
		PendingStrike pending = new PendingStrike(
				level.dimension(),
				caster.getUUID(),
				target.getUUID(),
				target.getId(),
				gameTime + ProjectJjkNobaraProfile.MEGA_NAIL_STRIKE_DELAY_TICKS,
				weight,
				count,
				direction,
				converge);
		PENDING.put(target.getUUID(), pending);
		return true;
	}

	/**
	 * Pure damage formula: caps per-nail base at {@link ProjectJjkNobaraProfile#MEGA_NAIL_DAMAGE_CAP}.
	 * Does not apply ResonantMomentum — the caller multiplies it separately.
	 */
	public static float megaNailDamage(float depthWeight) {
		return Math.min(ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_PER_NAIL * depthWeight,
				ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_CAP);
	}

	/**
	 * Pure knockback formula: base + per-nail bonus, capped.
	 */
	public static float megaNailKnockback(int nailCount) {
		return Math.min(ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_BASE
						+ ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_PER_NAIL * nailCount,
				ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP);
	}

	// -- Tick loop ------------------------------------------------------------------------------

	private static void onServerTick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		long gameTime = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, PendingStrike>> iterator = PENDING.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<UUID, PendingStrike> entry = iterator.next();
			PendingStrike pending = entry.getValue();
			if (pending.dueGameTime() > gameTime) {
				continue;
			}
			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null) {
				iterator.remove();
				continue;
			}
			ServerPlayer caster = owner(server, pending.casterId());
			if (resolveStrike(level, pending, caster, gameTime)) {
				iterator.remove();
			} else if (gameTime > pending.dueGameTime() + ProjectJjkNobaraProfile.MEGA_NAIL_RETRY_TIMEOUT_TICKS) {
				// Timeout: give up and play terminal VFX along the recorded trajectory
				terminalVfx(level, pending);
				iterator.remove();
			}
		}
	}

	/**
	 * Resolves a due strike. Returns {@code true} when the strike is final (hit, terminal, or
	 * confirmed-removed with no more retries possible). Returns {@code false} for RETRY.
	 */
	private static boolean resolveStrike(ServerLevel level, PendingStrike pending, ServerPlayer caster, long gameTime) {
		// Resolve target by entity ID first, then by UUID
		Entity entity = level.getEntity(pending.targetEntityId());
		LivingEntity target = entity instanceof LivingEntity candidate && candidate.isAlive()
				&& candidate.getUUID().equals(pending.targetId()) ? candidate : null;
		if (target == null) {
			Entity byUuid = level.getEntity(pending.targetId());
			if (byUuid instanceof LivingEntity living && living.isAlive()) {
				target = living;
			}
		}

		if (target != null) {
			// Strike: apply damage, knockback, stagger, VFX, sounds
			DamageSource source = NobaraDamageSources.hairpin(level, caster);
			float damage = megaNailDamage(pending.weight()) * ResonantMomentum.damageMultiplier(caster);
			target.hurtServer(level, source, damage);
			Vec3 knockbackDir = pending.direction(); // always caster→target
			target.knockback(megaNailKnockback(pending.count()), -knockbackDir.x, -knockbackDir.z);
			CombatStagger.GLOBAL.apply(target.getUUID(), gameTime, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
			// Strike VFX: origin in front of target, displacement along direction
			Vec3 origin = target.position().add(pending.direction().scale(-0.5));
			Vec3 displacement = pending.direction().scale(4.0);
			broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
					clampIntensity(pending.count()), gameTime, displacement);
			// Server sounds on the strike tick
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 0.9f, 0.7f);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_LONG_WHOOSH, SoundSource.PLAYERS, 1.0f, 0.55f);
			return true;
		}

		// Target is gone (dead or unloaded)
		if (NailAnchorLifecycle.isConfirmedRemoved(pending.targetId())) {
			terminalVfx(level, pending);
			return true;
		}
		// Temporarily unavailable (e.g. entity in unloaded chunk)
		return false;
	}

	// -- VFX helpers ----------------------------------------------------------------------------

	private static void terminalVfx(ServerLevel level, PendingStrike pending) {
		// Play the strike VFX along the fixed trajectory even though the target is gone
		Vec3 origin = pending.convergePoint().add(pending.direction().scale(-0.5));
		Vec3 displacement = pending.direction().scale(4.0);
		broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
				clampIntensity(pending.count()), level.getGameTime(), displacement);
	}

	private static void broadcastDisplacement(ServerLevel level, Vec3 origin, ResourceLocation effectId,
			int intensity, long gameTime, Vec3 displacement) {
		JujutsuNetworking.broadcastVfxCue(level, origin, VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(effectId, origin, intensity, gameTime,
						level.random.nextLong(), displacement));
	}

	private static void broadcast(ServerLevel level, Vec3 center, ResourceLocation effectId,
			int intensity, Vec3 at, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, center, VFX_DELIVERY_RADIUS,
				cue(level, effectId, intensity, at, gameTime));
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId,
			int intensity, Vec3 at, long gameTime) {
		return VfxCues.worldFixed(effectId, at, intensity, gameTime, level.random.nextLong());
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId,
			int intensity, Vec3 at, long gameTime, Entity anchor) {
		return VfxCues.anchored(effectId, at, anchor.getId(), anchor.position(),
				intensity, gameTime, level.random.nextLong());
	}

	private static int clampIntensity(int count) {
		return Math.max(1, Math.min(7, count));
	}

	// -- Misc helpers ---------------------------------------------------------------------------

	private static ServerPlayer owner(MinecraftServer server, UUID ownerUuid) {
		return ownerUuid == null ? null : server.getPlayerList().getPlayer(ownerUuid);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	// -- State records --------------------------------------------------------------------------

	/**
	 * A scheduled Mega Nail strike awaiting delivery.
	 *
	 * @param dimension      the level's dimension key for cross-dim lookup
	 * @param casterId       UUID of the caster
	 * @param targetId       stable UUID of the target
	 * @param targetEntityId entity ID of the target (for fast lookup)
	 * @param dueGameTime    tick when the strike should fire
	 * @param weight         accumulated depth-damage weight of all consumed nails
	 * @param count          number of nails consumed
	 * @param direction      unit vector from caster to target (frozen at cast time)
	 * @param convergePoint  average position of the consumed nails (for terminal VFX fallback)
	 */
	private record PendingStrike(
			ResourceKey<Level> dimension,
			UUID casterId,
			UUID targetId,
			int targetEntityId,
			long dueGameTime,
			float weight,
			int count,
			Vec3 direction,
			Vec3 convergePoint
	) {}
}
