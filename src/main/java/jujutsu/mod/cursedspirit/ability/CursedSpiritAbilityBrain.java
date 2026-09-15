package jujutsu.mod.cursedspirit.ability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;
import jujutsu.mod.cursedspirit.ability.effects.CursedSpiritAcidSpitEntity;
import jujutsu.mod.cursedspirit.ability.effects.ArmorEffect;
import jujutsu.mod.cursedspirit.ability.effects.BerserkEffect;
import jujutsu.mod.cursedspirit.ability.effects.DashEffect;
import jujutsu.mod.cursedspirit.ability.effects.FearEffect;
import jujutsu.mod.cursedspirit.ability.effects.RegenRetreatEffect;
import jujutsu.mod.cursedspirit.ability.effects.RunnerEffect;
import jujutsu.mod.cursedspirit.ability.effects.SlamEffect;

/**
 * The single ability brain per spirit (Block 3, #86, C3).
 *
 * <p>State is per-ability: every id owns its window ({@code until}), its params and its
 * cooldown ({@code readyAt}) in two {@link EnumMap}s. There is no shared {@code activeId}:
 * regen, armor, berserk and acid zones live alongside dash/slam/spit/fear. Two separate
 * rules gate new starts (never one):
 *
 * <ul>
 *   <li>movement ownership ({@link CursedSpiritAbilityId#takesMovement()}): while any of
 *       DASH/GROUND_SLAM/GRAB_RUNNER is active, no other member starts and the melee goal
 *       does not touch navigation;</li>
 *   <li>attack-clip commit ({@link CursedSpiritAbilityId#occupiesAttackClip()}): while any of
 *       the five clip players is active, the melee goal does not open a new WINDUP.</li>
 * </ul>
 *
 * <p>At most one <em>new</em> ability starts per tick; already-running effects never block a
 * start outside the two rules above. Retreat (regen) yields to the whole movement group.
 */
public final class CursedSpiritAbilityBrain {
	private static final org.slf4j.Logger LOGGER =
			com.mojang.logging.LogUtils.getLogger();

	/** Frozen NBT schema (C3): pool, per-id cooldowns. Windows are transient, never stored. */
	public static final String ABILITIES_TAG = "Abilities";
	public static final String READY_AT_PREFIX = "AbilityReadyAt_";

	/** One open window: its end tick, its params, its optional victim, its start tick. */
	public record EffectState(long untilGameTime, CursedSpiritAbilityParams params, UUID targetUuid,
			long startedGameTime) {
	}

	private List<CursedSpiritAbilityId> pool = List.of();
	private final EnumMap<CursedSpiritAbilityId, EffectState> active =
			new EnumMap<>(CursedSpiritAbilityId.class);
	private final EnumMap<CursedSpiritAbilityId, Long> readyAt =
			new EnumMap<>(CursedSpiritAbilityId.class);
	private long lastStartTick = Long.MIN_VALUE;
	private int startsThisTick;
	/** Rolls the pool once; a loaded pool is never re-rolled here. */
	public void ensurePool(RandomSource random, CursedSpiritGrade grade, CursedSpiritVariant variant) {
		if (pool.isEmpty()) {
			pool = CursedSpiritAbilityPolicy.rollThree(random, grade, variant);
		}
	}

	/**
	 * Test seam: pins an explicit trio so scenarios never depend on the roll. Rejects
	 * anything but a full trio — a partial pool would pass vacuously.
	 */
	public void forcePoolForTest(List<CursedSpiritAbilityId> forced) {
		if (forced.size() != CursedSpiritAbilityPolicy.POOL_SIZE) {
			throw new IllegalArgumentException("forced pool must hold exactly "
					+ CursedSpiritAbilityPolicy.POOL_SIZE + " ids");
		}
		if (new HashSet<>(forced).size() != forced.size()) {
			throw new IllegalArgumentException("forced pool must hold distinct ids: " + forced);
		}
		pool = List.copyOf(forced);
	}

	public List<CursedSpiritAbilityId> pool() {
		return pool;
	}

	public boolean isActive(CursedSpiritAbilityId id, long now) {
		EffectState state = active.get(id);
		return state != null && now < state.untilGameTime();
	}

