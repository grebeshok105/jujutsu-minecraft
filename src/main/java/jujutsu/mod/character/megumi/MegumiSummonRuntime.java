package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.HashSet;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.VfxCues;

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
			Set<UUID> ownerIds = new HashSet<>(PACKS.keySet());
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiDivineDogEntity dog : level.getEntities(
						EntityTypeTest.forClass(MegumiDivineDogEntity.class), candidate -> true)) {
					if (dog.ownerUuid() != null) {
						ownerIds.add(dog.ownerUuid());
					}
				}
			}
			for (UUID ownerId : ownerIds) {
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
			if (MegumiSummonState.isSameTickDuplicate(existing, gameTime)) {
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
		white.playShadowOpenSound();
		black.playShadowOpenSound();
		broadcastCue(level, player, MegumiVfxIds.DOGS_SUMMON_BODY, player.position(), player.getId(), Vec3.ZERO);
		broadcastDogCue(level, player, MegumiVfxIds.DOGS_SUMMON, white);
		broadcastDogCue(level, player, MegumiVfxIds.DOGS_SUMMON, black);
		return true;
	}

	public static boolean trySic(ServerPlayer player, boolean notify) {
		MegumiDivineDogPack pack = PACKS.get(player.getUUID());
		List<MegumiDivineDogEntity> livingPackDogs =
				pack == null ? List.of() : livingDogs(player.getServer(), player.getUUID(), pack);
		if (livingPackDogs.isEmpty()) {
			if (pack != null) {
				reconcile(player.getServer(), player.getUUID(), RemovalCause.TICK);
			}
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.dogs.none_out"), true);
			}
			return false;
		}
		List<MegumiDivineDogEntity> dogs = livingPackDogs.stream()
				.filter(MegumiDivineDogEntity::acceptsSicCommand)
				.toList();
		if (dogs.isEmpty()) {
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
			dog.assignSicTarget(target);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SNAP,
				SoundSource.PLAYERS, 0.66f, 0.88f);
		MegumiDivineDogEntity voice = dogs.getFirst();
		voice.playSicSound();
		broadcastCue(level, player, MegumiVfxIds.DOGS_SIC, target.position(), target.getId(),
				new Vec3(0.0, target.getBbHeight() * 0.55, 0.0));
		startCooldownIfLonger(player, CharacterAbility.PRIMARY_SNEAK, MegumiProfile.SIC_COOLDOWN_TICKS);
		return true;
	}

	static boolean isEligibleTarget(LivingEntity owner, LivingEntity target) {
		return MegumiTargetPolicy.accepts(new MegumiTargetPolicy.Facts(
				target == owner,
				target.isAlive(),
				!target.isRemoved(),
				target.level() == owner.level(),
				target instanceof Player player && player.isSpectator(),
				target instanceof MegumiDivineDogEntity dog && owner.getUUID().equals(dog.ownerUuid()),
				owner.isAlliedTo(target)));
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

	static boolean shouldHardDiscard(MegumiDivineDogEntity dog) {
		UUID ownerId = dog.ownerUuid();
		if (ownerId == null) {
			return true;
		}
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		if (pack == null && dog.canFinishRecallWithoutPack()) {
			return false;
		}
		return !MegumiSummonState.belongsToPack(
				pack, dog.getUUID(), dog.summonToken(), dog.level().dimension());
	}

	static void reconcile(MinecraftServer server, UUID ownerId, RemovalCause cause) {
		if (server == null) {
			return;
		}
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		if (MegumiLifecyclePolicy.reconcileAction(
				TEARDOWN_IN_PROGRESS.contains(ownerId), pack != null, 0)
				== MegumiLifecyclePolicy.ReconcileAction.IGNORE) {
			return;
		}
		List<MegumiDivineDogEntity> livingDogs = livingDogs(server, ownerId, pack);
		MegumiLifecyclePolicy.ReconcileAction action = MegumiLifecyclePolicy.reconcileAction(
				false, true, livingDogs.size());
		if (action == MegumiLifecyclePolicy.ReconcileAction.RETAIN) {
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			for (MegumiDivineDogEntity dog : livingDogs) {
				LivingEntity target = dog.getTarget();
				if (target != null && (owner == null || !isEligibleTarget(owner, target))) {
					dog.clearSicCommand();
					dog.setTarget(null);
				}
			}
			return;
		}
		if (PACKS.remove(ownerId, pack)) {
			startCooldownIfLonger(server.getPlayerList().getPlayer(ownerId), CharacterAbility.PRIMARY,
					MegumiCooldownPolicy.duration(MegumiCooldownPolicy.Cause.FINAL_LOSS));
		}
	}

	public static void teardown(MinecraftServer server, UUID ownerId, TeardownReason reason) {
		if (server == null || !TEARDOWN_IN_PROGRESS.add(ownerId)) {
			return;
		}
		MegumiDivineDogPack pack = PACKS.remove(ownerId);
		ServerPlayer owner = reason == TeardownReason.RECALL
				? server.getPlayerList().getPlayer(ownerId)
				: null;
		boolean foundCooldownOwningDog = false;
		boolean playedRecall = false;
		try {
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiDivineDogEntity dog : new ArrayList<>(level.getEntities(
						EntityTypeTest.forClass(MegumiDivineDogEntity.class),
							candidate -> ownerId.equals(candidate.ownerUuid())))) {
					if (MegumiLifecyclePolicy.dogOwnsTeardownCooldown(dog.presentationPhase())) {
						foundCooldownOwningDog = true;
					}
					boolean belongedToRemovedPack = MegumiSummonState.belongsToPack(
							pack, dog.getUUID(), dog.summonToken(), dog.level().dimension());
					MegumiLifecyclePolicy.DogCleanupAction cleanupAction = MegumiLifecyclePolicy
							.dogCleanupAction(reason == TeardownReason.RECALL, belongedToRemovedPack);
					if (cleanupAction == MegumiLifecyclePolicy.DogCleanupAction.BEGIN_RECALL) {
						if (owner != null) {
							broadcastDogCue(level, owner, MegumiVfxIds.DOGS_RECALL, dog);
						}
						if (!playedRecall) {
							dog.playRecallSound();
							playedRecall = true;
						}
						dog.beginRecall();
					} else {
						dog.discard();
					}
				}
			}
		} finally {
			TEARDOWN_IN_PROGRESS.remove(ownerId);
		}
		if (MegumiLifecyclePolicy.shouldApplyTeardownCooldown(pack != null, foundCooldownOwningDog)) {
			startCooldownIfLonger(server.getPlayerList().getPlayer(ownerId), CharacterAbility.PRIMARY, reason.cooldownTicks());
		}
	}

	private static void broadcastDogCue(
			ServerLevel level, ServerPlayer owner, ResourceLocation effectId, MegumiDivineDogEntity dog) {
		broadcastCue(level, owner, effectId, dog.position(), dog.getId(), Vec3.ZERO);
	}

	private static void broadcastCue(
			ServerLevel level, ServerPlayer owner, ResourceLocation effectId,
			Vec3 origin, int anchorEntityId, Vec3 anchorOffset) {
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredWithOffset(effectId, origin, anchorEntityId, anchorOffset, 1,
						level.getGameTime(), owner.getRandom().nextLong()));
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

	static void tickPounce(MegumiDivineDogEntity dog) {
		if (!(dog.level() instanceof ServerLevel level)) {
			return;
		}
		UUID ownerId = dog.ownerUuid();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		LivingEntity currentTarget = dog.getTarget();
		UUID assignedId = dog.sicTargetUuid();
		LivingEntity assignedTarget = assignedId == null ? null : resolveLiving(level, assignedId);
		boolean currentPack = ownerId != null && isCurrentPackDog(dog, ownerId);
		boolean active = dog.presentationPhase() == MegumiDogPresentationPolicy.Phase.ACTIVE;
		boolean validOwner = isValidPounceOwner(owner, level);
		boolean assignedMatches = assignedTarget != null && assignedId.equals(assignedTarget.getUUID());
		boolean currentMatches = assignedTarget != null && currentTarget == assignedTarget;
		boolean eligible = validOwner && assignedTarget != null && isEligibleTarget(owner, assignedTarget);

		long gameTime = level.getGameTime();
		if (dog.pounceInFlight()) {
			int elapsedTicks = (int) Math.max(0L, gameTime - dog.pounceStartedGameTime());
			MegumiPouncePolicy.InFlightAction action = MegumiPouncePolicy.inFlightAction(
					new MegumiPouncePolicy.InFlightFacts(
							active,
							currentPack,
							validOwner,
							assignedMatches,
							currentMatches,
							eligible,
							assignedId != null && assignedId.equals(dog.pounceTargetUuid()),
							MegumiPouncePolicy.timedOut(gameTime, dog.pounceDeadlineGameTime())));
			switch (action) {
				case CLEAR_SIC -> dog.clearSicCommand();
				case FINISH_POUNCE -> {
					dog.finishPounce();
					dog.resumeNavigation();
				}
				case CONTINUE -> {
					Vec3 beforeMove = dog.position();
					Vec3 flightVelocity = MegumiPouncePolicy.steerVelocity(
							dog.getDeltaMovement(), beforeMove, assignedTarget.position())
							.add(0.0, -MegumiProfile.POUNCE_GRAVITY, 0.0);
					dog.setDeltaMovement(flightVelocity);
					dog.move(MoverType.SELF, flightVelocity);
					if (flightVelocity.horizontalDistanceSqr() > 1.0E-6) {
						dog.setYRot((float) (Mth.atan2(flightVelocity.x, flightVelocity.z) * Mth.RAD_TO_DEG));
					}
					boolean crossedTarget = assignedTarget != null
							&& (dog.getBoundingBox().inflate(0.30).intersects(assignedTarget.getBoundingBox())
									|| assignedTarget.getBoundingBox().inflate(0.30).clip(beforeMove, dog.position()).isPresent());
					if (crossedTarget) {
						resolvePounceImpact(level, dog, owner, assignedTarget, flightVelocity);
						return;
					}
					MegumiPouncePolicy.FlightAction flightAction = MegumiPouncePolicy.flightAction(
							dog.horizontalCollision, dog.verticalCollision, dog.onGround(), elapsedTicks);
					if (flightAction == MegumiPouncePolicy.FlightAction.FINISH_POUNCE) {
						dog.finishPounce();
						dog.resumeNavigation();
					}
				}
			}
			return;
		}

		if (!active || !currentPack || !validOwner || !assignedMatches || !currentMatches || !eligible) {
			dog.clearSicCommand();
			return;
		}

		double distance = dog.distanceTo(assignedTarget);
		MegumiPouncePolicy.LaunchFacts facts = new MegumiPouncePolicy.LaunchFacts(
				active,
				currentPack,
				validOwner,
				currentMatches,
				eligible,
				dog.hasLineOfSight(assignedTarget),
				distance,
				dog.pounceReady(gameTime));
		if (!MegumiPouncePolicy.canLaunch(facts)) {
			return;
		}
		Vec3 velocity = MegumiPouncePolicy.launchVelocity(dog.position(), assignedTarget.position());
		if (velocity.lengthSqr() < 1.0E-6) {
			return;
		}
		dog.launchPounce(assignedTarget, gameTime, velocity);
	}

	private static void resolvePounceImpact(
			ServerLevel level, MegumiDivineDogEntity dog, ServerPlayer owner, LivingEntity target,
			Vec3 impactVelocity) {
		boolean validImpact = isValidPounceOwner(owner, level)
				&& target.isAlive() && !target.isRemoved() && target.level() == level
				&& dog.getTarget() == target && target.getUUID().equals(dog.sicTargetUuid())
				&& isEligibleTarget(owner, target);
		Vec3 exitVelocity = new Vec3(impactVelocity.x, 0.0, impactVelocity.z)
				.scale(MegumiProfile.POUNCE_EXIT_DAMPING);
		dog.finishPounce(exitVelocity);
		if (!validImpact) {
			return;
		}
		dog.resumeNavigation();
		float damage = (float) MegumiProfile.DOG_ATTACK_DAMAGE + MegumiProfile.POUNCE_BONUS_DAMAGE;
		if (!target.hurtServer(level, level.damageSources().playerAttack(owner), damage)) {
			return;
		}
		Vec3 direction = new Vec3(impactVelocity.x, 0.0, impactVelocity.z);
		if (direction.lengthSqr() <= 1.0E-6) {
			direction = target.position().subtract(dog.position()).multiply(1.0, 0.0, 1.0);
		}
		if (direction.lengthSqr() > 1.0E-6) {
			target.knockback(MegumiProfile.POUNCE_KNOCKBACK, -direction.x, -direction.z);
		}
		CombatStagger.GLOBAL.apply(target, level.getGameTime(), MegumiProfile.POUNCE_STAGGER_TICKS);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), JujutsuSounds.PROJECTJJK_AEC_BOOM,
				SoundSource.PLAYERS, 0.72f, 1.08f);
		broadcastCue(level, owner, MegumiVfxIds.DOGS_POUNCE, target.position(), target.getId(),
				new Vec3(0.0, target.getBbHeight() * 0.45, 0.0));
	}

	private static LivingEntity resolveLiving(ServerLevel level, UUID entityId) {
		Entity entity = level.getEntity(entityId);
		return entity instanceof LivingEntity living ? living : null;
	}

	private static boolean isValidPounceOwner(ServerPlayer owner, ServerLevel level) {
		return owner != null && owner.isAlive() && !owner.isRemoved() && owner.level() == level;
	}

	private static boolean isCurrentPackDog(MegumiDivineDogEntity dog, UUID ownerId) {
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		return MegumiSummonState.belongsToPack(
				pack, dog.getUUID(), dog.summonToken(), dog.level().dimension());
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
		if (player == null) {
			return;
		}
		int remaining = CharacterAbilityCooldowns.remainingTicks(player, ability);
		if (MegumiCooldownPolicy.preservedRemaining(remaining, durationTicks) == remaining) {
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
		RECALL(MegumiCooldownPolicy.Cause.RECALL),
		OWNER_DEATH(MegumiCooldownPolicy.Cause.FINAL_LOSS),
		RESPAWN(MegumiCooldownPolicy.Cause.NONE),
		DIMENSION_CHANGE(MegumiCooldownPolicy.Cause.RECALL),
		DISCONNECT(MegumiCooldownPolicy.Cause.NONE),
		SERVER_STOPPING(MegumiCooldownPolicy.Cause.NONE),
		DESELECTED(MegumiCooldownPolicy.Cause.RECALL),
		SUMMON_ROLLBACK(MegumiCooldownPolicy.Cause.NONE);

		private final int cooldownTicks;

		TeardownReason(MegumiCooldownPolicy.Cause cause) {
			this.cooldownTicks = MegumiCooldownPolicy.duration(cause);
		}

		int cooldownTicks() {
			return cooldownTicks;
		}
	}
}
