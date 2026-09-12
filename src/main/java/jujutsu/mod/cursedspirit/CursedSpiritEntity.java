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

	/**
	 * Animation sync ids, broadcast through the vanilla entity-event channel.
	 *
	 * <p>They live in a private high range on purpose. {@code ClientPacketListener.handleEntityEvent}
	 * special-cases three ids with an unconditional cast — 21 ({@code checkcast Guardian}), 35
	 * (totem) and 63 ({@code checkcast Sniffer} + {@code SnifferSoundInstance}) — so an id of 63
	 * broadcast by a non-sniffer kills the client with a {@code ClassCastException} and the
	 * connection dies with "Network Protocol Error" (found live, 2026-09-12). Vanilla's own
	 * entity-event tables stay far below 100 ({@code Entity} → 53, {@code LivingEntity} → 3/46/47…),
	 * so 100–103 cannot be claimed by another mob's handler either.
	 *
	 * <p>Pinned by {@code CursedSpiritAnimationStateTest}: exact values, distinctness, the 0..127
	 * byte range, and exclusion of the vanilla listener's reserved set.
	 */
	public static final byte ATTACK_START = 100;
	public static final byte SCREAM_START = 101;
	public static final byte ATTACK_END = 102;
	public static final byte SCREAM_END = 103;
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
		CursedSpiritVariant[] all = CursedSpiritVariant.values();
		int ordinal = entityData.get(DATA_VARIANT);
		CursedSpiritVariant stored = ordinal >= 0 && ordinal < all.length ? all[ordinal] : all[0];
		// The tier field is assigned after the superclass constructor (which defines the synched
		// data), so this stays total even when the tier is not set yet or the stored ordinal
		// belongs to another tier's roster.
		if (tier == null || stored.tier() == tier) {
			return stored;
		}
		List<CursedSpiritVariant> roster = CursedSpiritVariant.variantsOf(tier);
		return roster.isEmpty() ? all[0] : roster.get(0);
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
	public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level,
			EntitySpawnReason reason) {
		// javap-verified on 1.21.8: super is PathfinderMob's walk-target gate
		// (`getWalkTargetValue(blockPos, level) >= 0`, light-sensitive via Monster's
		// `-getPathfindingCostFromLightLevels` override) — darkness flows through it. Mob's own
		// checkSpawnRules is a bare `return true` and Monster's difficulty gate lives in the
		// STATIC checkMonsterSpawnRules (SpawnPlacements-only, unreachable), so the explicit
		// PEACEFUL gate below is load-bearing for difficulty. Crowd cap is Block 4's row.
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL) {
			// The crowd cap is population pressure for natural spawning only: a spawner, spawn
			// egg or command places its spirit even in a crowded spot. It also keeps the cap
			// conjunct out of the shared-GameTest-level oracle, where sibling arenas' bodies
			// are legitimately nearby.
			return super.checkSpawnRules(level, reason);
		}
		return super.checkSpawnRules(level, reason)
				&& CursedSpiritSpawnRules.belowLocalCap(level, this.blockPosition());
	}
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		// Tier-independent by necessity: this runs inside the superclass constructor, before the
		// tier field is assigned — any roster lookup here throws (P0: summon crash). The real
		// variant resolves in finalizeSpawn (weighted roll) and on NBT load (validated id);
		// variant() falls back to the tier roster head for the window in between.
		builder.define(DATA_VARIANT, 0);
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
		// A fresh hit only extends the window while the clip already runs: restarting it on every hit
		// of a combo re-entered the authored torso swing and the POSITION bob each time, which read as
		// the model sliding off its hitbox (issue #77). A first hit still stop-then-starts, because a
		// finished clip never restarts on its own.
		if (!screamAnimationState.isStarted()) {
			screamAnimationState.stop();
			screamAnimationState.start(tickCount);
		}
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
