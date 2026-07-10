package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ProjectJjkNailEntity extends Entity {
	private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_EMBEDDED = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Vector3f> DATA_FORWARD = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> DATA_EMBEDDED_TARGET_ID = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> DATA_EMBEDDED_LOCAL_OFFSET = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> DATA_EMBEDDED_LOCAL_FORWARD = SynchedEntityData.defineId(ProjectJjkNailEntity.class, EntityDataSerializers.VECTOR3);
	private static final String OWNER_UUID_TAG = "OwnerUuid";
	private static final String OWNER_ENTITY_ID_TAG = "OwnerEntityId";
	private static final String LAUNCHED_TAG = "Launched";
	private static final String EXPLOSIVE_IMPACT_TAG = "ExplosiveImpact";
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
	private static final String ANCHOR_KIND_TAG = "AnchorKind";
	private static final String ANCHOR_BLOCK_X_TAG = "AnchorBlockX";
	private static final String ANCHOR_BLOCK_Y_TAG = "AnchorBlockY";
	private static final String ANCHOR_BLOCK_Z_TAG = "AnchorBlockZ";
	private static final String ANCHOR_BLOCK_STATE_TAG = "AnchorBlockState";
	private static final String ANCHOR_STABLE_ID_TAG = "AnchorStableId";
	private static final String ANCHOR_RUNTIME_TYPE_TAG = "AnchorRuntimeType";
	private static final String ANCHOR_DIMENSION_TAG = "AnchorDimension";
	private static final String ANCHOR_FACE_TAG = "AnchorFace";

	private UUID ownerUuid;
	private int ownerEntityId = -1;
	private boolean launched;
	private boolean explosiveImpact;
	private boolean explosiveImpactTracked;
	private int launchDelayTicks;
	private Vec3 target = Vec3.ZERO;
	private Vec3 pendingLaunchDirection = Vec3.ZERO;
	private boolean embedded;
	private UUID embeddedTargetUuid;
	private int embeddedTargetId = -1;
	private int embeddedAgeTicks;
	private Vec3 embeddedOffset = Vec3.ZERO;
	private Vec3 embeddedLocalOffset = Vec3.ZERO;
	private Vec3 embeddedLocalForward = new Vec3(0.0, 0.0, 1.0);
	private NailAnchor anchor = NailAnchor.none();

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
		launchAt(target, delayTicks, false);
	}

	public void launchAt(Vec3 target, int delayTicks, boolean explosiveImpact) {
		Vec3 direction = safeDirection(target.subtract(position()));
		this.target = target;
		this.explosiveImpact = explosiveImpact;
		setLaunched(true);
		trackActiveExplosiveNail();
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

	public UUID embeddedTargetUuid() {
		return embeddedTargetUuid;
	}

	public int embeddedTargetEntityId() {
		return level().isClientSide() ? entityData.get(DATA_EMBEDDED_TARGET_ID) : embeddedTargetId;
	}

	public Vec3 embeddedLocalOffset() {
		return level().isClientSide() ? fromVector(entityData.get(DATA_EMBEDDED_LOCAL_OFFSET)) : embeddedLocalOffset;
	}

	public Vec3 embeddedLocalForward() {
		return level().isClientSide() ? fromVector(entityData.get(DATA_EMBEDDED_LOCAL_FORWARD)) : embeddedLocalForward;
	}

	public NailAnchor anchor() {
		return anchor;
	}

	public void attachToRuntimeObject(ResourceLocation type, UUID objectId, Vec3 localOffset, Vec3 localForward) {
		anchor = NailAnchor.runtime(type, objectId, localOffset, localForward);
		embeddedTargetUuid = objectId;
		embeddedTargetId = -1;
		embeddedLocalOffset = localOffset;
		embeddedLocalForward = localForward;
		setEmbedded(true);
	}

	public void driveDeeper(double depth) {
		if (!isEmbedded() || depth <= 0.0) return;
		embeddedLocalOffset = embeddedLocalOffset.add(embeddedLocalForward.scale(depth));
		anchor = switch (anchor.kind()) {
			case ENTITY -> NailAnchor.entity(anchor.stableId(), anchor.cachedEntityId(), embeddedLocalOffset, embeddedLocalForward);
			case BLOCK -> NailAnchor.block(anchor.blockPos(), anchor.dimension(), anchor.face(), anchor.blockStateSignature(), embeddedLocalOffset, embeddedLocalForward);
			case RUNTIME_OBJECT -> NailAnchor.runtime(anchor.runtimeType(), anchor.stableId(), embeddedLocalOffset, embeddedLocalForward);
			default -> anchor;
		};
		syncEmbeddedAttachment();
	}

	public void amplifyFlight(double multiplier) {
		if (isFlying() && multiplier > 1.0) {
			setDeltaMovement(getDeltaMovement().scale(multiplier));
			hasImpulse = true;
		}
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
			if (level() instanceof ServerLevel serverLevel && (tickCount & 3) == 0) {
				ProjectJjkNobaraRuntime.spawnPreparedNailPressure(serverLevel, position(), forwardDirection());
			}
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
			ProjectJjkNobaraRuntime.resolveNailImpact(serverLevel, this, hit, explosiveImpact);
			if (explosiveImpact) {
				discard();
				return;
			}
			if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
				embedInEntity(entityHit.getEntity(), hit.getLocation());
			} else if (hit instanceof BlockHitResult blockHit) {
				embedInBlock(serverLevel, blockHit);
			} else {
				discard();
			}
			return;
		}

		Vec3 movement = getDeltaMovement();
		Vec3 beforeMove = position();
		move(MoverType.SELF, movement);
		face(movement);
		if (level() instanceof ServerLevel serverLevel && explodeAtTargetIfPassed(serverLevel, beforeMove, movement)) {
			return;
		}
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
		builder.define(DATA_EMBEDDED_TARGET_ID, -1);
		builder.define(DATA_EMBEDDED_LOCAL_OFFSET, new Vector3f(0.0f, 0.0f, 0.0f));
		builder.define(DATA_EMBEDDED_LOCAL_FORWARD, new Vector3f(0.0f, 0.0f, 1.0f));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (ownerUuid != null) {
			output.putString(OWNER_UUID_TAG, ownerUuid.toString());
		}
		output.putInt(OWNER_ENTITY_ID_TAG, ownerEntityId);
		output.putBoolean(LAUNCHED_TAG, isLaunched());
		output.putBoolean(EXPLOSIVE_IMPACT_TAG, explosiveImpact);
		output.putInt(LAUNCH_DELAY_TAG, launchDelayTicks);
		output.putBoolean(EMBEDDED_TAG, isEmbedded());
		if (embeddedTargetUuid != null) {
			output.putString(EMBEDDED_TARGET_UUID_TAG, embeddedTargetUuid.toString());
		}
		output.putInt(EMBEDDED_TARGET_ID_TAG, embeddedTargetId);
		output.putInt(EMBEDDED_AGE_TAG, embeddedAgeTicks);
		output.putDouble(EMBEDDED_OFFSET_X_TAG, embeddedLocalOffset.x);
		output.putDouble(EMBEDDED_OFFSET_Y_TAG, embeddedLocalOffset.y);
		output.putDouble(EMBEDDED_OFFSET_Z_TAG, embeddedLocalOffset.z);
		output.putDouble(TARGET_X_TAG, target.x);
		output.putDouble(TARGET_Y_TAG, target.y);
		output.putDouble(TARGET_Z_TAG, target.z);
		Vec3 forward = isEmbedded() ? embeddedLocalForward : forwardDirection();
		output.putDouble(DIRECTION_X_TAG, forward.x);
		output.putDouble(DIRECTION_Y_TAG, forward.y);
		output.putDouble(DIRECTION_Z_TAG, forward.z);
		output.putString(ANCHOR_KIND_TAG, anchor.kind().name());
		if (anchor.blockPos() != null) {
			output.putInt(ANCHOR_BLOCK_X_TAG, anchor.blockPos().getX());
			output.putInt(ANCHOR_BLOCK_Y_TAG, anchor.blockPos().getY());
			output.putInt(ANCHOR_BLOCK_Z_TAG, anchor.blockPos().getZ());
			output.putString(ANCHOR_BLOCK_STATE_TAG, anchor.blockStateSignature());
			output.putString(ANCHOR_DIMENSION_TAG, anchor.dimension().toString());
			output.putString(ANCHOR_FACE_TAG, anchor.face().getName());
		}
		if (anchor.stableId() != null) {
			output.putString(ANCHOR_STABLE_ID_TAG, anchor.stableId().toString());
		}
		if (anchor.runtimeType() != null) {
			output.putString(ANCHOR_RUNTIME_TYPE_TAG, anchor.runtimeType().toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		String owner = input.getStringOr(OWNER_UUID_TAG, "");
		ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
		ownerEntityId = input.getIntOr(OWNER_ENTITY_ID_TAG, -1);
		setLaunched(input.getBooleanOr(LAUNCHED_TAG, false));
		explosiveImpact = input.getBooleanOr(EXPLOSIVE_IMPACT_TAG, false);
		launchDelayTicks = input.getIntOr(LAUNCH_DELAY_TAG, 0);
		setEmbedded(input.getBooleanOr(EMBEDDED_TAG, false));
		String embeddedTarget = input.getStringOr(EMBEDDED_TARGET_UUID_TAG, "");
		embeddedTargetUuid = embeddedTarget.isBlank() ? null : UUID.fromString(embeddedTarget);
		embeddedTargetId = input.getIntOr(EMBEDDED_TARGET_ID_TAG, -1);
		embeddedAgeTicks = input.getIntOr(EMBEDDED_AGE_TAG, 0);
		embeddedLocalOffset = new Vec3(input.getDoubleOr(EMBEDDED_OFFSET_X_TAG, 0.0), input.getDoubleOr(EMBEDDED_OFFSET_Y_TAG, 0.0), input.getDoubleOr(EMBEDDED_OFFSET_Z_TAG, 0.0));
		embeddedOffset = embeddedLocalOffset;
		target = new Vec3(input.getDoubleOr(TARGET_X_TAG, getX()), input.getDoubleOr(TARGET_Y_TAG, getY()), input.getDoubleOr(TARGET_Z_TAG, getZ()));
		pendingLaunchDirection = safeDirection(target.subtract(position()));
		embeddedLocalForward = safeDirection(new Vec3(input.getDoubleOr(DIRECTION_X_TAG, pendingLaunchDirection.x), input.getDoubleOr(DIRECTION_Y_TAG, pendingLaunchDirection.y), input.getDoubleOr(DIRECTION_Z_TAG, pendingLaunchDirection.z)));
		NailAnchor.Kind anchorKind;
		try {
			anchorKind = NailAnchor.Kind.valueOf(input.getStringOr(ANCHOR_KIND_TAG, embeddedTargetUuid == null ? "NONE" : "ENTITY"));
		} catch (IllegalArgumentException ignored) {
			anchorKind = NailAnchor.Kind.NONE;
		}
		anchor = switch (anchorKind) {
			case ENTITY -> embeddedTargetUuid == null ? NailAnchor.none() : NailAnchor.entity(embeddedTargetUuid, embeddedTargetId, embeddedLocalOffset, embeddedLocalForward);
			case BLOCK -> NailAnchor.block(
					new BlockPos(input.getIntOr(ANCHOR_BLOCK_X_TAG, 0), input.getIntOr(ANCHOR_BLOCK_Y_TAG, 0), input.getIntOr(ANCHOR_BLOCK_Z_TAG, 0)),
					ResourceLocation.parse(input.getStringOr(ANCHOR_DIMENSION_TAG, level().dimension().location().toString())),
					Direction.byName(input.getStringOr(ANCHOR_FACE_TAG, "up")) == null ? Direction.UP : Direction.byName(input.getStringOr(ANCHOR_FACE_TAG, "up")),
					input.getStringOr(ANCHOR_BLOCK_STATE_TAG, ""), embeddedLocalOffset, embeddedLocalForward);
			case RUNTIME_OBJECT -> {
				String stableId = input.getStringOr(ANCHOR_STABLE_ID_TAG, "");
				String runtimeType = input.getStringOr(ANCHOR_RUNTIME_TYPE_TAG, "");
				yield stableId.isBlank() || runtimeType.isBlank() ? NailAnchor.none() : NailAnchor.runtime(ResourceLocation.parse(runtimeType), UUID.fromString(stableId), embeddedLocalOffset, embeddedLocalForward);
			}
			default -> NailAnchor.none();
		};
		face(embeddedLocalForward);
		syncEmbeddedAttachment();
		setFlightSynced(launched && launchDelayTicks <= 0);
		trackActiveExplosiveNail();
	}

	private void setLaunched(boolean launched) {
		this.launched = launched;
		if (!launched) {
			untrackActiveExplosiveNail();
			setFlightSynced(false);
		}
	}

	private void setEmbedded(boolean embedded) {
		this.embedded = embedded;
		entityData.set(DATA_EMBEDDED, embedded);
		if (embedded) {
			setLaunched(false);
			setDeltaMovement(Vec3.ZERO);
		} else {
			embeddedTargetId = -1;
			embeddedLocalOffset = Vec3.ZERO;
			embeddedLocalForward = new Vec3(0.0, 0.0, 1.0);
			syncEmbeddedAttachment();
		}
	}

	private void embedInEntity(Entity target, Vec3 hitPoint) {
		Vec3 direction = forwardDirection();
		Vec3 embedPoint = target instanceof LivingEntity living
				? ProjectJjkNailEmbedding.bodyEmbedPoint(target.position(), target.getBbWidth(), target.getBbHeight(), hitPoint, direction, getId())
				: hitPoint.add(direction.scale(0.08));
		float bodyYaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
		Vec3 localOffset = rotateY(embedPoint.subtract(target.position()), -bodyYaw);
		Vec3 localForward = safeDirection(rotateY(direction, -bodyYaw));
		embeddedTargetUuid = target.getUUID();
		embeddedTargetId = target.getId();
		embeddedAgeTicks = 0;
		embeddedOffset = embedPoint.subtract(target.position());
		embeddedLocalOffset = localOffset;
		embeddedLocalForward = localForward;
		anchor = NailAnchor.entity(embeddedTargetUuid, embeddedTargetId, localOffset, localForward);
		syncEmbeddedAttachment();
		setPos(embedPoint);
		setEmbedded(true);
		hasImpulse = false;
		updateEmbeddedPosition(target);
	}

	private void embedInBlock(ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
		embeddedTargetUuid = null;
		embeddedTargetId = -1;
		embeddedLocalOffset = local;
		embeddedLocalForward = forwardDirection();
		anchor = NailAnchor.block(pos, level.dimension().location(), hit.getDirection(), level.getBlockState(pos).toString(), local, embeddedLocalForward);
		setPos(hit.getLocation().add(embeddedLocalForward.scale(0.04)));
		setEmbedded(true);
		hasImpulse = false;
	}

	private void tickEmbedded() {
		setDeltaMovement(Vec3.ZERO);
		if (!level().isClientSide() && ProjectJjkNobaraProfile.EMBEDDED_NAIL_AGE_TICKS > 0
				&& embeddedAgeTicks++ >= ProjectJjkNobaraProfile.EMBEDDED_NAIL_AGE_TICKS) {
			discard();
			return;
		}
		if (!level().isClientSide() && anchor.kind() == NailAnchor.Kind.BLOCK && level() instanceof ServerLevel serverLevel) {
			BlockPos pos = anchor.blockPos();
			if (!serverLevel.dimension().location().equals(anchor.dimension())) return;
			if (!serverLevel.hasChunkAt(pos)) {
				return;
			}
			if (!serverLevel.getBlockState(pos).toString().equals(anchor.blockStateSignature())) {
				discard();
				return;
			}
			setPos(Vec3.atLowerCornerOf(pos).add(anchor.localOffset()).add(anchor.localForward().scale(0.04)));
			face(anchor.localForward());
			return;
		}
		if (!level().isClientSide() && anchor.kind() == NailAnchor.Kind.RUNTIME_OBJECT) {
			NailRuntimeAnchorRegistry.Result result = NailRuntimeAnchorRegistry.GLOBAL.resolve(anchor, position());
			if (result.resolution() == NailAnchorResolution.CONFIRMED_REMOVED || result.resolution() == NailAnchorResolution.INVALID) {
				discard();
			} else if (result.resolution() == NailAnchorResolution.RESOLVED) {
				setPos(result.position().add(anchor.localOffset()));
				face(result.forward());
			}
			return;
		}
		int targetId = level().isClientSide() ? entityData.get(DATA_EMBEDDED_TARGET_ID) : anchor.cachedEntityId();
		Entity target = targetId < 0 ? null : level().getEntity(targetId);
		if (target == null || (embeddedTargetUuid != null && !target.getUUID().equals(embeddedTargetUuid))) {
			target = embeddedTargetUuid == null || !(level() instanceof ServerLevel serverLevel) ? null : serverLevel.getEntity(embeddedTargetUuid);
		}
		if (target instanceof LivingEntity living && !living.isAlive()) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		if (target == null) {
			if (!level().isClientSide() && NailAnchorLifecycle.isConfirmedRemoved(embeddedTargetUuid)) {
				discard();
			}
			return;
		}
		NailAnchorLifecycle.observeLoaded(target.getUUID());
		embeddedTargetId = target.getId();
		anchor = anchor.withCachedEntityId(embeddedTargetId);
		syncEmbeddedAttachment();
		updateEmbeddedPosition(target);
	}

	private void startFlight(Vec3 direction) {
		trackActiveExplosiveNail();
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
		return entity.isAlive()
				&& (entity.isPickable() || entity instanceof ItemEntity)
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

	private boolean explodeAtTargetIfPassed(ServerLevel level, Vec3 beforeMove, Vec3 movement) {
		if (!explosiveImpact || movement.lengthSqr() <= 1.0E-5) {
			return false;
		}
		Vec3 afterMove = position();
		Vec3 beforeToTarget = target.subtract(beforeMove);
		Vec3 afterToTarget = target.subtract(afterMove);
		boolean passedTarget = beforeToTarget.dot(movement) >= 0.0 && afterToTarget.dot(movement) <= 0.0;
		boolean closeEnough = afterToTarget.lengthSqr() <= 0.25;
		if (!passedTarget && !closeEnough) {
			return false;
		}
		setPos(target);
		ProjectJjkNobaraRuntime.resolveNailImpact(level, this, BlockHitResult.miss(target, Direction.UP, BlockPos.containing(target)), true);
		discard();
		return true;
	}

	private void face(Vec3 vector) {
		Vec3 direction = safeDirection(vector);
		entityData.set(DATA_FORWARD, new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		setYRot((float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG));
		setXRot((float) (-Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
	}

	private static Vec3 launchVelocity(Vec3 direction) {
		return safeDirection(direction).scale(ProjectJjkNobaraProfile.LAUNCH_SPEED_BLOCKS_PER_TICK);
	}

	private void updateEmbeddedPosition(Entity target) {
		float bodyYaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
		Vec3 worldOffset = ProjectJjkNailEmbedding.worldOffset(embeddedLocalOffset(), bodyYaw);
		Vec3 worldForward = ProjectJjkNailEmbedding.worldForward(embeddedLocalForward(), bodyYaw);
		Vec3 next = target.position().add(worldOffset);
		setPos(next);
		face(worldForward);
	}

	private void syncEmbeddedAttachment() {
		entityData.set(DATA_EMBEDDED_TARGET_ID, embeddedTargetId);
		entityData.set(DATA_EMBEDDED_LOCAL_OFFSET, toVector(embeddedLocalOffset));
		entityData.set(DATA_EMBEDDED_LOCAL_FORWARD, toVector(embeddedLocalForward));
	}

	@Override
	public void onRemoval(RemovalReason removalReason) {
		untrackActiveExplosiveNail();
		super.onRemoval(removalReason);
	}

	private void trackActiveExplosiveNail() {
		if (!level().isClientSide() && ownerUuid != null && explosiveImpact && isLaunched() && !isEmbedded() && !explosiveImpactTracked) {
			ProjectJjkNobaraRuntime.registerActiveExplosiveNail(ownerUuid);
			explosiveImpactTracked = true;
		}
	}

	private void untrackActiveExplosiveNail() {
		if (explosiveImpactTracked) {
			ProjectJjkNobaraRuntime.unregisterActiveExplosiveNail(ownerUuid);
			explosiveImpactTracked = false;
		}
	}

	private static Vec3 rotateY(Vec3 vector, float yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
	}

	private static Vector3f toVector(Vec3 vector) {
		return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
	}

	private static Vec3 fromVector(Vector3f vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
