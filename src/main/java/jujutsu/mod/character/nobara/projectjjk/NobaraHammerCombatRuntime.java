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
import jujutsu.mod.combat.BlackFlashWindow;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

public final class NobaraHammerCombatRuntime {
	private static final Map<UUID, PendingAttack> PENDING = new HashMap<>();
	private static final Map<UUID, BlackFlashWindow> WINDOWS = new HashMap<>();
	private static final Map<UUID, Boolean> OVERHEAD_NEXT = new HashMap<>();

	private NobaraHammerCombatRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NobaraHammerCombatRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	public static boolean handleInput(ServerPlayer player) {
		InteractionHand hammerHand = heldHammerHand(player);
		if (hammerHand == null || CombatStagger.GLOBAL.isStaggered(player.getUUID(), player.level().getGameTime())) return false;
		long now = player.level().getGameTime();
		BlackFlashWindow window = WINDOWS.get(player.getUUID());
		if (window != null && window.consume(now)) return resolveBlackFlash(player, window);
		if (PENDING.containsKey(player.getUUID())) return false;

		ProjectJjkNailEntity prepared = findPreparedNail(player);
		if (prepared != null) {
			prepared.launchAt(player.getEyePosition().add(player.getLookAngle().scale(ProjectJjkNobaraProfile.TARGET_RANGE)), 0, false);
			emit(player, NobaraVfxIds.HAMMER_NAIL_LAUNCH, player.getEyePosition(), 1);
			WINDOWS.put(player.getUUID(), new BlackFlashWindow(prepared.getUUID(), BlackFlashImpact.PREPARED_NAIL, now, now + ProjectJjkNobaraProfile.BLACK_FLASH_WINDOW_LATE_TICKS, 0.0f));
			if (ForcedBlackFlash.isEnabled(player)) resolveBlackFlash(player, WINDOWS.remove(player.getUUID()));
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
		if (player == null || target == null) return;
		long now = player.level().getGameTime();
		openWindow(player, target, BlackFlashImpact.NAIL_EMBED, damage, now);
	}

	private static void tick(MinecraftServer server) {
		for (var entry : List.copyOf(PENDING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			PendingAttack pending = entry.getValue();
			if (player == null || !player.isAlive()) { PENDING.remove(entry.getKey()); continue; }
			long now = player.level().getGameTime();
			if (!pending.resolved() && now >= pending.impactAt()) {
				resolveImpact(player, pending, now);
				PENDING.put(entry.getKey(), pending.resolvedCopy());
			}
			if (now >= pending.recoveryAt()) PENDING.remove(entry.getKey());
		}
		WINDOWS.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			return player == null || !entry.getValue().accepts(player.level().getGameTime()) && !PENDING.containsKey(entry.getKey());
		});
	}

