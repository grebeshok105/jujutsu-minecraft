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

/** One transient Round Deer body: an imposing support shikigami that heals and cleanses one ally per pulse. */
public final class MegumiDeerEntity extends MegumiShikigamiEntity {
	private long nextScanGameTime;
	private long nextPulseGameTime;

	public MegumiDeerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.DEER_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.DEER_MOVEMENT_SPEED)
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
				(float) MegumiShikigamiProfile.DEER_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.DEER_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// There is deliberately no melee goal: a sic mark remains threat-awareness only.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.AMETHYST_BLOCK_CHIME, 0.62f, 0.82f);
	}

	@Override
	public void playShadowOpenSound() {
		playSpatial(SoundEvents.AMETHYST_BLOCK_CHIME, 0.62f, 0.72f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_RESONATE;
	}

	long nextScanGameTime() {
		return nextScanGameTime;
	}

	void scheduleNextScan(long gameTime) {
		nextScanGameTime = gameTime;
	}

	long nextPulseGameTime() {
		return nextPulseGameTime;
	}

	void beginPulse(long gameTime) {
		nextPulseGameTime = gameTime + MegumiShikigamiProfile.DEER_PULSE_COOLDOWN_TICKS;
		beginAction(MegumiShikigamiProfile.DEER_PULSE_TICKS);
	}
}
