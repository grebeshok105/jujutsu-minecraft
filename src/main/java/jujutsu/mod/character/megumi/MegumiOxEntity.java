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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * One transient Piercing Ox body: commits to a straight, unsteerable charge whose impact scales
 * with real distance traveled. The charge loop is owned by {@link MegumiOxBrain}; this entity only
 * carries the summoned-body contract.
 */
public final class MegumiOxEntity extends MegumiShikigamiEntity {

	public MegumiOxEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	// --- charge state, driven by MegumiOxBrain (worker island fills these) ---

	boolean charging() {
		return false;
	}

	java.util.UUID chargeTargetUuid() {
		return null;
	}

	/** Which action clip the client should hold: 0=none, 1=windup, 3=impact, 4=recover (2=charge rides the run cycle). */
	public int presentationAction() {
		return 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.OX_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.OX_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.OX_SPEED)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.OX;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.OX_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.OX_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.OX_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.OX_FOLLOW_START,
				(float) MegumiShikigamiProfile.OX_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: the charge is the whole attack, and it is driven by the brain.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RAVAGER_AMBIENT, 0.5f, 0.9f);
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
}
