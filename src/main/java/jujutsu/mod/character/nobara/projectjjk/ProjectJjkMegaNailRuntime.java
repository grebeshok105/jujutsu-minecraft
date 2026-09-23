package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative runtime for Nobara's Mega Nail (B): gathers every embedded nail on the
 * aimed target into a material mega-nail entity that charges in front of Nobara, then
 * launches through the target at piercing speed.
 *
 * <p>The gather phase is a server-end-tick world event. Once setup is consumed, it is not cancelled
 * by a caster disconnect or vessel switch; the material mega nail completes its own charge, flight,
 * impact and timeout lifecycle through {@link ProjectJjkNailEntity}.
 */
public final class ProjectJjkMegaNailRuntime {
	/** Boxed so the VFX radius contract test can read the delivery radius from bytecode field accesses. */
	private static final Double VFX_DELIVERY_RADIUS = 64.0;
	private static final Map<UUID, PendingGather> PENDING_GATHERS = new ConcurrentHashMap<>();
	private static boolean registered;

	private ProjectJjkMegaNailRuntime() {}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkMegaNailRuntime::tickPendingGathers);
		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
				PENDING_GATHERS.entrySet().removeIf(entry -> entry.getValue().level.getServer() == server));
	}

	// -- Public API -----------------------------------------------------------------------------

	/**
	 * Starts the B branch for the aimed target.
	 *
	 * <p>Target setup is read from the anchor registry rather than from a radius scan. That keeps
	 * owner isolation and the physical entity/registry consistency check in one place. The setup is
	 * consumed before this method returns; only the gather presentation is deferred.
	 */
	public static AbilityResult start(ServerPlayer caster) {
		ServerLevel level = caster.level();
		TargetResolver.Result result = TargetResolver.resolve(level, caster,
				ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE);
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return failNoSetup(caster);
		}
		Entity entity = level.getEntity(result.entityId().get());
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return failNoSetup(caster);
		}

		UUID ownerId = caster.getUUID();
		UUID targetId = target.getUUID();
		List<NailAnchorRegistry.Entry> entries =
				NailAnchorRegistry.anchorsOnTarget(level, ownerId, targetId);
		if (entries.isEmpty()) {
			return failNoSetup(caster);
		}

		Vec3 look = safeDirection(caster.getLookAngle());
		Vec3 gatherPoint = caster.getEyePosition().add(look.scale(1.6)).subtract(0.0, 0.2, 0.0);
		List<GatherNail> gathered = new ArrayList<>(entries.size());
		float depthWeight = 0.0f;
		boolean critical = false;
		for (NailAnchorRegistry.Entry entry : entries) {
			Entity anchored = level.getEntity(entry.nailId());
			if (!(anchored instanceof ProjectJjkNailEntity nail) || nail.isRemoved()) {
				continue;
			}
			depthWeight += ProjectJjkNobaraProfile.nailDepthMultiplier(entry.depth());
			critical |= entry.depth() == 3;
			gathered.add(new GatherNail(nail.position(),
					safeDirection(gatherPoint.subtract(nail.position())), entry.depth()));
		}
		if (gathered.isEmpty()) {
			// The registry normally filters this race. Keep the cast fail-closed if an entity is removed
			// between the query and this single-threaded snapshot.
			return failNoSetup(caster);
		}

		// D12: consumption is atomic at t0. The delayed cues below are presentation only.
		for (NailAnchorRegistry.Entry entry : entries) {
			Entity anchored = level.getEntity(entry.nailId());
			if (anchored instanceof ProjectJjkNailEntity nail && !nail.isRemoved()) {
				nail.discard();
			}
		}
		ProjectJjkNailMarks.consume(ownerId, targetId);
		HairpinRuntime.clearGlowingMark(level, targetId);

		PendingGather pending = new PendingGather(level, caster, targetId, target.getId(),
				gatherPoint, depthWeight, gathered.size(), critical, level.getGameTime(), gathered);
		PENDING_GATHERS.put(UUID.randomUUID(), pending);

		// The caster action is immediate; MEGA_NAIL_CHARGE starts when the material mega nail exists.
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_DELIVERY_RADIUS,
				cue(level, NobaraVfxIds.CASTER_ACTION, NobaraVfxIds.CASTER_MEGA_NAIL,
						caster.position(), level.getGameTime(), caster));
		return AbilityResult.SUCCESS;
	}

	/** Emitted by the mega entity during its charge; critical entities use intensified values. */
	static void broadcastChargePulse(ServerLevel level, Vec3 at, int intensity) {
		broadcast(level, at, NobaraVfxIds.MEGA_NAIL_CHARGE, Math.max(2, intensity), level.getGameTime());
	}

	/**
	 * Pure damage formula: {@code min(4 * depthWeight, 42)}. Momentum is applied by the impact
	 * callback so this helper remains useful for deterministic math tests.
	 */
	public static float megaNailDamage(float depthWeight) {
		return Math.min(ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_PER_NAIL * depthWeight,
				ProjectJjkNobaraProfile.MEGA_NAIL_DAMAGE_CAP);
	}

	/** Pure knockback formula with the profile's hard cap. */
	public static float megaNailKnockback(int nailCount) {
		return Math.min(ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_BASE
						+ ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_PER_NAIL * nailCount,
				ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP);
	}

	// -- Gather world event ----------------------------------------------------------------------

	private static void tickPendingGathers(MinecraftServer server) {
		List<UUID> completed = new ArrayList<>();
		for (Map.Entry<UUID, PendingGather> entry : PENDING_GATHERS.entrySet()) {
			PendingGather pending = entry.getValue();
			if (pending.level.getServer() != server) {
				continue;
			}
			long elapsed = Math.max(0L, pending.level.getGameTime() - pending.startedGameTime);
			emitGatherCues(pending, elapsed);
			if (elapsed < ProjectJjkNobaraProfile.MEGA_GATHER_TICKS) {
				continue;
			}
			spawnMegaNail(pending);
			completed.add(entry.getKey());
		}
		for (UUID key : completed) {
			PENDING_GATHERS.remove(key);
		}
	}

	private static void emitGatherCues(PendingGather pending, long elapsed) {
		for (int index = 0; index < pending.nails.size(); index++) {
			if (pending.emitted[index]) {
				continue;
			}
			int dueTick = cueTick(index, pending.nails.size());
			if (elapsed < dueTick) {
				continue;
			}
			GatherNail nail = pending.nails.get(index);
			broadcastDirectional(pending.level, nail.origin, NobaraVfxIds.MEGA_GATHER, nail.depth,
					nail.direction, pending.level.getGameTime());
			pending.emitted[index] = true;
		}
	}

	private static int cueTick(int index, int size) {
		if (size <= 1) {
			return 0;
		}
		return (index * ProjectJjkNobaraProfile.MEGA_GATHER_TICKS) / (size - 1);
	}

	private static void spawnMegaNail(PendingGather pending) {
		ProjectJjkNailEntity megaNail =
				new ProjectJjkNailEntity(JujutsuEntities.PROJECTJJK_NAIL, pending.level);
		megaNail.initializeAsMegaNail(pending.caster, pending.gatherPoint, pending.depthWeight,
				pending.count, pending.targetUuid, pending.targetEntityId);
		megaNail.setMegaCritical(pending.critical);
		pending.level.addFreshEntity(megaNail);

		long gameTime = pending.level.getGameTime();
		broadcast(pending.level, pending.gatherPoint, NobaraVfxIds.MEGA_NAIL_CHARGE,
				pending.critical ? 2 : 1, gameTime);
		pending.level.playSound(null, pending.gatherPoint.x, pending.gatherPoint.y, pending.gatherPoint.z,
				JujutsuSounds.NOBARA_MEGA_CHARGE_RISER, SoundSource.PLAYERS, 1.9f, 1.0f);
	}

	// -- Callbacks from ProjectJjkNailEntity ----------------------------------------------------

	/**
	 * Applies the single-target strike. The entity owns charge/retarget/flight timing; this callback
	 * owns the server-side impact consequences.
	 */
	public static void onMegaNailImpact(ServerLevel level, ProjectJjkNailEntity entity, HitResult hit) {
		long gameTime = level.getGameTime();
		ServerPlayer caster = owner(level, entity.ownerUuid());

		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			DamageSource source = NobaraDamageSources.hairpin(level, caster);
			float damage = megaNailDamage(entity.megaWeight())
					* ResonantMomentum.damageMultiplier(caster);
			target.hurtServer(level, source, damage);
			CombatStagger.GLOBAL.apply(target, gameTime, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);

			Vec3 knockbackDir = entity.megaLaunchDirection();
			float shove = Math.min(megaNailKnockback(entity.megaCount()),
					ProjectJjkNobaraProfile.MEGA_NAIL_KNOCKBACK_CAP);
			target.setDeltaMovement(target.getDeltaMovement()
					.add(knockbackDir.x * shove, 0.1 + shove * 0.18, knockbackDir.z * shove));
			target.hurtMarked = true;

			Vec3 origin = target.position().add(knockbackDir.scale(-0.5));
			Vec3 displacement = knockbackDir.scale(4.0);
			int intensity = clampIntensity(entity.megaCount() + (entity.megaCritical() ? 2 : 0));
			broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
					intensity, gameTime, displacement);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 1.35f, 0.68f);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_AEC_BOOM, SoundSource.PLAYERS, 1.1f, 0.55f);
			if (caster != null) {
				ResonantMomentum.grantExecutionMoment(caster,
						ProjectJjkNobaraProfile.MOMENTUM_WINDOW_TICKS);
			}
		} else {
			terminalVfx(level, entity);
		}
	}

	public static void onMegaNailTimeout(ServerLevel level, ProjectJjkNailEntity entity) {
		terminalVfx(level, entity);
	}

	// -- VFX helpers ----------------------------------------------------------------------------

	private static void terminalVfx(ServerLevel level, ProjectJjkNailEntity entity) {
		Vec3 dir = entity.megaLaunchDirection();
		if (dir.lengthSqr() < 1.0E-5) {
			dir = entity.forwardDirection();
		}
		Vec3 origin = entity.position().add(dir.scale(-0.5));
		broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
				clampIntensity(entity.megaCount() + (entity.megaCritical() ? 2 : 0)),
				level.getGameTime(), dir.scale(4.0));
	}

	private static void broadcastDirectional(ServerLevel level, Vec3 origin, ResourceLocation effectId,
			int intensity, Vec3 direction, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, origin, VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(effectId, origin, intensity, gameTime,
						level.random.nextLong(), direction));
	}

	private static void broadcastDisplacement(ServerLevel level, Vec3 origin, ResourceLocation effectId,
			int intensity, long gameTime, Vec3 displacement) {
		JujutsuNetworking.broadcastVfxCue(level, origin, VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDisplacement(effectId, origin, intensity, gameTime,
						level.random.nextLong(), displacement));
	}

	private static void broadcast(ServerLevel level, Vec3 center, ResourceLocation effectId,
			int intensity, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, center, VFX_DELIVERY_RADIUS,
				cue(level, effectId, intensity, center, gameTime));
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

	private static AbilityResult failNoSetup(ServerPlayer caster) {
		caster.displayClientMessage(Component.translatable("message.jujutsumod.nobara.mega.no_setup"), true);
		return AbilityResult.HANDLED_FAILURE;
	}

	private static ServerPlayer owner(ServerLevel level, UUID ownerUuid) {
		return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private record GatherNail(Vec3 origin, Vec3 direction, int depth) {}

	private static final class PendingGather {
		private final ServerLevel level;
		private final ServerPlayer caster;
		private final UUID targetUuid;
		private final int targetEntityId;
		private final Vec3 gatherPoint;
		private final float depthWeight;
		private final int count;
		private final boolean critical;
		private final long startedGameTime;
		private final List<GatherNail> nails;
		private final boolean[] emitted;

		private PendingGather(ServerLevel level, ServerPlayer caster, UUID targetUuid, int targetEntityId,
				Vec3 gatherPoint, float depthWeight, int count, boolean critical, long startedGameTime,
				List<GatherNail> nails) {
			this.level = level;
			this.caster = caster;
			this.targetUuid = targetUuid;
			this.targetEntityId = targetEntityId;
			this.gatherPoint = gatherPoint;
			this.depthWeight = depthWeight;
			this.count = count;
			this.critical = critical;
			this.startedGameTime = startedGameTime;
			this.nails = List.copyOf(nails);
			this.emitted = new boolean[nails.size()];
		}
	}
}
