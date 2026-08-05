package jujutsu.mod.character.nobara.projectjjk;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
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
 * <p>No tick loop — the {@link ProjectJjkNailEntity} drives its own lifecycle (charge → launch
 * → impact/timeout) and calls back into this runtime for damage, VFX, and sound.
 */
public final class ProjectJjkMegaNailRuntime {
	/** Boxed so the VFX radius contract test can read the delivery radius from bytecode field accesses. */
	private static final Double VFX_DELIVERY_RADIUS = 64.0;

	private ProjectJjkMegaNailRuntime() {}

	public static void register() {
		// No tick loop needed — the mega nail entity is self-sufficient.
	}

	// -- Public API -----------------------------------------------------------------------------

	/**
	 * Attempts to start a Mega Nail on the aimed living target.
	 *
	 * <p>All owned embedded nails on the target are atomically consumed and a mega-nail entity
	 * is spawned at the gather point (in front of the caster). Returns {@code false} when there
	 * is no valid target or no nails, letting the caller produce the fallback toast.
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
		// Snapshot weight and count
		float weight = 0.0f;
		int count = nails.size();
		for (ProjectJjkNailEntity nail : nails) {
			weight += nail.depthDamageMultiplier();
		}
		// Gather point: in front of caster's eyes
		Vec3 look = caster.getLookAngle();
		Vec3 gatherPoint = caster.getEyePosition().add(look.scale(1.6)).subtract(0.0, 0.2, 0.0);
		// Spawn the mega nail entity at the gather point
		ProjectJjkNailEntity megaNail = new ProjectJjkNailEntity(JujutsuEntities.PROJECTJJK_NAIL, level);
		megaNail.initializeAsMegaNail(caster, gatherPoint, weight, count, target.getUUID(), target.getId());
		level.addFreshEntity(megaNail);
		// Directed ENLARGE cue per consumed nail (nail → gather point for particle stream)
		for (ProjectJjkNailEntity nail : nails) {
			int depth = nail.embedDepthLevel();
			Vec3 dir = safeDirection(gatherPoint.subtract(nail.position()));
			broadcastDirectional(level, nail.position(), NobaraVfxIds.ENLARGE, depth, dir, gameTime);
			nail.discard();
		}
		ProjectJjkNailMarks.consume(target.getUUID(), gameTime);
		ProjectJjkRitualRuntime.clearGlowingMark(target);
		// Charge start cue (intensity 1). The entity re-emits this id with intensity 2..6
		// during the charge as escalating camera-shake pulses; 1 marks the start beat.
		broadcast(level, gatherPoint, NobaraVfxIds.MEGA_NAIL_CHARGE, 1, gameTime);
		// The 1.3 s synthesized riser owns the charge audio, timed to end at launch.
		level.playSound(null, gatherPoint.x, gatherPoint.y, gatherPoint.z,
				JujutsuSounds.NOBARA_MEGA_CHARGE_RISER, SoundSource.PLAYERS, 1.9f, 1.0f);
		// Caster presentation cue
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_DELIVERY_RADIUS,
				cue(level, NobaraVfxIds.CASTER_ACTION, NobaraVfxIds.CASTER_MEGA_NAIL,
						caster.position(), gameTime, caster));
		return true;
	}

	/**
	 * Escalating charge-shake pulse, re-emitted by the hovering mega nail every few ticks.
	 * Intensity 1 is reserved for the charge start beat; pulses climb 2..5 over the charge.
	 */
	static void broadcastChargePulse(ServerLevel level, Vec3 at, int intensity) {
		broadcast(level, at, NobaraVfxIds.MEGA_NAIL_CHARGE, Math.max(2, intensity), level.getGameTime());
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

	// -- Callbacks from ProjectJjkNailEntity ------------------------------------------------

	/**
	 * Called when the mega nail entity hits a living entity or block.
	 * Applies damage, knockback, stagger, strike VFX, and sounds on entity hits.
	 * Block hits produce terminal VFX without damage.
	 */
	public static void onMegaNailImpact(ServerLevel level, ProjectJjkNailEntity entity, HitResult hit) {
		long gameTime = level.getGameTime();
		// Caster may be offline mid-flight: both hairpin(level, null) and damageMultiplier(null)
		// tolerate it, so the strike still lands attributed to the world.
		ServerPlayer caster = owner(level, entity.ownerUuid());

		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			// Damage
			DamageSource source = NobaraDamageSources.hairpin(level, caster);
			float damage = megaNailDamage(entity.megaWeight()) * ResonantMomentum.damageMultiplier(caster);
			target.hurtServer(level, source, damage);
			// Stagger before the shove: LivingEntity overload damps velocity, so the push lands after it.
			CombatStagger.GLOBAL.apply(target, gameTime, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
			Vec3 knockbackDir = entity.megaLaunchDirection();
			// Physical shove, not LivingEntity.knockback(): a giant nail moves EVERY body.
			// knockback() multiplies by KNOCKBACK_RESISTANCE, which zeroes it on iron golems.
			float shove = megaNailKnockback(entity.megaCount()) * 0.55f;
			target.setDeltaMovement(target.getDeltaMovement()
					.add(knockbackDir.x * shove, 0.1 + shove * 0.18, knockbackDir.z * shove));
			target.hurtMarked = true;
			// Strike VFX: origin in front of target, displacement along direction
			Vec3 origin = target.position().add(knockbackDir.scale(-0.5));
			Vec3 displacement = knockbackDir.scale(4.0);
			broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
					clampIntensity(entity.megaCount()), gameTime, displacement);
			// Server sounds on the strike tick: deep blast plus a low body boom.
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 1.35f, 0.68f);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					JujutsuSounds.PROJECTJJK_AEC_BOOM, SoundSource.PLAYERS, 1.1f, 0.55f);
		} else if (hit instanceof BlockHitResult) {
			// Block impact: terminal VFX only
			terminalVfx(level, entity);
		} else {
			// Miss / other: terminal VFX
			terminalVfx(level, entity);
		}
	}

	/**
	 * Called when the mega nail exceeds its flight timeout (missed or blocked).
	 * Plays terminal VFX at the entity's current position.
	 */
	public static void onMegaNailTimeout(ServerLevel level, ProjectJjkNailEntity entity) {
		terminalVfx(level, entity);
	}

	// -- VFX helpers ----------------------------------------------------------------------------

	private static void terminalVfx(ServerLevel level, ProjectJjkNailEntity entity) {
		Vec3 dir = entity.megaLaunchDirection();
		if (dir.lengthSqr() < 1.0E-5) dir = entity.forwardDirection();
		Vec3 origin = entity.position().add(dir.scale(-0.5));
		Vec3 displacement = dir.scale(4.0);
		broadcastDisplacement(level, origin, NobaraVfxIds.MEGA_NAIL_STRIKE,
				clampIntensity(entity.megaCount()), level.getGameTime(), displacement);
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

	private static ServerPlayer owner(ServerLevel level, UUID ownerUuid) {
		return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
