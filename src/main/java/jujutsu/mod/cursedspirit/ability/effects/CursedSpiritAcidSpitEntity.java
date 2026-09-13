package jujutsu.mod.cursedspirit.ability.effects;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.cursedspirit.perception.CursePerceptionSubject;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * The acid glob (Block 3, Step 5): a small straight-flying body on the
 * {@code TodoStoneEntity} skeleton ({@code ProjectileUtil} + {@code ClipContext},
 * {@code noSave}, immune to damage). Unlike the stone it strikes bodies, not just walls:
 * the closest living body along the segment wins over the block ray when nearer. A body
 * hit deals the direct damage and still lands the zone; block hits only land the zone;
 * pure lifetime expiry vanishes silently. Every landed zone goes through
 * {@link AcidZoneRuntime}.
 *
 * <p>The type implements {@link CursePerceptionSubject} and is listed in the
 * {@code curse_perception_subject} tag (R24 extension): perception gates inherit it.
 */
public final class CursedSpiritAcidSpitEntity extends Entity implements CursePerceptionSubject {
	private static final String OWNER_UUID_TAG = "OwnerUuid";
	private static final String LIFE_TAG = "Life";
	private static final String DAMAGE_TAG = "Damage";
	private static final String ZONE_RADIUS_TAG = "ZoneRadius";
	private static final String ZONE_LIFE_TAG = "ZoneLife";
	private static final String ZONE_PULSE_TAG = "ZonePulse";

	private UUID ownerUuid;
	private int remainingTicks;
	private float damage;
	private float zoneRadius;
	private int zoneLifetimeTicks;
	private float zonePulseDamage;

	public CursedSpiritAcidSpitEntity(EntityType<? extends CursedSpiritAcidSpitEntity> entityType,
			Level level) {
		super(entityType, level);
		setNoGravity(true);
		setRequiresPrecisePosition(true);
	}

	/** Server-side launch from the spitting spirit toward the target's chest. */
	public static boolean launchFrom(CursedSpiritEntity spirit, LivingEntity target, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		if (!(spirit.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 from = spirit.position().add(0.0, spirit.getBbHeight() * 0.6, 0.0);
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from);
		if (aim.lengthSqr() < 1.0E-6) {
			return false;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.ACID_SPIT,
				now + CursedSpiritAbilityProfile.ACID_CAST_TICKS, params, target.getUUID(), now)) {
			return false;
		}
		CursedSpiritAcidSpitEntity glob = new CursedSpiritAcidSpitEntity(
				JujutsuEntities.CURSED_ACID_SPIT, level);
		glob.ownerUuid = spirit.getUUID();
		glob.damage = (float) params.damage();
		glob.zoneRadius = (float) params.radius();
		glob.zoneLifetimeTicks = params.durationTicks();
		glob.zonePulseDamage = (float) params.strength();
		glob.remainingTicks = (int) (CursedSpiritAbilityProfile.ACID_MAX_RANGE / params.speed()) + 20;
		glob.setPos(from);
		glob.setDeltaMovement(aim.normalize().scale(params.speed()));
		glob.hasImpulse = true;
		level.addFreshEntity(glob);
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
		JujutsuNetworking.broadcastVfxCue(level, from, CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(CursedSpiritVfxIds.ACID_SPIT, from, 1, now,
						spirit.getRandom().nextLong()),
				CursePerception::perceives);
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
			return;
		}
		ServerLevel serverLevel = (ServerLevel) level();
		remainingTicks--;
		if (remainingTicks <= 0 || getY() < level().getMinY()) {
			discard();
			return;
		}
		HitResult blockHit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> false,
				ClipContext.Block.COLLIDER);
		EntityHitResult bodyHit = findBodyHit();
		if (bodyHit != null && (blockHit.getType() == HitResult.Type.MISS
				|| bodyHit.getLocation().distanceToSqr(position())
						< blockHit.getLocation().distanceToSqr(position()))) {
			impact(serverLevel, bodyHit.getLocation(),
					bodyHit.getEntity() instanceof LivingEntity living ? living : null);
			return;
		}
		if (blockHit.getType() != HitResult.Type.MISS) {
			setPos(blockHit.getLocation());
			impact(serverLevel, blockHit.getLocation(), null);
			return;
		}
		move(MoverType.SELF, getDeltaMovement());
		if (horizontalCollision || verticalCollision || getDeltaMovement().lengthSqr() < 1.0E-8) {
			impact(serverLevel, position(), null);
		}
	}

	/** Closest living body along this tick's segment, owner excluded. */
	private EntityHitResult findBodyHit() {
		Vec3 from = position();
		Vec3 to = from.add(getDeltaMovement());
		if (to.distanceToSqr(from) < 1.0E-8) {
			return null;
		}
		AABB sweep = getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.3);
		List<LivingEntity> bodies = level().getEntitiesOfClass(LivingEntity.class, sweep,
				candidate -> candidate.isAlive()
						&& !candidate.getUUID().equals(ownerUuid));
		return bodies.stream()
				.min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(from)))
				.map(candidate -> new EntityHitResult(candidate))
				.orElse(null);
	}

	private void impact(ServerLevel level, Vec3 at, LivingEntity directHit) {
		Entity owner = ownerUuid == null ? null : level.getEntity(ownerUuid);
		if (directHit != null && CursePerception.mayTouch(directHit, this)) {
			DamageSource acid = JujutsuDamageSources.cursedAcid(level,
					owner instanceof LivingEntity living ? living : null);
			directHit.hurtServer(level, acid, damage);
		}
		AcidZoneRuntime.place(level, ownerUuid, at, zoneRadius, zoneLifetimeTicks, zonePulseDamage);
		if (!isRemoved()) {
			discard();
		}
	}

	/**
	 * The ends list is exhaustive — body hit, block hit, void, lifetime expiry. Damage is
	 * not on it: an arrow must not delete the glob mid-flight, so it is immune.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (ownerUuid != null) {
			output.putString(OWNER_UUID_TAG, ownerUuid.toString());
		}
		output.putInt(LIFE_TAG, remainingTicks);
		output.putDouble(DAMAGE_TAG, damage);
		output.putDouble(ZONE_RADIUS_TAG, zoneRadius);
		output.putInt(ZONE_LIFE_TAG, zoneLifetimeTicks);
		output.putDouble(ZONE_PULSE_TAG, zonePulseDamage);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		String owner = input.getStringOr(OWNER_UUID_TAG, "");
		ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
		remainingTicks = input.getIntOr(LIFE_TAG, 0);
		damage = (float) input.getDoubleOr(DAMAGE_TAG, 0.0);
		zoneRadius = (float) input.getDoubleOr(ZONE_RADIUS_TAG, 0.0);
		zoneLifetimeTicks = input.getIntOr(ZONE_LIFE_TAG, 0);
		zonePulseDamage = (float) input.getDoubleOr(ZONE_PULSE_TAG, 0.0);
	}

	private void clientTick() {
		Vec3 movement = getDeltaMovement();
		if (movement.lengthSqr() > 1.0E-5) {
			setPos(position().add(movement));
		}
		if (tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 0.0, 0.02, 0.0);
		}
	}
}
