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
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** One transient Toad body: a ground walker whose sic command answers with a tongue grab. */
public final class MegumiToadEntity extends MegumiShikigamiEntity {
	public MegumiToadEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.TOAD_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.TOAD_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.TOAD_SPEED)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.TOAD;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.TOAD_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.TOAD_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.TOAD_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.TOAD_FOLLOW_START,
				(float) MegumiShikigamiProfile.TOAD_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No OwnerHurtByTargetGoal: vanilla would set the owner's attacker as the target directly,
		// bypassing the pack's priorities and stealing a manual sic's mark. The retaliation pass
		// owns that behaviour now, with eligibility and the sic outranking it (issue #76).
		targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.FROG_LONG_JUMP, 0.55f, 0.95f);
		playSpatial(SoundEvents.FROG_AMBIENT, 0.5f, 1.0f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}
}
