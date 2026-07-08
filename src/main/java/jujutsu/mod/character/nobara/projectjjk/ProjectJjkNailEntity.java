package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ProjectJjkNailEntity extends Entity {
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_EMBEDDED = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Vector3f> DATA_FORWARD = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.VECTOR3);
	private static final String OWNER_UUID_TAG = "OwnerUuid";
	private static final String OWNER_ENTITY_ID_TAG = "OwnerEntityId";
	private static final String LAUNCHED_TAG = "Launched";
	private static final String LAUNCH_DELAY_TAG = "LaunchDelay";
	private static final String EMBEDDED_TAG = "Embedded";
	private static final String EMBEDDED_TARGET_UUID_TAG = "EmbeddedTargetUuid";
	private static final String EMBEDDED_TARGET_ID_TAG = "EmbeddedTargetId";
	private static final String EMBEDDED_AGE_TAG = "EmbeddedAge";
	private static final String EMBEDDED_OFFSET_X_TAG = "EmbeddedOffsetX";
	private static final String EMBEDDED_OFFSET_Y_TAG = "EmbeddedOffsetY";
	private static final String EMBEDDED_OFFSET_Z_TAG = "EmbeddedOffsetZ";
	private static final String TARGET_X_TAG = "TargetX";
	private static final String TARGET_Y_TAG = "TargetY";
	private static final String TARGET_Z_TAG = "TargetZ";
	private static final String DIRECTION_X_TAG = "DirectionX";
	private static final String DIRECTION_Y_TAG = "DirectionY";
	private static final String DIRECTION_Z_TAG = "DirectionZ";

	private UUID ownerUuid;
	private int ownerEntityId = -1;
	private boolean launched;
	private int launchDelayTicks;
	private Vec3 target = Vec3.ZERO;
	private Vec3 pendingLaunchDirection = Vec3.ZERO;
	private boolean embedded;
	private UUID embeddedTargetUuid;
	private int embeddedTargetId = -1;
	private int embeddedAgeTicks;
	private Vec3 embeddedOffset = Vec3.ZERO;

	public ProjectJjkNailEntity(EntityType<? extends ProjectJjkNailEntity> entityType, Level level) {
		super(entityType, level);
		setNoGravity(true);
		setRequiresPrecisePosition(true);
	}

	public void prepare(ServerPlayer owner, Vec3 position, Vec3 direction) {
		ownerUuid = owner.getUUID();
		ownerEntityId = owner.getId();
		setLaunched(false);
		target = position;
		setDeltaMovement(Vec3.ZERO);
		setPos(position);
		face(direction);
	}

	public void launchAt(Vec3 target, int delayTicks) {
		Vec3 direction = safeDirection(target.subtract(position()));
		this.target = target;
		setLaunched(true);
		launchDelayTicks = Math.max(0, delayTicks);
		pendingLaunchDirection = direction;
		if (launchDelayTicks <= 0) {
			startFlight(direction);
		} else {
			setFlightSynced(false);
			setDeltaMovement(Vec3.ZERO);
			hasImpulse = false;
		}
		face(direction);
	}

	public boolean isPrepared() {
		return !isLaunched() && !isEmbedded() && !isRemoved();
	}

	public boolean isLaunched() {
		return launched || entityData.get(DATA_FLYING);
	}

	public boolean isFlying() {
		return entityData.get(DATA_FLYING);
	}

	public boolean isEmbedded() {
		return embedded || entityData.get(DATA_EMBEDDED);
	}

	public boolean isOwnedBy(UUID playerId) {
		return ownerUuid != null && ownerUuid.equals(playerId);
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public Vec3 forwardDirection() {
		Vector3f forward = entityData.get(DATA_FORWARD);
		return safeDirection(new Vec3(forward.x(), forward.y(), forward.z()));
	}

	@Override
	public void tick() {
		super.tick();
		if (isEmbedded()) {
			tickEmbedded();
			return;
		}

		if (level().isClientSide()) {
			clientTickMovement();
			return;
		}

		if (tickCount > ProjectJjkNobaraProfile.MAX_NAIL_AGE_TICKS) {
			discard();
			return;
		}

		if (!isLaunched()) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}

		if (launchDelayTicks > 0) {
			launchDelayTicks--;
			if (launchDelayTicks <= 0) {
				Vec3 direction = safeDirection(target.subtract(position()));
				pendingLaunchDirection = direction;
				startFlight(direction);
			}
			return;
		}

		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, ClipContext.Block.COLLIDER);
		if (hit.getType() != HitResult.Type.MISS && level() instanceof ServerLevel serverLevel) {
			setPos(hit.getLocation());
			ProjectJjkNobaraRuntime.resolveNailImpact(serverLevel, this, hit);
			if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
				embedIn(living, hit.getLocation());
			} else {
				discard();
			}
			return;
		}

		Vec3 movement = getDeltaMovement();
		move(MoverType.SELF, movement);
		face(movement);
		if (level() instanceof ServerLevel serverLevel && (tickCount & 1) == 0) {
			ProjectJjkNobaraRuntime.spawnNailFlightTrail(serverLevel, position(), movement);
		}
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public float getPickRadius() {
		return 0.35f;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		discard();
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_FLYING, false);
		builder.define(DATA_EMBEDDED, false);
		builder.define(DATA_FORWARD, new Vector3f(0.0f, 0.0f, 1.0f));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (ownerUuid != null) {
			output.putString(OWNER_UUID_TAG, ownerUuid.toString());
		}
		output.putInt(OWNER_ENTITY_ID_TAG, ownerEntityId);
		output.putBoolean(LAUNCHED_TAG, isLaunched());
		output.putInt(LAUNCH_DELAY_TAG, launchDelayTicks);
		output.putBoolean(EMBEDDED_TAG, isEmbedded());
		if (embeddedTargetUuid != null) {
			output.putString(EMBEDDED_TARGET_UUID_TAG, embeddedTargetUuid.toString());
		}
		output.putInt(EMBEDDED_TARGET_ID_TAG, embeddedTargetId);
		output.putInt(EMBEDDED_AGE_TAG, embeddedAgeTicks);
		output.putDouble(EMBEDDED_OFFSET_X_TAG, embeddedOffset.x);
		output.putDouble(EMBEDDED_OFFSET_Y_TAG, embeddedOffset.y);
		output.putDouble(EMBEDDED_OFFSET_Z_TAG, embeddedOffset.z);
		output.putDouble(TARGET_X_TAG, target.x);
		output.putDouble(TARGET_Y_TAG, target.y);
		output.putDouble(TARGET_Z_TAG, target.z);
		Vec3 forward = forwardDirection();
		output.putDouble(DIRECTION_X_TAG, forward.x);
		output.putDouble(DIRECTION_Y_TAG, forward.y);
		output.putDouble(DIRECTION_Z_TAG, forward.z);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		String owner = input.getStringOr(OWNER_UUID_TAG, "");
		ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
		ownerEntityId = input.getIntOr(OWNER_ENTITY_ID_TAG, -1);
		setLaunched(input.getBooleanOr(LAUNCHED_TAG, false));
		launchDelayTicks = input.getIntOr(LAUNCH_DELAY_TAG, 0);
		setEmbedded(input.getBooleanOr(EMBEDDED_TAG, false));
		String embeddedTarget = input.getStringOr(EMBEDDED_TARGET_UUID_TAG, "");
		embeddedTargetUuid = embeddedTarget.isBlank() ? null : UUID.fromString(embeddedTarget);
		embeddedTargetId = input.getIntOr(EMBEDDED_TARGET_ID_TAG, -1);
		embeddedAgeTicks = input.getIntOr(EMBEDDED_AGE_TAG, 0);
		embeddedOffset = new Vec3(input.getDoubleOr(EMBEDDED_OFFSET_X_TAG, 0.0), input.getDoubleOr(EMBEDDED_OFFSET_Y_TAG, 0.0), input.getDoubleOr(EMBEDDED_OFFSET_Z_TAG, 0.0));
		target = new Vec3(input.getDoubleOr(TARGET_X_TAG, getX()), input.getDoubleOr(TARGET_Y_TAG, getY()), input.getDoubleOr(TARGET_Z_TAG, getZ()));
		pendingLaunchDirection = safeDirection(target.subtract(position()));
		face(new Vec3(input.getDoubleOr(DIRECTION_X_TAG, pendingLaunchDirection.x), input.getDoubleOr(DIRECTION_Y_TAG, pendingLaunchDirection.y), input.getDoubleOr(DIRECTION_Z_TAG, pendingLaunchDirection.z)));
		setFlightSynced(launched && launchDelayTicks <= 0);
	}

	private void setLaunched(boolean launched) {
		this.launched = launched;
		if (!launched) {
			setFlightSynced(false);
		}
	}

	private void setEmbedded(boolean embedded) {
		this.embedded = embedded;
		entityData.set(DATA_EMBEDDED, embedded);
		if (embedded) {
			setLaunched(false);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	private void embedIn(LivingEntity target, Vec3 hitPoint) {
		embeddedTargetUuid = target.getUUID();
		embeddedTargetId = target.getId();
		embeddedAgeTicks = 0;
		embeddedOffset = hitPoint.subtract(target.position());
		setPos(hitPoint);
		setEmbedded(true);
		hasImpulse = false;
	}

	private void tickEmbedded() {
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide()) {
			return;
		}
		if (embeddedAgeTicks++ >= ProjectJjkNobaraProfile.EMBEDDED_NAIL_AGE_TICKS) {
			discard();
			return;
		}
		Entity target = embeddedTargetId < 0 ? null : level().getEntity(embeddedTargetId);
		if (!(target instanceof LivingEntity living) || !living.isAlive() || (embeddedTargetUuid != null && !living.getUUID().equals(embeddedTargetUuid))) {
			target = embeddedTargetUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(embeddedTargetUuid);
		}
		if (!(target instanceof LivingEntity living) || !living.isAlive()) {
			discard();
			return;
		}
		Vec3 next = target.position().add(embeddedOffset);
		setPos(next);
		if ((embeddedAgeTicks & 15) == 0 && level() instanceof ServerLevel serverLevel) {
			ProjectJjkNobaraRuntime.spawnEmbeddedNailMark(serverLevel, next, forwardDirection());
		}
	}

	private void startFlight(Vec3 direction) {
		setDeltaMovement(launchVelocity(direction));
		setFlightSynced(true);
		hasImpulse = true;
		if (level() instanceof ServerLevel serverLevel) {
			ProjectJjkNobaraRuntime.spawnNailLaunchParticles(serverLevel, position(), direction);
		}
	}

	private void setFlightSynced(boolean flying) {
		entityData.set(DATA_FLYING, flying);
	}

	private boolean canHitEntity(Entity entity) {
		return entity instanceof LivingEntity
				&& entity.isAlive()
				&& entity.isPickable()
				&& entity.getId() != ownerEntityId
				&& !entity.getUUID().equals(ownerUuid)
				&& !(entity instanceof ProjectJjkNailEntity);
	}

	private void clientTickMovement() {
		Vec3 movement = getDeltaMovement();
		if (movement.lengthSqr() <= 1.0E-5) {
			if (isLaunched() && pendingLaunchDirection.lengthSqr() > 1.0E-5) {
				face(pendingLaunchDirection);
			}
			return;
		}
		setPos(position().add(movement));
		face(movement);
	}

	private void face(Vec3 vector) {
		Vec3 direction = safeDirection(vector);
		entityData.set(DATA_FORWARD, new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		setYRot((float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG));
		setXRot((float) (-Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
		setOldPosAndRot(position(), getYRot(), getXRot());
	}

	private static Vec3 launchVelocity(Vec3 direction) {
		return safeDirection(direction).scale(ProjectJjkNobaraProfile.LAUNCH_SPEED_BLOCKS_PER_TICK);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
