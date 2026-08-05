package jujutsu.mod.character.todo;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown stone of Todo's reworked kit: one small body that exists only in flight.
 *
 * <p>Deliberately the plainest entity in the kit. Straight-line flight along a stored velocity (no
 * gravity, no arc), entities ignored entirely, water and fire passed through, and every end — block
 * collision, void, lifetime expiry, state cleanup — is a vanish routed through
 * {@link TodoTransientState#clearStone}, never an anchor. The type is {@code noSave()}, so the stone
 * cannot outlive the session that threw it, and an unloaded chunk discards it outright — which is
 * what makes the expiry sweep's "entity gone" branch sound.
 *
 * <p>Movement sync follows {@code ProjectJjkNailEntity}: the client advances itself by its synced
 * velocity between tracking updates, while {@code setRequiresPrecisePosition} makes the server send
 * authoritative absolute positions, so a swap snap and any drift are corrected instead of accumulated.
 */
public final class TodoStoneEntity extends Entity {
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER_UUID =
			SynchedEntityData.defineId(TodoStoneEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
	private static final EntityDataAccessor<Integer> DATA_REMAINING_TICKS =
			SynchedEntityData.defineId(TodoStoneEntity.class, EntityDataSerializers.INT);

	private static final String OWNER_UUID_TAG = "OwnerUuid";
	private static final String REMAINING_TICKS_TAG = "RemainingTicks";

	private UUID ownerUuid;
	private int remainingTicks;

	public TodoStoneEntity(EntityType<? extends TodoStoneEntity> entityType, Level level) {
		super(entityType, level);
		setNoGravity(true);
		setRequiresPrecisePosition(true);
	}

	/** Server-side launch: the stone leaves Todo's hand and flies forever along this velocity. */
	public void launch(ServerPlayer owner, Vec3 position, Vec3 velocity) {
		ownerUuid = owner.getUUID();
		entityData.set(DATA_OWNER_UUID, Optional.of(new EntityReference<>(ownerUuid)));
		remainingTicks = TodoProfile.STONE_LIFETIME_TICKS;
		setPos(position);
		setDeltaMovement(velocity);
		hasImpulse = true;
		face(velocity);
	}

	/** The owner's UUID from synched data, available on both sides for the HUD chip. */
	public Optional<UUID> clientOwnerUuid() {
		return entityData.get(DATA_OWNER_UUID).map(EntityReference::getUUID);
	}

	/**
	 * Remaining flight clock. The synced copy refreshes every few ticks — enough for a per-second HUD
	 * countdown, and a fifth of the traffic a per-tick sync would cost.
	 */
	public int remainingTicks() {
		return level().isClientSide() ? entityData.get(DATA_REMAINING_TICKS) : remainingTicks;
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
			// The clock ran out or the stone is in the void: a vanish, and the cleanup path owns the cue.
			endFlight(serverLevel);
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> false, ClipContext.Block.COLLIDER);
		if (hit.getType() != HitResult.Type.MISS) {
			// Any solid face ends the flight; entities are ignored by the predicate above. The vanish
			// lands at the impact point, so the puff sits on the wall it stopped against.
			setPos(hit.getLocation());
			endFlight(serverLevel);
			return;
		}
		move(MoverType.SELF, getDeltaMovement());
		if ((tickCount & 3) == 0) {
			entityData.set(DATA_REMAINING_TICKS, remainingTicks);
		}
	}

	/**
	 * The one server-side end of a flight. A stone Todo owns clears itself through the state owner so
	 * the ref and the vanish cue stay consistent; a stone that was never launched (e.g. summoned) has
	 * no ref to clear and simply discards — {@link TodoTransientState} assumes a non-null owner.
	 */
	private void endFlight(ServerLevel serverLevel) {
		if (ownerUuid != null) {
			TodoTransientState.clearStone(serverLevel.getServer(), ownerUuid);
		} else {
			discard();
		}
	}

	/**
	 * The swap's re-placement of the stone. A snap, never a rethrow: the stone keeps its velocity and
	 * its remaining clock, and flies on from the new position.
	 */
	public void snapTo(ServerLevel level, Vec3 position) {
		Vec3 velocity = getDeltaMovement();
		teleportTo(level, position.x, position.y, position.z, Set.of(), getYRot(), getXRot(), false);
		// teleportTo zeroes the motion; the swap policy says every body keeps its own velocity.
		setDeltaMovement(velocity);
		hasImpulse = true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	/**
	 * The design's list of ends is exhaustive — expiry, block collision, void, cleanup. Damage is not
	 * on it: an arrow must not be able to delete Todo's setup mid-flight, so the stone is immune.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER_UUID, Optional.empty());
		builder.define(DATA_REMAINING_TICKS, 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (ownerUuid != null) {
			output.putString(OWNER_UUID_TAG, ownerUuid.toString());
		}
		output.putInt(REMAINING_TICKS_TAG, remainingTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		String owner = input.getStringOr(OWNER_UUID_TAG, "");
		ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
		if (ownerUuid != null) {
			entityData.set(DATA_OWNER_UUID, Optional.of(new EntityReference<>(ownerUuid)));
		}
		remainingTicks = input.getIntOr(REMAINING_TICKS_TAG, 0);
	}

	private void clientTick() {
		Vec3 movement = getDeltaMovement();
		if (movement.lengthSqr() <= 1.0E-5) {
			return;
		}
		setPos(position().add(movement));
		// A few gray motes shed behind the stone; the renderer carries the body, the trail keeps it
		// readable against a wall. Near-zero velocity so the flakes hang where they were shed.
		if (tickCount % 3 == 0) {
			level().addParticle(ParticleTypes.ASH, getX(), getY(), getZ(),
					(level().random.nextDouble() - 0.5) * 0.02,
					(level().random.nextDouble() - 0.5) * 0.02,
					(level().random.nextDouble() - 0.5) * 0.02);
		}
	}

	private void face(Vec3 vector) {
		Vec3 direction = vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		setYRot((float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG));
		setXRot((float) (-Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
	}
}
