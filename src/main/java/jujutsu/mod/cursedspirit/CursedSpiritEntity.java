package jujutsu.mod.cursedspirit;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
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
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.effects.ArmorEffect;
import jujutsu.mod.cursedspirit.ability.effects.BerserkEffect;
import jujutsu.mod.cursedspirit.ability.effects.RunnerEffect;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.cursedspirit.perception.CursePerceptionSubject;

/**
 * One concrete hostile body for all three cursed-spirit tiers. The tier is constructor data:
 * AI parameters and sounds derive from it, while power stats come from the per-body grade
 * roll (D2) and the presentation variant is rolled per body and synced to the client.
 *
 * <p>Daylight: a plain {@link Monster} — no burning, no undead behaviour. Spawn gating lives in
 * Block 4's override; this class stays open (non-final, non-final methods) for that seam.
 */
public class CursedSpiritEntity extends Monster implements StaggerResistant, CursePerceptionSubject {
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
	/**
	 * Ability telegraph ids (Block 3, #86): 104+ continues the frozen quartet. Pinned by
	 * {@code CursedSpiritAnimationStateTest} alongside 100–103.
	 */
	public static final byte ABILITY_WINDUP = 104;
	public static final byte ABILITY_RELEASE = 105;
	/** NBT key for the latched berserk flag (C3 schema). */
	public static final String BERSERK_LATCHED_TAG = "BerserkLatched";

	private static final EntityDataAccessor<Integer> DATA_VARIANT =
			SynchedEntityData.defineId(CursedSpiritEntity.class, EntityDataSerializers.INT);
	/**
	 * Block 2 (#81): the synced grade level (5→1). The only grade state the client ever
	 * sees — HP/damage/speed finals never sync (the aura reads this alone).
	 */
	private static final EntityDataAccessor<Integer> DATA_GRADE =
			SynchedEntityData.defineId(CursedSpiritEntity.class, EntityDataSerializers.INT);

	private final CursedSpiritTier tier;
	private boolean variantSet;
	private CursedSpiritGrade grade;
	private CursedSpiritGradeStats gradeStats;
	private long rollSeed;
	private boolean gradeSet;
	private int screamTicks;
	/** Per-ability windows, cooldowns and pool (Block 3, #86, C3). Never null. */
	private final CursedSpiritAbilityBrain abilityBrain = new CursedSpiritAbilityBrain();
	/** Latched berserk flag (C3 schema): persists; modifiers are transient and re-applied. */
	private boolean berserkLatched;

	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState attackAnimationState = new AnimationState();
	public final AnimationState screamAnimationState = new AnimationState();

	public CursedSpiritEntity(EntityType<? extends CursedSpiritEntity> type, Level level, CursedSpiritTier tier) {
		super(type, level);
		this.tier = tier;
	}
	/** The ability brain: windows, cooldowns and pool. Never null, server-driven. */
	public CursedSpiritAbilityBrain abilityBrain() {
		return abilityBrain;
	}

	public boolean berserkLatched() {
		return berserkLatched;
	}

	public void setBerserkLatched(boolean latched) {
		this.berserkLatched = latched;
	}

	/** Current HP fraction in [0, 1]; drives regen and berserk thresholds. */
	public float hpFraction() {
		return getMaxHealth() <= 0.0f ? 1.0f : getHealth() / getMaxHealth();
	}

