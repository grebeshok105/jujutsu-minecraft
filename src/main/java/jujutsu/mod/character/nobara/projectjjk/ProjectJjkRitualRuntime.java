package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.network.ProjectJjkCursedEnergyPayload;
import jujutsu.mod.network.ProjectJjkNobaraImpulsePayload;
import jujutsu.mod.network.ProjectJjkTargetMarkPayload;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;

/**
 * The beating heart of Nobara's Straw Doll RPG loop. Owns cursed-energy regeneration, the resonance
 * ritual (remote strike through walls), the doll->target bind, and Hairpin mark detonation, plus the
 * owner-only HUD sync. Server-authoritative; all visuals are broadcast as impulse payloads.
 */
public final class ProjectJjkRitualRuntime {
	private static final double IMPULSE_RADIUS = 64.0;
	private static final Map<UUID, Float> LAST_SENT_CE = new HashMap<>();

	private ProjectJjkRitualRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ProjectJjkRitualRuntime::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ProjectJjkCursedEnergy.reset(handler.player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ProjectJjkCursedEnergy.clear(id);
			ProjectJjkResonanceLink.clear(id);
			LAST_SENT_CE.remove(id);
		});
	}

	private static void onServerTick(net.minecraft.server.MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		if ((gameTime & 63L) == 0L) {
			ProjectJjkNailMarks.pruneExpired(gameTime);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			ProjectJjkCursedEnergy.regenerate(id, ProjectJjkNobaraProfile.CE_REGEN_PER_TICK);
			syncCursedEnergy(player, gameTime, false);
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
		long expiresGameTime = level.getGameTime() + ProjectJjkNobaraProfile.TARGET_MARK_RENDER_TICKS;
		JujutsuNetworking.broadcastProjectJjkTargetMark(level, at, IMPULSE_RADIUS, new ProjectJjkTargetMarkPayload(target.getId(), marks, expiresGameTime));
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, at.x, at.y, at.z, 2 + marks, 0.08, 0.10, 0.08, 0.022);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y, at.z, 2, 0.12, 0.14, 0.12, 0.018);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_SIZZLE, SoundSource.PLAYERS, 0.5f, 0.8f + marks * 0.06f);
		if (owner != null) {
			syncCursedEnergy(owner, level.getGameTime(), true);
		}
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
			syncCursedEnergy(caster, gameTime, true);
			return;
		}

		ProjectJjkResonanceLink.Link link = ProjectJjkResonanceLink.get(casterId);
		LivingEntity target = resolveLinked(level, link);
		if (target == null || !target.isAlive()) {
			ProjectJjkResonanceLink.clear(casterId);
			caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.resonance.lost"), true);
			return;
		}

		if (!ProjectJjkCursedEnergy.spend(casterId, ProjectJjkNobaraProfile.CE_COST_RESONANCE)) {
			caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.no_energy"), true);
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 0.5f, 0.5f);
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
		broadcast(level, at, ProjectJjkNobaraImpulsePayload.RESONANCE_STRIKE, marks, at, gameTime);
		JujutsuNetworking.sendProjectJjkImpulse(caster, impulse(ProjectJjkNobaraImpulsePayload.RESONANCE_CHANNEL, marks, caster.getEyePosition(), gameTime));
		ProjectJjkResonanceLink.clear(casterId);
		syncCursedEnergy(caster, gameTime, true);
	}

	// -- Hairpin mark detonation ----------------------------------------------------------------

	/** Detonates embedded marks on every marked living target in range; damage scales per mark. */
	public static boolean detonateMarks(ServerPlayer caster) {
		ServerLevel level = caster.level();
		long gameTime = level.getGameTime();
		net.minecraft.world.phys.AABB area = caster.getBoundingBox().inflate(ProjectJjkNobaraProfile.DETONATE_RANGE);
		DamageSource source = level.damageSources().indirectMagic(caster, caster);
		boolean any = false;
		for (Entity entity : level.getEntities(caster, area, e -> e instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			int marks = ProjectJjkNailMarks.marks(living.getUUID(), gameTime);
			if (marks <= 0) {
				continue;
			}
			if (!any && !ProjectJjkCursedEnergy.spend(caster.getUUID(), ProjectJjkNobaraProfile.CE_COST_DETONATE)) {
				caster.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.jujutsumod.projectjjk.no_energy"), true);
				return false;
			}
			any = true;
			ProjectJjkNailMarks.consume(living.getUUID(), gameTime);
			living.hurtServer(level, source, ProjectJjkNobaraProfile.detonateDamage(marks));
			Vec3 at = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
			spawnResonanceStrike(level, at, marks);
			level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_EXPLODE, SoundSource.PLAYERS, 0.95f, 0.9f);
			JujutsuNetworking.broadcastProjectJjkTargetMark(level, at, IMPULSE_RADIUS, new ProjectJjkTargetMarkPayload(living.getId(), 0, gameTime));
			broadcast(level, at, ProjectJjkNobaraImpulsePayload.DETONATE, marks, at, gameTime);
		}
		if (any) {
			syncCursedEnergy(caster, gameTime, true);
		}
		return any;
	}

	public static boolean canAfford(ServerPlayer player, float cost) {
		return player.getAbilities().instabuild || ProjectJjkCursedEnergy.has(player.getUUID(), cost);
	}

	public static boolean spend(ServerPlayer player, float cost) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		boolean ok = ProjectJjkCursedEnergy.spend(player.getUUID(), cost);
		if (ok) {
			syncCursedEnergy(player, player.level().getGameTime(), true);
		}
		return ok;
	}

	// -- Helpers --------------------------------------------------------------------------------

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

	private static void syncCursedEnergy(ServerPlayer player, long gameTime, boolean force) {
		UUID id = player.getUUID();
		float current = ProjectJjkCursedEnergy.get(id);
		Float last = LAST_SENT_CE.get(id);
		boolean heartbeat = (gameTime % 10L) == 0L;
		if (!force && !heartbeat && last != null && Math.abs(last - current) < 0.5f) {
			return;
		}
		LAST_SENT_CE.put(id, current);
		boolean linked = ProjectJjkResonanceLink.isValid(id, gameTime);
		int linkedMarks = 0;
		if (linked) {
			ProjectJjkResonanceLink.Link link = ProjectJjkResonanceLink.get(id);
			if (link != null) {
				linkedMarks = ProjectJjkNailMarks.marks(link.targetId(), gameTime);
			}
		}
		JujutsuNetworking.sendCursedEnergy(player, new ProjectJjkCursedEnergyPayload(current, ProjectJjkNobaraProfile.CE_MAX, linkedMarks, linked));
	}

	private static void broadcast(ServerLevel level, Vec3 center, int kind, int marks, Vec3 at, long gameTime) {
		JujutsuNetworking.broadcastProjectJjkImpulse(level, center, IMPULSE_RADIUS, impulse(kind, marks, at, gameTime));
	}

	private static ProjectJjkNobaraImpulsePayload impulse(int kind, int marks, Vec3 at, long gameTime) {
		return new ProjectJjkNobaraImpulsePayload(kind, marks, at.x, at.y, at.z, gameTime);
	}
}
