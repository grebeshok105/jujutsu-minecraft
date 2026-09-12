package jujutsu.mod.cursedspirit;

import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import jujutsu.mod.combat.StaggerResistant;

/**
 * One concrete hostile body for all three cursed-spirit tiers. The tier is constructor data (D2):
 * attributes, AI parameters and sounds all derive from it, while the presentation variant is
 * rolled per body and synced to the client.
 *
 * <p>Daylight: a plain {@link Monster} — no burning, no undead behaviour. Spawn gating lives in
 * Block 4's override; this class stays open (non-final, non-final methods) for that seam.
 */
public class CursedSpiritEntity extends Monster implements StaggerResistant {
	/** NBT key for the variant string id (persistence contract). */
	public static final String VARIANT_TAG = "Variant";

	/** Frozen animation sync ids (Warden precedent 61/62; avoids LivingEntity's own ids). */
	public static final byte ATTACK_START = 61;
	public static final byte SCREAM_START = 62;
	public static final byte ATTACK_END = 63;
	public static final byte SCREAM_END = 64;
	/** All SCREAMER clips are 0.56 s = 11.2 ticks, rounded up (plan rev 3 pinned literal). */
	public static final int SCREAM_DURATION_TICKS = 12;

	private static final EntityDataAccessor<Integer> DATA_VARIANT =
			SynchedEntityData.defineId(CursedSpiritEntity.class, EntityDataSerializers.INT);

	private final CursedSpiritTier tier;
	private boolean variantSet;
	private int screamTicks;

	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState attackAnimationState = new AnimationState();
	public final AnimationState screamAnimationState = new AnimationState();

	public CursedSpiritEntity(EntityType<? extends CursedSpiritEntity> type, Level level, CursedSpiritTier tier) {
		super(type, level);
		this.tier = tier;
	}

	public CursedSpiritTier tier() {
		return tier;
	}

	public CursedSpiritVariant variant() {
		List<CursedSpiritVariant> roster = CursedSpiritVariant.variantsOf(tier);
		int ordinal = entityData.get(DATA_VARIANT);
		if (ordinal < 0 || ordinal >= CursedSpiritVariant.values().length) {
			return roster.get(0);
		}
		CursedSpiritVariant stored = CursedSpiritVariant.values()[ordinal];
		return stored.tier() == tier ? stored : roster.get(0);
	}

	/** Server-side (and test) variant assignment. */
	public void setVariant(CursedSpiritVariant variant) {
		entityData.set(DATA_VARIANT, variant.ordinal());
		variantSet = true;
	}

	public static AttributeSupplier.Builder createAttributes(CursedSpiritTier tier) {
		CursedSpiritTierStats stats = CursedSpiritProfile.of(tier);
		return Monster.createMobAttributes()
				.add(Attributes.MAX_HEALTH, stats.maxHealth())
				.add(Attributes.ATTACK_DAMAGE, stats.attackDamage())
				.add(Attributes.MOVEMENT_SPEED, stats.movementSpeed())
				.add(Attributes.FOLLOW_RANGE, stats.followRange())
				.add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance());
	}

	/**
	 * Scales incoming stagger by the profile multiplier with a floor of 1 tick: even the greater
	 * tier visibly flinches, it just recovers fast (D4).
	 */
	@Override
	public int adjustIncomingStaggerTicks(int ticks) {
		double scaled = ticks * CursedSpiritProfile.of(tier).staggerMultiplier();
		return Math.max(1, (int) Math.round(scaled));
	}

	/** Weighted roll inside the tier; delegates to the pure index core for boundary testing. */
	public static CursedSpiritVariant rollVariant(CursedSpiritTier tier, RandomSource random) {
		List<CursedSpiritVariant> roster = CursedSpiritVariant.variantsOf(tier);
		int total = 0;
		for (CursedSpiritVariant variant : roster) {
			total += variant.weight();
		}
		return roster.get(selectVariantIndex(roster, total, random.nextInt(total)));
	}

	/** Pure weighted-selection core: {@code roll} in {@code [0, totalWeight)}. */
	static int selectVariantIndex(List<CursedSpiritVariant> roster, int totalWeight, int roll) {
		int cursor = roll;
		for (int i = 0; i < roster.size(); i++) {
			cursor -= roster.get(i).weight();
			if (cursor < 0) {
				return i;
			}
		}
		return roster.size() - 1;
	}

	/**
	 * Maps a loaded variant id to the effective variant: unknown ids and cross-tier ids re-roll
	 * (persistence contract). Pure for unit testing; the NBT read path delegates here.
	 */
	static CursedSpiritVariant resolveLoadedVariant(CursedSpiritTier tier, String id, RandomSource random) {
		CursedSpiritVariant loaded = CursedSpiritVariant.byId(id).orElse(null);
		if (loaded != null && loaded.tier() == tier) {
			return loaded;
		}
		return rollVariant(tier, random);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason spawnReason, SpawnGroupData spawnData) {
		if (!variantSet) {
			setVariant(rollVariant(tier, level.getRandom()));
		}
		return super.finalizeSpawn(level, difficulty, spawnReason, spawnData);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, defaultVariantOrdinal());
	}

	private int defaultVariantOrdinal() {
		return CursedSpiritVariant.variantsOf(tier).get(0).ordinal();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString(VARIANT_TAG, variant().id());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String id = input.getStringOr(VARIANT_TAG, "");
		setVariant(resolveLoadedVariant(tier, id, level().random));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return variant().ambientSound();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return variant().hurtSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return variant().deathSound();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean accepted = super.hurtServer(level, source, amount);
		// The hurt scream is a voice, not a damage effect: only variants with a scream channel
		// (all but GULBER/GUZZLER) broadcast it, and only on accepted damage.
		if (accepted && variant().screamSound() != null) {
			beginScreamAnim();
			level.broadcastEntityEvent(this, SCREAM_START);
		}
		return accepted;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return CursedSpiritProfile.of(tier).xpReward();
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == ATTACK_START) {
			beginAttackAnim();
		} else if (id == ATTACK_END) {
			endAttackAnim();
		} else if (id == SCREAM_START) {
			beginScreamAnim();
		} else if (id == SCREAM_END) {
			endScreamAnim();
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!idleAnimationState.isStarted()) {
			idleAnimationState.start(tickCount);
		}
		if (!level().isClientSide && screamTicks > 0) {
			screamTicks--;
			if (screamTicks == 0) {
				endScreamAnim();
				level().broadcastEntityEvent(this, SCREAM_END);
			}
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new CursedSpiritAttackGoal(this));
		goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	void beginAttackAnim() {
		// stop() before start() is mandatory, not hygiene: startIfStopped never restarts a
		// finished clip (the 2nd attack would be invisible).
		attackAnimationState.stop();
		attackAnimationState.start(tickCount);
	}

	void endAttackAnim() {
		attackAnimationState.stop();
	}

	void beginScreamAnim() {
		// Same restart rule, plus the looping SCREAMER clip would otherwise hold the ported
		// setupAnims' walk gate forever.
		screamAnimationState.stop();
		screamAnimationState.start(tickCount);
		screamTicks = SCREAM_DURATION_TICKS;
	}

	void endScreamAnim() {
		screamAnimationState.stop();
		screamTicks = 0;
	}

	/** Current target as a living body, or null. Read by the attack goal. */
	LivingEntity currentVictim() {
		return getTarget();
	}
}
