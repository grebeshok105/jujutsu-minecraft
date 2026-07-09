package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.network.ProjectJjkNobaraImpulsePayload;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;

/**
 * The beating heart of Nobara's Straw Doll RPG loop. Owns the resonance ritual (remote strike
 * through walls), the doll->target bind, and Hairpin mark detonation. Server-authoritative; all
 * visuals are broadcast as impulse payloads.
 */
public final class ProjectJjkRitualRuntime {
	private static final double IMPULSE_RADIUS = 64.0;
	private static final List<PendingExplosion> PENDING_EXPLOSIONS = new ArrayList<>();
	private static final List<PendingEnlarge> PENDING_ENLARGES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final DustParticleOptions PROJECTJJK_CYAN = new DustParticleOptions(0x2CE8F5, 1.15f);

	private ProjectJjkRitualRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkRitualRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING_EXPLOSIONS.clear();
			PENDING_ENLARGES.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ProjectJjkResonanceLink.clear(id);
		});
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		tickHairpinTasks(gameTime);
		if ((gameTime & 63L) == 0L) {
			ProjectJjkNailMarks.pruneExpired(gameTime);
		}
	}

	// -- Nail marks -----------------------------------------------------------------------------

	/** Called on a direct nail hit: embeds a cursed mark and paints the wound. */
	public static void markTarget(ServerLevel level, LivingEntity target, ServerPlayer owner) {
		markTarget(level, target, owner, target.position().add(0.0, target.getBbHeight() * 0.55, 0.0));
	}

	/** Called on a direct nail hit: embeds a cursed mark and paints the wound. */
	public static void markTarget(ServerLevel level, LivingEntity target, ServerPlayer owner, Vec3 wound) {
		int marks = ProjectJjkNailMarks.apply(target.getUUID(), level.getGameTime());
		Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
		Vec3 at = wound == null ? center : wound.lerp(center, 0.18);
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ProjectJjkNobaraProfile.MARK_DURATION_TICKS, 0, false, false, true));
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 2 + marks, 0.08, 0.10, 0.08, 0.022);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y, at.z, 2, 0.12, 0.14, 0.12, 0.018);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_SIZZLE, SoundSource.PLAYERS, 0.5f, 0.8f + marks * 0.06f);
	}

	// -- Resonance ritual (Shift + doll-hammer) -------------------------------------------------

	/**
	 * The signature move. If not yet bound, binds the straw doll to the marked target you are looking
	 * at (or any nearby marked target). If already bound, drives the ritual nail home: a remote strike
	 * transmitted straight to the linked target regardless of walls or distance.
	 */
	public static void performResonance(ServerPlayer caster, net.minecraft.world.item.ItemStack doll, net.minecraft.world.InteractionHand hand) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		UUID casterId = caster.getUUID();

		if (!ProjectJjkResonanceLink.isValid(casterId, gameTime)) {
			LivingEntity target = findMarkedTarget(level, caster);
			if (target == null) {
				caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.resonance.no_target"), true);
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 0.4f, 0.6f);
				return;
			}
			ProjectJjkResonanceLink.bind(casterId, target.getUUID(), target.getId(), gameTime);
			Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_CHIME, SoundSource.PLAYERS, 0.9f, 1.1f);
			level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_MAGIC, SoundSource.PLAYERS, 0.7f, 1.2f);
			level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y, at.z, 24, 0.5, 0.7, 0.5, 0.05);
			broadcast(level, caster.position(), ProjectJjkNobaraImpulsePayload.LINK_BIND, 1, at, gameTime);
			caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.resonance.bound"), true);
			return;
		}

		ProjectJjkResonanceLink.Link link = ProjectJjkResonanceLink.get(casterId);
		LivingEntity target = resolveLinked(level, link);
		if (target == null || !target.isAlive()) {
			ProjectJjkResonanceLink.clear(casterId);
			caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.resonance.lost"), true);
			return;
		}

		int marks = ProjectJjkNailMarks.marks(target.getUUID(), gameTime);
		float damage = ProjectJjkNobaraProfile.resonanceDamage(marks);
		DamageSource source = level.damageSources().indirectMagic(caster, caster);
		target.hurtServer(level, source, damage);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ProjectJjkNobaraProfile.RESONANCE_WEAKNESS_TICKS, Math.min(2, marks)));
		if (marks >= 2) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ProjectJjkNobaraProfile.RESONANCE_WEAKNESS_TICKS, 1));
		}

		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		spawnResonanceStrike(level, at, marks);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 1.0f, 0.78f);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, SoundSource.PLAYERS, 0.7f, 1.0f);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_CLAP, SoundSource.PLAYERS, 0.9f, 0.9f);
		ProjectJjkNailMarks.consume(target.getUUID(), gameTime);
		discardOwnedEmbeddedNails(level, casterId, target);
		clearGlowingMark(target);
		broadcast(level, at, ProjectJjkNobaraImpulsePayload.RESONANCE_STRIKE, marks, at, gameTime);
		JujutsuNetworking.sendProjectJjkImpulse(caster, impulse(ProjectJjkNobaraImpulsePayload.RESONANCE_CHANNEL, marks, caster.getEyePosition(), gameTime));
		ProjectJjkResonanceLink.clear(casterId);
	}

	// -- Hairpin mark detonation ----------------------------------------------------------------

	/** ProjectJJK Hairpin Enlargement: snap, wait one second, then crush the looked-at marked target. */
	public static boolean tryEnlargeMarkedTarget(ServerPlayer caster) {
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
		int marks = ProjectJjkNailMarks.marks(target.getUUID(), gameTime);
		if (marks <= 0) {
			return false;
		}

		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 2.0f, 1.0f);
		JujutsuNetworking.sendProjectJjkImpulse(caster, impulse(ProjectJjkNobaraImpulsePayload.FP_SNAP, Math.max(1, marks), caster.getEyePosition(), gameTime));
		PENDING_ENLARGES.add(new PendingEnlarge(level, caster.getUUID(), target.getUUID(), target.getId(), gameTime + ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DELAY_TICKS, marks));
		return true;
	}

	/** Detonates embedded nails/marks with ProjectJJK's staggered Hairpin Explosion cadence. */
	public static boolean detonateMarks(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		List<ExplosionAnchor> anchors = collectExplosionAnchors(level, caster, gameTime);
		if (anchors.isEmpty()) {
			return false;
		}

		Collections.shuffle(anchors);
		for (ExplosionAnchor anchor : anchors) {
			Entity entity = level.getEntity(anchor.entityId());
			if (entity != null) {
				spawnProjectJjkPrime(level, entity.position());
			}
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 2.0f, 1.0f);
		JujutsuNetworking.sendProjectJjkImpulse(caster, impulse(ProjectJjkNobaraImpulsePayload.FP_SNAP, anchors.size(), caster.getEyePosition(), gameTime));
		consumeAnchorMarks(level, anchors, gameTime);
		PENDING_EXPLOSIONS.add(new PendingExplosion(level, caster.getUUID(), anchors, gameTime + ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_START_DELAY_TICKS));
		return true;
	}

	private static List<ExplosionAnchor> collectExplosionAnchors(ServerLevel level, ServerPlayer caster, long gameTime) {
		Vec3 look = safeDirection(caster.getLookAngle());
		Vec3 searchStart = caster.getEyePosition().add(look.scale(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET));
		Vec3 searchEnd = searchStart.add(look.scale(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_DETECT_RANGE));
		double searchRadius = ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_DETECT_RADIUS;
		AABB area = new AABB(searchStart, searchEnd).inflate(searchRadius);
		List<ExplosionAnchor> anchors = new ArrayList<>();
		Set<UUID> anchoredTargets = new HashSet<>();
		Set<Integer> anchoredEntities = new HashSet<>();
		for (Entity entity : level.getEntities(caster, area, e -> e instanceof ProjectJjkNailEntity nail && nail.isOwnedBy(caster.getUUID()))) {
			if (entity instanceof ProjectJjkNailEntity nail) {
				if (!isInsideSearchCapsule(nail.position(), searchStart, searchEnd, searchRadius)) {
					continue;
				}
				anchoredEntities.add(nail.getId());
				UUID targetId = nail.embeddedTargetUuid();
				int marks = targetId == null ? 1 : Math.max(1, ProjectJjkNailMarks.marks(targetId, gameTime));
				if (targetId != null) {
					anchoredTargets.add(targetId);
				}
				anchors.add(ExplosionAnchor.nail(nail.getId(), marks, targetId, nail.embeddedTargetEntityId()));
			}
		}
		for (Entity entity : level.getEntities(caster, area, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (!isInsideSearchCapsule(living.position().add(0.0, living.getBbHeight() * 0.5, 0.0), searchStart, searchEnd, searchRadius)) {
				continue;
			}
			if (anchoredTargets.contains(living.getUUID())) {
				continue;
			}
			int marks = ProjectJjkNailMarks.marks(living.getUUID(), gameTime);
			if (marks <= 0) {
				continue;
			}
			anchoredTargets.add(living.getUUID());
			anchoredEntities.add(living.getId());
			anchors.add(ExplosionAnchor.target(living.getId(), marks, living.getUUID(), living.getId()));
		}
		if (anchors.isEmpty()) {
			collectNearbyExplosionAnchors(level, caster, gameTime, anchors, anchoredTargets, anchoredEntities);
		}
		return anchors;
	}

	private static void collectNearbyExplosionAnchors(ServerLevel level, ServerPlayer caster, long gameTime, List<ExplosionAnchor> anchors, Set<UUID> anchoredTargets, Set<Integer> anchoredEntities) {
		AABB fallbackArea = caster.getBoundingBox().inflate(ProjectJjkNobaraProfile.DETONATE_RANGE);
		for (ProjectJjkNailEntity nail : level.getEntitiesOfClass(ProjectJjkNailEntity.class, fallbackArea, nail ->
				nail.isEmbedded() && nail.isOwnedBy(caster.getUUID()))) {
			if (!anchoredEntities.add(nail.getId())) {
				continue;
			}
			UUID targetId = nail.embeddedTargetUuid();
			int marks = targetId == null ? 1 : Math.max(1, ProjectJjkNailMarks.marks(targetId, gameTime));
			if (targetId != null) {
				anchoredTargets.add(targetId);
			}
			anchors.add(ExplosionAnchor.nail(nail.getId(), marks, targetId, nail.embeddedTargetEntityId()));
		}
		for (Entity entity : level.getEntities(caster, fallbackArea, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living) || anchoredEntities.contains(living.getId()) || anchoredTargets.contains(living.getUUID())) {
				continue;
			}
			int marks = ProjectJjkNailMarks.marks(living.getUUID(), gameTime);
			if (marks <= 0) {
				continue;
			}
			anchoredTargets.add(living.getUUID());
			anchoredEntities.add(living.getId());
			anchors.add(ExplosionAnchor.target(living.getId(), marks, living.getUUID(), living.getId()));
		}
	}

	// -- Helpers --------------------------------------------------------------------------------

	private static void tickHairpinTasks(long gameTime) {
		for (Iterator<PendingEnlarge> iterator = PENDING_ENLARGES.iterator(); iterator.hasNext();) {
			PendingEnlarge pending = iterator.next();
			if (pending.dueGameTime() > gameTime) {
				continue;
			}
			resolvePendingEnlarge(pending, gameTime);
			iterator.remove();
		}
		for (Iterator<PendingExplosion> iterator = PENDING_EXPLOSIONS.iterator(); iterator.hasNext();) {
			PendingExplosion pending = iterator.next();
			if (pending.startGameTime() > gameTime) {
				continue;
			}
			ServerPlayer caster = owner(pending.level(), pending.casterId());
			int count = 1 + RANDOM.nextInt(2);
			for (int index = 0; index < count && pending.hasNext(); index++) {
				explodeAnchor(pending.level(), caster, pending.next(), gameTime);
			}
			if (!pending.hasNext()) {
				discardExplosionTargetNails(pending);
				iterator.remove();
			}
		}
	}

	private static void resolvePendingEnlarge(PendingEnlarge pending, long gameTime) {
		ServerLevel level = pending.level();
		ServerPlayer caster = owner(level, pending.casterId());
		Entity entity = level.getEntity(pending.targetEntityId());
		LivingEntity target = entity instanceof LivingEntity candidate && candidate.isAlive() && candidate.getUUID().equals(pending.targetId()) ? candidate : null;
		if (target == null) {
			Entity byUuid = level.getEntity(pending.targetId());
			if (!(byUuid instanceof LivingEntity living) || !living.isAlive()) {
				return;
			}
			target = living;
		}
		DamageSource source = caster == null ? level.damageSources().magic() : level.damageSources().indirectMagic(caster, caster);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_STUN_TICKS, 2));
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_STUN_TICKS, 1));
		target.hurtServer(level, source, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DAMAGE);
		ProjectJjkNailMarks.consume(target.getUUID(), gameTime);
		discardOwnedEmbeddedNails(level, pending.casterId(), target);
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.56, 0.0);
		spawnProjectJjkEnlarge(level, at, pending.marks());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.25f, 2.0f);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, SoundSource.PLAYERS, 2.0f, 2.0f);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_GOO_FOLEY, SoundSource.PLAYERS, 0.25f, 1.5f);
		clearGlowingMark(target);
		broadcast(level, at, ProjectJjkNobaraImpulsePayload.HAIRPIN_ENLARGE, pending.marks(), at, gameTime);
	}

	private static void explodeAnchor(ServerLevel level, ServerPlayer caster, ExplosionAnchor anchor, long gameTime) {
		Entity sourceEntity = level.getEntity(anchor.entityId());
		if (sourceEntity == null) {
			return;
		}
		Vec3 at = sourceEntity instanceof LivingEntity living
				? living.position().add(0.0, living.getBbHeight() * 0.5, 0.0)
				: sourceEntity.position();
		if (sourceEntity instanceof ProjectJjkNailEntity nail) {
			nail.discard();
		}
		DamageSource source = caster == null ? level.damageSources().magic() : level.damageSources().indirectMagic(caster, caster);
		float damage = ProjectJjkNobaraProfile.detonateDamage(anchor.marks());
		AABB blast = new AABB(at, at).inflate(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_RADIUS);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, blast, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (caster != null && entity.getUUID().equals(caster.getUUID())) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				living.hurtServer(level, source, damage);
				Vec3 push = safeDirection(living.position().subtract(at));
				living.knockback(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_KNOCKBACK, -push.x, -push.z);
			}
		}
		spawnProjectJjkExplosion(level, at, anchor.marks());
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_EXPLODE, SoundSource.PLAYERS, 0.2f, 2.0f);
		broadcast(level, at, ProjectJjkNobaraImpulsePayload.HAIRPIN_EXPLOSION, anchor.marks(), at, gameTime);
		if (!anchor.nail() && anchor.targetId() != null && caster != null && sourceEntity instanceof LivingEntity living) {
			discardOwnedEmbeddedNails(level, caster.getUUID(), living);
		}
	}

	private static void spawnProjectJjkPrime(ServerLevel level, Vec3 at) {
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.1, at.z, 8, 0.18, 0.18, 0.18, 0.02);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 5, 0.16, 0.16, 0.16, 0.25);
		level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, at.x, at.y, at.z, 3, 0.07, 0.07, 0.07, 0.04);
	}

	private static void spawnProjectJjkExplosion(ServerLevel level, Vec3 at, int marks) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.1, at.z, 18 + marks * 4, 0.52, 0.34, 0.52, 0.16);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 10 + marks * 2, 0.28, 0.22, 0.28, 0.08);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 4 + marks, 0.18, 0.16, 0.18, 0.02);
		level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, at.x, at.y, at.z, 12 + marks * 3, 0.34, 0.26, 0.34, 0.18);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y + 0.1, at.z, 6, 0.28, 0.14, 0.28, 0.04);
	}

	private static void spawnProjectJjkEnlarge(ServerLevel level, Vec3 at, int marks) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.15, at.z, 28 + marks * 7, 0.7, 0.58, 0.7, 0.18);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 10, 0.18, 0.24, 0.18, 0.04);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 12 + marks * 2, 0.22, 0.3, 0.22, 0.06);
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 8, 0.18, 0.18, 0.18, 0.06);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, at.x, at.y, at.z, 16, 0.34, 0.28, 0.34, 0.24);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_RESIDUE, at.x, at.y, at.z, 18, 0.38, 0.34, 0.38, 0.16);
	}

	private static void spawnResonanceStrike(ServerLevel level, Vec3 at, int marks) {
		int intensity = 20 + marks * 12;
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 4 + marks, 0.2, 0.3, 0.2, 0.06);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_RESIDUE, at.x, at.y, at.z, intensity, 0.5, 0.6, 0.5, 0.22);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, at.x, at.y, at.z, 10 + marks * 4, 0.5, 0.4, 0.5, 0.35);
		level.sendParticles(JujutsuParticles.HAIRPIN_MARK_STAIN, at.x, at.y, at.z, 6 + marks * 2, 0.3, 0.4, 0.3, 0.01);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, intensity, 0.6, 0.7, 0.6, 0.1);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, intensity, 0.5, 0.6, 0.5, 0.2);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0.0, 0.0, 0.0, 0.0);
	}

	private static LivingEntity findMarkedTarget(ServerLevel level, ServerPlayer caster) {
		long gameTime = level.getGameTime();
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		net.minecraft.world.phys.AABB area = caster.getBoundingBox().inflate(ProjectJjkNobaraProfile.LINK_RANGE);
		LivingEntity best = null;
		double bestScore = -1.0;
		for (Entity entity : level.getEntities(caster, area, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (ProjectJjkNailMarks.marks(living.getUUID(), gameTime) <= 0) {
				continue;
			}
			Vec3 toTarget = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0).subtract(eye);
			double distance = toTarget.length();
			if (distance < 1.0e-3) {
				continue;
			}
			double alignment = look.dot(toTarget.scale(1.0 / distance));
			double score = alignment - distance / ProjectJjkNobaraProfile.LINK_RANGE * 0.5;
			if (score > bestScore) {
				bestScore = score;
				best = living;
			}
		}
		return best;
	}

	private static LivingEntity resolveLinked(ServerLevel level, ProjectJjkResonanceLink.Link link) {
		if (link == null) {
			return null;
		}
		Entity byId = level.getEntity(link.targetEntityId());
		if (byId instanceof LivingEntity living && living.getUUID().equals(link.targetId()) && living.isAlive()) {
			return living;
		}
		Entity byUuid = level.getEntity(link.targetId());
		return byUuid instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static ServerPlayer owner(ServerLevel level, UUID ownerUuid) {
		return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
	}

	private static void consumeAnchorMarks(ServerLevel level, List<ExplosionAnchor> anchors, long gameTime) {
		Set<UUID> consumed = new HashSet<>();
		for (ExplosionAnchor anchor : anchors) {
			UUID targetId = anchor.targetId();
			if (targetId == null || !consumed.add(targetId)) {
				continue;
			}
			ProjectJjkNailMarks.consume(targetId, gameTime);
			int targetEntityId = anchor.targetEntityId();
			if (targetEntityId < 0) {
				continue;
			}
			Entity target = level.getEntity(targetEntityId);
			if (target instanceof LivingEntity living) {
				clearGlowingMark(living);
			}
		}
	}

	private static void discardExplosionTargetNails(PendingExplosion pending) {
		Set<UUID> targets = new HashSet<>();
		for (ExplosionAnchor anchor : pending.anchors) {
			UUID targetId = anchor.targetId();
			if (targetId == null || !targets.add(targetId)) {
				continue;
			}
			discardOwnedEmbeddedNails(pending.level(), pending.casterId(), targetId, anchor.targetEntityId());
		}
	}

	private static void discardOwnedEmbeddedNails(ServerLevel level, UUID ownerId, UUID targetId, int targetEntityId) {
		if (ownerId == null || targetId == null) {
			return;
		}
		Entity byId = targetEntityId < 0 ? null : level.getEntity(targetEntityId);
		if (byId instanceof LivingEntity living && living.getUUID().equals(targetId)) {
			discardOwnedEmbeddedNails(level, ownerId, living);
			return;
		}
		Entity byUuid = level.getEntity(targetId);
		if (byUuid instanceof LivingEntity living) {
			discardOwnedEmbeddedNails(level, ownerId, living);
		}
	}

	private static void discardOwnedEmbeddedNails(ServerLevel level, UUID ownerId, LivingEntity target) {
		if (ownerId == null) {
			return;
		}
		UUID targetId = target.getUUID();
		AABB area = target.getBoundingBox().inflate(2.0);
		for (ProjectJjkNailEntity nail : level.getEntitiesOfClass(ProjectJjkNailEntity.class, area, nail ->
				nail.isEmbedded() && nail.isOwnedBy(ownerId) && targetId.equals(nail.embeddedTargetUuid()))) {
			nail.discard();
		}
	}

	private static void clearGlowingMark(LivingEntity target) {
		target.removeEffect(MobEffects.GLOWING);
	}

	private static boolean isInsideSearchCapsule(Vec3 point, Vec3 start, Vec3 end, double radius) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr < 1.0E-5) {
			return point.distanceToSqr(start) <= radius * radius;
		}
		double t = point.subtract(start).dot(segment) / lengthSqr;
		if (t < 0.0 || t > 1.0) {
			return false;
		}
		Vec3 closest = start.add(segment.scale(t));
		return point.distanceToSqr(closest) <= radius * radius;
	}

	private static void broadcast(ServerLevel level, Vec3 center, int kind, int marks, Vec3 at, long gameTime) {
		JujutsuNetworking.broadcastProjectJjkImpulse(level, center, IMPULSE_RADIUS, impulse(kind, marks, at, gameTime));
	}

	private static ProjectJjkNobaraImpulsePayload impulse(int kind, int marks, Vec3 at, long gameTime) {
		return new ProjectJjkNobaraImpulsePayload(kind, marks, at.x, at.y, at.z, gameTime);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private record PendingEnlarge(ServerLevel level, UUID casterId, UUID targetId, int targetEntityId, long dueGameTime, int marks) {}

	private static final class PendingExplosion {
		private final ServerLevel level;
		private final UUID casterId;
		private final List<ExplosionAnchor> anchors;
		private final long startGameTime;
		private int index;

		private PendingExplosion(ServerLevel level, UUID casterId, List<ExplosionAnchor> anchors, long startGameTime) {
			this.level = level;
			this.casterId = casterId;
			this.anchors = anchors;
			this.startGameTime = startGameTime;
		}

		private ServerLevel level() {
			return level;
		}

		private UUID casterId() {
			return casterId;
		}

		private long startGameTime() {
			return startGameTime;
		}

		private boolean hasNext() {
			return index < anchors.size();
		}

		private ExplosionAnchor next() {
			return anchors.get(index++);
		}
	}

	private record ExplosionAnchor(int entityId, int marks, boolean nail, UUID targetId, int targetEntityId) {
		private static ExplosionAnchor nail(int entityId, int marks, UUID targetId, int targetEntityId) {
			return new ExplosionAnchor(entityId, marks, true, targetId, targetEntityId);
		}

		private static ExplosionAnchor target(int entityId, int marks, UUID targetId, int targetEntityId) {
			return new ExplosionAnchor(entityId, marks, false, targetId, targetEntityId);
		}
	}
}
