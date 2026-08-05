package jujutsu.mod.character.megumi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative Shadow Trap (Megumi's B): one static pool of shadow per owner, opened under
 * the aimed target, gripping every eligible body whose feet stand inside it.
 *
 * <p>The grip itself is {@code megumi_shadow_grip}, re-applied for a few ticks at a time while a
 * body stays inside; everything a gripped body loses rides on the effect's vanilla attribute
 * modifiers, so a body that leaves the pool — or a trap that dies with the server — needs no manual
 * effect cleanup at all. The dogs get nothing artificial from the pool: no teleport, no forced
 * pounce, just a slower target.
 */
public final class MegumiShadowTrapRuntime {
	private static final Map<UUID, ShadowTrap> TRAPS = new ConcurrentHashMap<>();

	/** One live pool. The centre never moves; only the clock does. */
	record ShadowTrap(ResourceKey<Level> dimension, Vec3 center, long placedAtGameTime, long expiresAtGameTime) {}

	private MegumiShadowTrapRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiShadowTrapRuntime::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				clear(player.getServer(), player.getUUID(), true);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				clear(newPlayer.getServer(), newPlayer.getUUID(), true));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				clear(player.getServer(), player.getUUID(), true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				clear(server, handler.player.getUUID(), true));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID ownerId : Map.copyOf(TRAPS).keySet()) {
				clear(server, ownerId, false);
			}
			TRAPS.clear();
		});
	}

	public static boolean tryCast(ServerPlayer player, boolean notify) {
		if (TRAPS.containsKey(player.getUUID())) {
			// The cooldown outlives the pool, so this is reachable only through a cooldown clear; refuse
			// rather than silently re-anchor the live pool.
			return reject(player, notify, "message.jujutsumod.megumi.trap.already_open");
		}
		ServerLevel level = player.level();
		TargetResolver.Result result = TargetResolver.resolve(
				level, player, MegumiProfile.SHADOW_TRAP_RANGE,
				candidate -> MegumiSummonRuntime.isEligibleTarget(player, candidate));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return reject(player, notify, "message.jujutsumod.megumi.trap.no_target");
		}
		Entity resolved = level.getEntity(result.entityId().get());
		if (!(resolved instanceof LivingEntity target)
				|| !MegumiSummonRuntime.isEligibleTarget(player, target)
				|| !player.hasLineOfSight(target)) {
			return reject(player, notify, "message.jujutsumod.megumi.trap.no_target");
		}

		long gameTime = level.getGameTime();
		Vec3 center = snapToGround(level, target);
		TRAPS.put(player.getUUID(), new ShadowTrap(
				level.dimension(), center, gameTime, gameTime + MegumiProfile.SHADOW_TRAP_DURATION_TICKS));
		broadcastCue(level, player, MegumiVfxIds.SHADOW_TRAP_OPEN, center, radiusIntensity());
		broadcastCue(level, player, MegumiVfxIds.SHADOW_TRAP_ZONE, center, radiusIntensity());
		level.playSound(null, center.x, center.y, center.z, JujutsuSounds.PROJECTJJK_GOO_FOLEY,
				SoundSource.PLAYERS, 0.9f, 0.72f);
		MegumiSummonRuntime.startCooldownIfLonger(player, CharacterAbility.SECONDARY,
				MegumiProfile.SHADOW_TRAP_COOLDOWN_TICKS);
		return true;
	}

	/** The pool opens on the ground under an airborne target's feet, never in mid-air. */
	private static Vec3 snapToGround(ServerLevel level, LivingEntity target) {
		Vec3 feet = target.position();
		HitResult hit = level.clip(new ClipContext(
				feet, feet.add(0.0, -MegumiProfile.SHADOW_TRAP_GROUND_SNAP_BLOCKS, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : feet;
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, ShadowTrap> entry : Map.copyOf(TRAPS).entrySet()) {
			UUID ownerId = entry.getKey();
			ShadowTrap trap = entry.getValue();
			ServerLevel level = server.getLevel(trap.dimension());
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			long gameTime = level == null ? 0L : level.getGameTime();
			if (level == null || owner == null || gameTime >= trap.expiresAtGameTime()
					|| !level.getChunkSource().hasChunk(
							BlockPos.containing(trap.center()).getX() >> 4,
							BlockPos.containing(trap.center()).getZ() >> 4)) {
				// Owner-loss paths also arrive here one tick before their event fires; both roads close the pool.
				closeTrap(server, ownerId, trap, level != null);
				continue;
			}
			long age = gameTime - trap.placedAtGameTime();
			if (age > 0 && age % MegumiProfile.SHADOW_TRAP_ZONE_PULSE_TICKS == 0) {
				broadcastCue(level, owner, MegumiVfxIds.SHADOW_TRAP_ZONE, trap.center(), radiusIntensity());
			}
			gripBodies(level, owner, trap, gameTime);
		}
	}

	private static void gripBodies(ServerLevel level, ServerPlayer owner, ShadowTrap trap, long gameTime) {
		AABB search = new AABB(trap.center(), trap.center()).inflate(
				MegumiProfile.SHADOW_TRAP_RADIUS, MegumiProfile.SHADOW_TRAP_VERTICAL_REACH, MegumiProfile.SHADOW_TRAP_RADIUS);
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, search,
				candidate -> MegumiSummonRuntime.isEligibleTarget(owner, candidate))) {
			if (!insideCylinder(trap.center(), body.position())) {
				continue;
			}
			body.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SHADOW_GRIP,
					MegumiProfile.SHADOW_TRAP_GRIP_REFRESH_TICKS, 0, true, false, true), owner);
			if (gameTime % MegumiProfile.SHADOW_TRAP_GRIP_CUE_PERIOD_TICKS == 0) {
				JujutsuNetworking.broadcastVfxCue(level, body.position(), MegumiProfile.VFX_DELIVERY_RADIUS,
						VfxCues.anchored(MegumiVfxIds.SHADOW_TRAP_GRIP, body.position(), body.getId(),
								body.position(), 1, gameTime, owner.getRandom().nextLong()));
			}
		}
	}

	static boolean insideCylinder(Vec3 center, Vec3 feet) {
		double dx = feet.x - center.x;
		double dz = feet.z - center.z;
		double dy = feet.y - center.y;
		return dx * dx + dz * dz <= MegumiProfile.SHADOW_TRAP_RADIUS * MegumiProfile.SHADOW_TRAP_RADIUS
				&& dy >= -1.0
				&& dy <= MegumiProfile.SHADOW_TRAP_VERTICAL_REACH;
	}

	static void clear(MinecraftServer server, UUID ownerId, boolean withCue) {
		if (server == null) {
			return;
		}
		ShadowTrap trap = TRAPS.get(ownerId);
		if (trap != null) {
			closeTrap(server, ownerId, trap, withCue);
		}
	}

	private static void closeTrap(MinecraftServer server, UUID ownerId, ShadowTrap trap, boolean withCue) {
		if (!TRAPS.remove(ownerId, trap)) {
			return;
		}
		ServerLevel level = server.getLevel(trap.dimension());
		if (level == null || !withCue) {
			return;
		}
		JujutsuNetworking.broadcastVfxCue(level, trap.center(), MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(MegumiVfxIds.SHADOW_TRAP_CLOSE, trap.center(), radiusIntensity(),
						level.getGameTime(), level.getRandom().nextLong()));
		level.playSound(null, trap.center().x, trap.center().y, trap.center().z,
				JujutsuSounds.PROJECTJJK_IMPLODE, SoundSource.PLAYERS, 0.7f, 0.9f);
	}

	/** The zone radius rides in the cue's intensity so the recipes never re-state the profile. */
	private static int radiusIntensity() {
		return (int) Math.round(MegumiProfile.SHADOW_TRAP_RADIUS * 10.0);
	}

	private static void broadcastCue(
			ServerLevel level, ServerPlayer owner, ResourceLocation effectId, Vec3 origin, int intensity) {
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(effectId, origin, intensity, level.getGameTime(), owner.getRandom().nextLong()));
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey) {
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
