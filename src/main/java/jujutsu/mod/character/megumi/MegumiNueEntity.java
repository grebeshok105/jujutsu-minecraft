package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One transient Nue body: a fragile flyer that dives at whatever it is sic'd on. */
public final class MegumiNueEntity extends MegumiShikigamiEntity {
	private UUID diveTargetUuid;
	private long diveDeadlineGameTime;

	public MegumiNueEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.NUE_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.NUE_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.NUE_MOVEMENT_SPEED)
				.add(Attributes.FLYING_SPEED, 0.4)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.NUE;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.NUE_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.NUE_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.NUE_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.NUE_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.NUE_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(7, new HoverGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
		return false;
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.PHANTOM_FLAP, 0.42f, 0.82f);
		playSpatial(SoundEvents.PHANTOM_AMBIENT, 0.52f, 0.96f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PHANTOM_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	// --- dive state, driven by MegumiNueBrain ---

	boolean diving() {
		return diveTargetUuid != null;
	}

	UUID diveTargetUuid() {
		return diveTargetUuid;
	}

	long diveDeadlineGameTime() {
		return diveDeadlineGameTime;
	}

	void beginDive(LivingEntity target, long gameTime) {
		diveTargetUuid = target.getUUID();
		diveDeadlineGameTime = gameTime + MegumiShikigamiProfile.NUE_DIVE_TIMEOUT_TICKS;
		getNavigation().stop();
		setNoAi(true);
	}

	void endDive() {
		diveTargetUuid = null;
		diveDeadlineGameTime = 0L;
		setDeltaMovement(getDeltaMovement().scale(0.2));
		setNoAi(!combatEnabled());
	}

	/**
	 * Off-command resting spot: the flyer holds {@link MegumiShikigamiProfile#NUE_HOVER_HEIGHT} above the
	 * owner's head instead of dropping to their feet like a ground walker. Yields to the follow goal while
	 * the owner is still far away, and to combat whenever a target is set.
	 */
	private static final class HoverGoal extends Goal {
		private final MegumiNueEntity nue;

		private HoverGoal(MegumiNueEntity nue) {
			this.nue = nue;
			setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return nue.getTarget() == null && !nue.diving() && nue.getOwner() != null
					&& nue.distanceToSqr(nue.getOwner()) < 24.0 * 24.0;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void tick() {
			LivingEntity owner = nue.getOwner();
			if (owner == null) {
				return;
			}
			Vec3 hover = owner.position().add(0.0, MegumiShikigamiProfile.NUE_HOVER_HEIGHT, 0.0);
			if (nue.position().distanceTo(hover) > 0.6) {
				nue.getMoveControl().setWantedPosition(hover.x, hover.y, hover.z, 1.0);
			}
		}
	}
}