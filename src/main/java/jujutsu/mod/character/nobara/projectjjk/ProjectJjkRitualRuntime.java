package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
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
	private static final HairpinChainScheduler<ChainContext> HAIRPIN_CHAINS = new HairpinChainScheduler<>();
	private static final List<PendingEnlarge> PENDING_ENLARGES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final DustParticleOptions PROJECTJJK_CYAN = new DustParticleOptions(0x2CE8F5, 1.15f);
	private static final String MARK_GLOW_TEAM_NAME = "jjk_ce_mark";
	private static final Map<UUID, MarkGlowState> MARK_GLOW_RESTORE = new ConcurrentHashMap<>();
	private static final ExplosionDamageCalculator BLOCK_ONLY_EXPLOSION = new ExplosionDamageCalculator() {
		@Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
		@Override public float getKnockbackMultiplier(Entity entity) { return 0.0f; }
		@Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float seenPercent) { return 0.0f; }
	};

	private ProjectJjkRitualRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkRitualRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HAIRPIN_CHAINS.clear();
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
		List<UUID> nails = level.getEntitiesOfClass(ProjectJjkNailEntity.class, target.getBoundingBox().inflate(2.0), nail ->
				nail.isEmbedded() && nail.isOwnedBy(caster.getUUID()) && target.getUUID().equals(nail.anchor().stableId()))
				.stream().map(Entity::getUUID).toList();
		if (nails.isEmpty()) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}

		playCasterSnap(level, caster, nails.size(), gameTime);
		PENDING_ENLARGES.add(new PendingEnlarge(level, caster.getUUID(), target.getUUID(), target.getId(), gameTime + ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DELAY_TICKS, nails));
		return true;
	}

	/** Starts R from the aimed nail/target and chains through owned nails within ten blocks. */
	public static boolean startDirectedHairpin(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		List<ExplosionAnchor> available = collectAllLoadedOwnedNails(level, caster);
		ExplosionAnchor seed = findDirectedSeed(level, caster, available);
		if (seed == null) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}
		List<ExplosionAnchor> anchors = available.stream()
				.filter(anchor -> anchor.snapshotPosition().distanceToSqr(seed.snapshotPosition())
						<= ProjectJjkNobaraProfile.HAIRPIN_DIRECTED_CHAIN_RADIUS * ProjectJjkNobaraProfile.HAIRPIN_DIRECTED_CHAIN_RADIUS)
				.toList();
		return scheduleHairpin(caster, HairpinChain.Mode.DIRECTED, seed, anchors,
				ProjectJjkNobaraProfile.HAIRPIN_DIRECTED_CHAIN_DELAY_TICKS, gameTime);
	}

	/** Starts B over every currently loaded owned embedded nail. */
	public static boolean startMassHairpin(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		List<ExplosionAnchor> anchors = collectAllLoadedOwnedNails(level, caster);
		if (anchors.isEmpty()) {
			playCasterSnap(level, caster, 1, gameTime);
			return true;
		}
		return scheduleHairpin(caster, HairpinChain.Mode.MASS, null, anchors,
				ProjectJjkNobaraProfile.HAIRPIN_MASS_CHAIN_DELAY_TICKS, gameTime);
	}

	private static boolean scheduleHairpin(ServerPlayer caster, HairpinChain.Mode mode, ExplosionAnchor directedSeed, List<ExplosionAnchor> anchors, int cadence, long gameTime) {
		ServerLevel level = caster.level();
		Vec3 start = directedSeed == null ? caster.position() : directedSeed.snapshotPosition();
		List<ExplosionAnchor> ordered = orderedAnchors(mode, directedSeed, start, anchors);
		for (ExplosionAnchor anchor : anchors) {
			spawnProjectJjkPrime(level, anchor.snapshotPosition());
		}
		playCasterSnap(level, caster, anchors.size(), gameTime);
		JujutsuNetworking.sendVfxCue(caster,
				cue(level, NobaraVfxIds.DETONATE, anchors.size(), caster.getEyePosition(), gameTime, caster));
		Map<UUID, ExplosionAnchor> byId = ordered.stream().collect(java.util.stream.Collectors.toMap(ExplosionAnchor::nailId, anchor -> anchor));
		HairpinChain chain = HairpinChain.start(mode, ordered.stream().map(ExplosionAnchor::nailId).toList(),
				gameTime + ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_START_DELAY_TICKS, cadence);
		HAIRPIN_CHAINS.schedule(new ChainContext(level, caster.getUUID(), byId), chain);
		return true;
	}

	private static List<ExplosionAnchor> orderedAnchors(HairpinChain.Mode mode, ExplosionAnchor seed, Vec3 start, List<ExplosionAnchor> input) {
		Map<UUID, ExplosionAnchor> byId = input.stream().collect(java.util.stream.Collectors.toMap(ExplosionAnchor::nailId, anchor -> anchor));
		List<HairpinChainOrder.Candidate> candidates = input.stream()
				.map(anchor -> new HairpinChainOrder.Candidate(anchor.nailId(), anchor.snapshotPosition())).toList();
		List<HairpinChainOrder.Candidate> ordered = mode == HairpinChain.Mode.DIRECTED
				? HairpinChainOrder.directed(seed.nailId(), seed.snapshotPosition(), candidates)
				: HairpinChainOrder.nearestNeighbor(start, candidates);
		return ordered.stream().map(candidate -> byId.get(candidate.nailId())).toList();
	}

	private static List<ExplosionAnchor> collectAllLoadedOwnedNails(ServerLevel level, ServerPlayer caster) {
		return EmbeddedNailRegistry.loadedOwnedNails(level, caster.getUUID()).stream()
				.map(ExplosionAnchor::nail)
				.toList();
	}

	private static ExplosionAnchor findDirectedSeed(ServerLevel level, ServerPlayer caster, List<ExplosionAnchor> anchors) {
		TargetResolver.Result result = TargetResolver.resolve(level, caster, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE);
		if (result.mode() == TargetResolver.Mode.ENTITY && result.entityId().isPresent()) {
			Entity target = level.getEntity(result.entityId().get());
			if (target != null) {
				ExplosionAnchor onTarget = anchors.stream().filter(anchor -> target.getUUID().equals(anchor.targetId()))
						.min(Comparator.comparing(ExplosionAnchor::nailId)).orElse(null);
				if (onTarget != null) return onTarget;
			}
		}
		Vec3 start = caster.getEyePosition();
		Vec3 direction = safeDirection(caster.getLookAngle());
		return anchors.stream().filter(anchor -> {
			Vec3 offset = anchor.snapshotPosition().subtract(start);
			double along = offset.dot(direction);
			return along >= 0.0 && along <= ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE
					&& offset.subtract(direction.scale(along)).lengthSqr() <= 1.0;
		}).min(Comparator.comparingDouble(anchor -> anchor.snapshotPosition().distanceToSqr(start)))
				.orElseGet(() -> result.mode() == TargetResolver.Mode.BLOCK
						? anchors.stream().filter(anchor -> anchor.snapshotPosition().distanceToSqr(result.point()) <= 2.25)
								.min(Comparator.comparing(ExplosionAnchor::nailId)).orElse(null)
						: null);
	}

	// -- Helpers --------------------------------------------------------------------------------

	private static void tickHairpinTasks(long gameTime) {
		for (Iterator<PendingEnlarge> iterator = PENDING_ENLARGES.iterator(); iterator.hasNext();) {
			PendingEnlarge pending = iterator.next();
			if (pending.dueGameTime() > gameTime) {
				continue;
			}
			if (resolvePendingEnlarge(pending, gameTime) != EnlargeOutcome.RETRY) iterator.remove();
		}
		HAIRPIN_CHAINS.tick(gameTime, ProjectJjkRitualRuntime::resolveChainNail,
				ProjectJjkRitualRuntime::explodeChainNail, ProjectJjkRitualRuntime::finishHairpinChain);
	}

	private static EnlargeOutcome resolvePendingEnlarge(PendingEnlarge pending, long gameTime) {
		ServerLevel level = pending.level();
		ServerPlayer caster = owner(level, pending.casterId());
		Entity entity = level.getEntity(pending.targetEntityId());
		LivingEntity target = entity instanceof LivingEntity candidate && candidate.isAlive() && candidate.getUUID().equals(pending.targetId()) ? candidate : null;
		if (target == null) {
			Entity byUuid = level.getEntity(pending.targetId());
			if (!(byUuid instanceof LivingEntity living) || !living.isAlive()) return NailAnchorLifecycle.isConfirmedRemoved(pending.targetId()) ? EnlargeOutcome.TERMINAL : EnlargeOutcome.RETRY;
			target = living;
		}
		DamageSource source = NobaraDamageSources.hairpin(level, caster);
		int activated = 0;
		for (Iterator<UUID> iterator = pending.nailIds().iterator(); iterator.hasNext();) {
			UUID nailId = iterator.next();
			Entity entityNail = level.getEntity(nailId);
			if (!(entityNail instanceof ProjectJjkNailEntity nail)) { if (NailAnchorLifecycle.isConfirmedRemoved(nailId)) iterator.remove(); continue; }
			if (!nail.isOwnedBy(pending.casterId()) || !target.getUUID().equals(nail.anchor().stableId())) { iterator.remove(); continue; }
			target.hurtServer(level, source, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DAMAGE_PER_NAIL);
			activated++;
			Vec3 nailAt = nail.position();
			broadcast(level, nailAt, NobaraVfxIds.ENLARGE, 1, nailAt, gameTime);
			nail.discard();
			iterator.remove();
		}
		if (!pending.nailIds().isEmpty()) return EnlargeOutcome.RETRY;
		if (activated == 0) return EnlargeOutcome.TERMINAL;
		ProjectJjkNailMarks.consume(target.getUUID(), gameTime);
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.56, 0.0);
		spawnProjectJjkEnlarge(level, at, activated);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.25f, 2.0f);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, SoundSource.PLAYERS, 2.0f, 2.0f);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_GOO_FOLEY, SoundSource.PLAYERS, 0.25f, 1.5f);
		clearGlowingMark(target);
		return EnlargeOutcome.SUCCESS;
	}

	private static HairpinChain.Resolution resolveChainNail(ChainContext context, UUID nailId) {
		Entity entity = context.level().getEntity(nailId);
		if (entity instanceof ProjectJjkNailEntity nail) {
			return nail.isOwnedBy(context.casterId()) && nail.isEmbedded()
					? HairpinChain.Resolution.RESOLVED : HairpinChain.Resolution.INVALID;
		}
		return NailAnchorLifecycle.isConfirmedRemoved(nailId)
				? HairpinChain.Resolution.CONFIRMED_REMOVED : HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE;
	}

	private static void explodeChainNail(ChainContext context, HairpinChain.Mode mode, UUID nailId, boolean finale, long gameTime) {
		ServerLevel level = context.level();
		Entity entity = level.getEntity(nailId);
		if (!(entity instanceof ProjectJjkNailEntity nail) || !nail.isOwnedBy(context.casterId())) return;
		ServerPlayer caster = owner(level, context.casterId());
		ExplosionAnchor anchor = context.anchors().get(nailId);
		Vec3 at = nail.position();
		float depthMultiplier = nail.depthDamageMultiplier();
		int depth = nail.embedDepthLevel();
		NailAnchor.Kind anchorKind = nail.anchor().kind();
		DamageSource source = NobaraDamageSources.hairpin(level, caster);
		float baseDamage = mode == HairpinChain.Mode.DIRECTED
				? ProjectJjkNobaraProfile.HAIRPIN_DIRECTED_DAMAGE_PER_NAIL
				: ProjectJjkNobaraProfile.HAIRPIN_BOOM_DAMAGE_PER_NAIL;
		float damage = baseDamage * depthMultiplier * ResonantMomentum.damageMultiplier(caster);
		AABB blast = new AABB(at, at).inflate(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_RADIUS);
		for (Entity victim : level.getEntitiesOfClass(Entity.class, blast, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (caster != null && victim.getUUID().equals(caster.getUUID())) {
				continue;
			}
			if (victim instanceof LivingEntity living) {
				living.hurtServer(level, source, damage);
				Vec3 push = safeDirection(living.position().subtract(at));
				living.knockback(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_KNOCKBACK, -push.x, -push.z);
			}
		}
		if (mode == HairpinChain.Mode.DIRECTED && anchorKind == NailAnchor.Kind.BLOCK) {
			level.explode(caster, level.damageSources().explosion(caster, caster), BLOCK_ONLY_EXPLOSION, at,
					ProjectJjkNobaraProfile.HAIRPIN_BLOCK_EXPLOSION_POWER, false, Level.ExplosionInteraction.BLOCK);
		}
		nail.discard();
		spawnProjectJjkExplosion(level, at, 1);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_EXPLODE, SoundSource.PLAYERS, 0.2f, 2.0f);
		if (depth == 3) level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 0.72f, 0.78f);
		if (finale) level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_LONG_WHOOSH, SoundSource.PLAYERS, 0.9f, 0.62f);
		broadcast(level, at, NobaraVfxIds.EXPLOSION, NobaraVfxIds.hairpinExplosionIntensity(depth, finale), at, gameTime);
	}

	private static void finishHairpinChain(ChainContext context, HairpinChain.Mode mode, UUID lastSuccessfulId, long gameTime) {
		ExplosionAnchor anchor = context.anchors().get(lastSuccessfulId);
		if (anchor == null) return;
		Vec3 at = anchor.snapshotPosition();
		context.level().playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_LONG_WHOOSH,
				SoundSource.PLAYERS, 0.9f, 0.62f);
		broadcast(context.level(), at, NobaraVfxIds.EXPLOSION,
				NobaraVfxIds.hairpinExplosionIntensity(anchor.depth(), true), at, gameTime);
		for (UUID targetId : context.anchors().values().stream().map(ExplosionAnchor::targetId)
				.filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet())) {
			ProjectJjkNailMarks.consume(targetId, gameTime);
			Entity target = context.level().getEntity(targetId);
			if (target instanceof LivingEntity living) clearGlowingMark(living);
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

	private static void broadcast(ServerLevel level, Vec3 center, ResourceLocation effectId, int marks, Vec3 at, long gameTime) {
		JujutsuNetworking.broadcastVfxCue(level, center, IMPULSE_RADIUS, cue(level, effectId, marks, at, gameTime));
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 at, long gameTime) {
		return new VfxCue(effectId, at, VfxCue.NO_ANCHOR, Vec3.ZERO, Math.max(1, intensity), gameTime, level.random.nextLong(), Vec3.ZERO);
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 at, long gameTime, Entity anchor) {
		return new VfxCue(effectId, at, anchor.getId(), at.subtract(anchor.position()), Math.max(1, intensity), gameTime, level.random.nextLong(), Vec3.ZERO);
	}

	private static void playCasterSnap(ServerLevel level, ServerPlayer caster, int marks, long gameTime) {
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 2.0f, 1.0f);
		JujutsuNetworking.sendVfxCue(caster,
				cue(level, NobaraVfxIds.FIRST_PERSON_SNAP, Math.max(1, marks), caster.getEyePosition(), gameTime, caster));
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private enum EnlargeOutcome { SUCCESS, RETRY, TERMINAL }
	private record PendingEnlarge(ServerLevel level, UUID casterId, UUID targetId, int targetEntityId, long dueGameTime, List<UUID> nailIds) {
		private PendingEnlarge { nailIds = new ArrayList<>(nailIds); }
	}

	private record MarkGlowState(String scoreboardName, String previousTeamName) {}

	private record ChainContext(ServerLevel level, UUID casterId, Map<UUID, ExplosionAnchor> anchors) {}

	private record ExplosionAnchor(UUID nailId, UUID targetId, Vec3 snapshotPosition, int depth) {
		private static ExplosionAnchor nail(ProjectJjkNailEntity nail) {
			return new ExplosionAnchor(nail.getUUID(), nail.embeddedTargetUuid(), nail.position(), nail.embedDepthLevel());
		}
	}
}
