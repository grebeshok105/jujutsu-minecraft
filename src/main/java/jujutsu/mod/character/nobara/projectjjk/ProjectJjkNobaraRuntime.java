package jujutsu.mod.character.nobara.projectjjk;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;

public final class ProjectJjkNobaraRuntime {
	private static final double IMPULSE_BROADCAST_RADIUS = 56.0;
	private static final Map<UUID, Integer> ACTIVE_EXPLOSIVE_NAILS = new ConcurrentHashMap<>();

	private ProjectJjkNobaraRuntime() {}

	public static void prepareNails(ServerPlayer player, ItemStack usedStack, int useTicks) {
		ServerLevel level = player.level();
		int desiredCount = ProjectJjkNobaraProfile.nailCountForUseTicks(useTicks);
		boolean creative = player.getAbilities().instabuild;
		int available = creative ? desiredCount : countNails(player);
		int nailCount = Math.min(desiredCount, available);
		if (nailCount <= 0) {
			return;
		}

		if (!creative) {
			consumeNails(player, usedStack, nailCount);
		}

		Vec3 look = safeDirection(player.getLookAngle());
		List<Vec3> row = preparedRow(player.getEyePosition(), look, nailCount);
		for (Vec3 position : row) {
			ProjectJjkNailEntity nail = new ProjectJjkNailEntity(JujutsuEntities.PROJECTJJK_NAIL, level);
			nail.prepare(player, position, look);
			level.addFreshEntity(nail);
			level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, position.x, position.y, position.z, 3, 0.05, 0.05, 0.05, 0.03);
			level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, position.x, position.y, position.z, 4, 0.07, 0.09, 0.07, 0.035);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 0.82f, 1.16f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SPELL_SHOT, SoundSource.PLAYERS, 0.34f, 1.42f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.HAIRPIN_PREP, SoundSource.PLAYERS, 0.62f, 1.0f);
	}

	public static boolean launchHairpin(ServerPlayer player, ItemStack hammerStack, InteractionHand hand) {
		return launchHairpin(player, hammerStack, hand, false);
	}

	public static boolean launchHairpin(ServerPlayer player, boolean explosiveImpact) {
		ItemStack mainHand = player.getMainHandItem();
		if (isHairpinHammer(mainHand)) {
			return launchHairpin(player, mainHand, InteractionHand.MAIN_HAND, explosiveImpact);
		}
		ItemStack offHand = player.getOffhandItem();
		if (isHairpinHammer(offHand)) {
			return launchHairpin(player, offHand, InteractionHand.OFF_HAND, explosiveImpact);
		}
		return false;
	}

	public static boolean launchHairpin(ServerPlayer player, ItemStack hammerStack, InteractionHand hand, boolean explosiveImpact) {
		ServerLevel level = player.level();
		List<ProjectJjkNailEntity> nails = findPreparedNails(level, player);
		if (nails.isEmpty()) {
			return false;
		}

		TargetResolver.Result target = TargetResolver.resolve(level, player, ProjectJjkNobaraProfile.TARGET_RANGE);
		Vec3 look = safeDirection(player.getLookAngle());
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		for (int index = 0; index < nails.size(); index++) {
			ProjectJjkNailEntity nail = nails.get(index);
			double centered = index - (nails.size() - 1.0) * 0.5;
			Vec3 targetPoint = target.point()
					.add(right.scale(centered * 0.11))
					.add(up.scale(((index & 1) == 0 ? 0.06 : -0.06)));
			nail.launchAt(targetPoint, ProjectJjkNobaraProfile.launchDelayForIndex(index), explosiveImpact);
		}

		// Forging anvil clang: the hammer strike must read like smithing on an anvil.
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.35f, 0.9f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.PLAYERS, 0.95f, 0.68f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.HAIRPIN_HAMMER_SNAP, SoundSource.PLAYERS, 0.9f, 1.0f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SNAP, SoundSource.PLAYERS, 1.2f, 0.82f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_CINEMATIC_WHOOSH, SoundSource.PLAYERS, 0.88f, 0.86f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), JujutsuSounds.PROJECTJJK_SPELL_SHOT, SoundSource.PLAYERS, 0.72f, 0.74f);
		JujutsuNetworking.broadcastVfxCue(level, player.position(), IMPULSE_BROADCAST_RADIUS,
				cue(level, NobaraVfxIds.HAMMER, nails.size(), player.position(), level.getGameTime(), player));
		damageHammer(player, hammerStack, hand);
		return true;
	}

	public static boolean isExplosiveLaunchLocked(ServerPlayer player) {
		return hasActiveExplosiveNails(player);
	}

	public static boolean canCastMarkedHairpin(ServerPlayer player) {
		return !isExplosiveLaunchLocked(player);
	}

	public static boolean hasActiveExplosiveNails(ServerPlayer player) {
		return ACTIVE_EXPLOSIVE_NAILS.getOrDefault(player.getUUID(), 0) > 0;
	}

	static void registerActiveExplosiveNail(UUID ownerUuid) {
		if (ownerUuid != null) {
			ACTIVE_EXPLOSIVE_NAILS.merge(ownerUuid, 1, Integer::sum);
		}
	}

	static void unregisterActiveExplosiveNail(UUID ownerUuid) {
		if (ownerUuid != null) {
			ACTIVE_EXPLOSIVE_NAILS.computeIfPresent(ownerUuid, (uuid, count) -> count <= 1 ? null : count - 1);
		}
	}

	public static void resolveNailImpact(ServerLevel level, ProjectJjkNailEntity nail, HitResult hit, boolean explosiveImpact) {
		Vec3 point = hit.getLocation();
		ServerPlayer owner = owner(level, nail.ownerUuid());
		DamageSource source = owner == null ? level.damageSources().magic() : level.damageSources().playerAttack(owner);
		LivingEntity directTarget = null;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
			directTarget = livingTarget;
			hurtTarget(level, owner, directTarget, source, ProjectJjkNobaraProfile.NAIL_DAMAGE, point, 0.9f);
			// Direct hits preserve Hairpin marks and independently advance remnant acquisition.
			if (!explosiveImpact && !(owner != null && directTarget.getUUID().equals(owner.getUUID()))) {
				ProjectJjkRitualRuntime.markTarget(level, directTarget, owner, point);
				ProjectJjkStrawDollRuntime.onOrdinaryNailHit(level, owner, directTarget, point);
			}
		}

		if (!explosiveImpact) {
			spawnPiercingImpactFeedback(level, point, nail.forwardDirection());
			return;
		}

		double impactRadius = directTarget == null ? ProjectJjkNobaraProfile.GROUND_IMPACT_RADIUS : ProjectJjkNobaraProfile.IMPACT_RADIUS;
		AABB area = new AABB(point, point).inflate(impactRadius);
		float areaDamage = ProjectJjkNobaraProfile.HAIRPIN_DAMAGE / 4.0f;
		for (Entity entity : level.getEntities(nail, area, candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
			if (directTarget != null && entity.getId() == directTarget.getId()) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				hurtTarget(level, owner, living, source, areaDamage, point, ProjectJjkNobaraProfile.HAIRPIN_KNOCKBACK);
			}
		}

		spawnCustomImpactParticles(level, point, nail.forwardDirection());
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.HAIRPIN_BLOOM, SoundSource.PLAYERS, 0.85f, 0.92f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.HAIRPIN_AFTERGLOW, SoundSource.PLAYERS, 0.4f, 0.8f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 62, 0.64, 0.52, 0.64, 0.24);
		level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 38, 0.46, 0.42, 0.46, 0.2);
		level.sendParticles(ParticleTypes.FLASH, point.x, point.y, point.z, 3, 0.12, 0.12, 0.12, 0.0);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_WHOOSH_HIT, SoundSource.PLAYERS, 0.95f, 0.74f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_EXPLODE, SoundSource.PLAYERS, 0.88f, 0.78f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, SoundSource.PLAYERS, 0.58f, 0.72f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_IMPLODE, SoundSource.PLAYERS, 0.42f, 0.9f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, SoundSource.PLAYERS, 0.56f, 1.08f);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT_2, SoundSource.PLAYERS, 0.42f, 0.96f);
		JujutsuNetworking.broadcastVfxCue(level, point, IMPULSE_BROADCAST_RADIUS,
				cue(level, NobaraVfxIds.IMPACT, 1, point, level.getGameTime()));
		if (owner != null) {
			JujutsuNetworking.sendVfxCue(owner, cue(level, NobaraVfxIds.IMPACT_SOUND, 1, point, level.getGameTime()));
		}
	}

	private static void spawnPiercingImpactFeedback(ServerLevel level, Vec3 point, Vec3 direction) {
		Vec3 forward = safeDirection(direction);
		Vec3 core = point.add(forward.scale(-0.04));
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, core.x, core.y, core.z, 3, 0.08, 0.07, 0.08, 0.025);
		level.sendParticles(JujutsuParticles.HAIRPIN_MARK_STAIN, core.x, core.y, core.z, 4, 0.12, 0.10, 0.12, 0.004);
		level.playSound(null, point.x, point.y, point.z, JujutsuSounds.PROJECTJJK_WHOOSH_HIT, SoundSource.PLAYERS, 0.42f, 1.18f);
	}

	static void spawnNailLaunchParticles(ServerLevel level, Vec3 point, Vec3 direction) {
		Vec3 forward = safeDirection(direction);
		Vec3 anchor = point.add(forward.scale(0.16));
		level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, anchor.x, anchor.y, anchor.z, 13, 0.12, 0.14, 0.12, 0.07);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y, anchor.z, 6, 0.10, 0.10, 0.10, 0.16);
		level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, anchor.x, anchor.y, anchor.z, 8, 0.16, 0.12, 0.16, 0.18);
	}

	private static void spawnCustomImpactParticles(ServerLevel level, Vec3 point, Vec3 direction) {
		Vec3 forward = safeDirection(direction);
		Vec3 core = point.add(forward.scale(-0.08));
		level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, point.x, point.y, point.z, 6, 0.08, 0.08, 0.08, 0.05);
		level.sendParticles(JujutsuParticles.HAIRPIN_SPARK, point.x, point.y, point.z, 30, 0.38, 0.30, 0.38, 0.28);
		level.sendParticles(JujutsuParticles.HAIRPIN_SNAP_CRACK, core.x, core.y, core.z, 5, 0.18, 0.12, 0.18, 0.07);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_RESIDUE, core.x, core.y, core.z, 20, 0.46, 0.34, 0.46, 0.20);
		level.sendParticles(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, point.x, point.y, point.z, 16, 0.42, 0.26, 0.42, 0.34);
		// Legacy families ported: dark stain lingers at the wound, warn edge flashes the impact ring.
		level.sendParticles(JujutsuParticles.HAIRPIN_MARK_STAIN, core.x, core.y, core.z, 9, 0.18, 0.14, 0.18, 0.008);
		level.sendParticles(JujutsuParticles.HAIRPIN_WARN_EDGE, point.x, point.y, point.z, 8, 0.34, 0.24, 0.34, 0.05);
	}

	static void spawnNailFlightTrail(ServerLevel level, Vec3 point, Vec3 direction) {
		Vec3 forward = safeDirection(direction);
		Vec3 tail = point.subtract(forward.scale(0.35));
		level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, tail.x, tail.y, tail.z, 4, 0.045, 0.055, 0.045, 0.035);
		level.sendParticles(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, tail.x, tail.y, tail.z, 1, 0.025, 0.025, 0.025, 0.018);
	}

	static void spawnPreparedNailFlame(ServerLevel level, Vec3 point, Vec3 direction) {
		Vec3 forward = safeDirection(direction);
		Vec3 center = point.add(forward.scale(0.05));
		level.sendParticles(JujutsuParticles.HAIRPIN_IGNITION_TICK, center.x, center.y, center.z, 1, 0.05, 0.06, 0.05, 0.018);
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 point, long gameTime) {
		return new VfxCue(effectId, point, VfxCue.NO_ANCHOR, Vec3.ZERO, Math.max(1, intensity), gameTime, level.random.nextLong());
	}

	private static VfxCue cue(ServerLevel level, ResourceLocation effectId, int intensity, Vec3 point, long gameTime, Entity anchor) {
		return new VfxCue(effectId, point, anchor.getId(), point.subtract(anchor.position()), Math.max(1, intensity), gameTime, level.random.nextLong());
	}

	private static List<ProjectJjkNailEntity> findPreparedNails(ServerLevel level, ServerPlayer player) {
		Vec3 anchor = player.getEyePosition();
		double range = ProjectJjkNobaraProfile.PREPARED_LAUNCH_RANGE;
		AABB playerBounds = player.getBoundingBox();
		AABB bounds = playerBounds.inflate(range);
		return level.getEntities(player, bounds, entity -> entity instanceof ProjectJjkNailEntity nail && nail.isPrepared() && nail.isOwnedBy(player.getUUID()))
				.stream()
				.map(ProjectJjkNailEntity.class::cast)
				.filter(nail -> isPreparedNailWithinLaunchRange(playerBounds, nail.position()))
				.sorted(Comparator.comparingDouble(nail -> playerBounds.distanceToSqr(nail.position())))
				.limit(ProjectJjkNobaraProfile.BARRAGE_NAILS)
				.toList();
	}

	static boolean isPreparedNailWithinLaunchRange(AABB playerBounds, Vec3 nailPosition) {
		double range = ProjectJjkNobaraProfile.PREPARED_LAUNCH_RANGE;
		return playerBounds.distanceToSqr(nailPosition) <= range * range;
	}

	private static List<Vec3> preparedRow(Vec3 origin, Vec3 look, int nailCount) {
		Vec3 forward = safeDirection(look);
		Vec3 right = rightOf(forward);
		Vec3 up = right.cross(forward).normalize();
		int count = Math.max(0, Math.min(ProjectJjkNobaraProfile.BARRAGE_NAILS, nailCount));
		return java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> {
					double centered = index - (count - 1.0) * 0.5;
					double row = count <= 4 ? 0.0 : (index < 4 ? 0.22 : -0.22);
					double column = count <= 4 ? centered : (index % 4) - 1.5;
					return origin
							.add(forward.scale(ProjectJjkNobaraProfile.PREPARED_FORWARD_OFFSET + (index % 2) * 0.06))
							.add(right.scale(column * 0.38))
							.add(up.scale(row + ProjectJjkNobaraProfile.PREPARED_VERTICAL_OFFSET));
				})
				.toList();
	}

	private static void hurtTarget(ServerLevel level, ServerPlayer owner, LivingEntity target, DamageSource source, float damage, Vec3 impact, float knockback) {
		if (owner != null && target.getUUID().equals(owner.getUUID())) {
			return;
		}
		target.hurtServer(level, source, damage);
		Vec3 direction = target.position().subtract(impact);
		if (direction.lengthSqr() < 1.0E-5 && owner != null) {
			direction = owner.getLookAngle();
		}
		direction = safeDirection(direction);
		target.knockback(knockback, -direction.x, -direction.z);
	}

	private static ServerPlayer owner(ServerLevel level, UUID ownerUuid) {
		return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
	}

	private static int countNails(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isHairpinNail(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void consumeNails(ServerPlayer player, ItemStack usedStack, int count) {
		int remaining = count;
		if (isHairpinNail(usedStack)) {
			int consumed = Math.min(remaining, usedStack.getCount());
			usedStack.shrink(consumed);
			remaining -= consumed;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!isHairpinNail(stack)) {
				continue;
			}
			int consumed = Math.min(remaining, stack.getCount());
			stack.shrink(consumed);
			remaining -= consumed;
		}
	}

	private static boolean isHairpinNail(ItemStack stack) {
		return stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL);
	}

	private static boolean isHairpinHammer(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}

	private static void damageHammer(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		if (player.getAbilities().instabuild || stack.isEmpty()) {
			return;
		}
		EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
		stack.hurtAndBreak(1, player.level(), player, item -> player.onEquippedItemBroken(item, slot));
	}

	private static Vec3 rightOf(Vec3 forward) {
		Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
		return right.lengthSqr() < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
