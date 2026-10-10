package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.EnumMap;
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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Owns every non-dog shikigami pack: the summon/recall technique key, the sic command, the
 * owner-keyed pack map (one pack per type since issue #107) and the single authoritative teardown.
 * Mirrors the proven Divine Dog lifecycle; the dog runtime answers for itself whenever the
 * selection is DOGS and, since coexistence, its pack stands beside the shikigami packs.
 */
public final class MegumiShikigamiRuntime {
	/**
	 * One pack per type (issue #107 D1): the owner's map is keyed by shikigami type, so summoning
	 * Nue no longer displaces the Toad. An {@link EnumMap} keeps the iteration order the enum's own
	 * — which makes "the owner's first pack" a stable reading for the single-pack accessors — and
	 * every mutation happens on the server thread (runtimes, hotbar hooks and the dev control
	 * surface all dispatch there).
	 */
	private static final Map<UUID, Map<MegumiShikigami, MegumiShikigamiPack>> PACKS =
			new ConcurrentHashMap<>();
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
		// A fresh connection starts from the default selection, so the client's cache must be told what
		// that is instead of keeping whatever the previous world left in it (the client also clears on
		// disconnect, but the two ends are independent and only this one is authoritative).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				MegumiShikigamiSync.push(handler.player));
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
			MegumiSummonCooldowns.clearAll();
		});
	}

	/**
	 * The technique key: summon the selection, or recall it when exactly that type is already out
	 * (issue #107 D1 — the types are additive, so there is no swap branch left to run, and the dogs
	 * answer the dog row themselves through their own toggle).
	 */
	public static boolean tryPrimary(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		MegumiShikigami selection = MegumiShikigamiSelection.selected(ownerId);
		if (selection == MegumiShikigami.DOGS) {
			// The dogs' runtime executes its own row of MegumiShikigamiSwapPolicy: its toggle is
			// exactly recall-when-out / summon-when-not, with the dog pack's own guards.
			return MegumiSummonRuntime.tryToggle(player, notify);
		}
		long gameTime = player.level().getGameTime();
		MegumiShikigamiPack ours = pack(ownerId, selection);
		if (ours != null && ours.summonedAtGameTime() == gameTime) {
			// Same-tick duplicate: key repeat or a doubled packet must not summon-then-recall.
			return true;
		}
		MegumiShikigamiSwapPolicy.Action action = MegumiShikigamiSwapPolicy.decide(
				selection, activeTypes(ownerId), MegumiSummonRuntime.pack(ownerId) != null);
		if (action == MegumiShikigamiSwapPolicy.Action.RECALL_SELF) {
			teardownType(player.getServer(), ownerId, selection, TeardownReason.RECALL);
			return true;
		}
		if (MegumiPartialRuntime.isActiveForType(ownerId, selection)) {
			// Spec §19 (issue #108): the same type cannot be materialized twice, so a partial has to
			// end before the full body may be summoned.
			return rejectPartial(player, selection, notify);
		}
		if (MegumiSummonCooldowns.onCooldown(ownerId, selection, gameTime)) {
			return MegumiSummonRuntime.rejectRecharging(player, selection, notify);
		}
		return summon(player, selection, notify);
	}

	/** The sneaking technique key: advance the selection to the next usable shikigami; never costs anything. */
	public static boolean tryCycle(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		if (MegumiPartialRuntime.isAnyActive(ownerId)) {
			// Issue #108 D11/§20: the selection is frozen while a partial is materialized — the key
			// that would swap the shikigami must not silently retarget the partial form.
			if (notify) {
				player.displayClientMessage(Component.translatable(
						"message.jujutsumod.megumi.shikigami.selection_locked"), true);
			}
			return false;
		}
		long now = player.level().getGameTime();
		MegumiShikigami next = MegumiShikigamiSelection.cycleAvailable(ownerId,
				type -> !MegumiSummonCooldowns.onCooldown(ownerId, type, now));
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.selected",
					Component.translatable(nameKey(next))), true);
		}
		MegumiShikigamiSync.push(player);
		return true;
	}

	/**
	 * The sneaking technique key command: one aim, every body (issue #107 D1/D5). The target is
	 * resolved once against the owner's aim and handed to every living, commandable body of both
	 * families — dogs and shikigami alike. An aim that names nothing is the cancel order: it clears
	 * every MANUAL mark the owner placed and lets the packs fall back to their own reads, which is
	 * what "sic into thin air" means once a body can hold a target without being told to.
	 */
	public static boolean trySic(ServerPlayer player, boolean notify) {
		UUID ownerId = player.getUUID();
		List<MegumiShikigamiEntity> living = livingBodiesAll(player.getServer(), ownerId);
		List<MegumiDivineDogEntity> livingDogs = MegumiSummonRuntime.livingDogs(player.getServer(), ownerId);
		if (living.isEmpty() && livingDogs.isEmpty()) {
			reconcile(player.getServer(), ownerId, RemovalCause.TICK);
			MegumiSummonRuntime.reconcile(player.getServer(), ownerId, MegumiSummonRuntime.RemovalCause.TICK);
			if (notify) {
				// The dog family keeps its own wording when the dogs are what the player is commanding.
				player.displayClientMessage(Component.translatable(
						MegumiShikigamiSelection.selected(ownerId) == MegumiShikigami.DOGS
								? "message.jujutsumod.megumi.dogs.none_out"
								: "message.jujutsumod.megumi.shikigami.none"), true);
			}
			return false;
		}
		List<MegumiShikigamiEntity> bodies = commandableBodies(living);
		List<MegumiDivineDogEntity> dogs = MegumiSummonRuntime.commandableDogs(livingDogs);
		if (bodies.isEmpty() && dogs.isEmpty()) {
			// Every body is still materializing: the key has nobody to confirm an order to, and the
			// silent refusal is the reading it has always had.
			return false;
		}
		ServerLevel level = player.level();
		LivingEntity target = resolveSicTarget(level, player);
		if (target == null) {
			int cleared = clearManualMarks(living, livingDogs);
			if (cleared == 0) {
				return false;
			}
			if (notify) {
				player.displayClientMessage(Component.translatable(
						"message.jujutsumod.megumi.shikigami.sic_cleared"), true);
			}
			return true;
		}
		for (MegumiShikigamiEntity body : bodies) {
			body.assignSicTarget(target);
		}
		for (MegumiDivineDogEntity dog : dogs) {
			dog.assignSicTarget(target);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SNAP,
				SoundSource.PLAYERS, 0.66f, 0.88f);
		if (!bodies.isEmpty()) {
			bodies.getFirst().playSicSound();
		} else {
			dogs.getFirst().playSicSound();
		}
		Vec3 anchorOffset = new Vec3(0.0, target.getBbHeight() * 0.55, 0.0);
		if (!bodies.isEmpty()) {
			broadcastCue(level, player, MegumiVfxIds.SHIKIGAMI_SIC, target.position(), target.getId(), anchorOffset);
		}
		if (!dogs.isEmpty()) {
			broadcastCue(level, player, MegumiVfxIds.DOGS_SIC, target.position(), target.getId(), anchorOffset);
		}
		MegumiSummonRuntime.startCooldownIfLonger(player, CharacterAbility.PRIMARY_SNEAK,
				MegumiShikigamiProfile.SIC_COOLDOWN_TICKS);
		return true;
	}

	/** The body the owner's aim resolves to, re-verified against the same eligibility a pack uses. */
	private static LivingEntity resolveSicTarget(ServerLevel level, ServerPlayer player) {
		TargetResolver.Result result = TargetResolver.resolve(
				level, player, MegumiShikigamiProfile.SIC_RANGE,
				target -> MegumiSummonRuntime.isEligibleTarget(player, target));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return null;
		}
		Entity resolved = level.getEntity(result.entityId().get());
		return resolved instanceof LivingEntity target
				&& MegumiSummonRuntime.isEligibleTarget(player, target)
				&& player.hasLineOfSight(target) ? target : null;
	}

	/**
	 * The cancel order (D5): only the owner's own orders are dropped. A retaliation mark expires on
	 * its own pass and an autonomous mark belongs to the coordinator, so neither is the key's to
	 * take away. Returns how many orders were actually cancelled.
	 */
	private static int clearManualMarks(
			List<MegumiShikigamiEntity> bodies, List<MegumiDivineDogEntity> dogs) {
		int cleared = 0;
		for (MegumiShikigamiEntity body : bodies) {
			if (body.markKind() == MegumiMarkKind.MANUAL) {
				body.clearSicCommand();
				cleared++;
			}
		}
		for (MegumiDivineDogEntity dog : dogs) {
			if (dog.markKind() == MegumiMarkKind.MANUAL) {
				dog.clearSicCommand();
				cleared++;
			}
		}
		return cleared;
	}

	/** Clears the player's saved selection; wired to disconnect (unit-testable seam). */
	static void onPlayerDisconnect(UUID playerId) {
		MegumiShikigamiSelection.clear(playerId);
		MegumiSummonCooldowns.clear(playerId);
	}

	/**
	 * The single owner-wide destructive entry point: removes every pack record of the owner and
	 * sweeps every owned body. The reasons that reach it (deselect, respawn, disconnect, dimension
	 * change, server stop, fixture reset) are owner-scoped by nature; a recall or a death belongs to
	 * one type and goes through {@link #teardownType}.
	 */
	public static void teardown(MinecraftServer server, UUID ownerId, TeardownReason reason) {
		teardown(server, ownerId, null, reason);
	}

	/** One type's teardown (issue #107 D1): the recall of Nue must not sweep the Toad's pack. */
	static void teardownType(
			MinecraftServer server, UUID ownerId, MegumiShikigami type, TeardownReason reason) {
		teardown(server, ownerId, type, reason);
	}

	/**
	 * @param onlyType the single type to sweep, or null to sweep every pack of the owner
	 */
	private static void teardown(
			MinecraftServer server, UUID ownerId, MegumiShikigami onlyType, TeardownReason reason) {
		if (server == null || !TEARDOWN_IN_PROGRESS.add(ownerId)) {
			return;
		}
		List<MegumiShikigamiPack> removed = onlyType == null
				? removeAllPacks(ownerId)
				: removePack(ownerId, onlyType);
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		List<UUID> sweptBodyIds = new ArrayList<>();
		try {
			for (ServerLevel level : server.getAllLevels()) {
				for (MegumiShikigamiEntity body : new ArrayList<>(level.getEntities(
						EntityTypeTest.forClass(MegumiShikigamiEntity.class),
						candidate -> ownerId.equals(candidate.ownerUuid())))) {
					if (onlyType != null && body.shikigamiType() != onlyType) {
						continue;
					}
					sweptBodyIds.add(body.getUUID());
					boolean belongedToRemovedPack = removed.stream().anyMatch(
							pack -> pack.contains(body.getUUID(), body.summonToken(), body.level().dimension()));
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
		for (MegumiShikigamiPack pack : removed) {
			startTeardownCooldown(server, ownerId, pack.type(), reason);
		}
		// Failure memory is keyed by body id, not owner id: clear exactly the bodies this sweep
		// took down. The coordinator's retainOnly sweep is the backstop for anything missed.
		for (UUID bodyId : sweptBodyIds) {
			MegumiFailureMemory.clear(bodyId);
		}
		// The pack records are gone whether or not they cost anything, so the selector's SUMMONED
		// markers and cooldown rows must be republished — one push per sweep, not per pack.
		MegumiShikigamiSync.push(server.getPlayerList().getPlayer(ownerId));
	}

	/** The reason table is unchanged — only the storage moved off the shared PRIMARY slot per type. */
	private static void startTeardownCooldown(
			MinecraftServer server, UUID ownerId, MegumiShikigami type, TeardownReason reason) {
		int ticks = reason.appliesRecallCooldown() ? MegumiShikigamiProfile.recallCooldownTicks(type)
				: reason.appliesDeathCooldown() ? MegumiShikigamiProfile.deathCooldownTicks(type)
				: reason.appliesExpiryCooldown() ? MegumiShikigamiProfile.expiryCooldownTicks(type)
				: 0;
		MegumiSummonRuntime.startSummonCooldown(server, ownerId, type, ticks);
	}

	/** The owner's pack of exactly this type, or null (issue #107: several types may be out at once). */
	static MegumiShikigamiPack pack(UUID ownerId, MegumiShikigami type) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.get(ownerId);
		return perType == null ? null : perType.get(type);
	}

	/** Every live pack of this owner, one per type, in enum order. Never null. */
	static List<MegumiShikigamiPack> packs(UUID ownerId) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.get(ownerId);
		return perType == null ? List.of() : List.copyOf(perType.values());
	}

	/** The types the owner has out; the reading {@link MegumiShikigamiSwapPolicy} decides on. */
	private static Set<MegumiShikigami> activeTypes(UUID ownerId) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.get(ownerId);
		return perType == null ? Set.of() : Set.copyOf(perType.keySet());
	}

	private static void putPack(UUID ownerId, MegumiShikigamiPack pack) {
		PACKS.computeIfAbsent(ownerId, key -> new EnumMap<>(MegumiShikigami.class))
				.put(pack.type(), pack);
	}

	/** Removes one type's record and collapses the owner's row when it was the last one. */
	private static List<MegumiShikigamiPack> removePack(UUID ownerId, MegumiShikigami type) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.get(ownerId);
		if (perType == null) {
			return List.of();
		}
		MegumiShikigamiPack removed = perType.remove(type);
		if (perType.isEmpty()) {
			PACKS.remove(ownerId, perType);
		}
		return removed == null ? List.of() : List.of(removed);
	}

	/** Removes every type's record of this owner and returns them for the cooldown pass. */
	private static List<MegumiShikigamiPack> removeAllPacks(UUID ownerId) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.remove(ownerId);
		return perType == null ? List.of() : List.copyOf(perType.values());
	}

	/** Every living summoned body of this owner across all types (coordinator + sic fan-out). */
	public static List<MegumiShikigamiEntity> livingBodiesAll(
			MinecraftServer server, UUID ownerId) {
		List<MegumiShikigamiEntity> living = new ArrayList<>();
		for (MegumiShikigamiPack pack : packs(ownerId)) {
			living.addAll(livingBodies(server, ownerId, pack));
		}
		return List.copyOf(living);
	}

	/** The bodies of {@code living} that can answer the sic command right now (issue #107). */
	private static List<MegumiShikigamiEntity> commandableBodies(List<MegumiShikigamiEntity> living) {
		return living.stream()
				.filter(MegumiShikigamiEntity::acceptsSicCommand)
				.toList();
	}

	/** Live snapshots of every pack the owner holds, one per type (issue #107 coexistence). */
	public static List<PackView> packViews(MinecraftServer server, UUID ownerId) {
		if (server == null) {
			return List.of();
		}
		List<PackView> views = new ArrayList<>();
		for (MegumiShikigamiPack pack : packs(ownerId)) {
			views.add(packView(server, ownerId, pack));
		}
		return List.copyOf(views);
	}

	/**
	 * Live snapshot of the owner's first pack, if one exists — the lowest-ordinal type that is out,
	 * so a scenario that summoned exactly one type reads back exactly that pack. Exists for the dev
	 * control surface + gametests; {@link #packViews} is the coexistence-aware reading.
	 */
	public static Optional<PackView> packView(MinecraftServer server, UUID ownerId) {
		if (server == null) {
			return Optional.empty();
		}
		for (MegumiShikigamiPack pack : packs(ownerId)) {
			return Optional.of(packView(server, ownerId, pack));
		}
		return Optional.empty();
	}

	private static PackView packView(MinecraftServer server, UUID ownerId, MegumiShikigamiPack pack) {
		List<MegumiShikigamiEntity> living = livingBodies(server, ownerId, pack);
		boolean anchorAlive = living.stream().anyMatch(body -> body.getUUID().equals(pack.anchorId()));
		return new PackView(pack.type().id(), pack.dimension().location().toString(),
				anchorAlive, living.size(), pack.summonedAtGameTime(), pack.anchorId().toString());
	}

	/** Read-only identity of one owner's live pack for observation. Exists for the dev control surface + gametests. */
	public record PackView(String type, String dimension, boolean anchorAlive, int aliveBodies,
			long summonedAtGameTime, String anchorId) {}

	/** Registers a body spawned after the initial summon (Rabbit Escape upkeep) into its type's record. */
	static void registerExtraBody(UUID ownerId, MegumiShikigamiEntity body) {
		Map<MegumiShikigami, MegumiShikigamiPack> perType = PACKS.get(ownerId);
		if (perType == null) {
			return;
		}
		perType.computeIfPresent(body.shikigamiType(), (key, pack) -> {
			if (!pack.dimension().equals(body.level().dimension())
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

	/**
	 * The shikigami leash (PR118 fix): a body that drifts past {@code SHIKIGAMI_LEASH_TELEPORT}
	 * blocks from its owner teleports back beside them instead of walking home — walking back is
	 * exactly where bodies used to stall on terrain and never catch up. Any sic mark is dropped
	 * too: keeping an unreachable order would rubber-band the body every retry tick — teleport
	 * out, re-path toward the mark, teleport back — so the recall always wins over the mark.
	 */
	private static void tickLeash(MegumiShikigamiEntity body, ServerPlayer owner, long gameTime) {
		if (owner == null || owner.level() != body.level()
				|| gameTime % MegumiShikigamiProfile.SHIKIGAMI_LEASH_RETRY_TICKS != 0) {
			return;
		}
		double leashSqr = MegumiShikigamiProfile.SHIKIGAMI_LEASH_TELEPORT
				* MegumiShikigamiProfile.SHIKIGAMI_LEASH_TELEPORT;
		if (body.distanceToSqr(owner) <= leashSqr) {
			return;
		}
		ServerLevel level = (ServerLevel) body.level();
		MegumiGroundSafety.findLeashPosition(level, owner.position(), body).ifPresent(destination -> {
			body.clearSicCommand();
			body.getNavigation().stop();
			body.teleportTo(level, destination.x, destination.y, destination.z,
					Set.<Relative>of(), body.getYRot(), body.getXRot(), false);
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
		MegumiShikigamiPack pack = pack(body.ownerUuid(), body.shikigamiType());
		if (pack == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(body.ownerUuid());
		long gameTime = body.level().getGameTime();
		tickLeash(body, owner, gameTime);
		if (body.isRemoved()) {
			return;
		}
		switch (body.shikigamiType()) {
			case NUE -> MegumiNueBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiNueEntity) body, gameTime);
			case TOAD -> MegumiToadBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiToadEntity) body, gameTime);
			case RABBITS -> MegumiRabbitsBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiRabbitEntity) body, gameTime);
			case ELEPHANT -> MegumiElephantBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiElephantEntity) body, gameTime);
			case SERPENT -> MegumiSerpentBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiSerpentEntity) body, gameTime);
			case DEER -> MegumiDeerBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiDeerEntity) body, gameTime);
			case OX -> MegumiOxBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiOxEntity) body, gameTime);
			case TIGER -> MegumiTigerBrain.tick((ServerLevel) body.level(), owner, pack, (MegumiTigerEntity) body, gameTime);
			case DOGS -> throw new IllegalStateException("dogs never enter the shikigami runtime");
		}
	}

	static boolean shouldHardDiscard(MegumiShikigamiEntity body) {
		UUID ownerId = body.ownerUuid();
		if (ownerId == null) {
			return true;
		}
		MegumiShikigamiPack pack = pack(ownerId, body.shikigamiType());
		if (pack == null) {
			// A swept body (recalled, swapped out or lost with its pack): the sink it started is still
			// playing, and discarding here cut the twelve-tick recall to one tick. The body has its own
			// finisher, so only a body that cannot finish may be swept.
			return !body.canFinishRecallWithoutPack();
		}
		return !pack.contains(body.getUUID(), body.summonToken(), body.level().dimension());
	}

	private static void tick(MinecraftServer server) {
		// The per-type summon ledger reads the level clock at each call site, so this runtime needs
		// no clock feed of its own — reconcile and retaliate are the only per-tick work left.
		for (UUID ownerId : Set.copyOf(PACKS.keySet())) {
			reconcile(server, ownerId, RemovalCause.TICK);
			retaliate(server, ownerId);
		}
	}

	/**
	 * The packs answer for their owner without a key press (issue #76): whoever just hit the owner,
	 * or the nearest body already aggroed on the owner, becomes the mark of every body that carries
	 * none. Every type joins (issue #107 — they coexist); a body the owner sics by hand keeps its
	 * mark — ⇧R outranks this pass.
	 */
	private static void retaliate(MinecraftServer server, UUID ownerId) {
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (packs(ownerId).isEmpty() || owner == null || TEARDOWN_IN_PROGRESS.contains(ownerId)) {
			return;
		}
		List<MegumiShikigamiEntity> living = livingBodiesAll(server, ownerId);
		if (living.isEmpty()) {
			return;
		}
		LivingEntity aggressor = MegumiSummonRuntime.retaliationTarget(owner);
		if (aggressor == null) {
			// Issue #96/#107: the mark was never meant to outlive the answer. With no aggressor in
			// reach, every mark this pass placed expires; the owner's manual sic and the coordinator's
			// autonomous mark both answer to other owners and stand.
			for (MegumiShikigamiEntity body : living) {
				if (body.sicTargetUuid() != null
						&& MegumiRetaliationPolicy.markExpiresWithoutAggressor(body.markKind())) {
					body.clearSicCommand();
					body.setTarget(null);
				}
			}
			return;
		}
		for (MegumiShikigamiEntity body : living) {
			if (body.acceptsSicCommand() && !body.hasManualSicTarget()) {
				body.assignRetaliationTarget(aggressor);
			}
		}
	}

	private static void reconcile(MinecraftServer server, UUID ownerId, RemovalCause cause) {
		if (server == null || TEARDOWN_IN_PROGRESS.contains(ownerId)) {
			return;
		}
		for (MegumiShikigamiPack pack : packs(ownerId)) {
			List<MegumiShikigamiEntity> living = livingBodies(server, ownerId, pack);
			if (living.isEmpty()) {
				// The pack record outlived its bodies: the whole type is charged the final-loss price
				// and its own entry is dropped — the other types keep fighting (issue #107 D1).
				if (!removePack(ownerId, pack.type()).isEmpty()) {
					MegumiSummonRuntime.startSummonCooldown(server, ownerId, pack.type(),
							MegumiShikigamiProfile.deathCooldownTicks(pack.type()));
					MegumiShikigamiSync.push(server.getPlayerList().getPlayer(ownerId));
				}
				continue;
			}
			boolean anchorAlive = living.stream().anyMatch(body -> body.getUUID().equals(pack.anchorId()));
			if (!anchorAlive) {
				// The keystone body is gone: this type disperses (Rabbit Escape's canon rule).
				teardownType(server, ownerId, pack.type(), TeardownReason.DEATH);
				continue;
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
	 * separate so a refused summon leaves the world untouched — the cooldown and partial gates run
	 * before anything is spawned.
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
		putPack(player.getUUID(), new MegumiShikigamiPack(
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
				case SERPENT -> broadcastCue(level, player, MegumiVfxIds.SERPENT_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case DEER -> broadcastCue(level, player, MegumiVfxIds.DEER_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case OX -> broadcastCue(level, player, MegumiVfxIds.OX_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case TIGER -> broadcastCue(level, player, MegumiVfxIds.TIGER_SUMMON,
						body.position(), body.getId(), Vec3.ZERO);
				case DOGS -> throw new IllegalStateException("dogs do not use the shikigami runtime");
			}
		}
		// One player-anchored body cue per accepted summon: the per-type hand-sign clip fires only
		// here — after the pack is committed — so a refused summon, a recall and a cooldown gate
		// never play a sign (spec §10). Tracking clients resolve the anchor to the player model.
		switch (type) {
			case SERPENT -> broadcastCue(level, player, MegumiVfxIds.SERPENT_SUMMON_BODY,
					player.position(), player.getId(), Vec3.ZERO);
			case DEER -> broadcastCue(level, player, MegumiVfxIds.DEER_SUMMON_BODY,
					player.position(), player.getId(), Vec3.ZERO);
			case OX -> broadcastCue(level, player, MegumiVfxIds.OX_SUMMON_BODY,
					player.position(), player.getId(), Vec3.ZERO);
			case TIGER -> broadcastCue(level, player, MegumiVfxIds.TIGER_SUMMON_BODY,
					player.position(), player.getId(), Vec3.ZERO);
			case DOGS, NUE, TOAD, RABBITS, ELEPHANT -> { }
		}
		// The pack is live: the owner's snapshot marks this type as out without implying any despawn.
		MegumiShikigamiSync.push(player);
		return true;
	}

	private static List<MegumiShikigamiEntity> spawnBodies(
			ServerLevel level, ServerPlayer owner, MegumiShikigami type, long token) {
		return switch (type) {
			case NUE -> spawnNue(level, owner, token);
			case TOAD -> spawnToad(level, owner, token);
			case RABBITS -> spawnRabbits(level, owner, token);
			case ELEPHANT -> spawnElephant(level, owner, token);
			case SERPENT -> spawnSerpent(level, owner, token);
			case DEER -> spawnDeer(level, owner, token);
			case OX -> spawnOx(level, owner, token);
			case TIGER -> spawnTiger(level, owner, token);
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

	private static MegumiShikigamiEntity spawnGroundBody(
			ServerLevel level, ServerPlayer owner, long token,
			EntityType<? extends MegumiShikigamiEntity> entityType,
			java.util.function.BiFunction<EntityType<? extends MegumiShikigamiEntity>, Level, ? extends MegumiShikigamiEntity> factory) {
		Vec3 spot = MegumiShikigamiSpawnPlacement.ground(level, owner.position(),
				owner.getYRot(), entityType.getDimensions());
		if (spot == null) {
			return null;
		}
		MegumiShikigamiEntity body = factory.apply(entityType, level);
		body.setPos(spot);
		body.setYRot(owner.getYRot());
		body.setTame(true, false);
		body.setOwner(owner);
		body.configureSummon(owner.getUUID(), token);
		return body;
	}

	private static List<MegumiShikigamiEntity> spawnSerpent(ServerLevel level, ServerPlayer owner, long token) {
		MegumiShikigamiEntity body = spawnGroundBody(level, owner, token,
				JujutsuEntities.MEGUMI_SERPENT, MegumiSerpentEntity::new);
		return body == null ? List.of() : List.of(body);
	}

	private static List<MegumiShikigamiEntity> spawnDeer(ServerLevel level, ServerPlayer owner, long token) {
		MegumiShikigamiEntity body = spawnGroundBody(level, owner, token,
				JujutsuEntities.MEGUMI_DEER, MegumiDeerEntity::new);
		return body == null ? List.of() : List.of(body);
	}

	private static List<MegumiShikigamiEntity> spawnOx(ServerLevel level, ServerPlayer owner, long token) {
		MegumiShikigamiEntity body = spawnGroundBody(level, owner, token,
				JujutsuEntities.MEGUMI_OX, MegumiOxEntity::new);
		return body == null ? List.of() : List.of(body);
	}

	private static List<MegumiShikigamiEntity> spawnTiger(ServerLevel level, ServerPlayer owner, long token) {
		MegumiShikigamiEntity body = spawnGroundBody(level, owner, token,
				JujutsuEntities.MEGUMI_TIGER, MegumiTigerEntity::new);
		return body == null ? List.of() : List.of(body);
	}

	private static int anchorIndex(MegumiShikigami type, int size, RandomSource random) {
		return switch (type) {
			case RABBITS -> random.nextInt(size);
			case DOGS -> throw new IllegalStateException("dogs do not use the shikigami runtime");
			case NUE, TOAD, ELEPHANT, SERPENT, DEER, OX, TIGER -> 0;
		};
	}

	private static boolean rejectNoRoom(ServerPlayer player, boolean notify) {
		if (notify) {
			player.displayClientMessage(Component.translatable("message.jujutsumod.megumi.shikigami.no_room"), true);
		}
		return false;
	}

	/**
	 * Spec §19 (issue #108): the same type is materialized in its partial form, and one shikigami
	 * cannot be out twice — the partial has to end before the full body may be summoned.
	 */
	private static boolean rejectPartial(ServerPlayer player, MegumiShikigami type, boolean notify) {
		if (notify) {
			player.displayClientMessage(Component.translatable(
					"message.jujutsumod.megumi.shikigami.partial_materialized",
					Component.translatable(nameKey(type))), true);
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
		broadcastCue(level, owner, VfxCues.anchoredWithOffset(effectId, origin, anchorEntityId, anchorOffset, 1,
				level.getGameTime(), owner.getRandom().nextLong()));
	}

	/**
	 * Directed variant for visuals whose source and impact point are both meaningful. It intentionally
	 * routes through the same radius broadcast as the legacy overload, so audience filtering cannot
	 * diverge between a directed and an ordinary shikigami cue.
	 */
	static void broadcastCue(ServerLevel level, ServerPlayer owner, ResourceLocation effectId,
			Vec3 origin, int anchorEntityId, Vec3 anchorPosition, Vec3 direction) {
		if (owner == null) {
			return;
		}
		broadcastCue(level, owner, directedCue(effectId, origin, anchorEntityId, anchorPosition, 1,
				level.getGameTime(), owner.getRandom().nextLong(), direction));
	}

	/** Production/test seam: the Brain builds its directed payload through this exact factory. */
	static VfxCue directedCue(ResourceLocation effectId, Vec3 origin, int anchorEntityId,
			Vec3 anchorPosition, int intensity, long gameTime, long seed, Vec3 direction) {
		return VfxCues.anchoredDirected(effectId, origin, anchorEntityId, anchorPosition, intensity,
				gameTime, seed, direction);
	}

	static void broadcastCue(ServerLevel level, ServerPlayer owner, VfxCue cue) {
		if (owner == null || cue == null) {
			return;
		}
		JujutsuNetworking.broadcastVfxCue(level, cue.origin(), MegumiProfile.VFX_DELIVERY_RADIUS, cue);
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
		DISCONNECT,
		RESPAWN,
		DIMENSION_CHANGE,
		SERVER_STOPPING,
		DESELECTED,
		FIXTURE_RESET,
		/** The body's own lifetime ran out (Rabbit Escape). Priced by the expiry row, not by recall. */
		EXPIRED;

		/** Whether the swept bodies play their sink-out instead of vanishing on the spot. */
		boolean recallsVisually() {
			return this == RECALL || this == DESELECTED
					|| this == DIMENSION_CHANGE || this == FIXTURE_RESET || this == EXPIRED;
		}

		boolean appliesRecallCooldown() {
			// DESELECTED is deliberately absent: a vessel switch is a clean slate (issue #84), and the
			// per-type deadlines that teardown would arm have no clearing seam on the switch — the
			// shared ability-slot store used to be the thing that got wiped, so the honest reading is
			// that a dismissed vessel leaves no summon deadline behind at all.
			return this == RECALL || this == DIMENSION_CHANGE;
		}

		boolean appliesDeathCooldown() {
			return this == DEATH;
		}

		boolean appliesExpiryCooldown() {
			return this == EXPIRED;
		}
	}
}