	public boolean ready(CursedSpiritAbilityId id, long now) {
		return now >= readyAt.getOrDefault(id, 0L);
	}

	public EffectState window(CursedSpiritAbilityId id) {
		return active.get(id);
	}

	/** Any movement-owning window open: navigation belongs to abilities, not to melee. */
	public boolean movementOwned(long now) {
		for (Map.Entry<CursedSpiritAbilityId, EffectState> entry : active.entrySet()) {
			if (entry.getKey().takesMovement() && now < entry.getValue().untilGameTime()) {
				return true;
			}
		}
		return false;
	}

	/** Any attack-clip window open: no new melee WINDUP until it closes. */
	public boolean attackClipOccupied(long now) {
		for (Map.Entry<CursedSpiritAbilityId, EffectState> entry : active.entrySet()) {
			if (entry.getKey().occupiesAttackClip() && now < entry.getValue().untilGameTime()) {
				return true;
			}
		}
		return false;
	}

	/** Regen retreat: the HoT walks the body away from its target (yields to movement owners). */
	public boolean shouldRetreat(long now, boolean hasTarget) {
		return hasTarget && isActive(CursedSpiritAbilityId.REGEN, now);
	}

	/**
	 * Opens a window after enforcing every start rule: pooled, not already active, cooled
	 * down, movement-group free for movers, clip free for clip players, at most one new
	 * start per tick. Entity-free so the rules unit-test without a world.
	 */
	public boolean tryStart(CursedSpiritAbilityId id, long untilGameTime,
			CursedSpiritAbilityParams params, UUID targetUuid, long now) {
		if (!pool.contains(id) || isActive(id, now) || !ready(id, now)) {
			return false;
		}
		if (id.takesMovement() && movementOwned(now)) {
			return false;
		}
		if (id.occupiesAttackClip() && attackClipOccupied(now)) {
			return false;
		}
		if (now != lastStartTick) {
			lastStartTick = now;
			startsThisTick = 0;
		}
		if (startsThisTick >= 1) {
			return false;
		}
		startsThisTick++;
		active.put(id, new EffectState(untilGameTime, params, targetUuid, now));
		readyAt.put(id, now + params.cooldownTicks());
		return true;
	}

	public void forceEnd(CursedSpiritAbilityId id) {
		active.remove(id);
	}

	public Set<CursedSpiritAbilityId> activeIds(long now) {
		Set<CursedSpiritAbilityId> ids = EnumSet.noneOf(CursedSpiritAbilityId.class);
		for (Map.Entry<CursedSpiritAbilityId, EffectState> entry : active.entrySet()) {
			if (now < entry.getValue().untilGameTime()) {
				ids.add(entry.getKey());
			}
		}
		return ids;
	}

	/**
	 * Server tick: out-of-combat self pool when targetless, then per-window effect ticks.
	 * Called from {@code CursedSpiritEntity.tick} on the server only.
	 */
	public void tick(CursedSpiritEntity spirit, ServerLevel level, long now) {
		LivingEntity target = spirit.getTarget();
		if (target == null || !target.isAlive()) {
			decideOutOfCombat(spirit, level, now);
		}
		for (CursedSpiritAbilityId id : List.copyOf(active.keySet())) {
			EffectState state = active.get(id);
			if (state == null || now >= state.untilGameTime()) {
				expireWindow(id, spirit, state);
				continue;
			}
			tickWindow(id, spirit, level, state, now);
		}
	}

