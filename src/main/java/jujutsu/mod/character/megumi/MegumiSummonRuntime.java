package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.VfxCue;

/** Owns every Divine Dog pack, including its single authoritative cross-level teardown. */
public final class MegumiSummonRuntime {
	private static final Map<UUID, MegumiDivineDogPack> PACKS = new ConcurrentHashMap<>();
	private static final Set<UUID> TEARDOWN_IN_PROGRESS = ConcurrentHashMap.newKeySet();
	private static final AtomicLong NEXT_SUMMON_TOKEN = new AtomicLong();

	private MegumiSummonRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiSummonRuntime::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof MegumiDivineDogEntity dog && dog.ownerUuid() != null) {
				reconcile(entity.getServer(), dog.ownerUuid(), RemovalCause.DEATH);
			} else if (entity instanceof ServerPlayer player) {
				teardown(player.getServer(), player.getUUID(), TeardownReason.OWNER_DEATH);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof MegumiDivineDogEntity dog && dog.ownerUuid() != null) {
				reconcile(level.getServer(), dog.ownerUuid(), RemovalCause.UNLOAD);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				teardown(newPlayer.getServer(), newPlayer.getUUID(), TeardownReason.RESPAWN));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				teardown(player.getServer(), player.getUUID(), TeardownReason.DIMENSION_CHANGE));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				teardown(server, handler.player.getUUID(), TeardownReason.DISCONNECT));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID ownerId : Set.copyOf(PACKS.keySet())) {
				teardown(server, ownerId, TeardownReason.SERVER_STOPPING);
			}
			PACKS.clear();
			TEARDOWN_IN_PROGRESS.clear();
		});
	}

	static long nextSummonToken() {
		return NEXT_SUMMON_TOKEN.incrementAndGet();
	}

	static MegumiDivineDogPack pack(UUID ownerId) {
		return PACKS.get(ownerId);
	}

	public static boolean tryToggle(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		MegumiDivineDogPack existing = PACKS.get(ownerId);
		long gameTime = player.level().getGameTime();
		if (existing != null) {
			if (existing.wasSummonedAt(gameTime)) {
				return true;
			}
			teardown(player.getServer(), ownerId, TeardownReason.RECALL);
			return true;
		}

		ServerLevel level = player.level();
		MegumiGroundSafety.SpawnPair positions = MegumiGroundSafety.findSummonPair(
				level, player.position(), player.getLookAngle(), JujutsuEntities.MEGUMI_DIVINE_DOG.getDimensions())
				.orElse(null);
		if (positions == null) {
			return rejectNoRoom(player, notify);
		}

		long token = nextSummonToken();
		MegumiDivineDogEntity white = createDog(level, player, token, positions.white(), WolfVariants.SNOWY, DyeColor.BLACK);
		MegumiDivineDogEntity black = createDog(level, player, token, positions.black(), WolfVariants.BLACK, DyeColor.WHITE);
		if (!level.addFreshEntity(white)) {
			black.discard();
			return rejectNoRoom(player, notify);
		}
		if (!level.addFreshEntity(black)) {
			teardown(player.getServer(), ownerId, TeardownReason.SUMMON_ROLLBACK);
			return rejectNoRoom(player, notify);
		}
		PACKS.put(ownerId, new MegumiDivineDogPack(
				level.dimension(), white.getUUID(), black.getUUID(), token, gameTime));
		white.playSummonSound();
		broadcastCue(level, player, MegumiVfxIds.DOGS_SUMMON, player.position(), VfxCue.NO_ANCHOR, Vec3.ZERO);
		return true;
	}

	public static boolean trySic(ServerPlayer player, boolean notify) {
		MegumiDivineDogPack pack = PACKS.get(player.getUUID());
		List<MegumiDivineDogEntity> dogs = pack == null ? List.of() : livingDogs(player.getServer(), player.getUUID(), pack);
		if (dogs.isEmpty()) {
			if (pack != null) {
				reconcile(player.getServer(), player.getUUID(), RemovalCause.TICK);
			}
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.dogs.none_out"), true);
			}
			return false;
		}

		ServerLevel level = player.level();
		TargetResolver.Result result = TargetResolver.resolve(
				level, player, MegumiProfile.SIC_RANGE, target -> isEligibleTarget(player, target));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return false;
		}
		Entity resolved = level.getEntity(result.entityId().get());
		if (!(resolved instanceof LivingEntity target)
				|| !isEligibleTarget(player, target)
				|| !player.hasLineOfSight(target)) {
			return false;
		}
		for (MegumiDivineDogEntity dog : dogs) {
			dog.setTarget(target);
		}
		MegumiDivineDogEntity voice = dogs.getFirst();
		voice.playSicSound();
		broadcastCue(level, player, MegumiVfxIds.DOGS_SIC, target.position(), target.getId(),
				new Vec3(0.0, target.getBbHeight() * 0.55, 0.0));
		startCooldownIfLonger(player, CharacterAbility.PRIMARY_SNEAK, MegumiProfile.SIC_COOLDOWN_TICKS);
		return true;
	}

	static boolean isEligibleTarget(LivingEntity owner, LivingEntity target) {
		return target != owner
				&& target.isAlive()
				&& !target.isRemoved()
				&& target.level() == owner.level()
				&& (!(target instanceof Player player) || !player.isSpectator())
				&& (!(target instanceof MegumiDivineDogEntity dog) || !owner.getUUID().equals(dog.ownerUuid()))
				&& !owner.isAlliedTo(target);
	}

	private static MegumiDivineDogEntity createDog(
			ServerLevel level,
			ServerPlayer owner,
			long token,
			Vec3 position,
			ResourceKey<WolfVariant> variantKey,
			DyeColor collar) {
		MegumiDivineDogEntity dog = new MegumiDivineDogEntity(JujutsuEntities.MEGUMI_DIVINE_DOG, level);
		dog.setPos(position);
		dog.setYRot(owner.getYRot());
		dog.setTame(true, false);
		dog.setOwner(owner);
		dog.configureSummon(owner.getUUID(), token);
		Holder<WolfVariant> variant = level.registryAccess()
				.lookupOrThrow(Registries.WOLF_VARIANT)
				.getOrThrow(variantKey);
		dog.setComponent(DataComponents.WOLF_VARIANT, variant);
		dog.setComponent(DataComponents.WOLF_COLLAR, collar);
		return dog;
	}

	private static boolean rejectNoRoom(ServerPlayer player, boolean notify) {
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.dogs.no_room"), true);
		}
		return false;
	}

	static boolean isCurrent(MegumiDivineDogEntity dog) {
		UUID ownerId = dog.ownerUuid();
		MegumiDivineDogPack pack = ownerId == null ? null : PACKS.get(ownerId);
		return pack != null && pack.contains(dog.getUUID(), dog.summonToken(), dog.level().dimension());
	}

	static void reconcile(MinecraftServer server, UUID ownerId, RemovalCause cause) {
		if (server == null || TEARDOWN_IN_PROGRESS.contains(ownerId)) {
			return;
		}
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		if (pack == null) {
			return;
		}
		List<MegumiDivineDogEntity> livingDogs = livingDogs(server, ownerId, pack);
		if (!livingDogs.isEmpty()) {
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			for (MegumiDivineDogEntity dog : livingDogs) {
				LivingEntity target = dog.getTarget();
				if (target != null && (owner == null || !isEligibleTarget(owner, target))) {
					dog.setTarget(null);
				}
			}
			return;
		}
		if (PACKS.remove(ownerId, pack)) {
			startCooldownIfLonger(server.getPlayerList().getPlayer(ownerId), CharacterAbility.PRIMARY,
					MegumiProfile.PACK_DEATH_COOLDOWN_TICKS);
		}
	}

	public static void teardown(MinecraftServer server, UUID ownerId, TeardownReason reason) {
		if (server == null || !TEARDOWN_IN_PROGRESS.add(ownerId)) {
			return;
		}
		MegumiDivineDogPack pack = PACKS.remove(ownerId);
		boolean foundDog = false;
		boolean playedRecall = false;
		try {
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiDivineDogEntity dog : new ArrayList<>(level.getEntities(
						EntityTypeTest.forClass(MegumiDivineDogEntity.class),
						candidate -> ownerId.equals(candidate.ownerUuid())))) {
					foundDog = true;
					if (reason == TeardownReason.RECALL && !playedRecall) {
						dog.playRecallSound();
						playedRecall = true;
					}
					dog.discard();
				}
			}
		} finally {
			TEARDOWN_IN_PROGRESS.remove(ownerId);
		}
		if (pack != null || foundDog) {
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (reason == TeardownReason.RECALL && owner != null) {
				ServerLevel level = owner.level();
				broadcastCue(level, owner, MegumiVfxIds.DOGS_RECALL, owner.position(), VfxCue.NO_ANCHOR, Vec3.ZERO);
			}
			startCooldownIfLonger(owner, CharacterAbility.PRIMARY, reason.cooldownTicks());
		}
	}

	private static void broadcastCue(
			ServerLevel level, ServerPlayer owner, ResourceLocation effectId,
			Vec3 origin, int anchorEntityId, Vec3 anchorOffset) {
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_CUE_RADIUS,
				new VfxCue(effectId, origin, anchorEntityId, anchorOffset, 1, level.getGameTime(),
						owner.getRandom().nextLong(), Vec3.ZERO));
	}

	private static void tick(MinecraftServer server) {
		for (UUID ownerId : Set.copyOf(PACKS.keySet())) {
			reconcile(server, ownerId, RemovalCause.TICK);
			MegumiDivineDogPack pack = PACKS.get(ownerId);
			if (pack != null) {
				recoverLeash(server, ownerId, pack);
			}
		}
	}

	private static void recoverLeash(MinecraftServer server, UUID ownerId, MegumiDivineDogPack pack) {
		ServerLevel level = server.getLevel(pack.dimension());
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (level == null || owner == null || owner.level() != level
				|| level.getGameTime() % MegumiProfile.LEASH_RETRY_TICKS != 0) {
			return;
		}
		double leashDistanceSquared = MegumiProfile.LEASH_DISTANCE * MegumiProfile.LEASH_DISTANCE;
		for (MegumiDivineDogEntity dog : livingDogs(server, ownerId, pack)) {
			if (dog.distanceToSqr(owner) <= leashDistanceSquared) {
				continue;
			}
			MegumiGroundSafety.findLeashPosition(level, owner.position(), dog).ifPresent(destination ->
					dog.teleportTo(level, destination.x, destination.y, destination.z, Set.<Relative>of(),
							dog.getYRot(), dog.getXRot(), false));
		}
	}

	private static boolean isLivePackDog(ServerLevel level, UUID dogId, UUID ownerId, long summonToken) {
		return level.getEntity(dogId) instanceof MegumiDivineDogEntity dog
				&& dog.isAlive()
				&& !dog.isRemoved()
				&& ownerId.equals(dog.ownerUuid())
				&& dog.summonToken() == summonToken;
	}

	private static List<MegumiDivineDogEntity> livingDogs(
			MinecraftServer server, UUID ownerId, MegumiDivineDogPack pack) {
		ServerLevel level = server.getLevel(pack.dimension());
		if (level == null) {
			return List.of();
		}
		return pack.dogIds().stream()
				.map(level::getEntity)
				.filter(MegumiDivineDogEntity.class::isInstance)
				.map(MegumiDivineDogEntity.class::cast)
				.filter(dog -> isLivePackDog(level, dog.getUUID(), ownerId, pack.summonToken()))
				.toList();
	}

	static void startCooldownIfLonger(ServerPlayer player, CharacterAbility ability, int durationTicks) {
		if (player == null || durationTicks <= CharacterAbilityCooldowns.remainingTicks(player, ability)) {
			return;
		}
		CharacterAbilityCooldowns.start(player, ability, durationTicks);
		JujutsuNetworking.sendAbilityCooldown(player, ability, durationTicks);
	}

	enum RemovalCause {
		TICK,
		DEATH,
		UNLOAD
	}

	public enum TeardownReason {
		RECALL(MegumiProfile.RECALL_COOLDOWN_TICKS),
		OWNER_DEATH(MegumiProfile.PACK_DEATH_COOLDOWN_TICKS),
		RESPAWN(0),
		DIMENSION_CHANGE(MegumiProfile.RECALL_COOLDOWN_TICKS),
		DISCONNECT(0),
		SERVER_STOPPING(0),
		DESELECTED(MegumiProfile.RECALL_COOLDOWN_TICKS),
		SUMMON_ROLLBACK(0);

		private final int cooldownTicks;

		TeardownReason(int cooldownTicks) {
			this.cooldownTicks = cooldownTicks;
		}

		int cooldownTicks() {
			return cooldownTicks;
		}
	}
}
