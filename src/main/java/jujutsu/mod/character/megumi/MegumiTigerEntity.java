package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * mid-sequence. The combo is owned by {@link MegumiTigerBrain}; this entity only carries the
 * summoned-body contract and the combo fields.
 *
 * <p>Tiger Funeral отдельно в каноне не раскрыт достаточно, чтобы притворяться, что мы реализуем
 * подтверждённую способность.
 *
 * <p>Эта реализация является AUTHORIAL.
 *
 * <p>Это должно быть честно записано в docs.
 *
 * <p>Tiger Funeral appearance and standalone combat behavior are authorial because canon does not
 * disclose them independently.
 */
public final class MegumiTigerEntity extends MegumiShikigamiEntity {

	/**
	 * The combo presentation selector the client reads through {@link #comboBeat()}:
	 * 0 = no combo action (stalk/follow), 1 = windup, 2 = strike_1, 3 = strike_2, 4 = finisher,
	 * 5 = recover — the same indices the frozen {@code actionIndex} contract maps to action clips.
	 * Inside a combo the field walks 0 → 1 → 2 → 3 → 4 → 5 → 0.
	 */
	private static final EntityDataAccessor<Integer> DATA_COMBO_BEAT =
			SynchedEntityData.defineId(MegumiTigerEntity.class, EntityDataSerializers.INT);

	/** The combo's own states; the brain owns every transition. */
	enum TigerState {
		STALK,
		COMBO_WINDUP,
		STRIKE_1,
		STRIKE_2,
		FINISHER,
		RECOVERY
	}

	private TigerState state = TigerState.STALK;
	private int stateTicks;
	/** The locked target identity for the whole combo — written once at windup entry. */
	private UUID comboTargetUuid;
	/** The facing frozen at windup end (degrees): every beat resolves against it, never re-aimed. */
	private float comboYawDeg;
	/** When the current state resolves (windup end, a beat's hit test, recovery end). */
	private long resolveGameTime;
	/** No new windup before this game time — armed when a combo ends. */
	private long nextComboGameTime;
	/** Whether any beat of the running combo whiffed, for the failure-memory entry. */
	private boolean comboWhiffed;

	public MegumiTigerEntity(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COMBO_BEAT, 0);
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
		// No MeleeAttackGoal and no OwnerHurt* goals: vanilla melee chases, lunges and picks its
		// own target — the combo's misses must stay misses and its lock must hold.
	}

	@Override
	protected void onActivated() {
		playSpatial(SoundEvents.CAT_HISS, 0.55f, 0.9f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.POLAR_BEAR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.POLAR_BEAR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.POLAR_BEAR_DEATH;
	}

	// --- combo state, driven by MegumiTigerBrain ---

	/**
	 * The locked combo target's uuid, or null outside a running sequence. The mark slots
	 * ({@code sicTargetUuid}) may move under the body mid-combo — this field is what the brain
	 * strikes, and it is why the tests read it rather than the marks.
	 */
	public UUID comboTargetUuid() {
		return comboTargetUuid;
	}

	/**
	 * The action-layer index the client holds (see {@link #DATA_COMBO_BEAT}): 0 = none,
	 * 1 = windup, 2/3/4 = the three strikes, 5 = recover.
	 */
	public int comboBeat() {
		return entityData.get(DATA_COMBO_BEAT);
	}

	TigerState state() {
		return state;
	}

	int stateTicks() {
		return stateTicks;
	}

	float comboYawDeg() {
		return comboYawDeg;
	}

	long resolveGameTime() {
		return resolveGameTime;
	}

	long nextComboGameTime() {
		return nextComboGameTime;
	}

	boolean comboWhiffed() {
		return comboWhiffed;
	}

	void markComboWhiffed() {
		comboWhiffed = true;
	}

	void setComboYawDeg(float yawDeg) {
		comboYawDeg = yawDeg;
	}

	void setResolveGameTime(long gameTime) {
		resolveGameTime = gameTime;
	}

	void setNextComboGameTime(long gameTime) {
		nextComboGameTime = gameTime;
	}

	/**
	 * Enters {@code next} with its action-layer index on the synced field and the state timer
	 * reset. Every transition inside the combo funnels through here.
	 */
	void enterComboState(TigerState next, int beatIndex) {
		state = next;
		stateTicks = 0;
		entityData.set(DATA_COMBO_BEAT, beatIndex);
	}

	/** Locks the target identity for the sequence; called once, at windup entry. */
	void lockComboTarget(UUID targetUuid) {
		comboTargetUuid = targetUuid;
		comboWhiffed = false;
	}

	/** Releases the lock when the body stands back up out of recovery. */
	void clearComboTarget() {
		comboTargetUuid = null;
		comboWhiffed = false;
	}

	void advanceStateTicks() {
		stateTicks++;
	}
}
