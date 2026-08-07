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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative Shadow Drop (Megumi's V): a small shadow zone opens above the aimed target's
 * head, follows the target for a one-second telegraph, then drops one weighted falling block out of
 * it — sand, gravel, clay, or (rarely) an anvil.
 *
 * <p>The payload is a vanilla {@link FallingBlockEntity}, which is the whole trick: rendering,
 * physics, crush damage and helmet/armor interactions all come from the base game. The entity never
 * places a block or drops an item ({@code disableDrop()}), so the ability leaves no litter in the
 * world no matter where the block lands.
 *
 * <p>Unlike the trap, this zone has no fixed centre — it re-anchors over the live target on every
 * pulse. State is therefore the target, not a point, and the drop point is computed at the moment
 * the telegraph expires, exactly where the disc is hovering then.
 */
public final class MegumiShadowDropRuntime {
	private static final Map<UUID, ShadowDrop> DROPS = new ConcurrentHashMap<>();

	/** One telegraphing zone. The clock is fixed at cast; the anchor follows the target. */
	record ShadowDrop(ResourceKey<Level> dimension, UUID targetId, long castGameTime) {
		long dropsAtGameTime() {
			return castGameTime + MegumiProfile.DROP_TELEGRAPH_TICKS;
		}
	}

	private MegumiShadowDropRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiShadowDropRuntime::tick);
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
			for (UUID ownerId : Map.copyOf(DROPS).keySet()) {
				clear(server, ownerId, false);
			}
			DROPS.clear();
		});
	}

	public static boolean tryCast(ServerPlayer player, boolean notify) {
		if (DROPS.containsKey(player.getUUID())) {
			// The cooldown is far longer than the telegraph, so this is reachable only through a cooldown
			// clear; refuse rather than orphan the live zone by overwriting it.
			return reject(player, notify, "message.jujutsumod.megumi.drop.already_open");
		}
		ServerLevel level = player.level();
		TargetResolver.Result result = TargetResolver.resolve(
				level, player, MegumiProfile.DROP_RANGE,
				candidate -> MegumiSummonRuntime.isEligibleTarget(player, candidate));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return reject(player, notify, "message.jujutsumod.megumi.drop.no_target");
		}
		Entity resolved = level.getEntity(result.entityId().get());
		if (!(resolved instanceof LivingEntity target)
				|| !MegumiSummonRuntime.isEligibleTarget(player, target)
				|| !player.hasLineOfSight(target)) {
			return reject(player, notify, "message.jujutsumod.megumi.drop.no_target");
		}

		long gameTime = level.getGameTime();
		Vec3 anchor = anchorAbove(target);
		DROPS.put(player.getUUID(), new ShadowDrop(level.dimension(), target.getUUID(), gameTime));
		broadcastCue(level, player, MegumiVfxIds.DROP_ZONE_OPEN, anchor);
		broadcastCue(level, player, MegumiVfxIds.DROP_ZONE, anchor);
		level.playSound(null, anchor.x, anchor.y, anchor.z, JujutsuSounds.PROJECTJJK_WHOOSH_VORTEX,
				SoundSource.PLAYERS, 0.8f, 0.9f);
		MegumiSummonRuntime.startCooldownIfLonger(player, CharacterAbility.TERTIARY,
				MegumiProfile.DROP_COOLDOWN_TICKS);
		return true;
	}

	/** The hovering point: one zone-height above the top of the target's head, tracked live. */
	private static Vec3 anchorAbove(LivingEntity target) {
		return new Vec3(target.getX(),
				target.getY() + target.getBbHeight() + MegumiProfile.DROP_ZONE_HEIGHT_BLOCKS,
				target.getZ());
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, ShadowDrop> entry : Map.copyOf(DROPS).entrySet()) {
			UUID ownerId = entry.getKey();
			ShadowDrop drop = entry.getValue();
			ServerLevel level = server.getLevel(drop.dimension());
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			LivingEntity target = level == null ? null : liveTarget(level, drop.targetId());
			if (level == null || owner == null || target == null) {
				// The telegraph dies with its target (or owner); the cooldown already ran from the cast.
				closeZone(server, ownerId, drop, anchorOf(level, drop));
				continue;
			}
			long gameTime = level.getGameTime();
			Vec3 anchor = anchorAbove(target);
			if (gameTime >= drop.dropsAtGameTime()) {
				releaseBlocks(level, anchor);
				closeZone(server, ownerId, drop, anchor);
				continue;
			}
			long age = gameTime - drop.castGameTime();
			if (age > 0 && age % MegumiProfile.DROP_ZONE_PULSE_TICKS == 0) {
				broadcastCue(level, owner, MegumiVfxIds.DROP_ZONE, anchor);
			}
		}
	}

	/** A target that died, despawned or left this level ends the telegraph early. */
	private static LivingEntity liveTarget(ServerLevel level, UUID targetId) {
		Entity entity = level.getEntity(targetId);
		if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
			return null;
		}
		return living;
	}

	private static void releaseBlocks(ServerLevel level, Vec3 anchor) {
		RandomSource random = level.getRandom();
		int count = MegumiProfile.DROP_MIN_BLOCKS
				+ random.nextInt(MegumiProfile.DROP_MAX_BLOCKS - MegumiProfile.DROP_MIN_BLOCKS + 1);
		for (int i = 0; i < count; i++) {
			// The first block falls dead-centre; the rest scatter inside the disc so a volley reads
			// as a volley instead of one triple-height stack.
			Vec3 point = i == 0 ? anchor : scatter(anchor, random);
			releaseOne(level, point, random);
		}
		level.playSound(null, anchor.x, anchor.y, anchor.z, JujutsuSounds.PROJECTJJK_CINEMATIC_WHOOSH,
				SoundSource.PLAYERS, 0.7f, 1.05f);
	}

	private static Vec3 scatter(Vec3 anchor, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = random.nextDouble() * MegumiProfile.DROP_SCATTER_RADIUS;
		return anchor.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
	}

	private static void releaseOne(ServerLevel level, Vec3 point, RandomSource random) {
		BlockPos spawn = spawnPosFor(level, point);
		if (spawn == null) {
			// The column above the target is sealed; skip the block rather than delete the ceiling.
			return;
		}
		BlockState state = pickBlock(random);
		FallingBlockEntity block = FallingBlockEntity.fall(level, spawn, state);
		block.disableDrop();
		if (state.is(Blocks.ANVIL)) {
			block.setHurtsEntities(MegumiProfile.DROP_ANVIL_DAMAGE_PER_BLOCK, MegumiProfile.DROP_ANVIL_DAMAGE_MAX);
		} else {
			block.setHurtsEntities(MegumiProfile.DROP_SOFT_DAMAGE_PER_BLOCK, MegumiProfile.DROP_SOFT_DAMAGE_MAX);
		}
	}

	/**
	 * {@code FallingBlockEntity.fall} unconditionally replaces the block at its spawn position —
	 * that is how vanilla lifts sand off the ground — so a synthetic drop must only ever spawn
	 * where there is nothing real to replace. Walks down from the hover point toward the target's
	 * head and returns the first dry, replaceable position; null when the whole column is sealed
	 * (a cast under a solid ceiling), which skips the block instead of deleting world geometry.
	 * Under a low ceiling the volley therefore spawns below it and still falls.
	 */
	private static BlockPos spawnPosFor(ServerLevel level, Vec3 point) {
		BlockPos top = BlockPos.containing(point);
		for (int dy = 0; dy < (int) MegumiProfile.DROP_ZONE_HEIGHT_BLOCKS; dy++) {
			BlockPos pos = top.below(dy);
			BlockState state = level.getBlockState(pos);
			if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** Weighted table; weights sum to 100, so each weight reads as a percent chance. */
	private static BlockState pickBlock(RandomSource random) {
		int roll = random.nextInt(MegumiProfile.DROP_WEIGHT_SAND + MegumiProfile.DROP_WEIGHT_GRAVEL
				+ MegumiProfile.DROP_WEIGHT_CLAY + MegumiProfile.DROP_WEIGHT_ANVIL);
		if ((roll -= MegumiProfile.DROP_WEIGHT_SAND) < 0) {
			return Blocks.SAND.defaultBlockState();
		}
		if ((roll -= MegumiProfile.DROP_WEIGHT_GRAVEL) < 0) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if ((roll -= MegumiProfile.DROP_WEIGHT_CLAY) < 0) {
			return Blocks.CLAY.defaultBlockState();
		}
		return Blocks.ANVIL.defaultBlockState();
	}

	/** Whether the owner has a live shadow drop zone. Exists for the dev control surface + gametests. */
	public static boolean hasOwned(UUID ownerId) {
		return DROPS.containsKey(ownerId);
	}

	/** Clears the owner's shadow drop zone, if any, with its close cue. Exists for the dev control surface + gametests. */
	public static void clearOwned(MinecraftServer server, UUID ownerId) {
		clear(server, ownerId, true);
	}

	static void clear(MinecraftServer server, UUID ownerId, boolean withCue) {
		if (server == null) {
			return;
		}
		ShadowDrop drop = DROPS.get(ownerId);
		if (drop != null) {
			ServerLevel level = server.getLevel(drop.dimension());
			closeZone(server, ownerId, drop, withCue ? anchorOf(level, drop) : null);
		}
	}

	/**
	 * Where the close cue should play: over the target's body if it is still present. A corpse
	 * mid death-animation still anchors the collapse beat — only a fully removed or cross-level
	 * target leaves the zone nowhere to close over.
	 */
	private static Vec3 anchorOf(ServerLevel level, ShadowDrop drop) {
		if (level == null) {
			return null;
		}
		return level.getEntity(drop.targetId()) instanceof LivingEntity living ? anchorAbove(living) : null;
	}

	private static void closeZone(MinecraftServer server, UUID ownerId, ShadowDrop drop, Vec3 anchor) {
		if (!DROPS.remove(ownerId, drop)) {
			return;
		}
		ServerLevel level = server.getLevel(drop.dimension());
		if (level == null || anchor == null) {
			return;
		}
		JujutsuNetworking.broadcastVfxCue(level, anchor, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(MegumiVfxIds.DROP_ZONE_CLOSE, anchor, radiusIntensity(),
						level.getGameTime(), level.getRandom().nextLong()));
	}

	/** The zone radius rides in the cue's intensity so the recipes never re-state the profile. */
	private static int radiusIntensity() {
		return (int) Math.round(MegumiProfile.DROP_ZONE_RADIUS * 10.0);
	}

	private static void broadcastCue(ServerLevel level, ServerPlayer owner, ResourceLocation effectId, Vec3 origin) {
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(effectId, origin, radiusIntensity(), level.getGameTime(),
						owner.getRandom().nextLong()));
	}

	private static boolean reject(ServerPlayer player, boolean notify, String messageKey) {
		if (notify) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
		return false;
	}
}
