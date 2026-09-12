package jujutsu.mod.character.megumi;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.level.Level;

/**
 * One expendable Rabbit Escape body: fragile, harmless (no melee goal, zero damage), and hopping.
 * The pack hides a single anchor — its loss disperses the swarm — while the upkeep replaces fallen
 * bodies and every body shoves nearby hostiles on its own bump window.
 */
public final class MegumiRabbitEntity extends MegumiShikigamiEntity {
	private long nextBumpGameTime;

	public MegumiRabbitEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
		moveControl = new RabbitHopMoveControl(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.RABBIT_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.RABBIT_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.RABBIT_MOVEMENT_SPEED)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.RABBITS;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.RABBITS_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.RABBITS_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.RABBIT_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 6.0f, 3.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RABBIT_AMBIENT, 0.55f, 1.1f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RABBIT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.RABBIT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RABBIT_DEATH;
	}

	/** Next game time this body may bump; zero until the first bump window passes. */
	long nextBumpGameTime() {
		return nextBumpGameTime;
	}

	void postponeBump(long nextBumpGameTime) {
		this.nextBumpGameTime = nextBumpGameTime;
	}
	/**
	 * Ground steering with a hop: faces the wanted point, feeds the profile speed into the travel
	 * step, and every {@code RABBIT_HOP_INTERVAL_TICKS} while moving flags a jump so the body
	 * leaves the ground instead of sliding. Written from the behaviour, not from any vanilla
	 * source.
	 */
	private static final class RabbitHopMoveControl extends MoveControl {
		private int ticksUntilHop;

		private RabbitHopMoveControl(MegumiRabbitEntity rabbit) {
			super(rabbit);
		}

		@Override
		public void tick() {
			if (!hasWanted()) {
				return;
			}
			Mob rabbit = mob;
			double dx = getWantedX() - rabbit.getX();
			double dz = getWantedZ() - rabbit.getZ();
			if (dx * dx + dz * dz < 0.04) {
				return;
			}
			float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
			rabbit.setYRot(yaw);
			rabbit.yBodyRot = yaw;
			rabbit.yHeadRot = yaw;
			rabbit.setSpeed((float) (getSpeedModifier()
					* rabbit.getAttributeValue(Attributes.MOVEMENT_SPEED)));
			if (ticksUntilHop-- <= 0) {
				ticksUntilHop = MegumiShikigamiProfile.RABBIT_HOP_INTERVAL_TICKS;
				rabbit.setJumping(true);
				if (rabbit.onGround()) {
					rabbit.getJumpControl().jump();
				}
			}
		}
	}

}
