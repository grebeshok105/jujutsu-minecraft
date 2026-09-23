package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/** Server-authoritative Hairpin chain runtime and owner-scoped target-mark presentation. */
public final class HairpinRuntime {
	private static final Double VFX_DELIVERY_RADIUS = 64.0;
	private static final HairpinChainScheduler<ChainContext> HAIRPIN_CHAINS = new HairpinChainScheduler<>();
	private static final DustParticleOptions PROJECTJJK_CYAN = new DustParticleOptions(0x2CE8F5, 1.15f);
	private static final String MARK_GLOW_TEAM_NAME = "jjk_ce_mark";
	private static final Map<UUID, MarkGlowState> MARK_GLOW_RESTORE = new ConcurrentHashMap<>();
	private static final ExplosionDamageCalculator BLOCK_ONLY_EXPLOSION = new ExplosionDamageCalculator() {
		@Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
		@Override public float getKnockbackMultiplier(Entity entity) { return 0.0f; }
		@Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float seenPercent) { return 0.0f; }
	};

	private HairpinRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HairpinRuntime::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HAIRPIN_CHAINS.clear();
			ProjectJjkNailMarks.clearAll();
			for (ServerLevel level : server.getAllLevels()) restoreAllGlow(level);
		});
	}

	private static void onServerTick(MinecraftServer server) {
		// Each chain ticks on its own dimension's clock; the overworld clock would detonate
		// cross-dimension chains early or late.
		HAIRPIN_CHAINS.tick(context -> context.level().getGameTime(), HairpinRuntime::resolveChainNail,
				HairpinRuntime::explodeChainNail, HairpinRuntime::finishHairpinChain);
		long gameTime = server.overworld().getGameTime();
		if ((gameTime & 63L) == 0L) {
			ProjectJjkNailMarks.pruneExpired(gameTime);
			pruneGlowingMarks(server, gameTime);
		}
	}

	/** Applies one owner-scoped mark and refreshes the shared cursed glow. */
	public static void markTarget(ServerLevel level, UUID ownerId, LivingEntity target) {
		if (level == null || ownerId == null || target == null) return;
		int marks = ProjectJjkNailMarks.apply(ownerId, target.getUUID(), level.getGameTime());
		Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
		applyCursedGlow(level, target);
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, center.x, center.y, center.z,
				2 + marks, 0.08, 0.10, 0.08, 0.022);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, center.x, center.y, center.z,
				2, 0.12, 0.14, 0.12, 0.018);
		level.playSound(null, center.x, center.y, center.z, JujutsuSounds.PROJECTJJK_SIZZLE,
				SoundSource.PLAYERS, 0.5f, 0.8f + marks * 0.06f);
	}

	/** Starts the server-authoritative directed Hairpin chain from the same pure resolver as preview. */
	public static AbilityResult startDirectedHairpin(ServerPlayer caster) {
		Objects.requireNonNull(caster, "caster");
		ServerLevel level = caster.level();
		List<HairpinNetwork.Node> candidates = NailAnchorRegistry.ownedAnchors(level, caster.getUUID()).stream()
				.map(entry -> snapshotNode(level, entry))
				.filter(Objects::nonNull)
				.toList();
		TargetResolver.Result aim = TargetResolver.resolve(level, caster, ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE);
		Entity aimedEntity = aim.entityId().isPresent() ? level.getEntity(aim.entityId().get()) : null;
		BlockHitResult blockHit = blockHit(level, caster);
		UUID seedId = HairpinSeedResolver.resolveSeed(level, caster.getEyePosition(), caster.getLookAngle(),
				aimedEntity, blockHit, candidates);
		if (seedId == null) {
			caster.displayClientMessage(ComponentKeys.noAnchors(), true);
			return AbilityResult.HANDLED_FAILURE;
		}
		HairpinNetwork.Node seed = candidates.stream().filter(node -> node.nailId().equals(seedId)).findFirst().orElse(null);
		if (seed == null) {
			caster.displayClientMessage(ComponentKeys.noAnchors(), true);
			return AbilityResult.HANDLED_FAILURE;
		}
		List<HairpinNetwork.Node> ordered = HairpinNetwork.build(candidates, seed,
				ProjectJjkNobaraProfile.HAIRPIN_CHAIN_RADIUS);
		if (ordered.isEmpty()) {
			caster.displayClientMessage(ComponentKeys.noAnchors(), true);
			return AbilityResult.HANDLED_FAILURE;
		}
		Map<UUID, HairpinNetwork.Node> byId = new HashMap<>();
		for (HairpinNetwork.Node node : ordered) byId.put(node.nailId(), node);
		long now = level.getGameTime();
		playCasterSnap(level, caster, ordered.size(), now);
		JujutsuNetworking.sendVfxCue(caster, VfxCues.anchored(NobaraVfxIds.DETONATE, caster.getEyePosition(),
				caster.getId(), caster.position(), ordered.size(), now, level.random.nextLong()));
		JujutsuNetworking.broadcastVfxCue(level, caster.position(), VFX_DELIVERY_RADIUS,
				VfxCues.anchored(NobaraVfxIds.CASTER_ACTION, caster.position(), caster.getId(), caster.position(),
						1, now, level.random.nextLong()));
		HairpinChain chain = HairpinChain.start(ordered.stream().map(HairpinNetwork.Node::nailId).toList(),
				now + ProjectJjkNobaraProfile.HAIRPIN_FIRST_DETONATION_TICKS,
				ProjectJjkNobaraProfile.HAIRPIN_CADENCE_TICKS);
		HAIRPIN_CHAINS.schedule(new ChainContext(level, caster.getUUID(), ordered, byId), chain);
		return AbilityResult.SUCCESS;
	}

	private static HairpinNetwork.Node snapshotNode(ServerLevel level, NailAnchorRegistry.Entry entry) {
		Entity entity = level.getEntity(entry.nailId());
		return entity instanceof ProjectJjkNailEntity nail && nail.isEmbedded()
				? new HairpinNetwork.Node(entry.nailId(), nail.position(), entry.targetId(), entry.depth(), entry.origin())
				: null;
	}

	private static BlockHitResult blockHit(ServerLevel level, ServerPlayer caster) {
		Vec3 eye = caster.getEyePosition();
		Vec3 end = eye.add(caster.getLookAngle().normalize().scale(ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
	}

	private static HairpinChain.Resolution resolveChainNail(ChainContext context, UUID nailId) {
		Entity entity = context.level().getEntity(nailId);
		if (entity instanceof ProjectJjkNailEntity nail) {
			NailAnchorResolution resolution = NailAnchorResolution.forEntity(true, false,
					nail.isOwnedBy(context.casterId()) && nail.isEmbedded());
			return HairpinChain.Resolution.valueOf(resolution.name());
		}
		return NailAnchorLifecycle.isConfirmedRemoved(nailId)
				? HairpinChain.Resolution.CONFIRMED_REMOVED
				: HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE;
	}

	private static void explodeChainNail(ChainContext context, UUID nailId, boolean finale, long gameTime) {
		ServerLevel level = context.level();
		Entity entity = level.getEntity(nailId);
		if (!(entity instanceof ProjectJjkNailEntity nail) || !nail.isOwnedBy(context.casterId()) || !nail.isEmbedded()) return;
		ServerPlayer caster = owner(level, context.casterId());
		Vec3 at = nail.position();
		int depth = context.nodes().getOrDefault(nailId,
				new HairpinNetwork.Node(nailId, at, nail.embeddedTargetUuid(), nail.embedDepthLevel(), nail.origin())).depth();
		float depthMultiplier = ProjectJjkNobaraProfile.nailDepthMultiplier(depth);
		DamageSource source = NobaraDamageSources.hairpin(level, caster);
		AABB blast = new AABB(at, at).inflate(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_RADIUS);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, blast, LivingEntity::isAlive)) {
			if (caster != null && victim.getUUID().equals(caster.getUUID())) continue;
			victim.hurtServer(level, source,
					ProjectJjkNobaraProfile.HAIRPIN_DIRECTED_DAMAGE_PER_NAIL * depthMultiplier
							* ResonantMomentum.damageMultiplier(caster));
			Vec3 push = safeDirection(victim.position().subtract(at));
			victim.knockback(ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_KNOCKBACK, -push.x, -push.z);
		}
		if (nail.anchor().kind() == NailAnchor.Kind.BLOCK) {
			level.explode(caster, level.damageSources().explosion(caster, caster), BLOCK_ONLY_EXPLOSION, at,
					ProjectJjkNobaraProfile.HAIRPIN_BLOCK_EXPLOSION_POWER, false, Level.ExplosionInteraction.BLOCK);
		}
		nail.discard();
		spawnProjectJjkExplosion(level, at);
		context.noteSuccess();
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_EXPLODE,
				SoundSource.PLAYERS, 0.2f, 2.0f);
		if (depth == 3) {
			level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION,
					SoundSource.PLAYERS, 0.72f, 0.78f);
			JujutsuNetworking.broadcastVfxCue(level, at, VFX_DELIVERY_RADIUS,
					VfxCues.worldFixed(NobaraVfxIds.DEEPLY_ANCHORED, at, 3, gameTime, level.random.nextLong()));
		}
		HairpinNetwork.Node next = nextNode(context, nailId);
		if (next != null) {
			JujutsuNetworking.broadcastVfxCue(level, at, VFX_DELIVERY_RADIUS,
					VfxCues.worldFixedDisplacement(NobaraVfxIds.HAIRPIN_LINK, at, depth,
							gameTime, level.random.nextLong(), next.position().subtract(at)));
		}
	}

	private static void finishHairpinChain(ChainContext context, UUID lastSuccessfulId, long gameTime) {
		for (UUID targetId : context.nodes().values().stream().map(HairpinNetwork.Node::targetId)
				.filter(Objects::nonNull).distinct().toList()) {
			ProjectJjkNailMarks.consume(context.casterId(), targetId);
			clearGlowingMark(context.level(), targetId);
		}
		if (context.chainSuccesses() >= 3) {
			ServerPlayer caster = owner(context.level(), context.casterId());
			if (caster != null) ResonantMomentum.grantExecutionMoment(caster,
					ProjectJjkNobaraProfile.MOMENTUM_WINDOW_TICKS);
		}
	}

	private static HairpinNetwork.Node nextNode(ChainContext context, UUID nailId) {
		for (int index = 0; index + 1 < context.ordered().size(); index++) {
			if (context.ordered().get(index).nailId().equals(nailId)) return context.ordered().get(index + 1);
		}
		return null;
	}

	/** Removes target glow only if no owner still has an active mark on that target. */
	public static void clearGlowingMark(ServerLevel level, UUID targetId) {
		if (level == null || targetId == null || ProjectJjkNailMarks.anyMarks(targetId, level.getGameTime())) return;
		Entity entity = level.getEntity(targetId);
		if (entity instanceof LivingEntity living) living.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
		restoreGlowTeam(level.getScoreboard(), targetId);
	}

	/** Restores every displaced scoreboard membership in one level; retained for fixture reset. */
	public static void restoreAllGlow(ServerLevel level) {
		if (level != null) restoreAllGlowTeams(level.getScoreboard());
	}

	/** Compatibility form for control surfaces that own a full server rather than one level. */
	public static void restoreAllGlow(MinecraftServer server) {
		if (server == null) return;
		for (ServerLevel level : server.getAllLevels()) restoreAllGlow(level);
	}

	private static void applyCursedGlow(ServerLevel level, LivingEntity target) {
		Scoreboard scoreboard = level.getScoreboard();
		PlayerTeam current = scoreboard.getPlayersTeam(target.getScoreboardName());
		if (current == null || !MARK_GLOW_TEAM_NAME.equals(current.getName())) {
			MARK_GLOW_RESTORE.putIfAbsent(target.getUUID(),
					new MarkGlowState(target.getScoreboardName(), current == null ? null : current.getName()));
		}
		PlayerTeam markTeam = scoreboard.getPlayerTeam(MARK_GLOW_TEAM_NAME);
		if (markTeam == null) markTeam = scoreboard.addPlayerTeam(MARK_GLOW_TEAM_NAME);
		markTeam.setColor(ChatFormatting.AQUA);
		scoreboard.addPlayerToTeam(target.getScoreboardName(), markTeam);
		target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.GLOWING, ProjectJjkNobaraProfile.MARK_DURATION_TICKS,
				0, false, false, true));
	}

	private static void pruneGlowingMarks(MinecraftServer server, long gameTime) {
		for (UUID targetId : new ArrayList<>(MARK_GLOW_RESTORE.keySet())) {
			if (ProjectJjkNailMarks.anyMarks(targetId, gameTime)) continue;
			Entity entity = findEntity(server, targetId);
			if (entity instanceof LivingEntity living) living.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
			restoreGlowTeam(server.getScoreboard(), targetId);
		}
	}

	/**
	 * Restores glow on every target that no longer holds an active mark. Owner-scoped in
	 * effect: after an owner's marks are cleared, targets marked only by that owner lose the
	 * glow while targets still marked by other owners keep it.
	 */
	public static void restoreUnmarkedGlow(MinecraftServer server) {
		pruneGlowingMarks(server, server.overworld().getGameTime());
	}

	/** Drops every scheduled chain owned by one caster (fixture reset / teardown). */
	public static void clearChains(UUID ownerId) {
		if (ownerId != null) {
			HAIRPIN_CHAINS.removeIf(context -> ownerId.equals(context.casterId()));
		}
	}

	private static Entity findEntity(MinecraftServer server, UUID targetId) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(targetId);
			if (entity != null) return entity;
		}
		return null;
	}

	private static void restoreGlowTeam(Scoreboard scoreboard, UUID targetId) {
		MarkGlowState state = MARK_GLOW_RESTORE.remove(targetId);
		if (state != null) restoreGlowTeam(scoreboard, state);
	}

	private static void restoreAllGlowTeams(Scoreboard scoreboard) {
		for (MarkGlowState state : new ArrayList<>(MARK_GLOW_RESTORE.values())) restoreGlowTeam(scoreboard, state);
		MARK_GLOW_RESTORE.clear();
	}

	private static void restoreGlowTeam(Scoreboard scoreboard, MarkGlowState state) {
		PlayerTeam markTeam = scoreboard.getPlayerTeam(MARK_GLOW_TEAM_NAME);
		if (markTeam != null && markTeam.getPlayers().contains(state.scoreboardName())) {
			scoreboard.removePlayerFromTeam(state.scoreboardName(), markTeam);
		}
		if (state.previousTeamName() != null) {
			PlayerTeam previous = scoreboard.getPlayerTeam(state.previousTeamName());
			if (previous != null) scoreboard.addPlayerToTeam(state.scoreboardName(), previous);
		}
	}

	private static void spawnProjectJjkExplosion(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(PROJECTJJK_CYAN, at.x, at.y + 0.1, at.z, 18, 0.52, 0.34, 0.52, 0.16);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at.x, at.y, at.z, 10, 0.28, 0.22, 0.28, 0.08);
		level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, at.x, at.y, at.z, 12, 0.34, 0.26, 0.34, 0.18);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y + 0.1, at.z, 6, 0.28, 0.14, 0.28, 0.04);
	}

	private static void playCasterSnap(ServerLevel level, ServerPlayer caster, int anchors, long gameTime) {
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP,
				SoundSource.PLAYERS, 2.0f, 1.0f);
		JujutsuNetworking.sendVfxCue(caster, VfxCues.anchored(NobaraVfxIds.FIRST_PERSON_SNAP,
				caster.getEyePosition(), caster.getId(), caster.position(), Math.max(1, anchors), gameTime,
				level.random.nextLong()));
	}

	private static ServerPlayer owner(ServerLevel level, UUID ownerId) {
		return ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static final class ChainContext {
		private final ServerLevel level;
		private final UUID casterId;
		private final List<HairpinNetwork.Node> ordered;
		private final Map<UUID, HairpinNetwork.Node> nodes;
		private int chainSuccesses;

		private ChainContext(ServerLevel level, UUID casterId, List<HairpinNetwork.Node> ordered,
				Map<UUID, HairpinNetwork.Node> nodes) {
			this.level = level;
			this.casterId = casterId;
			this.ordered = List.copyOf(ordered);
			this.nodes = Map.copyOf(nodes);
		}

		private ServerLevel level() { return level; }
		private UUID casterId() { return casterId; }
		private List<HairpinNetwork.Node> ordered() { return ordered; }
		private Map<UUID, HairpinNetwork.Node> nodes() { return nodes; }
		private int chainSuccesses() { return chainSuccesses; }
		private void noteSuccess() { chainSuccesses++; }
	}

	private record MarkGlowState(String scoreboardName, String previousTeamName) {}

	/** Keeps the translatable key in one place without adding a client dependency. */
	private static final class ComponentKeys {
		private ComponentKeys() {}
		private static net.minecraft.network.chat.Component noAnchors() {
			return net.minecraft.network.chat.Component.translatable("message.jujutsumod.nobara.hairpin.no_anchors");
		}
	}
}
