package jujutsu.mod.character.megumi;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One transient Max Elephant body: a heavy bruiser that hoses hostiles with its trunk jet. */
public final class MegumiElephantEntity extends MegumiShikigamiEntity {
	private boolean jetActive;
	/** Last footprint sample: where the body was and when, for the walking gate. */
	private Vec3 footprintSamplePos;
	private long footprintSampleTick;

	public MegumiElephantEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	/**
	 * Per-tick movement since the previous footprint sample, or {@link Vec3#ZERO} on the first call
	 * (nothing to compare against yet).
	 *
	 * <p>The walking gate cannot read {@code getDeltaMovement()}: this brain runs before the body's
	 * own travel for the tick, so the velocity field there is a leftover fraction of the real step
	 * and sits below any honest walking threshold. Measuring where the body actually went over the
	 * footprint period is both closer to "is the elephant walking" and independent of tick order.
	 */
	Vec3 sampleFootprintStep(long gameTime) {
		Vec3 now = position();
		Vec3 previous = footprintSamplePos;
		long previousTick = footprintSampleTick;
		footprintSamplePos = now;
		footprintSampleTick = gameTime;
		long elapsed = gameTime - previousTick;
		if (previous == null || elapsed <= 0L) {
			return Vec3.ZERO;
		}
		return now.subtract(previous).scale(1.0 / elapsed);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.ELEPHANT_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.ELEPHANT_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.ELEPHANT_MOVEMENT_SPEED)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.ELEPHANT;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.ELEPHANT_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.ELEPHANT_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.ELEPHANT_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.ELEPHANT_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.ELEPHANT_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No OwnerHurtByTargetGoal and no OwnerHurtTargetGoal: both set a target straight from the
		// owner's own fight — the first from whoever hit the owner, the second from whoever the
		// owner hit — bypassing the pack's priorities and stealing a manual sic's mark (issue #76).
		// Target acquisition belongs to the sic command and the retaliation pass, which know
		// eligibility, the sic's precedence and how to re-mark without cancelling a leap.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RAVAGER_ROAR, 0.52f, 1.15f);
		playSpatial(SoundEvents.RAVAGER_AMBIENT, 0.55f, 0.95f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	// --- jet state, driven by MegumiElephantBrain ---

	boolean jetActive() {
		return jetActive;
	}

	void beginJet(int totalTicks) {
		jetActive = true;
		beginAction(totalTicks);
	}

	void endJet() {
		jetActive = false;
		beginAction(0);
	}
}
