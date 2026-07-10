package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Owns Nobara's server-authoritative nail marks and Hairpin detonation tasks. The physical
 * remnant-gated Resonance ritual lives in {@link ProjectJjkStrawDollRuntime}.
 */
public final class ProjectJjkRitualRuntime {
	private static final double IMPULSE_RADIUS = 64.0;
	private static final List<PendingExplosion> PENDING_EXPLOSIONS = new ArrayList<>();
	private static final List<PendingEnlarge> PENDING_ENLARGES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final DustParticleOptions PROJECTJJK_CYAN = new DustParticleOptions(0x2CE8F5, 1.15f);
	private static final String MARK_GLOW_TEAM_NAME = "jjk_ce_mark";
	private static final Map<UUID, MarkGlowState> MARK_GLOW_RESTORE = new ConcurrentHashMap<>();

	private ProjectJjkRitualRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkRitualRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING_EXPLOSIONS.clear();
			PENDING_ENLARGES.clear();
			restoreAllGlowTeams(server.getScoreboard());
		});
	}

	private static void onServerTick(MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		tickHairpinTasks(gameTime);
		if ((gameTime & 63L) == 0L) {
			ProjectJjkNailMarks.pruneExpired(gameTime);
			pruneGlowingMarks(server, gameTime);
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
		applyCursedGlow(level, target);
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ProjectJjkNobaraProfile.MARK_DURATION_TICKS, 0, false, false, true));
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 2 + marks, 0.08, 0.10, 0.08, 0.022);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y, at.z, 2, 0.12, 0.14, 0.12, 0.018);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_SIZZLE, SoundSource.PLAYERS, 0.5f, 0.8f + marks * 0.06f);
	}

	// -- Hairpin mark detonation ----------------------------------------------------------------

	/** ProjectJJK Hairpin Enlargement: snap, wait one second, then crush the looked-at marked target. */
	public static boolean tryEnlargeMarkedTarget(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		TargetResolver.Result result = TargetResolver.resolve(level, caster, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE);
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}
		Entity entity = level.getEntity(result.entityId().get());
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}
		int marks = ProjectJjkNailMarks.marks(target.getUUID(), gameTime);
		if (marks <= 0) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}

		playCasterSnap(level, caster, Math.max(1, marks), gameTime);
		PENDING_ENLARGES.add(new PendingEnlarge(level, caster.getUUID(), target.getUUID(), target.getId(), gameTime + ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DELAY_TICKS, marks));
		return true;
	}

	/** Detonates embedded nails/marks with ProjectJJK's staggered Hairpin Explosion cadence. */
	public static boolean detonateMarks(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		List<ExplosionAnchor> anchors = collectExplosionAnchors(level, caster, gameTime);
		if (anchors.isEmpty()) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}

		Collections.shuffle(anchors);
		for (ExplosionAnchor anchor : anchors) {
			Entity entity = level.getEntity(anchor.entityId());
			if (entity != null) {
				spawnProjectJjkPrime(level, entity.position());
			}
		}
		playCasterSnap(level, caster, anchors.size(), gameTime);
		JujutsuNetworking.sendVfxCue(caster,
				cue(level, NobaraVfxIds.DETONATE, anchors.size(), caster.getEyePosition(), gameTime, caster));
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
		broadcast(level, at, NobaraVfxIds.ENLARGE, pending.marks(), at, gameTime);
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
		broadcast(level, at, NobaraVfxIds.EXPLOSION, anchor.marks(), at, gameTime);
		if (!anchor.nail() && anchor.targetId() != null && caster != null && sourceEntity instanceof LivingEntity living) {
			discardOwnedEmbeddedNails(level, caster.getUUID(), living);
		}
	}

	private static void spawnProjectJjkPrime(ServerLevel level, Vec3 at) {
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.1, at.z, 8, 0.18, 0.18, 0.18, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 5, 0.16, 0.16, 0.16, 0.25);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 4, 0.07, 0.07, 0.07, 0.04);
	}

	private static void spawnProjectJjkExplosion(ServerLevel level, Vec3 at, int marks) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.1, at.z, 18 + marks * 4, 0.52, 0.34, 0.52, 0.16);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 10 + marks * 2, 0.28, 0.22, 0.28, 0.08);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 4 + marks, 0.18, 0.16, 0.18, 0.02);
		level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, at.x, at.y, at.z, 12 + marks * 3, 0.34, 0.26, 0.34, 0.18);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y + 0.1, at.z, 6, 0.28, 0.14, 0.28, 0.04);
	}

	private static void spawnProjectJjkEnlarge(ServerLevel level, Vec3 at, int marks) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.15, at.z, 28 + marks * 7, 0.7, 0.58, 0.7, 0.18);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 10, 0.18, 0.24, 0.18, 0.04);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 12 + marks * 2, 0.22, 0.3, 0.22, 0.06);
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 8, 0.18, 0.18, 0.18, 0.06);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, at.x, at.y, at.z, 16, 0.34, 0.28, 0.34, 0.24);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_RESIDUE, at.x, at.y, at.z, 18, 0.38, 0.34, 0.38, 0.16);
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

	static void discardOwnedEmbeddedNails(ServerLevel level, UUID ownerId, LivingEntity target) {
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

	static void clearGlowingMark(LivingEntity target) {
		target.removeEffect(MobEffects.GLOWING);
		restoreGlowTeam(target.level().getScoreboard(), target.getUUID());
	}

	private static void applyCursedGlow(ServerLevel level, LivingEntity target) {
		Scoreboard scoreboard = level.getScoreboard();
		PlayerTeam current = scoreboard.getPlayersTeam(target.getScoreboardName());
		if (current == null || !MARK_GLOW_TEAM_NAME.equals(current.getName())) {
			MARK_GLOW_RESTORE.putIfAbsent(target.getUUID(),
					new MarkGlowState(target.getScoreboardName(), current == null ? null : current.getName()));
		}
		PlayerTeam markTeam = scoreboard.getPlayerTeam(MARK_GLOW_TEAM_NAME);
		if (markTeam == null) {
			markTeam = scoreboard.addPlayerTeam(MARK_GLOW_TEAM_NAME);
		}
		markTeam.setColor(ChatFormatting.AQUA);
		scoreboard.addPlayerToTeam(target.getScoreboardName(), markTeam);
	}

	private static void pruneGlowingMarks(MinecraftServer server, long gameTime) {
		for (UUID targetId : new ArrayList<>(MARK_GLOW_RESTORE.keySet())) {
			if (ProjectJjkNailMarks.marks(targetId, gameTime) > 0) {
				continue;
			}
			Entity entity = findEntity(server, targetId);
			if (entity instanceof LivingEntity living) {
				clearGlowingMark(living);
			} else {
				restoreGlowTeam(server.getScoreboard(), targetId);
			}
		}
	}

	private static Entity findEntity(MinecraftServer server, UUID targetId) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(targetId);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static void restoreGlowTeam(Scoreboard scoreboard, UUID targetId) {
		MarkGlowState state = MARK_GLOW_RESTORE.remove(targetId);
		if (state == null) {
			return;
		}
		restoreGlowTeam(scoreboard, state);
	}

	private static void restoreAllGlowTeams(Scoreboard scoreboard) {
		for (MarkGlowState state : new ArrayList<>(MARK_GLOW_RESTORE.values())) {
			restoreGlowTeam(scoreboard, state);
		}
		MARK_GLOW_RESTORE.clear();
	}

	private static void restoreGlowTeam(Scoreboard scoreboard, MarkGlowState state) {
		PlayerTeam markTeam = scoreboard.getPlayerTeam(MARK_GLOW_TEAM_NAME);
		if (markTeam != null && markTeam.getPlayers().contains(state.scoreboardName())) {
			scoreboard.removePlayerFromTeam(state.scoreboardName(), markTeam);
		}
		if (state.previousTeamName() == null) {
			return;
		}
		PlayerTeam previous = scoreboard.getPlayerTeam(state.previousTeamName());
		if (previous != null) {
			scoreboard.addPlayerToTeam(state.scoreboardName(), previous);
		}
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

	private static void broadcast(ServerLevel level, Vec3 center, ResourceLocation effectId, int marks, Vec3 at, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, center, IMPULSE_RADIUS, cue(level, effectId, marks, at, gameTime));
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 at, long gameTime) {
		return new VfxCue(effectId, at, VfxCue.NO_ANCHOR, Vec3.ZERO, Math.max(1, intensity), gameTime, level.random.nextLong());
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 at, long gameTime, Entity anchor) {
		return new VfxCue(effectId, at, anchor.getId(), at.subtract(anchor.position()), Math.max(1, intensity), gameTime, level.random.nextLong());
	}

	private static void playCasterSnap(ServerLevel level, ServerPlayer caster, int marks, long gameTime) {
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 2.0f, 1.0f);
		JujutsuNetworking.sendVfxCue(caster,
				cue(level, NobaraVfxIds.FIRST_PERSON_SNAP, Math.max(1, marks), caster.getEyePosition(), gameTime, caster));
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private record PendingEnlarge(ServerLevel level, UUID casterId, UUID targetId, int targetEntityId, long dueGameTime, int marks) {}

	private record MarkGlowState(String scoreboardName, String previousTeamName) {}

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
