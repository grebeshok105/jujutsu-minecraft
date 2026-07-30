package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuSounds;

/** One transient Divine Dog body. Pack identity is the stored owner UUID plus summon token. */
public final class MegumiDivineDogEntity extends Wolf {
	private static final EntityDataAccessor<Integer> DATA_PRESENTATION_PHASE =
			SynchedEntityData.defineId(MegumiDivineDogEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PRESENTATION_TICKS =
			SynchedEntityData.defineId(MegumiDivineDogEntity.class, EntityDataSerializers.INT);

	private UUID ownerUuid;
	private long summonToken;
	private ResourceKey<Level> recallDimension;
	private UUID sicTargetUuid;
	private UUID pounceTargetUuid;
	private UUID pounceSicTargetUuid;
	private long nextPounceReadyGameTime;
	private long pounceStartedGameTime;
	private long pounceDeadlineGameTime;

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

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PRESENTATION_PHASE, MegumiDogPresentationPolicy.Phase.MATERIALIZING.networkId());
		builder.define(DATA_PRESENTATION_TICKS, 0);
	}

	void configureSummon(UUID ownerUuid, long summonToken) {
		this.ownerUuid = ownerUuid;
		this.summonToken = summonToken;
		recallDimension = null;
		setPresentationPhase(MegumiDogPresentationPolicy.Phase.MATERIALIZING);
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public long summonToken() {
		return summonToken;
	}

	public MegumiDogPresentationPolicy.Phase presentationPhase() {
		return MegumiDogPresentationPolicy.Phase.fromNetworkId(entityData.get(DATA_PRESENTATION_PHASE));
	}

	public int presentationTicks() {
		return entityData.get(DATA_PRESENTATION_TICKS);
	}

	void beginRecall() {
		if (!isRemoved() && presentationPhase() != MegumiDogPresentationPolicy.Phase.RECALLING) {
			clearSicCommand();
			recallDimension = level().dimension();
			setPresentationPhase(MegumiDogPresentationPolicy.Phase.RECALLING);
		}
	}

	boolean canFinishRecallWithoutPack() {
		return presentationPhase() == MegumiDogPresentationPolicy.Phase.RECALLING
				&& recallDimension != null
				&& recallDimension.equals(level().dimension());
	}

	void playShadowOpenSound() {
		playSpatial(JujutsuSounds.PROJECTJJK_GOO_FOLEY, 0.52f, 0.62f);
	}

	void playRecallSound() {
		playSpatial(JujutsuSounds.PROJECTJJK_IMPLODE, 0.62f, 0.58f);
	}

	void playSicSound() {
		Holder<WolfSoundVariant> soundVariant = get(DataComponents.WOLF_SOUND_VARIANT);
		playSpatial(soundVariant == null ? getAmbientSound() : soundVariant.value().growlSound().value(), 0.9f, 0.9f);
	}

	void assignSicTarget(LivingEntity target) {
		finishPounce();
		sicTargetUuid = target.getUUID();
		super.setTarget(target);
	}

	UUID sicTargetUuid() {
		return sicTargetUuid;
	}

	UUID pounceTargetUuid() {
		return pounceTargetUuid;
	}

	long pounceDeadlineGameTime() {
		return pounceDeadlineGameTime;
	}

	long pounceStartedGameTime() {
		return pounceStartedGameTime;
	}

	boolean pounceInFlight() {
		return pounceTargetUuid != null;
	}

	boolean pounceReady(long gameTime) {
		return MegumiPouncePolicy.deadlineReady(gameTime, nextPounceReadyGameTime);
	}

	void launchPounce(LivingEntity target, long gameTime, Vec3 velocity) {
		pounceTargetUuid = target.getUUID();
		pounceSicTargetUuid = sicTargetUuid;
		pounceStartedGameTime = gameTime;
		pounceDeadlineGameTime = gameTime + MegumiProfile.POUNCE_TIMEOUT_TICKS;
		nextPounceReadyGameTime = gameTime + MegumiProfile.POUNCE_COOLDOWN_TICKS;
		getNavigation().stop();
		setNoAi(true);
		setDeltaMovement(velocity);
		hurtMarked = true;
	}

	void finishPounce() {
		finishPounce(Vec3.ZERO);
	}

	void finishPounce(Vec3 exitVelocity) {
		pounceTargetUuid = null;
		pounceSicTargetUuid = null;
		pounceStartedGameTime = 0L;
		pounceDeadlineGameTime = 0L;
		setDeltaMovement(exitVelocity);
		resetFallDistance();
		if (!isRemoved() && presentationPhase() == MegumiDogPresentationPolicy.Phase.ACTIVE) {
			setNoAi(false);
		}
	}

	UUID pounceSicTargetUuid() {
		return pounceSicTargetUuid;
	}

	void resumeNavigation(LivingEntity target) {
		if (!isRemoved() && presentationPhase() == MegumiDogPresentationPolicy.Phase.ACTIVE) {
			getNavigation().moveTo(target, MegumiProfile.DOG_MOVEMENT_SPEED);
		}
	}

	void clearSicCommand() {
		sicTargetUuid = null;
		finishPounce();
	}

	private void playEmergenceSounds() {
		playSpatial(JujutsuSounds.PROJECTJJK_WHOOSH_HIT, 0.42f, 0.82f);
		playSpatial(getAmbientSound(), 0.72f, 0.96f);
	}

	private void playSpatial(SoundEvent sound, float volume, float pitch) {
		if (sound != null && level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS, volume, pitch);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		tickPresentationPhase();
		if (!isRemoved() && MegumiSummonRuntime.shouldHardDiscard(this)) {
			discard();
			return;
		}
		if (!isRemoved()) {
			MegumiSummonRuntime.tickPounce(this);
		}
	}

	private void tickPresentationPhase() {
		MegumiDogPresentationPolicy.Phase phase = presentationPhase();
		if (phase == MegumiDogPresentationPolicy.Phase.ACTIVE) {
			return;
		}
		int nextTicks = presentationTicks() + 1;
		entityData.set(DATA_PRESENTATION_TICKS, nextTicks);
		if (phase == MegumiDogPresentationPolicy.Phase.RECALLING) {
			if (MegumiDogPresentationPolicy.recallComplete(nextTicks)) {
				discard();
			}
			return;
		}
		MegumiDogPresentationPolicy.Phase nextPhase =
				MegumiDogPresentationPolicy.phaseAfterTick(phase, nextTicks);
		if (nextPhase != phase) {
			setPresentationPhase(nextPhase);
			if (nextPhase == MegumiDogPresentationPolicy.Phase.ACTIVE) {
				playEmergenceSounds();
			}
		}
	}

	private void setPresentationPhase(MegumiDogPresentationPolicy.Phase phase) {
		entityData.set(DATA_PRESENTATION_PHASE, phase.networkId());
		entityData.set(DATA_PRESENTATION_TICKS, 0);
		boolean combatEnabled = MegumiDogPresentationPolicy.combatEnabled(phase);
		setNoAi(!combatEnabled);
		if (!combatEnabled) {
			clearSicCommand();
			super.setTarget(null);
			getNavigation().stop();
		}
	}

	private boolean combatEnabled() {
		return MegumiDogPresentationPolicy.combatEnabled(presentationPhase());
	}

	boolean acceptsSicCommand() {
		return combatEnabled();
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(combatEnabled() ? target : null);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return combatEnabled() && !pounceInFlight() && super.doHurtTarget(level, target);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return combatEnabled() && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPickable() {
		return combatEnabled() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return combatEnabled() && super.isPushable();
	}

	@Override
	public void push(Entity other) {
		if (combatEnabled()) {
			super.push(other);
		}
	}

	@Override
	public boolean canCollideWith(Entity other) {
		return combatEnabled() && super.canCollideWith(other);
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return combatEnabled() && super.canBeCollidedWith(other);
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
		return combatEnabled() && target != this && MegumiSummonRuntime.isEligibleTarget(owner, target);
	}
}
