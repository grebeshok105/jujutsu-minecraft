package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** One transient Divine Dog body. Pack identity is the stored owner UUID plus summon token. */
public final class MegumiDivineDogEntity extends Wolf {
	private UUID ownerUuid;
	private long summonToken;

	public MegumiDivineDogEntity(EntityType<? extends Wolf> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(6, new FollowOwnerGoal(
				this, 1.0, (float) MegumiProfile.FOLLOW_START_DISTANCE,
				(float) MegumiProfile.FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
	}

	void configureSummon(UUID ownerUuid, long summonToken) {
		this.ownerUuid = ownerUuid;
		this.summonToken = summonToken;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public long summonToken() {
		return summonToken;
	}

	void playSummonSound() {
		playSound(getAmbientSound(), 0.9f, 0.82f);
	}

	void playRecallSound() {
		playSound(getAmbientSound(), 0.65f, 0.58f);
	}

	void playSicSound() {
		Holder<WolfSoundVariant> soundVariant = get(DataComponents.WOLF_SOUND_VARIANT);
		playSound(soundVariant == null ? getAmbientSound() : soundVariant.value().growlSound().value(), 0.9f, 0.9f);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && !MegumiSummonRuntime.isCurrent(this)) {
			discard();
		}
	}

	@Override
	protected void applyTamingSideEffects() {
		AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(MegumiProfile.DOG_HEALTH);
		}
		setHealth((float) MegumiProfile.DOG_HEALTH);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canMate(Animal other) {
		return false;
	}

	@Override
	public Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public void startPersistentAngerTimer() {}

	@Override
	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		return target != this && MegumiSummonRuntime.isEligibleTarget(owner, target);
	}
}