	/**
	 * Rolls the ability stage of the newborn stream (Block 3, #86). Replays
	 * {@code grade → variant → stats} from {@link #rollSeed()} on a fresh stream and draws
	 * the trio after it — the order can never drift because the replay uses Block 2's own
	 * entry point. Production calls this from {@code finalizeSpawn} and the NBT fallback;
	 * tests call it directly after spawning.
	 */
	public void rollAbilityPool() {
		RandomSource stream = RandomSource.create(rollSeed);
		CursedSpiritRollPolicy.rollNewborn(stream, tier);
		abilityBrain.ensurePool(stream, grade(), variant());
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

	/**
	 * Block 2 (#81): the individual's grade. Server bodies read the rolled field; client
	 * bodies (and pre-roll windows) read the synced level, defaulting to the weakest
	 * grade — total by construction, never throws.
	 */
	public CursedSpiritGrade grade() {
		if (grade != null) {
			return grade;
		}
		return CursedSpiritRollPolicy.resolveLoaded(entityData.get(DATA_GRADE))
				.filter(CursedSpiritGrade.SPAWNABLE_V1::contains)
				.orElse(CursedSpiritGrade.GRADE_5);
	}

	/**
	 * The rolled finals. Server lifecycle paths set them explicitly ({@code finalizeSpawn},
	 * end of {@code readAdditionalSaveData}); the lazy fallback exists only for
	 * finalize-skipping harnesses (GameTest {@code helper.spawn}) and never fires in
	 * production, where every creation path finalizes.
	 */
	public CursedSpiritGradeStats gradeStats() {
		if (gradeStats == null && !level().isClientSide) {
			rollSeed = level().getRandom().nextLong();
			CursedSpiritRollPolicy.Newborn newborn =
					CursedSpiritRollPolicy.rollNewborn(RandomSource.create(rollSeed), tier);
			applyStats(newborn.grade(), newborn.stats());
		}
		return gradeStats;
	}

	/** Provenance seed for the newborn roll (C2: grade → variant → stats → abilities). */
	public long rollSeed() {
		return rollSeed;
	}

	/**
	 * The single stat-application function, called from both server paths. Writes the
	 * finals into the attributes and mirrors the grade level to the client. Health is
	 * the caller's job: fresh spawns top up, NBT loads preserve the stored wounds.
	 */
	public void applyStats(CursedSpiritGrade newGrade, CursedSpiritGradeStats stats) {
		this.grade = newGrade;
		this.gradeStats = stats;
		this.gradeSet = true;
		entityData.set(DATA_GRADE, newGrade.level());
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.maxHealth());
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.attackDamage());
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats.movementSpeed());
	}

	public static AttributeSupplier.Builder createAttributes(CursedSpiritTier tier) {
		// Registration placeholder (weakest-band norms): finalizeSpawn/readNBT overwrite
		// these on both server paths before the body ever fights.
		CursedSpiritGradeStats defaults = CursedSpiritGradeProfile.registrationDefaults();
		CursedSpiritTierStats row = CursedSpiritProfile.of(tier);
		return Monster.createMobAttributes()
				.add(Attributes.MAX_HEALTH, defaults.maxHealth())
				.add(Attributes.ATTACK_DAMAGE, defaults.attackDamage())
				.add(Attributes.MOVEMENT_SPEED, defaults.movementSpeed())
				.add(Attributes.FOLLOW_RANGE, row.followRange())
				.add(Attributes.KNOCKBACK_RESISTANCE, row.knockbackResistance());
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
		if (!gradeSet) {
			rollSeed = level.getRandom().nextLong();
			CursedSpiritRollPolicy.Newborn newborn =
					CursedSpiritRollPolicy.rollNewborn(RandomSource.create(rollSeed), tier);
			if (!variantSet) {
				setVariant(newborn.variant());
			}
			applyStats(newborn.grade(), newborn.stats());
			setHealth(getMaxHealth());
			rollAbilityPool();
		} else if (!variantSet) {
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
		if (!CursedSpiritSpawnRules.difficultyAllows(level.getDifficulty())) {
			return false;
		}
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			// Non-natural placements (spawner, egg, command, summon, patrol...) get the bare
			// vanilla gate: no crowd cap — a spawner places its spirit even in a crowded
			// spot — and crucially NO day roll. The 0.6 daylight mercy is population
			// pressure relief for the natural spawn loop only; a spawner or command must
			// never luck past the light check. (Post-merge review: the roll previously
			// also armed the non-natural branch.)
			return super.checkSpawnRules(level, reason);
		}
		// Natural branch (world-gen chunk population and the natural spawn loop share it):
		// crowd cap first, then the vanilla light gate, then the day roll.
		if (!CursedSpiritSpawnRules.belowLocalCap(level, this.blockPosition())) {
			return false;
		}
		if (super.checkSpawnRules(level, reason)) {
			return true;
		}
		// Block 4 (#82, Step 2): daylight is no absolute ban — a passed day roll still allows
		// the spawn after the vanilla light half refuses. Strictly weaker, never stricter, and
		// day-only: at night a refused light half stays refused.
		if (!CursedSpiritSpawnSchedule.isDaytime(level.dayTime())) {
			return false;
		}
		return CursedSpiritSpawnRules.daylightAllows(level);
	}
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		// Tier-independent by necessity: this runs inside the superclass constructor, before the
		// tier field is assigned — any roster lookup here throws (P0: summon crash). The real
		// variant resolves in finalizeSpawn (weighted roll) and on NBT load (validated id);
		// variant() falls back to the tier roster head for the window in between.
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_GRADE, CursedSpiritGrade.GRADE_5.level());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString(VARIANT_TAG, variant().id());
		CursedSpiritGradeStats stats = gradeStats();
		output.putInt(CursedSpiritGradeNbt.GRADE, grade().level());
		output.putDouble(CursedSpiritGradeNbt.STAT_HP, stats.maxHealth());
		output.putDouble(CursedSpiritGradeNbt.STAT_DAMAGE, stats.attackDamage());
		output.putDouble(CursedSpiritGradeNbt.STAT_SPEED, stats.movementSpeed());
		output.putLong(CursedSpiritGradeNbt.ROLL_SEED, rollSeed);
		abilityBrain.saveTo(output);
		output.putBoolean(BERSERK_LATCHED_TAG, berserkLatched);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String id = input.getStringOr(VARIANT_TAG, "");
		setVariant(resolveLoadedVariant(tier, id, level().random));
		CursedSpiritGrade loadedGrade = CursedSpiritRollPolicy
				.resolveLoaded(input.getIntOr(CursedSpiritGradeNbt.GRADE, 0))
				.filter(CursedSpiritGrade.SPAWNABLE_V1::contains).orElse(null);
		CursedSpiritGradeStats loadedStats = new CursedSpiritGradeStats(
				input.getDoubleOr(CursedSpiritGradeNbt.STAT_HP, Double.NaN),
				input.getDoubleOr(CursedSpiritGradeNbt.STAT_DAMAGE, Double.NaN),
				input.getDoubleOr(CursedSpiritGradeNbt.STAT_SPEED, Double.NaN));
		// An absent RollSeed (old saves, hand-edited NBT) must not collapse every loaded body
		// onto seed 0 — identical ability pools. Re-roll the seed the same way the corrupt-
		// stats fallback below does.
		rollSeed = input.getLong(CursedSpiritGradeNbt.ROLL_SEED)
				.orElseGet(() -> level().random.nextLong());
		if (loadedGrade == null || !CursedSpiritGradeNbt.statsInBand(loadedStats, loadedGrade)) {
			rollSeed = level().random.nextLong();
			CursedSpiritRollPolicy.Newborn fallback =
					CursedSpiritRollPolicy.rollNewborn(RandomSource.create(rollSeed), tier);
			loadedGrade = fallback.grade();
			loadedStats = fallback.stats();
		}
		applyStats(loadedGrade, loadedStats);
		setHealth(Math.min(getHealth(), getMaxHealth()));
			if (!abilityBrain.loadFrom(input, grade())) {
			rollAbilityPool();
		}
		berserkLatched = input.getBooleanOr(BERSERK_LATCHED_TAG, false);
		BerserkEffect.restoreIfLatched(this, grade());
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
		float afterArmor = ArmorEffect.absorb(grade(), abilityBrain.pool(), amount, source);
		if (afterArmor <= 0.0f) {
			ArmorEffect.emitBlocked(this, level.getGameTime());
			return false;
		}
		boolean accepted = super.hurtServer(level, source, afterArmor);
		// The hurt scream is a voice, not a damage effect: only variants with a scream channel
		// (all but GULBER/GUZZLER) broadcast it, and only on accepted damage.
		if (accepted && variant().screamSound() != null) {
			beginScreamAnim();
			level.broadcastEntityEvent(this, SCREAM_START);
		}
		if (accepted) {
			BerserkEffect.maybeLatch(this, grade(), level.getGameTime());
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
		} else if (id == ABILITY_WINDUP) {
			beginAttackAnim();
		} else if (id == ABILITY_RELEASE) {
			endAttackAnim();
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
		if (!level().isClientSide) {
			// Issue #80: a live target that loses the interaction right (vessel switch)
			// is dropped promptly instead of being hunted until death.
			LivingEntity target = getTarget();
			if (target != null && !CursePerception.interacts(this, target)) {
				setTarget(null);
			}
			// Brain work (regen, VFX, cooldowns) is for living bodies only — a corpse in the
			// death window must not keep ticking it.
			if (isAlive()) {
				abilityBrain.tick(this, (ServerLevel) level(), level().getGameTime());
			}
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
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide) {
			RunnerEffect.cancelAllFor(this, abilityBrain);
		}
		super.remove(reason);
	}


	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new CursedSpiritAttackGoal(this));
		// Block 4 (#82): daytime shelter-seeking, priority 5 — below melee (2) so combat always
		// wins the shared MOVE flag, above stroll (7) so the shelter walk is not wandered off.
		goalSelector.addGoal(5, new CursedSpiritShelterGoal(this));
		goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new PerceiverLookAtPlayerGoal(this, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// Issue #80: non-perceiving players are never valid targets. The selector stops
		// acquisition; the setTarget filter below stops retaliation paths that bypass it.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				(target, targetLevel) -> CursePerception.canInteract(target)));
	}

	/**
	 * Issue #80 look gate: the spirit must not visibly stare at a non-perceiving player —
	 * a perceiver watching it track "thin air" leaks the invisible player's position.
	 * 1.21.8's {@link LookAtPlayerGoal} takes no {@code Predicate} (javap-verified: the
	 * candidate filter is a constructor-built {@code TargetingConditions}), so the gate is
	 * a subclass rechecking the picked candidate. Nearest-wins stands: when the closest
	 * player cannot perceive the spirit the goal simply idles that poll instead of
	 * skipping to a farther perceiver — an accepted simplification, the leak is closed.
	 */
	private static final class PerceiverLookAtPlayerGoal extends LookAtPlayerGoal {
		private PerceiverLookAtPlayerGoal(net.minecraft.world.entity.Mob mob, float lookDistance) {
			super(mob, Player.class, lookDistance);
		}

		@Override
		public boolean canUse() {
			return super.canUse() && CursePerception.perceives(lookAt);
		}
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
		// of a combo re-entered the authored torso swing. Scream positional root/body channels are
		// sanitized in the shared animation pack, so this state keeps only the intended recoil.
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
	/**
	 * Issue #80: the choke point every acquisition path funnels through. Goals that bypass
	 * the target predicate (notably {@code HurtByTargetGoal} retaliation) still land here,
	 * so a non-perceiving attacker can never become the target.
	 */
	@Override
	public void setTarget(LivingEntity target) {
		if (target != null && !CursePerception.interacts(this, target)) {
			super.setTarget(null);
			return;
		}
		super.setTarget(target);
	}

	/**
	 * Issue #80: no shared collision with a non-perceiving player. The no-arg self-state
	 * gates ({@code isPushable}/{@code isPickable}) stay vanilla — they carry no pair to
	 * decide on, and denying them outright would un-collide perceivers too. Displacement
	 * itself is stopped by the push mixin (the vanilla loop consults no pair gate).
	 */
	@Override
	public boolean canCollideWith(Entity other) {
		return other != null && CursePerception.interacts(this, other) && super.canCollideWith(other);
	}

	/**
	 * Issue #80: curse voices go to perceivers only, one packet each. {@code makeSound} is
	 * the voice entry (ambient, hurt and the server-side death sound all flow through it);
	 * it delegates to {@link #playSound} — the shared funnel below — so the audience rule
	 * lives in exactly one place.
	 */
	@Override
	public void makeSound(SoundEvent sound) {
		if (sound != null) {
			playSound(sound, getSoundVolume(), getVoicePitch());
		}
	}

	/**
	 * The single sound sink (javap-verified on 1.21.8): every voice AND every mechanical
	 * sound — {@code playCombinationStepSounds}, {@code playMuffledStepSound},
	 * {@code playSwimSound}/{@code waterSwimSound}, {@code playBlockFallSound},
	 * {@code causeFallDamage}'s fall sound, thorns' {@code playSecondaryHurtSound} — funnels
	 * into {@code Entity.playSound(SoundEvent,float,float)}, whose body is a bare
	 * {@code level.playSound(null, x,y,z,...)} server broadcast to ALL players in radius.
	 * Without this override a non-perceiver heard the invisible spirit's footsteps, falls
	 * and splashes. Vanilla {@code Level.playSound} takes an <i>excluded</i> entity, never
	 * an addressee, so the server path addresses one {@link ClientboundSoundPacket} per
	 * perceiver in range instead. Client-side playback stays on the super path, where the
	 * tracking filter already removed non-perceivers.
	 */
	@Override
	public void playSound(SoundEvent sound, float volume, float pitch) {
		if (sound == null || isSilent()) {
			return;
		}
		if (level() instanceof ServerLevel server) {
			sendSoundToPerceivers(server, sound, volume, pitch);
			return;
		}
		super.playSound(sound, volume, pitch);
	}

	/**
	 * The per-perceiver send loop behind {@link #playSound}: one addressed packet for each
	 * perceiver inside the sound's range, none for anyone else.
	 */
	private void sendSoundToPerceivers(ServerLevel server, SoundEvent sound,
			float volume, float pitch) {
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		double range = sound.getRange(volume);
		double rangeSqr = range * range;
		double x = getX();
		double y = getY();
		double z = getZ();
		ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, getSoundSource(),
				x, y, z, volume, pitch, level().random.nextLong());
		for (ServerPlayer player : server.players()) {
			if (!voiceReaches(CursePerception.perceives(player),
					player.distanceToSqr(x, y, z), rangeSqr)) {
				continue;
			}
			player.connection.send(packet);
		}
	}

	/**
	 * Pure voice-audience rule behind {@link #makeSound}: perceivers in range hear the
	 * curse. Kept separate so the truth table unit-tests without a world.
	 */
	static boolean voiceReaches(boolean perceivesViewer, double distanceSqr, double rangeSqr) {
		return perceivesViewer && distanceSqr <= rangeSqr;
	}


	/**
	 * Block 4 observability seam for the shelter GameTests: the registered shelter goal. The
	 * goal selector itself is protected, so without this the night/combat-silence oracles
	 * (goal never engages) would have nothing to observe.
	 */
	public CursedSpiritShelterGoal shelterGoal() {
		for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof CursedSpiritShelterGoal shelter) {
				return shelter;
			}
		}
		return null;
	}
}
