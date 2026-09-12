package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy.Phase;
import jujutsu.mod.registry.JujutsuSounds;
import net.minecraft.world.item.ItemStack;

/**
 * One transient shikigami body (the four non-dog shikigami share this base). Same contract as the
 * Divine Dog body: identity is owner UUID + summon token, only the presentation phase/age and the
 * action timer are synchronized, and every combat/collision interaction is gated on the ACTIVE
 * phase. The Divine Dog entity keeps its own implementation untouched.
 */
public abstract class MegumiShikigamiEntity extends TamableAnimal {
	private static final EntityDataAccessor<Integer> DATA_PHASE =
			SynchedEntityData.defineId(MegumiShikigamiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE_TICKS =
			SynchedEntityData.defineId(MegumiShikigamiEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS =
			SynchedEntityData.defineId(MegumiShikigamiEntity.class, EntityDataSerializers.INT);

	private UUID ownerUuid;
	private long summonToken;
	private ResourceKey<Level> recallDimension;
	private UUID sicTargetUuid;
	/** True while the current mark came from the owner's own sic command, not from retaliation. */
	private boolean sicManual;
	private long nextAttackReadyGameTime;

	protected MegumiShikigamiEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	/** Which of the four this body is; drives the runtime dispatch and the cue selection. */
	public abstract MegumiShikigami shikigamiType();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, Phase.MATERIALIZING.networkId());
		builder.define(DATA_PHASE_TICKS, 0);
		builder.define(DATA_ACTION_TICKS, 0);
	}

	/** Presentation window length while rising out of the shadow. */
	public abstract int materializeTicks();

	/** Presentation window length while sinking back. */
	public abstract int recallTicks();

	/** This type's registered max health; re-asserted after vanilla taming side effects. */
	protected abstract double baseHealth();

	/** Called on the tick the body becomes ACTIVE (type-specific emergence feedback). */
	protected void onActivated() {}

	void configureSummon(UUID ownerUuid, long summonToken) {
		this.ownerUuid = ownerUuid;
		this.summonToken = summonToken;
		recallDimension = null;
		setPresentationPhase(Phase.MATERIALIZING);
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public long summonToken() {
		return summonToken;
	}

	public Phase phase() {
		return Phase.fromNetworkId(entityData.get(DATA_PHASE));
	}

	public int phaseTicks() {
		return entityData.get(DATA_PHASE_TICKS);
	}

	/** Ticks left in this body's current action (tongue, jet, ...); drives the action animation layer. */
	public int actionTicks() {
		return entityData.get(DATA_ACTION_TICKS);
	}

	public void beginAction(int ticks) {
		entityData.set(DATA_ACTION_TICKS, Math.max(0, ticks));
	}

	public boolean combatEnabled() {
		return MegumiShikigamiPresentationPolicy.combatEnabled(phase());
	}

	void beginRecall() {
		if (!isRemoved() && phase() != Phase.RECALLING) {
			clearSicCommand();
			recallDimension = level().dimension();
			setPresentationPhase(Phase.RECALLING);
		}
	}

	boolean canFinishRecallWithoutPack() {
		return phase() == Phase.RECALLING
				&& recallDimension != null
				&& recallDimension.equals(level().dimension());
	}

	void assignSicTarget(LivingEntity target) {
		sicTargetUuid = target.getUUID();
		sicManual = true;
		setTarget(target);
	}

	/**
	 * A mark the pack picked for itself (issue #76): the owner was hit, or something already has the
	 * owner as its target. Never overrides a manual sic — the runtime skips bodies whose mark the
	 * owner chose.
	 */
	void assignRetaliationTarget(LivingEntity target) {
		sicTargetUuid = target.getUUID();
		sicManual = false;
		setTarget(target);
	}

	/** True while the current mark came from the owner's own ⇧R command. */
	boolean hasManualSicTarget() {
		return sicManual;
	}

	UUID sicTargetUuid() {
		return sicTargetUuid;
	}

	public boolean attackReady(long gameTime) {
		return gameTime >= nextAttackReadyGameTime;
	}

	public void markAttackUsed(long gameTime, int cooldownTicks) {
		nextAttackReadyGameTime = gameTime + cooldownTicks;
	}

	boolean acceptsSicCommand() {
		return combatEnabled();
	}

	void clearSicCommand() {
		sicTargetUuid = null;
		sicManual = false;
		setTarget(null);
	}

	void playShadowOpenSound() {
		playSpatial(JujutsuSounds.PROJECTJJK_GOO_FOLEY, 0.52f, 0.62f);
	}

	void playRecallSound() {
		playSpatial(JujutsuSounds.PROJECTJJK_IMPLODE, 0.62f, 0.58f);
	}

	void playSicSound() {
		SoundEvent ambient = getAmbientSound();
		if (ambient != null) {
			playSpatial(ambient, 0.9f, 0.9f);
		}
	}

	protected void playSpatial(SoundEvent sound, float volume, float pitch) {
		if (sound != null && level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		if (actionTicks() > 0) {
			entityData.set(DATA_ACTION_TICKS, actionTicks() - 1);
		}
		tickPresentationPhase();
		if (!isRemoved() && MegumiShikigamiRuntime.shouldHardDiscard(this)) {
			discard();
			return;
		}
		if (!isRemoved()) {
			MegumiShikigamiRuntime.tickBody(this);
		}
	}

	private void tickPresentationPhase() {
		Phase phase = phase();
		if (phase == Phase.ACTIVE) {
			return;
		}
		int nextTicks = phaseTicks() + 1;
		entityData.set(DATA_PHASE_TICKS, nextTicks);
		if (phase == Phase.RECALLING) {
			if (MegumiShikigamiPresentationPolicy.recallComplete(nextTicks, recallTicks())) {
				discard();
			}
			return;
		}
		Phase nextPhase = MegumiShikigamiPresentationPolicy.phaseAfterTick(phase, nextTicks, materializeTicks());
		if (nextPhase != phase) {
			setPresentationPhase(nextPhase);
			if (nextPhase == Phase.ACTIVE) {
				onActivated();
			}
		}
	}

	private void setPresentationPhase(Phase phase) {
		entityData.set(DATA_PHASE, phase.networkId());
		entityData.set(DATA_PHASE_TICKS, 0);
		boolean combatEnabled = MegumiShikigamiPresentationPolicy.combatEnabled(phase);
		setNoAi(!combatEnabled);
		if (!combatEnabled) {
			clearSicCommand();
			super.setTarget(null);
			getNavigation().stop();
		}
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(combatEnabled() ? target : null);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return combatEnabled() && super.doHurtTarget(level, target);
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
			health.setBaseValue(baseHealth());
		}
		setHealth((float) baseHealth());
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

	/** Summoned constructs never eat: feeding is not part of the shikigami contract. */
	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		return combatEnabled() && target != this && MegumiSummonRuntime.isEligibleTarget(owner, target);
	}

	/** Yaw in degrees from a movement vector, safe for a zero horizontal component. */
	protected static float yawTowards(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		if (Math.abs(dx) < 1.0E-6 && Math.abs(dz) < 1.0E-6) {
			return 0.0f;
		}
		return (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0f;
	}
}