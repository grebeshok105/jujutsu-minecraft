package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * One transient Round Deer body: the roster's support — heals and cleanses its own, shoves with
 * its antlers only when cornered. The heal/cleanse cadence is owned by {@link MegumiDeerBrain};
 * this entity only carries the summoned-body contract plus the scan clocks and the pulse's
 * presentation target.
 */
public final class MegumiDeerEntity extends MegumiShikigamiEntity {

	/** Action-clip indices the client reads: heal pulse / antler shove (0 = none). */
	public static final int ACTION_NONE = 0;
	public static final int ACTION_HEAL_PULSE = 1;
	public static final int ACTION_SHOVE = 2;

	/** How many ticks of the antler cooldown still show the shove clip (first ticks after the hit). */
	private static final int SHOVE_FLASH_TICKS = 8;

	private static final EntityDataAccessor<Integer> DATA_PRESENTATION =
			SynchedEntityData.defineId(MegumiDeerEntity.class, EntityDataSerializers.INT);

	private long nextHealScanGameTime;
	private long nextCleanseScanGameTime;
	private long antlerCooldownUntil;
	private UUID healTargetUuid;
	/** The last interpose anchor the brain steered to — kept so the nav re-issue is throttled. */
	private Vec3 interposeAnchor;

	public MegumiDeerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PRESENTATION, ACTION_NONE);
	}

	/** Which action clip the client should hold: 0=none, 1=heal pulse, 2=antler shove. */
	public int presentationAction() {
		return entityData.get(DATA_PRESENTATION);
	}

	void setPresentationAction(int actionIndex) {
		entityData.set(DATA_PRESENTATION, actionIndex);
	}

	/**
	 * The shove clip rides only the first {@link #SHOVE_FLASH_TICKS} of the antler cooldown —
	 * the brain calls this each tick to drop the clip once the flash window has passed.
	 */
	boolean shoveFlashOver(long gameTime) {
		return gameTime >= antlerCooldownUntil
				|| antlerCooldownUntil - gameTime
						<= MegumiShikigamiProfile.DEER_ANTLER_COOLDOWN_TICKS - SHOVE_FLASH_TICKS;
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

	/**
	 * Every mark path (sic, retaliation, coordinator) ends in {@code setTarget} — the Mob-level
	 * attack order. For the deer that order is always void: the mark itself is kept in
	 * {@code sicTargetUuid} as threat awareness for the brain's interpose, but this body can
	 * never acquire an attack target (spec §7 — it never attacks).
	 */
	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(null);
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.2f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.LLAMA_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.LLAMA_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.LLAMA_DEATH;
	}

	long nextHealScanGameTime() {
		return nextHealScanGameTime;
	}

	/** Arm (or re-arm) the heal scan clock — called when a scan runs, target found or not. */
	void markHealScan(long untilGameTime) {
		nextHealScanGameTime = untilGameTime;
	}

	long nextCleanseScanGameTime() {
		return nextCleanseScanGameTime;
	}

	void markCleanseScan(long untilGameTime) {
		nextCleanseScanGameTime = untilGameTime;
	}

	/** The pulse's presentation target while the action window runs (brain-owned). */
	UUID healTargetUuid() {
		return healTargetUuid;
	}

	void setHealTarget(UUID uuid) {
		healTargetUuid = uuid;
	}

	long antlerCooldownUntil() {
		return antlerCooldownUntil;
	}

	void markAntlerShove(long untilGameTime) {
		antlerCooldownUntil = untilGameTime;
	}

	Vec3 interposeAnchor() {
		return interposeAnchor;
	}

	void steerInterpose(Vec3 anchor) {
		interposeAnchor = anchor;
	}
}
