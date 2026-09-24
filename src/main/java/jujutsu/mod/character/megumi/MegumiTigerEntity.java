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
 * One transient Tiger Funeral body: a committed three-beat melee combo that never retargets
 * mid-sequence. Canon does not disclose Tiger Funeral's standalone kit — this combat behaviour is
 * authorial. The combo is owned by {@link MegumiTigerBrain}; this entity only carries the
 * summoned-body contract.
 */
public final class MegumiTigerEntity extends MegumiShikigamiEntity {

	public MegumiTigerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	// --- combo state, driven by MegumiTigerBrain (worker island fills these) ---

	java.util.UUID comboTargetUuid() {
		return null;
	}

	/** 0-3: which combo beat the client should hold, driven by DATA_COMBO_BEAT. */
	public int comboBeat() {
		return 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.TIGER_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, MegumiShikigamiProfile.TIGER_ATTACK_DAMAGE)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.TIGER_SPEED)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public MegumiShikigami shikigamiType() {
		return MegumiShikigami.TIGER;
	}

	@Override
	public int materializeTicks() {
		return MegumiShikigamiProfile.TIGER_MATERIALIZE_TICKS;
	}

	@Override
	public int recallTicks() {
		return MegumiShikigamiProfile.TIGER_RECALL_TICKS;
	}

	@Override
	protected double baseHealth() {
		return MegumiShikigamiProfile.TIGER_HEALTH;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.TIGER_FOLLOW_START,
				(float) MegumiShikigamiProfile.TIGER_FOLLOW_STOP));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: vanilla melee chases and lunges — the combo's misses must stay misses.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RAVAGER_ROAR, 0.4f, 1.2f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.OCELOT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.OCELOT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.OCELOT_DEATH;
	}
}
