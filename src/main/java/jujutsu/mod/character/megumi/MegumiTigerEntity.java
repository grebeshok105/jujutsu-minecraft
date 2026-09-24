package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
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
 * Tiger Funeral is an authorial design: canon does not disclose Tiger Funeral's standalone
 * appearance/behavior. It is a grounded, heavy committed melee body, not a second Divine Dog.
 */
public final class MegumiTigerEntity extends MegumiShikigamiEntity {
	private static final EntityDataAccessor<Integer> DATA_COMBO_STEP =
			SynchedEntityData.defineId(MegumiTigerEntity.class, EntityDataSerializers.INT);

	private MegumiTigerPolicy.State comboState = MegumiTigerPolicy.State.STALK_APPROACH;
	private UUID comboTargetUuid;
	private long comboDeadlineGameTime;
	/** Number of authored hit windows already consumed in the current combo. */
	private int strikeIndex;

	public MegumiTigerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COMBO_STEP, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.TIGER_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.TIGER_MOVEMENT_SPEED)
				.add(Attributes.FOLLOW_RANGE, MegumiShikigamiProfile.TIGER_APPROACH_RANGE);
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
		goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0,
				(float) MegumiShikigamiProfile.TIGER_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.TIGER_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// Intentionally no MeleeAttackGoal: every hit belongs to an authored combo window.
	}

	public int comboStep() {
		return entityData.get(DATA_COMBO_STEP);
	}

	/** Locked target while the combo is committed; null outside windup/strike windows. */
	public UUID comboTargetUuid() {
		return switch (comboState) {
			case COMBO_WINDUP, STRIKE_1, STRIKE_2, FINISHER -> comboTargetUuid;
			case STALK_APPROACH, RECOVERY -> null;
		};
	}

	MegumiTigerPolicy.State comboState() {
		return comboState;
	}

	long comboDeadlineGameTime() {
		return comboDeadlineGameTime;
	}

	int strikeIndex() {
		return strikeIndex;
	}

	void beginCombo(UUID targetUuid, long gameTime) {
		if (comboState != MegumiTigerPolicy.State.STALK_APPROACH || targetUuid == null) {
			return;
		}
		comboState = MegumiTigerPolicy.State.COMBO_WINDUP;
		comboTargetUuid = targetUuid;
		comboDeadlineGameTime = gameTime
				+ MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK + 1L;
		strikeIndex = 0;
		setComboStep(-1);
		getNavigation().stop();
		setNoAi(true);
		setDeltaMovement(0.0, 0.0, 0.0);
		beginAction(MegumiShikigamiProfile.TIGER_WINDUP_TICKS
				+ MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK);
	}

	void enterComboState(MegumiTigerPolicy.State next, long gameTime) {
		MegumiTigerPolicy.State previous = comboState;
		comboState = next;
		switch (next) {
			case STALK_APPROACH -> {
				comboTargetUuid = null;
				comboDeadlineGameTime = 0L;
				strikeIndex = 0;
				setComboStep(0);
				beginAction(0);
				getNavigation().stop();
				setNoAi(!combatEnabled());
			}
			case COMBO_WINDUP -> setComboStep(-1);
			case STRIKE_1 -> setComboStep(1);
			case STRIKE_2 -> setComboStep(2);
			case FINISHER -> setComboStep(3);
			case RECOVERY -> {
				setComboStep(4);
				getNavigation().stop();
				setNoAi(true);
				setDeltaMovement(0.0, 0.0, 0.0);
				beginAction(MegumiShikigamiProfile.TIGER_RECOVERY_TICKS);
				if (previous != MegumiTigerPolicy.State.RECOVERY) {
					markAttackUsed(gameTime, MegumiShikigamiProfile.TIGER_COMBO_COOLDOWN_TICKS);
				}
			}
		}
	}

	void consumeStrike() {
		if (strikeIndex < 3) {
			strikeIndex++;
		}
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RAVAGER_ROAR, 0.55f, 0.82f);
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

	@Override
	void beginRecall() {
		resetCombo();
		super.beginRecall();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) {
			resetCombo();
		}
		super.remove(reason);
	}

	private void resetCombo() {
		comboState = MegumiTigerPolicy.State.STALK_APPROACH;
		comboTargetUuid = null;
		comboDeadlineGameTime = 0L;
		strikeIndex = 0;
		setComboStep(0);
		beginAction(0);
		getNavigation().stop();
	}

	private void setComboStep(int step) {
		entityData.set(DATA_COMBO_STEP, step);
	}
}
