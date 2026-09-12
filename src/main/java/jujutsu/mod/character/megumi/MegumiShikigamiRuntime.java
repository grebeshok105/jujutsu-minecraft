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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Owns every non-dog shikigami pack: the summon/recall/swap technique key, the sic command, the
 * owner-keyed pack map and the single authoritative teardown. Mirrors the proven Divine Dog
 * lifecycle; the dog runtime is untouched and answers for itself whenever the selection is DOGS.
 */
public final class MegumiShikigamiRuntime {
	private static final Map<UUID, MegumiShikigamiPack> PACKS = new ConcurrentHashMap<>();
	private static final Set<UUID> TEARDOWN_IN_PROGRESS = ConcurrentHashMap.newKeySet();
	private static final AtomicLong NEXT_SUMMON_TOKEN = new AtomicLong();

	private MegumiShikigamiRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiShikigamiRuntime::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof MegumiShikigamiEntity body && body.ownerUuid() != null) {
				reconcile(entity.getServer(), body.ownerUuid(), RemovalCause.DEATH);
			} else if (entity instanceof ServerPlayer player) {
				teardown(player.getServer(), player.getUUID(), TeardownReason.DEATH);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof MegumiShikigamiEntity body && body.ownerUuid() != null) {
				reconcile(level.getServer(), body.ownerUuid(), RemovalCause.UNLOAD);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				teardown(newPlayer.getServer(), newPlayer.getUUID(), TeardownReason.RESPAWN));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				teardown(player.getServer(), player.getUUID(), TeardownReason.DIMENSION_CHANGE));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerId = handler.player.getUUID();
			teardown(server, playerId, TeardownReason.DISCONNECT);
			onPlayerDisconnect(playerId);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Set<UUID> ownerIds = new HashSet<>(PACKS.keySet());
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiShikigamiEntity body : level.getEntities(
						EntityTypeTest.forClass(MegumiShikigamiEntity.class), candidate -> true)) {
					if (body.ownerUuid() != null) {
						ownerIds.add(body.ownerUuid());
					}
				}
			}
			for (UUID ownerId : ownerIds) {
				teardown(server, ownerId, TeardownReason.SERVER_STOPPING);
			}
			PACKS.clear();
			TEARDOWN_IN_PROGRESS.clear();
			MegumiShikigamiSelection.clearAll();
		});
	}

	/** The technique key: summon the selection, recall it, swap it, or let the dogs answer. */
	public static boolean tryPrimary(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		long gameTime = player.level().getGameTime();
		MegumiShikigami selection = MegumiShikigamiSelection.selected(ownerId);
		MegumiShikigamiPack ours = PACKS.get(ownerId);
		if (ours != null && ours.summonedAtGameTime() == gameTime) {
			// Same-tick duplicate: key repeat or a doubled packet must not summon-then-recall.
			return true;
		}
		boolean dogActive = MegumiSummonRuntime.pack(ownerId) != null;
		MegumiShikigami activeType = ours == null ? null : ours.type();
		MegumiShikigamiSwapPolicy.Action action = MegumiShikigamiSwapPolicy.decide(selection, activeType, dogActive);
		boolean swappedOut = false;
		switch (action) {
			case DELEGATE_DOGS -> {
				// The dogs answer first: their summon can be refused (no room, other reasons) and the
				// refusal must not cost the player the shikigami already out. Only once the dogs are
				// committed does the shikigami pack step aside.
				boolean dogs = MegumiSummonRuntime.tryToggle(player, notify);
				if (dogs && ours != null) {
					teardown(player.getServer(), ownerId, TeardownReason.SWAPPED);
				}
				return dogs;
			}
			case RECALL_SELF -> {
				teardown(player.getServer(), ownerId, TeardownReason.RECALL);
				return true;
			}
			case RECALL_OTHER_THEN_SUMMON -> {
				// Stage the arrival before anything is torn down: a placement failure (a cave, a wall of
				// the owner's own bodies) used to cost the previous body for a no_room message, which is
				// the whole point of the swap being free. The staged bodies are not in the level yet, so
				// the sweep that follows cannot see them.
				long token = NEXT_SUMMON_TOKEN.incrementAndGet();
				List<MegumiShikigamiEntity> staged = spawnBodies(player.level(), player, selection, token);
				if (staged.isEmpty()) {
					return rejectNoRoom(player, notify);
				}
				if (ours != null) {
					teardown(player.getServer(), ownerId, TeardownReason.SWAPPED);
					swappedOut = true;
				}
				if (dogActive) {
					MegumiSummonRuntime.teardown(player.getServer(), ownerId, MegumiSummonRuntime.TeardownReason.SWAPPED);
					swappedOut = true;
				}
				boolean summoned = commitSummon(player, selection, token, staged, notify);
				if (summoned && swappedOut && notify) {
					player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.swap",
							Component.translatable(activeType != null ? nameKey(activeType) : nameKey(MegumiShikigami.DOGS)),
							Component.translatable(nameKey(selection))), true);
				}
				return summoned;
			}
			case SUMMON -> {
				return summon(player, selection, notify);
			}
		}
		return false;
	}

	/** The sneaking technique key: advance the selection; never costs anything. */
	public static boolean tryCycle(ServerPlayer player, boolean notify) {
		MegumiShikigami next = MegumiShikigamiSelection.cycle(player.getUUID());
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.selected",
					Component.translatable(nameKey(next))), true);
		}
		return true;
	}

	/** The sneaking technique key command: send every living body at the aimed target. */
	public static boolean trySic(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		MegumiShikigami selection = MegumiShikigamiSelection.selected(ownerId);
		if (selection == MegumiShikigami.DOGS) {
			return MegumiSummonRuntime.trySic(player, notify);
		}
		MegumiShikigamiPack pack = PACKS.get(ownerId);
		List<MegumiShikigamiEntity> living = pack == null ? List.of() : livingBodies(player.getServer(), ownerId, pack);
		if (living.isEmpty()) {
			if (pack != null) {
				reconcile(player.getServer(), ownerId, RemovalCause.TICK);
			}
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.none"), true);
			}
			return false;
		}
		List<MegumiShikigamiEntity> commandable = living.stream()
				.filter(MegumiShikigamiEntity::acceptsSicCommand)
				.toList();
		if (commandable.isEmpty()) {
			return false;
		}
		ServerLevel level = player.level();
		TargetResolver.Result result = TargetResolver.resolve(
				level, player, MegumiShikigamiProfile.SIC_RANGE,
				target -> MegumiSummonRuntime.isEligibleTarget(player, target) && !isOwnBody(player, target));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return false;
		}
		Entity resolved = level.getEntity(result.entityId().get());
		if (!(resolved instanceof LivingEntity target)
				|| !MegumiSummonRuntime.isEligibleTarget(player, target)
				|| isOwnBody(player, target)
				|| !player.hasLineOfSight(target)) {
			return false;
		}
		for (MegumiShikigamiEntity body : commandable) {
			body.assignSicTarget(target);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SNAP,
				SoundSource.PLAYERS, 0.66f, 0.88f);
		commandable.getFirst().playSicSound();
		broadcastCue(level, player, MegumiVfxIds.SHIKIGAMI_SIC, target.position(), target.getId(),
				new Vec3(0.0, target.getBbHeight() * 0.55, 0.0));
		MegumiSummonRuntime.startCooldownIfLonger(player, CharacterAbility.PRIMARY_SNEAK,
				MegumiShikigamiProfile.SIC_COOLDOWN_TICKS);
		return true;
	}

	/** Clears the player's saved selection; wired to disconnect (unit-testable seam). */
	static void onPlayerDisconnect(UUID playerId) {
		MegumiShikigamiSelection.clear(playerId);
	}

	/** The single destructive entry point: removes the pack record and sweeps every owned body. */
	public static void teardown(MinecraftServer server, UUID ownerId, TeardownReason reason) {
		if (server == null || !TEARDOWN_IN_PROGRESS.add(ownerId)) {
			return;
		}
		MegumiShikigamiPack pack = PACKS.remove(ownerId);
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		try {
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiShikigamiEntity body : new ArrayList<>(level.getEntities(
						EntityTypeTest.forClass(MegumiShikigamiEntity.class),
						candidate -> ownerId.equals(candidate.ownerUuid())))) {
					boolean belongedToRemovedPack = pack != null
							&& pack.contains(body.getUUID(), body.summonToken(), body.level().dimension());
					if (reason.recallsVisually() && belongedToRemovedPack) {
						if (owner != null) {
							broadcastCue(level, owner, MegumiVfxIds.SHIKIGAMI_RECALL, body.position(), body.getId(), Vec3.ZERO);
						}
						body.playRecallSound();
						body.beginRecall();
					} else {
						body.discard();
					}
				}
			}
		} finally {
			TEARDOWN_IN_PROGRESS.remove(ownerId);
		}
		if (pack != null) {
			int ticks = reason.appliesRecallCooldown() ? MegumiShikigamiProfile.recallCooldownTicks(pack.type())
					: reason.appliesDeathCooldown() ? MegumiShikigamiProfile.deathCooldownTicks(pack.type())
					: 0;
			if (ticks > 0) {
				MegumiSummonRuntime.startCooldownIfLonger(server.getPlayerList().getPlayer(ownerId),
						CharacterAbility.PRIMARY, ticks);
			}
		}
	}

	static MegumiShikigamiPack pack(UUID ownerId) {
		return PACKS.get(ownerId);
	}

	/** Live snapshot of the owner's pack, if one exists. Exists for the dev control surface + gametests. */
	public static Optional<PackView> packView(MinecraftServer server, UUID ownerId) {
		if (server == null) {
			return Optional.empty();
		}
		MegumiShikigamiPack pack = PACKS.get(ownerId);
		if (pack == null) {
			return Optional.empty();
		}
		List<MegumiShikigamiEntity> living = livingBodies(server, ownerId, pack);
		boolean anchorAlive = living.stream().anyMatch(body -> body.getUUID().equals(pack.anchorId()));
		return Optional.of(new PackView(pack.type().id(), pack.dimension().location().toString(),
				anchorAlive, living.size(), pack.summonedAtGameTime(), pack.anchorId().toString()));
	}

	/** Read-only identity of one owner's live pack for observation. Exists for the dev control surface + gametests. */
	public record PackView(String type, String dimension, boolean anchorAlive, int aliveBodies,
			long summonedAtGameTime, String anchorId) {}

	/** Registers a body spawned after the initial summon (Rabbit Escape upkeep) into the pack record. */
	static void registerExtraBody(UUID ownerId, MegumiShikigamiEntity body) {
		PACKS.computeIfPresent(ownerId, (key, pack) -> {
			if (pack.type() != body.shikigamiType()
					|| !pack.dimension().equals(body.level().dimension())
					|| pack.summonToken() != body.summonToken()
					|| pack.bodyIds().contains(body.getUUID())) {
				return pack;
			}
			List<UUID> ids = new ArrayList<>(pack.bodyIds());
			ids.add(body.getUUID());
			return new MegumiShikigamiPack(pack.type(), pack.dimension(), pack.anchorId(),
					List.copyOf(ids), pack.summonToken(), pack.summonedAtGameTime());
		});
	}

	/** Per-body tick dispatch, called from the body itself. */
	static void tickBody(MegumiShikigamiEntity body) {
		if (!body.combatEnabled()) {
			return;
		}
		MinecraftServer server = body.getServer();
		if (server == null) {
			return;
		}
		MegumiShikigamiPack pack = PACKS.get(body.ownerUuid());
		if (pack == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(body.ownerUuid());
		long gameTime = body.level().getGameTime();
		switch (body.shikigamiType()) {
			case NUE -> MegumiNueBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiNueEntity) body, gameTime);
			case TOAD -> MegumiToadBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiToadEntity) body, gameTime);
			case RABBITS -> MegumiRabbitsBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiRabbitEntity) body, gameTime);
			case ELEPHANT -> MegumiElephantBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiElephantEntity) body, gameTime);
			case DOGS -> throw new IllegalStateException("dogs never enter the shikigami runtime");
		}
	}

	static boolean shouldHardDiscard(MegumiShikigamiEntity body) {
		UUID ownerId = body.ownerUuid();
		if (ownerId == null) {
			return true;
		}
		MegumiShikigamiPack pack = PACKS.get(ownerId);
		if (pack == null) {
			return !body.canFinishRecallWithoutPack();
		}
		if (pack.type() != body.shikigamiType()) {
			// A swap-out body: its pack record is gone because a different type arrived, but the sink it
			// started is still playing. Discarding here cut the twelve-tick recall to one tick; the body
			// has its own finisher, so only a body that cannot finish may be swept.
			return !body.canFinishRecallWithoutPack();
		}
		return !pack.contains(body.getUUID(), body.summonToken(), body.level().dimension());
	}

	private static boolean isOwnBody(ServerPlayer player, LivingEntity candidate) {
		return MegumiSummonRuntime.isOwnSummonBody(player, candidate);
	}

	private static void tick(MinecraftServer server) {
		for (UUID ownerId : Set.copyOf(PACKS.keySet())) {
			reconcile(server, ownerId, RemovalCause.TICK);
		}
	}

	private static void reconcile(MinecraftServer server, UUID ownerId, RemovalCause cause) {
		if (server == null || TEARDOWN_IN_PROGRESS.contains(ownerId)) {
			return;
		}
		MegumiShikigamiPack pack = PACKS.get(ownerId);
		if (pack == null) {
			return;
		}
		List<MegumiShikigamiEntity> living = livingBodies(server, ownerId, pack);
		if (living.isEmpty()) {
			if (PACKS.remove(ownerId, pack)) {
				MegumiSummonRuntime.startCooldownIfLonger(server.getPlayerList().getPlayer(ownerId),
						CharacterAbility.PRIMARY, MegumiShikigamiProfile.deathCooldownTicks(pack.type()));
			}
			return;
		}
		boolean anchorAlive = living.stream().anyMatch(body -> body.getUUID().equals(pack.anchorId()));
		if (!anchorAlive) {
			// The keystone body is gone: the whole pack disperses (Rabbit Escape's canon rule).
			teardown(server, ownerId, TeardownReason.DEATH);
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		for (MegumiShikigamiEntity body : living) {
			LivingEntity target = body.getTarget();
			if (target != null && (owner == null || !MegumiSummonRuntime.isEligibleTarget(owner, target))) {
				body.clearSicCommand();
				body.setTarget(null);
			}
		}
	}

	private static boolean summon(ServerPlayer player, MegumiShikigami type, boolean notify) {
		long token = NEXT_SUMMON_TOKEN.incrementAndGet();
		List<MegumiShikigamiEntity> staged = spawnBodies(player.level(), player, type, token);
		if (staged.isEmpty()) {
			return rejectNoRoom(player, notify);
		}
		return commitSummon(player, type, token, staged, notify);
	}

	/**
	 * Inserts an already-staged summon into the level and registers its pack. The staging half is
	 * separate so a swap can prove the arrival has somewhere to stand before the outgoing pack is
	 * swept — a refused swap must leave the previous body in the world.
	 */
	private static boolean commitSummon(ServerPlayer player, MegumiShikigami type, long token,
			List<MegumiShikigamiEntity> staged, boolean notify) {
		ServerLevel level = player.level();
		List<MegumiShikigamiEntity> inserted = new ArrayList<>(staged.size());
		for (MegumiShikigamiEntity body : staged) {
			if (!level.addFreshEntity(body)) {
				for (MegumiShikigamiEntity other : staged) {
					other.discard();
				}
				return rejectNoRoom(player, notify);
			}
			inserted.add(body);
		}
		int anchor = anchorIndex(type, inserted.size(), level.getRandom());
		List<UUID> ids = inserted.stream().map(Entity::getUUID).toList();
		PACKS.put(player.getUUID(), new MegumiShikigamiPack(
				type, level.dimension(), ids.get(anchor), ids, token, level.getGameTime()));
		for (MegumiShikigamiEntity body : inserted) {
			body.playShadowOpenSound();
			// The cue id is referenced right here at the emit site, not through a per-type accessor:
			// the VFX completeness check reads the production emitter graph, and an id whose only
			// reference has no path to the network broadcast counts as an unemitted effect.
			switch (type) {
				case NUE -> broadcastCue(level, player, MegumiVfxIds.NUE_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case TOAD -> broadcastCue(level, player, MegumiVfxIds.TOAD_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case RABBITS -> broadcastCue(level, player, MegumiVfxIds.RABBITS_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case ELEPHANT -> broadcastCue(level, player, MegumiVfxIds.ELEPHANT_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case DOGS -> throw new IllegalStateException("dogs do not use the shikigami runtime");
			}
		}
		return true;
	}

	private static List<MegumiShikigamiEntity> spawnBodies(
			ServerLevel level, ServerPlayer owner, MegumiShikigami type, long token) {
		return switch (type) {
			case NUE -> spawnNue(level, owner, token);
			case TOAD -> spawnToad(level, owner, token);
			case RABBITS -> spawnRabbits(level, owner, token);
			case ELEPHANT -> spawnElephant(level, owner, token);
			case DOGS -> throw new IllegalStateException("dogs do not use the shikigami runtime");
		};
	}

	private static List<MegumiShikigamiEntity> spawnRabbits(ServerLevel level, ServerPlayer owner, long token) {
		List<Vec3> spots = MegumiShikigamiSpawnPlacement.ring(level, owner.position(),
				MegumiShikigamiProfile.RABBITS_SWARM_SIZE, MegumiShikigamiProfile.RABBITS_SPAWN_RADIUS,
				JujutsuEntities.MEGUMI_RABBIT.getDimensions());
		List<MegumiShikigamiEntity> bodies = new ArrayList<>(spots.size());
		for (Vec3 spot : spots) {
			MegumiRabbitEntity rabbit = new MegumiRabbitEntity(JujutsuEntities.MEGUMI_RABBIT, level);
			rabbit.setPos(spot);
			rabbit.setYRot(owner.getYRot());
			rabbit.setTame(true, false);
			rabbit.setOwner(owner);
			rabbit.configureSummon(owner.getUUID(), token);
			bodies.add(rabbit);
		}
		return bodies;
	}

	private static List<MegumiShikigamiEntity> spawnElephant(ServerLevel level, ServerPlayer owner, long token) {
		Vec3 spot = MegumiShikigamiSpawnPlacement.ground(level, owner.position(),
				owner.getYRot(), JujutsuEntities.MEGUMI_MAX_ELEPHANT.getDimensions());
		if (spot == null) {
			return List.of();
		}
		MegumiElephantEntity elephant = new MegumiElephantEntity(JujutsuEntities.MEGUMI_MAX_ELEPHANT, level);
		elephant.setPos(spot);
		elephant.setYRot(owner.getYRot());
		elephant.setTame(true, false);
		elephant.setOwner(owner);
		elephant.configureSummon(owner.getUUID(), token);
		return List.of(elephant);
	}

	private static List<MegumiShikigamiEntity> spawnToad(ServerLevel level, ServerPlayer owner, long token) {
		Vec3 spot = MegumiShikigamiSpawnPlacement.ground(level, owner.position(),
				owner.getYRot(), JujutsuEntities.MEGUMI_TOAD.getDimensions());
		if (spot == null) {
			return List.of();
		}
		MegumiToadEntity toad = new MegumiToadEntity(JujutsuEntities.MEGUMI_TOAD, level);
		toad.setPos(spot);
		toad.setYRot(owner.getYRot());
		toad.setTame(true, false);
		toad.setOwner(owner);
		toad.configureSummon(owner.getUUID(), token);
		return List.of(toad);
	}

	private static List<MegumiShikigamiEntity> spawnNue(ServerLevel level, ServerPlayer owner, long token) {
		Vec3 spot = MegumiShikigamiSpawnPlacement.air(level, owner.position(),
				JujutsuEntities.MEGUMI_NUE.getDimensions());
		if (spot == null) {
			return List.of();
		}
		MegumiNueEntity nue = new MegumiNueEntity(JujutsuEntities.MEGUMI_NUE, level);
		nue.setPos(spot);
		nue.setYRot(owner.getYRot());
		nue.setTame(true, false);
		nue.setOwner(owner);
		nue.configureSummon(owner.getUUID(), token);
		return List.of(nue);
	}

	private static int anchorIndex(MegumiShikigami type, int size, RandomSource random) {
		return switch (type) {
			case RABBITS -> random.nextInt(size);
			case DOGS -> throw new IllegalStateException("dogs do not use the shikigami runtime");
			case NUE, TOAD, ELEPHANT -> 0;
		};
	}

	private static boolean rejectNoRoom(ServerPlayer player, boolean notify) {
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.no_room"), true);
		}
		return false;
	}

	private static String nameKey(MegumiShikigami type) {
		return "jujutsumod.megumi.shikigami." + type.id();
	}

	static void broadcastCue(ServerLevel level, ServerPlayer owner, ResourceLocation effectId,
			Vec3 origin, int anchorEntityId, Vec3 anchorOffset) {
		if (owner == null) {
			return;
		}
		JujutsuNetworking.broadcastVfxCue(level, origin, MegumiProfile.VFX_DELIVERY_RADIUS,
				VfxCues.anchoredWithOffset(effectId, origin, anchorEntityId, anchorOffset, 1,
						level.getGameTime(), owner.getRandom().nextLong()));
	}

	private static boolean isLiveBody(ServerLevel level, UUID bodyId, UUID ownerId, long summonToken) {
		return level.getEntity(bodyId) instanceof MegumiShikigamiEntity body
				&& body.isAlive()
				&& !body.isRemoved()
				&& ownerId.equals(body.ownerUuid())
				&& body.summonToken() == summonToken;
	}

	static List<MegumiShikigamiEntity> livingBodies(MinecraftServer server, UUID ownerId, MegumiShikigamiPack pack) {
		if (server == null) {
			return List.of();
		}
		ServerLevel level = server.getLevel(pack.dimension());
		if (level == null) {
			return List.of();
		}
		List<MegumiShikigamiEntity> living = new ArrayList<>(pack.bodyIds().size());
		for (UUID bodyId : pack.bodyIds()) {
			if (isLiveBody(level, bodyId, ownerId, pack.summonToken())) {
				living.add((MegumiShikigamiEntity) level.getEntity(bodyId));
			}
		}
		return living;
	}

	enum RemovalCause {
		TICK,
		DEATH,
		UNLOAD
	}

	public enum TeardownReason {
		RECALL,
		DEATH,
		SWAPPED,
		DISCONNECT,
		RESPAWN,
		DIMENSION_CHANGE,
		SERVER_STOPPING,
		DESELECTED,
		FIXTURE_RESET;

		/** Whether the swept bodies play their sink-out instead of vanishing on the spot. */
		boolean recallsVisually() {
			return this == RECALL || this == SWAPPED || this == DESELECTED
					|| this == DIMENSION_CHANGE || this == FIXTURE_RESET;
		}

		boolean appliesRecallCooldown() {
			return this == RECALL || this == DESELECTED || this == DIMENSION_CHANGE;
		}

		boolean appliesDeathCooldown() {
			return this == DEATH;
		}
	}
}