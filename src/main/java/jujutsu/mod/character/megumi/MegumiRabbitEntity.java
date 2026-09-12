package jujutsu.mod.character.megumi;

import java.util.EnumSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
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
		// Chase before the follow: a body with a mark services it instead of standing in the ring
		// (manual sic and the runtime's retaliation both arrive as getTarget()).
		goalSelector.addGoal(4, new RabbitChaseGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 6.0f, 3.0f));
		// Issue #78: nothing in the imported kit ever asked a body to move. The drift goal gives the
		// swarm a reason to hop, so the pack mills around its owner instead of running in place.
		goalSelector.addGoal(7, new RabbitDriftGoal(this));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No OwnerHurtByTargetGoal: vanilla would set the owner's attacker as the target directly,
		// bypassing the pack's priorities and stealing a manual sic's mark. The retaliation pass
		// owns that behaviour now, with eligibility and the sic outranking it (issue #76).
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
	 * Closes on whatever mark the body carries until it is inside bump range — the brain's shove is
	 * the swarm's only weapon, and it needs the body next to the victim to land.
	 */
	private static final class RabbitChaseGoal extends Goal {
		private final MegumiRabbitEntity rabbit;

		private RabbitChaseGoal(MegumiRabbitEntity rabbit) {
			this.rabbit = rabbit;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return serviced();
		}

		@Override
		public boolean canContinueToUse() {
			return serviced();
		}

		private boolean serviced() {
			LivingEntity target = rabbit.getTarget();
			return target != null
					&& target.isAlive()
					&& MegumiRabbitSwarmPolicy.shouldChase(rabbit.distanceTo(target));
		}

		@Override
		public void tick() {
			LivingEntity target = rabbit.getTarget();
			if (target != null) {
				rabbit.getNavigation().moveTo(target, 1.0);
			}
		}

		@Override
		public void stop() {
			rabbit.getNavigation().stop();
		}
	}

	/**
	 * Picks a fresh point on a ring around the owner every {@code RABBIT_DRIFT_INTERVAL_TICKS}, which
	 * is what actually moves the swarm: the bodies spawn inside the follow goal's stop radius, so
	 * before this goal nothing ever handed them a wanted position (issue #78).
	 */
	private static final class RabbitDriftGoal extends Goal {
		private final MegumiRabbitEntity rabbit;
		private int ticksUntilDrift;

		private RabbitDriftGoal(MegumiRabbitEntity rabbit) {
			this.rabbit = rabbit;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return rabbit.getTarget() == null && withinLeash();
		}

		@Override
		public boolean canContinueToUse() {
			return rabbit.getTarget() == null && withinLeash();
		}

		private boolean withinLeash() {
			LivingEntity owner = rabbit.getOwner();
			return owner != null
					&& owner.isAlive()
					&& MegumiRabbitSwarmPolicy.shouldDrift(rabbit.distanceTo(owner));
		}

		@Override
		public void tick() {
			LivingEntity owner = rabbit.getOwner();
			if (owner == null || ticksUntilDrift-- > 0) {
				return;
			}
			ticksUntilDrift = MegumiShikigamiProfile.RABBIT_DRIFT_INTERVAL_TICKS;
			RandomSource random = rabbit.getRandom();
			Vec3 point = MegumiRabbitSwarmPolicy.driftTarget(owner.getX(), owner.getY(), owner.getZ(),
					random.nextDouble() * Math.PI * 2.0,
					MegumiRabbitSwarmPolicy.driftRadius(random.nextDouble()));
			rabbit.getNavigation().moveTo(point.x, point.y, point.z, 1.0);
		}

		@Override
		public void stop() {
			ticksUntilDrift = 0;
			rabbit.getNavigation().stop();
		}
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