	/**
	 * Window expiry cleanup. The generic path just drops the window, but the runner's
	 * carry set lives outside the window map — its expiry routes through
	 * {@link RunnerEffect#end}, which already calls {@code forceEnd} (idempotent).
	 */
	private void expireWindow(CursedSpiritAbilityId id, CursedSpiritEntity spirit,
			EffectState state) {
		if (id == CursedSpiritAbilityId.GRAB_RUNNER) {
			RunnerEffect.end(spirit, state == null ? null : state.targetUuid(), this);
		} else {
			active.remove(id);
		}
		// A window that dies by timeout must still close the client attack clip: every
		// clip player broadcasts WINDUP on start, but only dash/slam broadcast RELEASE
		// on a hit — without this the pose hangs until the next melee swing.
		if (id.occupiesAttackClip() && spirit.level() instanceof ServerLevel level) {
			level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_RELEASE);
		}
	}
	private void tickWindow(CursedSpiritAbilityId id, CursedSpiritEntity spirit, ServerLevel level,
			EffectState state, long now) {
		switch (id) {
			case DASH -> DashEffect.tick(spirit, level, this, state, now);
			case GROUND_SLAM -> SlamEffect.tick(spirit, level, this, state, now);
			case GRAB_RUNNER -> RunnerEffect.tick(spirit, level, this, state, now);
			case REGEN -> RegenRetreatEffect.tick(spirit, level, this, state, now);
			case ACID_SPIT, FEAR, ARMOR, BERSERK -> {
				// No per-tick work: the glob and the zones live in their own runtime, fear is a
				// debuff window, armor is passive absorption, berserk is a latched modifier.
			}
		}
	}

	/**
	 * In-combat decider, called from the attack goal in APPROACH/RECOVER. Picks at most one
	 * weighted candidate per call; the per-tick start cap holds across both deciders.
	 */
	public void decideInCombat(CursedSpiritEntity spirit, CursedSpiritGrade grade,
			LivingEntity target, long now) {
		if (movementOwned(now)) {
			return;
		}
		double distance = spirit.distanceTo(target);
		double hpFraction = spirit.hpFraction();
		Map<CursedSpiritAbilityId, Integer> candidates = new EnumMap<>(CursedSpiritAbilityId.class);
		for (CursedSpiritAbilityId id : pool) {
			if (!ready(id, now) || isActive(id, now)) {
				continue;
			}
			if (id.takesMovement() && movementOwned(now)) {
				continue;
			}
			if (id.occupiesAttackClip() && attackClipOccupied(now)) {
				continue;
			}
			if (!inRange(id, grade, distance, target)) {
				continue;
			}
			if (id == CursedSpiritAbilityId.REGEN
					&& hpFraction > CursedSpiritAbilityProfile.regenTriggerFraction()) {
				continue;
			}
			CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(id, grade);
			candidates.put(id, Math.max(1,
					params.weight() + CursedSpiritAbilityProfile.variantBias(id, spirit.variant())));
		}
		CursedSpiritAbilityPolicy.pick(spirit.getRandom(), candidates)
				.ifPresent(id -> startEffect(spirit, grade, target, id, now));
	}

	/** Out-of-combat self pool (C3): regen when hurt; passives need no start. */
	public void decideOutOfCombat(CursedSpiritEntity spirit, ServerLevel level, long now) {
		if (movementOwned(now) || attackClipOccupied(now)) {
			return;
		}
		if (spirit.hpFraction() > CursedSpiritAbilityProfile.regenTriggerFraction()) {
			return;
		}
		if (pool.contains(CursedSpiritAbilityId.REGEN) && ready(CursedSpiritAbilityId.REGEN, now)
				&& !isActive(CursedSpiritAbilityId.REGEN, now)) {
			RegenRetreatEffect.start(spirit, now,
					CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.REGEN, spirit.grade()), this);
		}
	}

	private boolean inRange(CursedSpiritAbilityId id, CursedSpiritGrade grade, double distance,
			LivingEntity target) {
		return switch (id) {
			case DASH -> distance >= CursedSpiritAbilityProfile.DASH_MIN_RANGE
					&& distance <= CursedSpiritAbilityProfile.DASH_MAX_RANGE;
			case GROUND_SLAM -> distance <= CursedSpiritAbilityProfile.SLAM_RANGE;
			case ACID_SPIT -> distance >= CursedSpiritAbilityProfile.ACID_MIN_RANGE
					&& distance <= CursedSpiritAbilityProfile.ACID_MAX_RANGE;
			case GRAB_RUNNER -> target instanceof ServerPlayer
					&& distance >= CursedSpiritAbilityProfile.RUNNER_MIN_RANGE
					&& distance <= CursedSpiritAbilityProfile.RUNNER_MAX_RANGE;
			// FEAR reads its profile cast range like ACID and, like GRAB_RUNNER, is a
			// player-only cast — its debuffs are inert on mobs; REGEN always in range.
			case FEAR -> target instanceof net.minecraft.world.entity.player.Player
					&& distance <= CursedSpiritAbilityProfile.of(id, grade).radius();
			case REGEN -> true;
			case ARMOR, BERSERK -> false;
		};
	}

	private void startEffect(CursedSpiritEntity spirit, CursedSpiritGrade grade,
			LivingEntity target, CursedSpiritAbilityId id, long now) {
		CursedSpiritAbilityParams params = CursedSpiritAbilityProfile.of(id, grade);
		switch (id) {
			case DASH -> DashEffect.start(spirit, target, now, params, this);
			case GROUND_SLAM -> SlamEffect.start(spirit, target, now, params, this);
			case ACID_SPIT -> CursedSpiritAcidSpitEntity.launchFrom(spirit, target, now, params, this);
			case GRAB_RUNNER -> {
				if (target instanceof ServerPlayer victim) {
					RunnerEffect.start(spirit, victim, now, params, this);
				}
			}
			case FEAR -> FearEffect.applyTo(spirit, target, now, params, this);
			case REGEN -> RegenRetreatEffect.start(spirit, now, params, this);
			case ARMOR, BERSERK -> {
				// States, never starts (see class javadoc).
			}
		}
	}

	/** Writes pool + per-id cooldowns (frozen C3 schema). Windows are transient: never written. */
	public void saveTo(ValueOutput output) {
		StringBuilder joined = new StringBuilder();
		for (CursedSpiritAbilityId id : pool) {
			if (joined.length() > 0) {
				joined.append(',');
			}
			joined.append(id.id());
		}
		output.putString(ABILITIES_TAG, joined.toString());
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			output.putLong(READY_AT_PREFIX + id.id(), readyAt.getOrDefault(id, 0L));
		}
	}

	/**
	 * Reads pool + cooldowns. Windows were never written and are never restored: active
	 * windows are transient and reset on reload (C3); cooldowns survive.
	 *
	 * @return true when the stored pool decoded to a full valid trio.
	 */
	public boolean loadFrom(ValueInput input) {
		String stored = input.getStringOr(ABILITIES_TAG, "");
		List<CursedSpiritAbilityId> loaded = new ArrayList<>();
		if (!stored.isBlank()) {
			for (String part : stored.split(",")) {
				CursedSpiritAbilityId.byId(part.trim()).ifPresent(loaded::add);
			}
		}
		active.clear();
		readyAt.clear();
		for (CursedSpiritAbilityId id : CursedSpiritAbilityId.values()) {
			long ready = input.getLongOr(READY_AT_PREFIX + id.id(), 0L);
			if (ready > 0L) {
				readyAt.put(id, ready);
			}
		}
		// "dash,dash,dash" is not a trio: the pool invariant is three DISTINCT ids, so a
		// full-length list with repeats is rejected (and re-rolled) like a short one.
		if (loaded.size() == CursedSpiritAbilityPolicy.POOL_SIZE
				&& new HashSet<>(loaded).size() == CursedSpiritAbilityPolicy.POOL_SIZE) {
			pool = List.copyOf(loaded);
			return true;
		}
		if (loaded.size() == CursedSpiritAbilityPolicy.POOL_SIZE) {
			LOGGER.warn("Stored cursed-spirit ability pool has duplicate ids ({}); re-rolling",
					stored);
		}
		pool = List.of();
		return false;
	}

	/**
	 * Loads and additionally enforces the power-rank gate against the spirit's grade
	 * ({@link CursedSpiritAbilityPolicy#eligible}). A stored id the grade may not roll —
	 * pool written before a demotion, or hand-edited NBT — invalidates the pool: the
	 * caller re-rolls, and the re-roll is eligible by construction.
	 */
	public boolean loadFrom(ValueInput input, CursedSpiritGrade grade) {
		if (!loadFrom(input)) {
			return false;
		}
		for (CursedSpiritAbilityId id : pool) {
			if (!CursedSpiritAbilityPolicy.eligible(id, grade)) {
				LOGGER.warn("Stored cursed-spirit ability pool holds {} which grade {} cannot "
						+ "roll; re-rolling", id.id(), grade);
				pool = List.of();
				return false;
			}
		}
		return true;
	}
}
