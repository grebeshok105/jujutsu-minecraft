package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.BlackFlashFocus;
import jujutsu.mod.combat.BlackFlashImpact;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Hammer combat + Black Flash.
 * Black Flash is a flat 10% proc on accepted impacts (or forced debug), not a second-click timing window.
 * Two Black Flashes in a row trigger a chain bonus (higher mult + heal + Resonant Momentum).
 */
public final class NobaraHammerCombatRuntime {
	private static final Map<UUID, PendingAttack> PENDING = new HashMap<>();
	private static final Map<UUID, Boolean> OVERHEAD_NEXT = new HashMap<>();
	/** Consecutive Black Flash procs; resets when a roll fails. */
	private static final Map<UUID, Integer> BF_STREAK = new HashMap<>();

	private NobaraHammerCombatRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NobaraHammerCombatRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	public static boolean handleInput(ServerPlayer player) {
		InteractionHand hammerHand = heldHammerHand(player);
		if (hammerHand == null || CombatStagger.GLOBAL.isStaggered(player.getUUID(), player.level().getGameTime())) {
			return false;
		}
		if (PENDING.containsKey(player.getUUID())) {
			return false;
		}

		long now = player.level().getGameTime();
		ProjectJjkNailEntity prepared = findPreparedNail(player);
		if (prepared != null) {
			prepared.launchAt(
					player.getEyePosition().add(player.getLookAngle().scale(ProjectJjkNobaraProfile.TARGET_RANGE)),
					0,
					false);
			emit(player, NobaraVfxIds.HAMMER_NAIL_LAUNCH, player.getEyePosition(), 1);
			tryProcPreparedNailBlackFlash(player, prepared);
			player.swing(hammerHand, true);
			return true;
		}

		LivingEntity looked = lookedTarget(player);
		AttackKind kind;
		NobaraActionTimeline timeline;
		UUID targetId = looked == null ? null : looked.getUUID();
		if (OVERHEAD_NEXT.getOrDefault(player.getUUID(), false)) {
			kind = AttackKind.OVERHEAD;
			timeline = NobaraActionTimeline.OVERHEAD;
			OVERHEAD_NEXT.put(player.getUUID(), false);
		} else {
			kind = AttackKind.HORIZONTAL;
			timeline = NobaraActionTimeline.HORIZONTAL;
			OVERHEAD_NEXT.put(player.getUUID(), true);
		}
		PENDING.put(player.getUUID(), new PendingAttack(kind, now + timeline.impactTick(), now + timeline.recoveryTicks(), targetId));
		emit(player, kind == AttackKind.HORIZONTAL ? NobaraVfxIds.HAMMER_HORIZONTAL : NobaraVfxIds.HAMMER_OVERHEAD, player.getEyePosition(), 1);
		player.swing(hammerHand, true);
		return true;
	}

	public static void openNailEmbedWindow(ServerPlayer player, LivingEntity target, float damage) {
		if (player == null || target == null) {
			return;
		}
		tryProcLivingBlackFlash(player, target, BlackFlashImpact.NAIL_EMBED, damage);
	}

