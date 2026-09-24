package jujutsu.mod.character.megumi;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.MegumiVfxIds;

/** One transient Piercing Ox body: a committed, straight-line charge fighter. */
public final class MegumiOxEntity extends MegumiShikigamiEntity {
	public static final int IMPACT_CLIP_TICKS = 6;

	private MegumiOxPolicy.State chargeState = MegumiOxPolicy.State.FOLLOW;
	private UUID chargeTargetUuid;
	private Vec3 chargeDirection = Vec3.ZERO;
	private double accumulatedChargeDistance;
	private int chargeElapsedTicks;
	private long stateStartedGameTime;
	private long chargeStartedGameTime;
	private long lastImpactGameTime = Long.MIN_VALUE;
	private final Set<UUID> hitTargetUuids = new HashSet<>();

	public MegumiOxEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MegumiShikigamiProfile.OX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MegumiShikigamiProfile.OX_MOVEMENT_SPEED)
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
				(float) MegumiShikigamiProfile.OX_FOLLOW_START_DISTANCE,
				(float) MegumiShikigamiProfile.OX_FOLLOW_STOP_DISTANCE));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// No MeleeAttackGoal: all offense belongs to the committed server-side charge.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.RAVAGER_ROAR, 0.55f, 1.05f);
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

	/** Locked mark used by the pack coordinator while the Ox is aligning or charging. */
	public UUID chargeTargetUuid() {
		return chargeTargetUuid;
	}

	MegumiOxPolicy.State chargeState() {
		return chargeState;
	}

	public boolean chargeInFlight() {
		return chargeState == MegumiOxPolicy.State.CHARGE
				|| chargeState == MegumiOxPolicy.State.IMPACT
				|| chargeState == MegumiOxPolicy.State.PASS_THROUGH;
	}

	/** The swept charge query handles contacts; entity pushes must not bend its committed path. */
	@Override
	public boolean isPushable() {
		return !chargeInFlight() && super.isPushable();
	}

	@Override
	public boolean canCollideWith(Entity other) {
		return !chargeInFlight() && super.canCollideWith(other);
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return !chargeInFlight() && super.canBeCollidedWith(other);
	}

	@Override
	public void push(Entity other) {
		if (!chargeInFlight()) {
			super.push(other);
		}
	}

	@Override
	public boolean suppressesLeash() {
		// A committed charge outranges the shared leash teleport (charge max 32 > leash 25);
		// teleporting mid-flight would corrupt the committed line and clear the sic mark.
		return chargeInFlight();
	}

	public boolean chargeRecovering() {
		return chargeState == MegumiOxPolicy.State.RECOVERY;
	}

	/** Real collision-resolved distance accumulated by the current charge. */
	public double accumulatedChargeDistance() {
		return accumulatedChargeDistance;
	}

	public Vec3 chargeDirection() {
		return chargeDirection;
	}

	/** Current server-side phase name for GameTest diagnostics. */
	public String chargeStateName() {
		return chargeState.name();
	}

	Set<UUID> hitTargetUuids() {
		return hitTargetUuids;
	}

	long stateStartedGameTime() {
		return stateStartedGameTime;
	}

	long chargeStartedGameTime() {
		return chargeStartedGameTime;
	}

	int chargeElapsedTicks() {
		return chargeElapsedTicks;
	}

	boolean impactClipActive(long gameTime) {
		return lastImpactGameTime != Long.MIN_VALUE
				&& gameTime - lastImpactGameTime <= IMPACT_CLIP_TICKS;
	}

	void setChargeState(MegumiOxPolicy.State chargeState, long gameTime) {
		this.chargeState = chargeState;
		this.stateStartedGameTime = gameTime;
	}

	void beginAlign(UUID targetUuid, long gameTime) {
		chargeTargetUuid = targetUuid;
		chargeDirection = Vec3.ZERO;
		accumulatedChargeDistance = 0.0;
		chargeElapsedTicks = 0;
		hitTargetUuids.clear();
		lastImpactGameTime = Long.MIN_VALUE;
		setChargeState(MegumiOxPolicy.State.ALIGN, gameTime);
		plantForAction();
	}

	void beginWindup(long gameTime) {
		// This is the only direction assignment in the charge lifecycle. Every CHARGE/PASS_THROUGH
		// tick uses this frozen vector even if the locked target moves or another mark arrives.
		chargeDirection = MegumiOxPolicy.chargeDirection(getLookAngle());
		setChargeState(MegumiOxPolicy.State.WINDUP, gameTime);
		beginAction(MegumiShikigamiProfile.OX_WINDUP_TICKS
				+ MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS
				+ MegumiShikigamiProfile.OX_RECOVERY_TICKS);
		plantForAction();
	}

	void beginCharge(long gameTime) {
		setChargeState(MegumiOxPolicy.State.CHARGE, gameTime);
		chargeStartedGameTime = gameTime;
		chargeElapsedTicks = 0;
		beginAction(MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS
				+ MegumiShikigamiProfile.OX_RECOVERY_TICKS);
		plantForAction();
	}

	void recordChargeStep(double realDistance) {
		accumulatedChargeDistance += realDistance;
		chargeElapsedTicks++;
	}

	void recordImpact(long gameTime) {
		lastImpactGameTime = gameTime;
		beginAction(MegumiShikigamiProfile.OX_RECOVERY_TICKS + IMPACT_CLIP_TICKS + 1);
	}

	void beginRecovery(long gameTime) {
		setChargeState(MegumiOxPolicy.State.RECOVERY, gameTime);
		chargeTargetUuid = null;
		setDeltaMovement(Vec3.ZERO);
		beginAction(MegumiShikigamiProfile.OX_RECOVERY_TICKS);
		plantForAction();
	}

	void finishRecovery(long gameTime) {
		chargeTargetUuid = null;
		chargeDirection = Vec3.ZERO;
		setChargeState(MegumiOxPolicy.State.FOLLOW, gameTime);
		beginAction(0);
		getNavigation().stop();
		setNoAi(false);
	}

	private void plantForAction() {
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
	}
}
