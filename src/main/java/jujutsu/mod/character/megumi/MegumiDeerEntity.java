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
 * One transient Round Deer body: the roster's support — heals and cleanses its own, shoves with
 * its antlers only when cornered. The heal/cleanse cadence is owned by {@link MegumiDeerBrain};
 * this entity only carries the summoned-body contract.
 */
public final class MegumiDeerEntity extends MegumiShikigamiEntity {

	public MegumiDeerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	/** Which action clip the client should hold: 0=none, 1=heal pulse, 2=antler shove. */
	public int presentationAction() {
		return 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.DEER_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.DEER_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.DEER_SPEED)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.DEER;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.DEER_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.DEER_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.DEER_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.DEER_FOLLOW_START,
				(float) MegumiShikigamiProfile.DEER_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: the antler shove is a scripted defensive reflex, not combat.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.GOAT_AMBIENT, 0.45f, 1.1f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.GOAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}
}