	private static void resolveImpact(ServerPlayer player, PendingAttack pending, long now) {
		ServerLevel level = player.level();
		if (pending.kind() == AttackKind.HORIZONTAL) {
			float damage = ProjectJjkNobaraProfile.HAMMER_HORIZONTAL_DAMAGE * ResonantMomentum.damageMultiplier(player);
			List<LivingEntity> targets = sweepTargets(player);
			LivingEntity firstSuccessful = null;
			for (LivingEntity target : targets) {
				if (target.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
					if (firstSuccessful == null) firstSuccessful = target;
					deepenOneNail(player, target);
					CombatStagger.GLOBAL.apply(target, now, ProjectJjkNobaraProfile.LIGHT_STAGGER_TICKS);
				}
			}
			if (firstSuccessful != null) openWindow(player, firstSuccessful, BlackFlashImpact.HAMMER, damage, now);
			return;
		}
		Entity entity = pending.targetId() == null ? null : level.getEntity(pending.targetId());
		if (!(entity instanceof LivingEntity target) || !target.isAlive() || player.distanceTo(target) > ProjectJjkNobaraProfile.HAMMER_MELEE_RANGE + ProjectJjkNobaraProfile.HAMMER_RANGE_TOLERANCE) return;
		if (pending.kind() == AttackKind.OVERHEAD) {
			float damage = ProjectJjkNobaraProfile.HAMMER_OVERHEAD_DAMAGE * ResonantMomentum.damageMultiplier(player);
			if (target.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
				deepenOneNail(player, target);
				CombatStagger.GLOBAL.apply(target, now, ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
				openWindow(player, target, BlackFlashImpact.HAMMER, damage, now);
			}
		}
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

	private static void openWindow(ServerPlayer player, LivingEntity target, BlackFlashImpact impact, float damage, long now) {
		BlackFlashWindow window = new BlackFlashWindow(target.getUUID(), impact, now, now + ProjectJjkNobaraProfile.BLACK_FLASH_WINDOW_LATE_TICKS, damage);
		if (ForcedBlackFlash.isEnabled(player)) resolveBlackFlash(player, window);
		else WINDOWS.put(player.getUUID(), window);
	}

	private static boolean resolveBlackFlash(ServerPlayer player, BlackFlashWindow window) {
		Entity entity = player.level().getEntity(window.targetId());
		if (window.impact() == BlackFlashImpact.PREPARED_NAIL && entity instanceof ProjectJjkNailEntity nail) {
			nail.amplifyFlight(ProjectJjkNobaraProfile.BLACK_FLASH_DAMAGE_MULTIPLIER);
			BlackFlashFocus.grant(player);
			Vec3 direction = nail.getDeltaMovement().lengthSqr() > 1e-6 ? nail.getDeltaMovement().normalize() : player.getLookAngle();
			emitDirected(player, NobaraVfxIds.BLACK_FLASH, nail.position(), 2, direction);
			return true;
		}
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) return false;
		target.hurtServer(player.level(), NobaraDamageSources.hairpin(player.level(), player), window.bonusDamage(ProjectJjkNobaraProfile.BLACK_FLASH_DAMAGE_MULTIPLIER));
		CombatStagger.GLOBAL.apply(target, player.level().getGameTime(), ProjectJjkNobaraProfile.HEAVY_STAGGER_TICKS);
		Vec3 look = player.getLookAngle();
		target.knockback(2.0, -look.x, -look.z);
		BlackFlashFocus.grant(player);
		emitDirected(player, NobaraVfxIds.BLACK_FLASH, target.position().add(0.0, target.getBbHeight() * 0.55, 0.0), 2, look);
		return true;
	}

	private static ProjectJjkNailEntity findPreparedNail(ServerPlayer player) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(ProjectJjkNobaraProfile.NAIL_CONTEXT_RANGE));
		AABB area = new AABB(start, end).inflate(ProjectJjkNobaraProfile.NAIL_CONTEXT_SCAN_INFLATE);
		return player.level().getEntitiesOfClass(ProjectJjkNailEntity.class, area, nail -> nail.isPrepared() && nail.isOwnedBy(player.getUUID()))
				.stream().min(Comparator.comparingDouble(nail -> distanceToSegmentSqr(nail.position(), start, end))).orElse(null);
	}

	private static LivingEntity lookedTarget(ServerPlayer player) {
		TargetResolver.Result result = TargetResolver.resolve(player.level(), player, ProjectJjkNobaraProfile.HAMMER_MELEE_RANGE);
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) return null;
		Entity entity = player.level().getEntity(result.entityId().get());
		return entity instanceof LivingEntity living ? living : null;
	}

	private static List<ProjectJjkNailEntity> findEmbeddedNails(ServerPlayer player, LivingEntity target) {
		return player.level().getEntitiesOfClass(ProjectJjkNailEntity.class, target.getBoundingBox().inflate(ProjectJjkNobaraProfile.NAIL_CONTEXT_SCAN_INFLATE), nail -> nail.isEmbedded() && nail.isOwnedBy(player.getUUID()) && target.getUUID().equals(nail.anchor().stableId()));
	}

	private static List<LivingEntity> sweepTargets(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		List<LivingEntity> result = new ArrayList<>();
		for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ProjectJjkNobaraProfile.HAMMER_SWEEP_RADIUS), target -> target.isAlive() && target != player)) {
			Vec3 to = target.position().subtract(player.position());
			if (to.lengthSqr() > 0.0 && to.normalize().dot(look) >= ProjectJjkNobaraProfile.HAMMER_SWEEP_REAR_DOT) result.add(target);
		}
		return result;
	}

	private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / segment.lengthSqr()));
		return point.distanceToSqr(start.add(segment.scale(t)));
	}

	private static boolean isHammer(net.minecraft.world.item.ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}

	private static InteractionHand heldHammerHand(ServerPlayer player) {
		if (isHammer(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
		if (isHammer(player.getOffhandItem())) return InteractionHand.OFF_HAND;
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

	private static void emitDirected(ServerPlayer player, net.minecraft.resources.ResourceLocation id, Vec3 origin, int intensity, Vec3 direction) {
		long gameTime = player.level().getGameTime();
		JujutsuNetworking.broadcastVfxCue(player.level(), player.position(), 64.0,
				new VfxCue(id, origin, player.getId(), origin.subtract(player.position()), intensity, gameTime, player.getRandom().nextLong(), direction));
	}

	private static void clear() { PENDING.clear(); WINDOWS.clear(); OVERHEAD_NEXT.clear(); }
	private enum AttackKind { HORIZONTAL, OVERHEAD }
	private record PendingAttack(AttackKind kind, long impactAt, long recoveryAt, UUID targetId, boolean resolved) {
		private PendingAttack(AttackKind kind, long impactAt, long recoveryAt, UUID targetId) { this(kind, impactAt, recoveryAt, targetId, false); }
		private PendingAttack resolvedCopy() { return new PendingAttack(kind, impactAt, recoveryAt, targetId, true); }
	}
}
