package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.vfx.MegumiVfxIds;
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
	private static final ResourceKey<WolfSoundVariant> DIRE_WOLF_SOUND =
			ResourceKey.create(Registries.WOLF_SOUND_VARIANT, JujutsuMod.id("dire_wolf"));
	/**
	 * The failure-memory action key a pounce abort is filed under (issue #107 §15). The dogs are
	 * the only pouncers, and the coordination pass reads the same key when it weighs an order.
	 */
	private static final String POUNCE_FAILURE_KEY = "pounce";

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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			teardown(server, handler.player.getUUID(), TeardownReason.DISCONNECT);
			// The per-type summon map is shared with the shikigami runtime (issue #107), so the
			// owner's whole row is dropped here: a reconnect greets the player with a clean slate,
			// exactly like the ability-slot map it replaced.
			MegumiSummonCooldowns.clear(handler.player.getUUID());
		});
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
			MegumiSummonCooldowns.clearAll();
		});
	}

	static long nextSummonToken() {
		return NEXT_SUMMON_TOKEN.incrementAndGet();
	}

	static MegumiDivineDogPack pack(UUID ownerId) {
		return PACKS.get(ownerId);
	}

	/** Live snapshot of the owner's pack, if one exists. Exists for the dev control surface + gametests. */
	public static Optional<PackView> packView(MinecraftServer server, UUID ownerId) {
		if (server == null) {
			return Optional.empty();
		}
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		if (pack == null) {
			return Optional.empty();
		}
		List<MegumiDivineDogEntity> living = livingDogs(server, ownerId, pack);
		boolean whiteAlive = living.stream().anyMatch(dog -> dog.getUUID().equals(pack.whiteId()));
		boolean blackAlive = living.stream().anyMatch(dog -> dog.getUUID().equals(pack.blackId()));
		return Optional.of(new PackView(
				pack.dimension().location().toString(), whiteAlive, blackAlive, pack.summonedAtGameTime()));
	}

	/** Read-only identity of one owner's live pack for observation. Exists for the dev control surface + gametests. */
	public record PackView(String dimension, boolean whiteAlive, boolean blackAlive, long summonedAtGameTime) {}

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
		if (MegumiSummonCooldowns.onCooldown(ownerId, MegumiShikigami.DOGS, gameTime)) {
			// Issue #107: the summon readiness of each type lives on its own per-type deadline, so a
			// recalled Nue no longer locks the dogs (and vice versa) the way the shared PRIMARY slot did.
			return rejectRecharging(player, MegumiShikigami.DOGS, notify);
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
		// The pack is live: the owner's snapshot marks DOGS as out without implying any despawn.
		MegumiShikigamiSync.push(player);
		return true;
	}

	/**
	 * The dogs this owner can actually command right now (issue #107: the sic command is global, so
	 * the dog half of it is gathered from the living list the caller already read). A dog still
	 * materializing answers no key, and the filter keeps the caller from confirming, emitting or
	 * charging for a command nobody received.
	 */
	static List<MegumiDivineDogEntity> commandableDogs(List<MegumiDivineDogEntity> livingDogs) {
		return livingDogs.stream()
				.filter(MegumiDivineDogEntity::acceptsSicCommand)
				.toList();
	}

	/**
	 * Any body the owner's own shikigami layer has out is never a legal mark, however it crosses
	 * the aim: a body standing between the owner and the target would otherwise be commanded as the
	 * mark. Shared with the shikigami branch so both readings of the aim agree, and read by
	 * {@link #isEligibleTarget} so auto-acquired targets obey the same rule.
	 */
	static boolean isOwnSummonBody(LivingEntity owner, LivingEntity candidate) {
		return candidate instanceof MegumiShikigamiEntity body && owner.getUUID().equals(body.ownerUuid());
	}

	static boolean isEligibleTarget(LivingEntity owner, LivingEntity target) {
		return MegumiTargetPolicy.accepts(new MegumiTargetPolicy.Facts(
				target == owner,
				target.isAlive(),
				!target.isRemoved(),
				target.level() == owner.level(),
				target instanceof Player player && player.isSpectator(),
				(isOwnSummonBody(owner, target))
						|| (target instanceof MegumiDivineDogEntity dog
								&& owner.getUUID().equals(dog.ownerUuid())),
				owner.isAlliedTo(target)));
	}

	/**
	 * The body the pack should answer for its owner this tick (issue #76): the owner's recent
	 * attacker, else the nearest body already aggroed on the owner, filtered through the same
	 * eligibility rule a manual sic uses. Null when the owner is unharmed and unaggroed.
	 *
	 * <p>The freshness window is measured on the OWNER's own clock: vanilla stamps
	 * {@code lastHurtByMobTimestamp} with the victim's {@code tickCount}, so comparing it against
	 * {@code level().getGameTime()} silently expires every hit in a world that has been ticking for
	 * longer than the player has existed — which is every world after a rejoin.
	 */
	static LivingEntity retaliationTarget(LivingEntity owner) {
		LivingEntity aggressor = MegumiRetaliationPolicy.pickAggressor(owner, owner.tickCount,
				MegumiProfile.RETALIATION_WINDOW_TICKS, () -> owner.level().getEntitiesOfClass(
						LivingEntity.class,
						owner.getBoundingBox().inflate(MegumiProfile.RETALIATION_RADIUS),
						candidate -> candidate != owner
								&& candidate instanceof Mob mob
								&& mob.getTarget() == owner));
		return aggressor != null && isEligibleTarget(owner, aggressor)
				&& MegumiRetaliationPolicy.withinRadius(owner, aggressor, MegumiProfile.RETALIATION_RADIUS)
				? aggressor : null;
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
		// Cosmetic lookup: a world without the mod's variant entry must not veto the summon. An unset
		// component leaves the dog on the vanilla wolf voice, which its sound hooks already handle.
		level.registryAccess()
				.lookup(Registries.WOLF_SOUND_VARIANT)
				.flatMap(registry -> registry.get(DIRE_WOLF_SOUND))
				.ifPresent(soundVariant -> dog.setComponent(DataComponents.WOLF_SOUND_VARIANT, soundVariant));
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
			startSummonCooldown(server, ownerId, MegumiShikigami.DOGS,
					MegumiCooldownPolicy.duration(MegumiCooldownPolicy.Cause.FINAL_LOSS));
			// The pack record is gone, so the selector's DOGS marker must go with it.
			MegumiShikigamiSync.push(server.getPlayerList().getPlayer(ownerId));
		}
	}

	public static void teardown(MinecraftServer server, UUID ownerId, TeardownReason reason) {
		if (server == null || !TEARDOWN_IN_PROGRESS.add(ownerId)) {
			return;
		}
		MegumiDivineDogPack pack = PACKS.remove(ownerId);
		ServerPlayer owner = reason == TeardownReason.RECALL || reason == TeardownReason.SWAPPED
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
							.dogCleanupAction(reason == TeardownReason.RECALL || reason == TeardownReason.SWAPPED,
									belongedToRemovedPack);
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
			startSummonCooldown(server, ownerId, MegumiShikigami.DOGS, reason.cooldownTicks());
			// The pack record is gone whichever way this went, so the DOGS marker must go with it.
			MegumiShikigamiSync.push(server.getPlayerList().getPlayer(ownerId));
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
			retaliate(server, ownerId);
			MegumiDivineDogPack pack = PACKS.get(ownerId);
			if (pack != null) {
				recoverLeash(server, ownerId, pack);
			}
		}
	}

	/**
	 * The dogs answer for their owner without a key press (issue #76), exactly like the non-dog
	 * pack: the owner's recent attacker, or the nearest body already aggroed on the owner, becomes
	 * the mark of every dog that carries none. A dog the owner sics by hand keeps its mark.
	 */
	private static void retaliate(MinecraftServer server, UUID ownerId) {
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (pack == null || owner == null) {
			return;
		}
		List<MegumiDivineDogEntity> living = livingDogs(server, ownerId, pack);
		if (living.isEmpty()) {
			return;
		}
		LivingEntity aggressor = retaliationTarget(owner);
		if (aggressor == null) {
			// Issue #96/#107: the mark was never meant to outlive the answer. With no aggressor in
			// reach, every mark this pass placed expires; the owner's manual sic and the
			// coordinator's autonomous mark both answer to other owners and stand.
			for (MegumiDivineDogEntity dog : living) {
				if (dog.sicTargetUuid() != null
						&& MegumiRetaliationPolicy.markExpiresWithoutAggressor(dog.markKind())) {
					dog.clearSicCommand();
					dog.setTarget(null);
				}
			}
			return;
		}
		for (MegumiDivineDogEntity dog : living) {
			if (dog.acceptsSicCommand() && !dog.hasManualSicTarget()) {
				dog.assignRetaliationTarget(aggressor);
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
					finishPounceAndMaybeResume(
							dog, owner, assignedTarget, MegumiPouncePolicy.ResumeTermination.ORDINARY,
							dog.onGround(), dog.getDeltaMovement());
				}
				case CONTINUE -> {
					Vec3 beforeMove = dog.position();
					Vec3 flightVelocity = MegumiPouncePolicy.steerVelocity(
							dog.getDeltaMovement(), beforeMove, assignedTarget.position())
							.add(0.0, -MegumiProfile.POUNCE_GRAVITY, 0.0);
					dog.setDeltaMovement(flightVelocity);
					dog.move(MoverType.SELF, flightVelocity);
					Vec3 resolvedVelocity = dog.position().subtract(beforeMove);
					if (flightVelocity.horizontalDistanceSqr() > 1.0E-6) {
						dog.setYRot((float) (Mth.atan2(flightVelocity.x, flightVelocity.z) * Mth.RAD_TO_DEG));
					}
					boolean crossedTarget = MegumiPouncePolicy.sweptTargetHit(
							dog.getBoundingBox(), assignedTarget.getBoundingBox(), beforeMove, dog.position());
					MegumiPouncePolicy.PostMoveAction postMoveAction = MegumiPouncePolicy.postMoveAction(
							crossedTarget, dog.horizontalCollision, dog.verticalCollision, dog.onGround(), elapsedTicks);
					if (postMoveAction == MegumiPouncePolicy.PostMoveAction.IMPACT) {
						resolvePounceImpact(level, dog, owner, assignedTarget, resolvedVelocity);
						return;
					}
					if (postMoveAction == MegumiPouncePolicy.PostMoveAction.FINISH) {
						if (dog.horizontalCollision) {
							// The flight ended on a wall instead of the target (issue #107 §15): remember
							// the abort so the next tick's launch is held instead of re-slammed into it.
							MegumiFailureMemory.recordFailure(dog.getUUID(), POUNCE_FAILURE_KEY, gameTime);
						}
						finishPounceAndMaybeResume(
								dog, owner, assignedTarget, MegumiPouncePolicy.ResumeTermination.ORDINARY,
								dog.onGround(), resolvedVelocity);
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
		if (MegumiFailureMemory.weight(dog.getUUID(), POUNCE_FAILURE_KEY, gameTime)
				< MegumiProfile.POUNCE_RETRY_MIN_WEIGHT) {
			// Issue #107 R16: the body just failed this action recently, so it holds the launch this
			// tick. The memory recovers on its own window, so the wall is not retried forever either.
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
				&& target.getUUID().equals(dog.pounceSicTargetUuid())
				&& isEligibleTarget(owner, target);
		Vec3 positionalDirection = target.position().subtract(dog.position()).multiply(1.0, 0.0, 1.0);
		Vec3 direction = MegumiPouncePolicy.impactDirection(impactVelocity, positionalDirection, Vec3.ZERO);
		Vec3 exitVelocity = MegumiPouncePolicy.exitVelocity(
				validImpact ? MegumiPouncePolicy.ExitReason.VALID_CONTACT
						: MegumiPouncePolicy.ExitReason.INVALID_CONTACT,
				dog.onGround(), impactVelocity);
		boolean resume = canResumeNavigation(
				dog, owner, target, MegumiPouncePolicy.ResumeTermination.VALID_CONTACT);
		dog.finishPounce(exitVelocity);
		if (!validImpact) {
			return;
		}
		if (resume) {
			dog.resumeNavigation(target);
		}
		float damage = (float) MegumiProfile.DOG_ATTACK_DAMAGE + MegumiProfile.POUNCE_BONUS_DAMAGE;
		if (!target.hurtServer(level, level.damageSources().playerAttack(owner), damage)) {
			return;
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

	private static void finishPounceAndMaybeResume(
			MegumiDivineDogEntity dog, ServerPlayer owner, LivingEntity target,
			MegumiPouncePolicy.ResumeTermination termination, boolean onGround, Vec3 resolvedVelocity) {
		boolean resume = canResumeNavigation(dog, owner, target, termination);
		dog.finishPounce(MegumiPouncePolicy.exitVelocity(
				MegumiPouncePolicy.ExitReason.ORDINARY, onGround, resolvedVelocity));
		if (resume) {
			dog.resumeNavigation(target);
		}
	}

	private static boolean canResumeNavigation(
			MegumiDivineDogEntity dog, ServerPlayer owner, LivingEntity target,
			MegumiPouncePolicy.ResumeTermination termination) {
		UUID pounceCommand = dog.pounceSicTargetUuid();
		UUID currentCommand = dog.sicTargetUuid();
		return MegumiPouncePolicy.canResumeNavigation(new MegumiPouncePolicy.ResumeFacts(
				dog.isRemoved(),
				dog.presentationPhase() == MegumiDogPresentationPolicy.Phase.ACTIVE,
				termination,
				pounceCommand != null && pounceCommand.equals(currentCommand),
				target != null && target.isAlive(),
				target == null || target.isRemoved(),
				target != null && target.level() == dog.level(),
				owner != null && target != null && isEligibleTarget(owner, target)));
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

	/** Every living dog of this owner (coordinator + sic fan-out, issue #107). */
	public static List<MegumiDivineDogEntity> livingDogs(MinecraftServer server, UUID ownerId) {
		MegumiDivineDogPack pack = PACKS.get(ownerId);
		return pack == null ? List.of() : livingDogs(server, ownerId, pack);
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

	/**
	 * Arms one type's summon deadline (issue #107): every summon rides the per-type map, the dogs
	 * included under {@link MegumiShikigami#DOGS}, so a recalled Nue can no longer keep the dogs
	 * waiting the way the shared PRIMARY slot did. The game time is read from the overworld — every
	 * level derives its clock from it, so the deadline is dimension-independent.
	 */
	static void startSummonCooldown(MinecraftServer server, UUID ownerId, MegumiShikigami type, int ticks) {
		if (server == null || ticks <= 0) {
			return;
		}
		MegumiSummonCooldowns.start(ownerId, type, server.overworld().getGameTime() + ticks);
	}

	/** The summon of {@code type} is not ready yet: name the type so the player knows which row it is. */
	static boolean rejectRecharging(ServerPlayer player, MegumiShikigami type, boolean notify) {
		if (notify) {
			player.displayClientMessage(Component.translatable(
					"message.jujutsumod.megumi.shikigami.recharging",
					Component.translatable("jujutsumod.megumi.shikigami." + type.id())), true);
		}
		return false;
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
		/**
		 * The vessel left the field. The summon price is deliberately NONE (issue #107): the
		 * deadline now lives on the per-type summon map, and a vessel switch is a clean slate
		 * (issue #84) — the shared ability-slot store that used to carry it was wiped by the
		 * switch, so arming a per-type deadline here would lock the returning player instead.
		 */
		DESELECTED(MegumiCooldownPolicy.Cause.NONE),
		SUMMON_ROLLBACK(MegumiCooldownPolicy.Cause.NONE),
		FIXTURE_RESET(MegumiCooldownPolicy.Cause.NONE),
		/** Dismissed because the player swapped to another shikigami: visual recall, no cooldown. */
		SWAPPED(MegumiCooldownPolicy.Cause.NONE);

		private final int cooldownTicks;

		TeardownReason(MegumiCooldownPolicy.Cause cause) {
			this.cooldownTicks = MegumiCooldownPolicy.duration(cause);
		}

		int cooldownTicks() {
			return cooldownTicks;
		}
	}
}