	private static void tick(MinecraftServer server) {
		for (var entry : List.copyOf(PENDING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			PendingAttack pending = entry.getValue();
			if (player == null || !player.isAlive()) {
				PENDING.remove(entry.getKey());
				continue;
			}
			long now = player.level().getGameTime();
			if (!pending.resolved() && now >= pending.impactAt()) {
				resolveImpact(player, pending, now);
				PENDING.put(entry.getKey(), pending.resolvedCopy());
			}
			if (now >= pending.recoveryAt()) {
				PENDING.remove(entry.getKey());
			}
		}
	}

	private static void resolveImpact(ServerPlayer player, PendingAttack pending, long now) {
		ServerLevel level = player.level();
		if (pending.kind() == AttackKind.HORIZONTAL) {
			float damage = ProjectJjkNobaraProfile.HAMMER_HORIZONTAL_DAMAGE * ResonantMomentum.damageMultiplier(player);
			List<LivingEntity> targets = sweepTargets(player);
			LivingEntity firstSuccessful = null;
			for (LivingEntity target : targets) {
				if (target.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
					if (firstSuccessful == null) {
						firstSuccessful = target;
					}
					deepenOneNail(player, target);
					CombatStagger.GLOBAL.apply(target, now, ProjectJjkNobaraProfile.LIGHT_STAGGER_TICKS);
				}
			}
			if (firstSuccessful != null) {
				tryProcLivingBlackFlash(player, firstSuccessful, BlackFlashImpact.HAMMER, damage);
			}
			return;
		}
		Entity entity = pending.targetId() == null ? null : level.getEntity(pending.targetId());
		if (!(entity instanceof LivingEntity target)
				|| !target.isAlive()
				|| player.distanceTo(target) > ProjectJjkNobaraProfile.HAMMER_MELEE_RANGE + ProjectJjkNobaraProfile.HAMMER_RANGE_TOLERANCE) {
			return;
		}
		if (pending.kind() == AttackKind.OVERHEAD) {
			float damage = ProjectJjkNobaraProfile.HAMMER_OVERHEAD_DAMAGE * ResonantMomentum.damageMultiplier(player);
			if (target.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
				deepenOneNail(player, target);
				CombatStagger.GLOBAL.apply(target, now, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
				tryProcLivingBlackFlash(player, target, BlackFlashImpact.HAMMER, damage);
			}
		}
	}

	private static void tryProcPreparedNailBlackFlash(ServerPlayer player, ProjectJjkNailEntity nail) {
		if (!rollBlackFlash(player)) {
			resetStreak(player);
			return;
		}
		boolean chain = bumpStreak(player) >= 2;
		float mult = chain
				? ProjectJjkNobaraProfile.BLACK_FLASH_CHAIN_MULTIPLIER
				: ProjectJjkNobaraProfile.BLACK_FLASH_DAMAGE_MULTIPLIER;
		nail.amplifyFlight(mult);
		BlackFlashFocus.grant(player);
		if (chain) {
			applyChainBonus(player);
		}
		Vec3 direction = nail.getDeltaMovement().lengthSqr() > 1e-6
				? nail.getDeltaMovement().normalize()
				: player.getLookAngle();
		emitDirected(player, NobaraVfxIds.BLACK_FLASH, nail.position(), chain ? 3 : 2, direction);
	}

	private static void tryProcLivingBlackFlash(
			ServerPlayer player,
			LivingEntity target,
			BlackFlashImpact impact,
			float baseDamage
	) {
		if (!rollBlackFlash(player)) {
			resetStreak(player);
			return;
		}
		boolean chain = bumpStreak(player) >= 2;
		float mult = chain
				? ProjectJjkNobaraProfile.BLACK_FLASH_CHAIN_MULTIPLIER
				: ProjectJjkNobaraProfile.BLACK_FLASH_DAMAGE_MULTIPLIER;
		float bonus = baseDamage * Math.max(0.0f, mult - 1.0f);
		if (bonus > 0.0f) {
			target.hurtServer(player.level(), NobaraDamageSources.hairpin(player.level(), player), bonus);
		}
		CombatStagger.GLOBAL.apply(target, player.level().getGameTime(), ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
		Vec3 look = player.getLookAngle();
		target.knockback(chain ? 2.4 : 2.0, -look.x, -look.z);
		BlackFlashFocus.grant(player);
		if (chain) {
			applyChainBonus(player);
		}
		emitDirected(
				player,
				NobaraVfxIds.BLACK_FLASH,
				target.position().add(0.0, target.getBbHeight() * 0.55, 0.0),
				chain ? 3 : 2,
				look);
	}

	private static boolean rollBlackFlash(ServerPlayer player) {
		if (ForcedBlackFlash.isEnabled(player)) {
			return true;
		}
		return player.getRandom().nextFloat() < ProjectJjkNobaraProfile.BLACK_FLASH_CHANCE;
	}

	private static int bumpStreak(ServerPlayer player) {
		int next = BF_STREAK.getOrDefault(player.getUUID(), 0) + 1;
		BF_STREAK.put(player.getUUID(), next);
		return next;
	}

	private static void resetStreak(ServerPlayer player) {
		BF_STREAK.put(player.getUUID(), 0);
	}

	/** Chain bonus ("Double Flash"): Resonant Momentum + small heal. */
	private static void applyChainBonus(ServerPlayer player) {
		ResonantMomentum.grant(player);
		player.heal(ProjectJjkNobaraProfile.BLACK_FLASH_CHAIN_HEAL);
	}

	private static void deepenOneNail(ServerPlayer player, LivingEntity target) {
		ProjectJjkNailEntity nail = findEmbeddedNails(player, target).stream()
				.filter(candidate -> candidate.embedDepthLevel() < 3)
				.min(Comparator.comparingInt(ProjectJjkNailEntity::embedDepthLevel).reversed()
						.thenComparing(Comparator.comparingInt(ProjectJjkNailEntity::embeddedAgeTicks).reversed())
						.thenComparing(Entity::getUUID))
				.orElse(null);
		if (nail != null && nail.deepen()) {
			emitAt(player, NobaraVfxIds.NAIL_DEEPEN, nail.position(), nail.embedDepthLevel());
		}
	}

	private static ProjectJjkNailEntity findPreparedNail(ServerPlayer player) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(ProjectJjkNobaraProfile.NAIL_CONTEXT_RANGE));
		AABB area = new AABB(start, end).inflate(ProjectJjkNobaraProfile.NAIL_CONTEXT_SCAN_INFLATE);
		return player.level().getEntitiesOfClass(ProjectJjkNailEntity.class, area,
						nail -> nail.isPrepared() && nail.isOwnedBy(player.getUUID()))
				.stream()
				.min(Comparator.comparingDouble(nail -> distanceToSegmentSqr(nail.position(), start, end)))
				.orElse(null);
	}

	private static LivingEntity lookedTarget(ServerPlayer player) {
		TargetResolver.Result result = TargetResolver.resolve(player.level(), player, ProjectJjkNobaraProfile.HAMMER_MELEE_RANGE);
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return null;
		}
		Entity entity = player.level().getEntity(result.entityId().get());
		return entity instanceof LivingEntity living ? living : null;
	}

