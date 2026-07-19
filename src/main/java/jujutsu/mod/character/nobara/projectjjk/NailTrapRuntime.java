package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

/** Server-authoritative placement, arming, collapse and impact of Shift+B nail traps. */
public final class NailTrapRuntime {
	private static final double VFX_RADIUS = 64.0;
	private static final NailTrap.Registry TRAPS = new NailTrap.Registry();
	private static final Map<UUID, CollapseState> COLLAPSES = new HashMap<>();

	private NailTrapRuntime() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NailTrapRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear(server, true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeOwned(server, handler.player.getUUID()));
	}

	public static boolean tryPlace(ServerPlayer owner) {
		ServerLevel level = owner.level();
		BlockHitResult centerHit = groundHit(level, owner, owner.getEyePosition(),
				owner.getEyePosition().add(owner.getLookAngle().scale(ProjectJjkNobaraProfile.NAIL_TRAP_PLACEMENT_RANGE)));
		if (centerHit == null || owner.getEyePosition().distanceTo(centerHit.getLocation()) > ProjectJjkNobaraProfile.NAIL_TRAP_PLACEMENT_RANGE) {
			fail(owner, "message.jujutsumod.nobara.trap.no_ground");
			return false;
		}
		List<Placement> placements = findPlacements(level, owner, centerHit.getLocation());
		if (placements.size() != ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT) {
			fail(owner, "message.jujutsumod.nobara.trap.unsupported");
			return false;
		}
		if (countNails(owner) < ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT) {
			fail(owner, "message.jujutsumod.nobara.trap.no_nails");
			return false;
		}

		consumeNails(owner, ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT);
		List<ProjectJjkNailEntity> entities = new ArrayList<>();
		for (Placement placement : placements) {
			ProjectJjkNailEntity nail = new ProjectJjkNailEntity(JujutsuEntities.PROJECTJJK_NAIL, level);
			Vec3 inward = centerHit.getLocation().subtract(placement.point()).normalize();
			nail.prepare(owner, placement.point(), inward);
			nail.markAsTrapNail();
			nail.attachToBlock(level, placement.support(), placement.point(), Direction.UP);
			if (!level.addFreshEntity(nail)) {
				entities.forEach(Entity::discard);
				refundNails(owner, ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT);
				fail(owner, "message.jujutsumod.nobara.trap.failed");
				return false;
			}
			entities.add(nail);
		}

		NailTrap trap = new NailTrap(owner.getUUID(), level.dimension().location().toString(), point(centerHit.getLocation()),
				placements.stream().map(placement -> point(placement.point())).toList(),
				entities.stream().map(Entity::getUUID).toList(), ProjectJjkNobaraProfile.NAIL_TRAP_LIFETIME_TICKS,
				ProjectJjkNobaraProfile.NAIL_TRAP_COLLAPSE_TICKS);
		TRAPS.replace(trap).ifPresent(previous -> cleanupTrap(serverLevel(owner.getServer(), previous), previous));
		COLLAPSES.remove(owner.getUUID());
		for (Placement placement : placements) emit(level, placement.point(), NobaraVfxIds.NAIL_TRAP_PLACED, 1, Vec3.ZERO);
		emit(level, centerHit.getLocation(), NobaraVfxIds.NAIL_TRAP_ARMED, 3, Vec3.ZERO);
		level.playSound(null, centerHit.getLocation().x, centerHit.getLocation().y, centerHit.getLocation().z,
				JujutsuSounds.PROJECTJJK_MAGIC, SoundSource.PLAYERS, 0.8f, 1.15f);
		owner.displayClientMessage(Component.translatable("message.jujutsumod.nobara.trap.armed"), true);
		return true;
	}

	public static boolean isTrapNail(UUID nailId) {
		if (nailId == null) return false;
		for (NailTrap trap : TRAPS.values()) if (trap.nailIds().contains(nailId)) return true;
		return false;
	}

	private static void tick(MinecraftServer server) {
		List<NailTrap> snapshot = new ArrayList<>();
		TRAPS.values().forEach(snapshot::add);
		for (NailTrap trap : snapshot) tickTrap(server, trap);
	}

	private static void tickTrap(MinecraftServer server, NailTrap trap) {
		ServerLevel level = serverLevel(server, trap);
		if (level == null) { TRAPS.remove(trap.ownerId(), trap); COLLAPSES.remove(trap.ownerId()); return; }
		if (!available(level, trap)) return;
		ServerPlayer owner = server.getPlayerList().getPlayer(trap.ownerId());
		if (owner == null || owner.level() != level) { remove(level, trap); return; }

		CollapseState collapse = COLLAPSES.get(trap.ownerId());
		if (collapse != null) {
			tickCollapse(level, owner, trap, collapse);
			return;
		}
		if (!allNailsResolved(level, trap)) {
			remove(level, trap);
			return;
		}
		trap.tick(true);
		if (trap.expired()) {
			remove(level, trap);
			return;
		}
		selectTarget(level, owner, trap).ifPresent(target -> {
			if (trap.trigger(target.getUUID())) COLLAPSES.put(trap.ownerId(), new CollapseState(target.getUUID(), 0));
		});
	}

	private static void tickCollapse(ServerLevel level, ServerPlayer owner, NailTrap trap, CollapseState collapse) {
		Entity resolved = level.getEntity(collapse.targetId());
		if (!(resolved instanceof LivingEntity target) || !target.isAlive()) {
			if (NailAnchorLifecycle.isConfirmedRemoved(collapse.targetId())) remove(level, trap);
			return;
		}
		int beat = trap.collapseBeat(collapse.elapsedTicks());
		if (beat >= 0) {
			UUID nailId = trap.nailIds().get(beat);
			Entity nail = level.getEntity(nailId);
			Vec3 from = nail == null ? vec(trap.vertices().get(beat)) : nail.position();
			Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.45, 0.0);
			if (nail != null) nail.discard();
			spawnCollapseTrail(level, from, to);
			emit(level, from, NobaraVfxIds.NAIL_TRAP_COLLAPSE, beat + 1, to.subtract(from));
		}
		int next = collapse.elapsedTicks() + 1;
		if (trap.impactDue(next)) {
			impact(level, owner, trap, target);
			return;
		}
		COLLAPSES.put(trap.ownerId(), new CollapseState(collapse.targetId(), next));
	}

	private static void impact(ServerLevel level, ServerPlayer owner, NailTrap trap, LivingEntity target) {
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		ProjectJjkNailEntity embedded = new ProjectJjkNailEntity(JujutsuEntities.PROJECTJJK_NAIL, level);
		Vec3 direction = target.position().subtract(owner.position()).normalize();
		embedded.prepare(owner, at, direction);
		embedded.attachToEntity(target, at);
		if (!level.addFreshEntity(embedded)) {
			remove(level, trap);
			return;
		}
		target.hurtServer(level, NobaraDamageSources.hairpin(level, owner), ProjectJjkNobaraProfile.NAIL_TRAP_DAMAGE);
		CombatStagger.GLOBAL.apply(target, level.getGameTime(), ProjectJjkNobaraProfile.NAIL_TRAP_INTERRUPT_TICKS);
		ProjectJjkRitualRuntime.markTarget(level, target, owner, at);

		for (UUID nailId : trap.nailIds()) {
			Entity nail = level.getEntity(nailId);
			if (nail != null) nail.discard();
		}
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.0);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, at.x, at.y, at.z, 24, 0.5, 0.45, 0.5, 0.24);
		level.playSound(null, at.x, at.y, at.z, JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 1.0f, 0.86f);
		emit(level, at, NobaraVfxIds.NAIL_TRAP_IMPACT, 3, Vec3.ZERO);
		remove(level, trap);
	}

	private static java.util.Optional<LivingEntity> selectTarget(ServerLevel level, ServerPlayer owner, NailTrap trap) {
		AABB bounds = bounds(trap);
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, bounds, living ->
				living.isAlive() && !living.getUUID().equals(owner.getUUID()) && !owner.isAlliedTo(living)
						&& (living instanceof Enemy || living instanceof ServerPlayer)
						&& trap.contains(living.getX(), living.getY(), living.getZ()));
		Map<UUID, LivingEntity> byId = new HashMap<>();
		List<NailTrap.TargetCandidate> order = candidates.stream().map(target -> {
			byId.put(target.getUUID(), target);
			return new NailTrap.TargetCandidate(target.getUUID(), target.position().distanceToSqr(vec(trap.center())));
		}).toList();
		return NailTrap.selectTarget(order).map(byId::get);
	}

	private static List<Placement> findPlacements(ServerLevel level, ServerPlayer owner, Vec3 center) {
		List<Placement> result = new ArrayList<>();
		double facing = Math.atan2(owner.getLookAngle().z, owner.getLookAngle().x);
		for (int index = 0; index < ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT; index++) {
			double angle = facing + Math.PI * 2.0 * index / ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT;
			Vec3 ideal = center.add(Math.cos(angle) * ProjectJjkNobaraProfile.NAIL_TRAP_RADIUS, 0.0,
					Math.sin(angle) * ProjectJjkNobaraProfile.NAIL_TRAP_RADIUS);
			BlockHitResult hit = groundHit(level, owner, ideal.add(0.0, ProjectJjkNobaraProfile.NAIL_TRAP_PRISM_HEIGHT, 0.0),
					ideal.add(0.0, -ProjectJjkNobaraProfile.NAIL_TRAP_PRISM_HEIGHT, 0.0));
			if (hit == null || hit.getDirection() != Direction.UP || !validSupport(level, hit.getBlockPos())) return List.of();
			result.add(new Placement(hit.getBlockPos(), hit.getLocation().add(0.0, 0.04, 0.0)));
		}
		return result;
	}

	private static BlockHitResult groundHit(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
	}

	private static boolean validSupport(ServerLevel level, BlockPos support) {
		BlockState state = level.getBlockState(support);
		BlockState above = level.getBlockState(support.above());
		return state.isFaceSturdy(level, support, Direction.UP) && (above.isAir() || above.canBeReplaced());
	}

	private static boolean allNailsResolved(ServerLevel level, NailTrap trap) {
		for (UUID nailId : trap.nailIds()) {
			Entity entity = level.getEntity(nailId);
			if (!(entity instanceof ProjectJjkNailEntity nail) || !nail.isOwnedBy(trap.ownerId())) return false;
		}
		return true;
	}

	private static boolean available(ServerLevel level, NailTrap trap) {
		for (NailTrap.Point vertex : trap.vertices()) if (!level.hasChunkAt(BlockPos.containing(vertex.x(), vertex.y(), vertex.z()))) return false;
		return true;
	}

	private static AABB bounds(NailTrap trap) {
		double minX = trap.vertices().stream().mapToDouble(NailTrap.Point::x).min().orElse(trap.center().x());
		double maxX = trap.vertices().stream().mapToDouble(NailTrap.Point::x).max().orElse(trap.center().x());
		double minZ = trap.vertices().stream().mapToDouble(NailTrap.Point::z).min().orElse(trap.center().z());
		double maxZ = trap.vertices().stream().mapToDouble(NailTrap.Point::z).max().orElse(trap.center().z());
		return new AABB(minX, trap.center().y() - 0.5, minZ, maxX,
				trap.center().y() + ProjectJjkNobaraProfile.NAIL_TRAP_PRISM_HEIGHT, maxZ);
	}

	private static int countNails(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isNail(stack)) count += stack.getCount();
		}
		return count;
	}

	private static void consumeNails(ServerPlayer player, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!isNail(stack)) continue;
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
		}
	}

	private static void refundNails(ServerPlayer player, int amount) {
		ItemStack refund = new ItemStack(JujutsuItems.HAIRPIN_NAIL, amount);
		if (!player.getInventory().add(refund)) player.drop(refund, false);
	}

	private static boolean isNail(ItemStack stack) {
		return stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL);
	}

	private static void spawnCollapseTrail(ServerLevel level, Vec3 from, Vec3 to) {
		for (int step = 0; step <= 8; step++) {
			Vec3 at = from.lerp(to, step / 8.0);
			level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.02);
		}
	}

	private static void emit(ServerLevel level, Vec3 at, ResourceLocation id, int intensity, Vec3 direction) {
		JujutsuNetworking.broadcastVfxCue(level, at, VFX_RADIUS,
				new VfxCue(id, at, VfxCue.NO_ANCHOR, Vec3.ZERO, intensity, level.getGameTime(), level.random.nextLong(), direction));
	}

	private static void remove(ServerLevel level, NailTrap trap) {
		cleanupTrap(level, trap);
		TRAPS.remove(trap.ownerId(), trap);
		COLLAPSES.remove(trap.ownerId());
	}

	private static void cleanupTrap(ServerLevel level, NailTrap trap) {
		if (level == null) return;
		for (UUID nailId : trap.nailIds()) {
			Entity nail = level.getEntity(nailId);
			if (nail != null) nail.discard();
		}
	}

	private static void clear(MinecraftServer server, boolean discardNails) {
		if (discardNails) for (NailTrap trap : TRAPS.values()) cleanupTrap(serverLevel(server, trap), trap);
		TRAPS.clear();
		COLLAPSES.clear();
	}

	private static void removeOwned(MinecraftServer server, UUID ownerId) {
		TRAPS.get(ownerId).ifPresent(trap -> {
			ServerLevel level = serverLevel(server, trap);
			if (level != null) remove(level, trap);
			else { TRAPS.remove(ownerId, trap); COLLAPSES.remove(ownerId); }
		});
	}

	private static ServerLevel serverLevel(MinecraftServer server, NailTrap trap) {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(trap.dimensionId()));
		return server.getLevel(key);
	}

	private static Vec3 vec(NailTrap.Point point) { return new Vec3(point.x(), point.y(), point.z()); }
	private static NailTrap.Point point(Vec3 point) { return new NailTrap.Point(point.x, point.y, point.z); }
	private static void fail(ServerPlayer player, String key) { player.displayClientMessage(Component.translatable(key), true); }

	private record Placement(BlockPos support, Vec3 point) {}
	private record CollapseState(UUID targetId, int elapsedTicks) {}
}
