package jujutsu.mod.character.megumi;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Piercing Ox — a committed linear-charge attacker. All charge behaviour lives in
 * {@link MegumiOxBrain}; this entity only carries the summoned-body contract plus the
 * charge state the brain writes: the frozen line, the real-distance accumulator, and the
 * per-charge hit set. Nothing here is persisted — summoned bodies are transient by design.
 *
 * <p>No MeleeAttackGoal: the charge is the whole attack, and it is driven by the brain.
 */
public final class MegumiOxEntity extends MegumiShikigamiEntity {

	/** Committed charge lifecycle, owned and transitioned by {@link MegumiOxBrain}. */
	enum OxState {
		FOLLOW,
		ACQUIRE,
		ALIGN,
		WINDUP,
		CHARGE,
		RECOVERY
	}

	/** Action-clip indices the client reads: windup / charge / impact / recover (0 = none). */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_WINDUP = 1;
	public static final int ACTION_CHARGE = 2;
	public static final int ACTION_IMPACT = 3;
	public static final int ACTION_RECOVER = 4;

	/** How long the impact clip flashes after an entity hit before the run cycle resumes. */
	static final int IMPACT_FLASH_TICKS = 6;

	private static final EntityDataAccessor<Integer> DATA_PRESENTATION =
			SynchedEntityData.defineId(MegumiOxEntity.class, EntityDataSerializers.INT);

	private OxState state = OxState.FOLLOW;
	private int stateTicks;
	/** Frozen at WINDUP start — read-only until the charge resolves. The no-homing invariant. */
	private Vec3 chargeDirection;
	private Vec3 chargeStartPos;
	private double accumulatedDistance;
	/** Entities already hit by this charge; cleared at every windup so a charge hits once each. */
	private final Set<UUID> hitUuids = new HashSet<>();
	private UUID chargeTargetUuid;
	private long chargeEndGameTime;
	private long nextChargeGameTime;
	/** Ticks left of the IMPACT clip flash inside CHARGE/RECOVERY (server only, drives synched idx). */
	private int impactFlashTicks;

	public MegumiOxEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PRESENTATION, ACTION_NONE);
	}

	/** Which action clip the client should hold: 0=none, 1=windup, 2=charge, 3=impact, 4=recover. */
	public int presentationAction() {
		return entityData.get(DATA_PRESENTATION);
	}

	void setPresentationAction(int actionIndex) {
		entityData.set(DATA_PRESENTATION, actionIndex);
	}

	/**
	 * True while a committed line exists (windup + charge): this is what the pack coordinator
	 * reads to publish {@link #chargeTargetUuid()} as a claimed intent.
	 */
	public boolean charging() {
		return state == OxState.WINDUP || state == OxState.CHARGE;
	}

	/** The entity this charge was ordered against; null outside ACQUIRE→CHARGE. */
	public UUID chargeTargetUuid() {
		return chargeTargetUuid;
	}

	OxState oxState() {
		return state;
	}

	int stateTicks() {
		return stateTicks;
	}

	void transitionTo(OxState next) {
		state = next;
		stateTicks = 0;
	}

	void advanceStateTick() {
		stateTicks++;
	}

	Vec3 chargeDirection() {
		return chargeDirection;
	}

	Vec3 chargeStartPos() {
		return chargeStartPos;
	}

	double accumulatedDistance() {
		return accumulatedDistance;
	}

	void addAccumulatedDistance(double delta) {
		accumulatedDistance += delta;
	}

	Set<UUID> hitUuids() {
		return hitUuids;
	}

	long chargeEndGameTime() {
		return chargeEndGameTime;
	}

	long nextChargeGameTime() {
		return nextChargeGameTime;
	}

	/** Records the mark this committed sequence was ordered against (ACQUIRE onward). */
	void armChargeTarget(UUID targetUuid) {
		chargeTargetUuid = targetUuid;
	}

	/**
	 * Buys the windup for a charge at {@code target}: freezes the line to the target's position
	 * at lock, resets the distance accumulator and the per-charge hit set.
	 */
	void beginWindup(Vec3 frozenDirection, UUID targetUuid) {
		chargeDirection = frozenDirection;
		chargeStartPos = position();
		accumulatedDistance = 0.0;
		hitUuids.clear();
		chargeTargetUuid = targetUuid;
		transitionTo(OxState.WINDUP);
	}

	/** Arms the charge's hard expiry and flips into CHARGE. */
	void beginCharge(long gameTime) {
		chargeEndGameTime = gameTime + MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS;
		transitionTo(OxState.CHARGE);
	}

	/**
	 * Ends the committed sequence: the charge cooldown starts now, the committed target is
	 * released (the sic mark itself survives for a later line), and the body rests a moment.
	 */
	void enterRecovery(long gameTime) {
		nextChargeGameTime = gameTime + MegumiShikigamiProfile.OX_CHARGE_COOLDOWN_TICKS;
		chargeTargetUuid = null;
		transitionTo(OxState.RECOVERY);
	}

	/** Drops every committed-charge field and returns the body to plain following. */
	void backToFollow() {
		chargeDirection = null;
		chargeStartPos = null;
		accumulatedDistance = 0.0;
		hitUuids.clear();
		chargeTargetUuid = null;
		impactFlashTicks = 0;
		transitionTo(OxState.FOLLOW);
	}

	void beginImpactFlash() {
		impactFlashTicks = IMPACT_FLASH_TICKS;
	}

	/** Ticks down the impact flash; returns true once it just expired this tick. */
	boolean tickImpactFlash() {
		if (impactFlashTicks <= 0) {
			return false;
		}
		impactFlashTicks--;
		return impactFlashTicks == 0;
	}

	boolean impactFlashing() {
		return impactFlashTicks > 0;
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
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(10, new RandomLookAroundGoal(this));
	}

	@Override
	protected void onActivated() {
		// An anvil ringing out under the shadow implode — the ox lands heavy.
		playSpatial(SoundEvents.ANVIL_LAND, 0.5f, 1.0f);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.COW_AMBIENT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.COW_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.COW_DEATH;
	}
}