	private static List<ProjectJjkNailEntity> findEmbeddedNails(ServerPlayer player, LivingEntity target) {
		return player.level().getEntitiesOfClass(
				ProjectJjkNailEntity.class,
				target.getBoundingBox().inflate(ProjectJjkNobaraProfile.NAIL_CONTEXT_SCAN_INFLATE),
				nail -> nail.isEmbedded()
						&& nail.isOwnedBy(player.getUUID())
						&& target.getUUID().equals(nail.anchor().stableId()));
	}

	private static List<LivingEntity> sweepTargets(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		List<LivingEntity> result = new ArrayList<>();
		for (LivingEntity target : player.level().getEntitiesOfClass(
				LivingEntity.class,
				player.getBoundingBox().inflate(ProjectJjkNobaraProfile.HAMMER_SWEEP_RADIUS),
				t -> t.isAlive() && t != player)) {
			Vec3 to = target.position().subtract(player.position());
			if (to.lengthSqr() > 0.0 && to.normalize().dot(look) >= ProjectJjkNobaraProfile.HAMMER_SWEEP_REAR_DOT) {
				result.add(target);
			}
		}
		return result;
	}

	private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double len2 = segment.lengthSqr();
		if (len2 < 1e-9) {
			return point.distanceToSqr(start);
		}
		double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / len2));
		return point.distanceToSqr(start.add(segment.scale(t)));
	}

	private static boolean isHammer(net.minecraft.world.item.ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}

	private static InteractionHand heldHammerHand(ServerPlayer player) {
		if (isHammer(player.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		if (isHammer(player.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	private static void emit(ServerPlayer player, net.minecraft.resources.ResourceLocation id, Vec3 origin, int intensity) {
		long gameTime = player.level().getGameTime();
		JujutsuNetworking.broadcastVfxCue(player.level(), player.position(), 64.0,
				new VfxCue(id, origin, player.getId(), origin.subtract(player.position()), intensity, gameTime, player.getRandom().nextLong(), Vec3.ZERO));
	}

	private static void emitAt(ServerPlayer player, net.minecraft.resources.ResourceLocation id, Vec3 origin, int intensity) {
		long gameTime = player.level().getGameTime();
		JujutsuNetworking.broadcastVfxCue(player.level(), origin, 64.0,
				new VfxCue(id, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, intensity, gameTime, player.getRandom().nextLong(), Vec3.ZERO));
	}

	private static void emitDirected(
			ServerPlayer player,
			net.minecraft.resources.ResourceLocation id,
			Vec3 origin,
			int intensity,
			Vec3 direction
	) {
		long gameTime = player.level().getGameTime();
		JujutsuNetworking.broadcastVfxCue(player.level(), player.position(), 64.0,
				new VfxCue(id, origin, player.getId(), origin.subtract(player.position()), intensity, gameTime, player.getRandom().nextLong(), direction));
	}

	private static void clear() {
		PENDING.clear();
		OVERHEAD_NEXT.clear();
		BF_STREAK.clear();
	}

	private enum AttackKind { HORIZONTAL, OVERHEAD }

	private record PendingAttack(AttackKind kind, long impactAt, long recoveryAt, UUID targetId, boolean resolved) {
		private PendingAttack(AttackKind kind, long impactAt, long recoveryAt, UUID targetId) {
			this(kind, impactAt, recoveryAt, targetId, false);
		}

		private PendingAttack resolvedCopy() {
			return new PendingAttack(kind, impactAt, recoveryAt, targetId, true);
		}
	}
}
