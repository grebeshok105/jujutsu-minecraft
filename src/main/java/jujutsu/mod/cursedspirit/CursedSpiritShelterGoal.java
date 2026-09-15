package jujutsu.mod.cursedspirit;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * Daytime shelter-seeking for the cursed spirits (#82, Block 4, Step 4).
 *
 * <p>Soft preference only: by day and out of combat the body walks to the best-scored nearby
 * cell ({@link CursedSpiritShelterPolicy#shelterScoreAt}) and stops once settled
 * ({@code score >= SETTLED_AT}). Combat and night silence the goal completely — no "run to a
 * tree mid-fight".
 *
 * <p>Wiring contract: flags are explicitly {@code MOVE + JUMP}, priority <b>5</b> — below the
 * melee goal (2) so an acquired target always wins the MOVE flag, above the stroll (7) so the
 * shelter walk is not wandered away from. The melee goal holds {@code MOVE + LOOK} (source-read
 * in {@code CursedSpiritAttackGoal:24-27}); the shared MOVE flag resolves by priority, as before.
 * The constructor reads no profile state (registerGoals-then-field order: the tier field is
 * still unset when goals are built), everything is resolved lazily in {@code canUse/tick}.
 */
public class CursedSpiritShelterGoal extends Goal {
	private final CursedSpiritEntity mob;
	private BlockPos shelterTarget;
	private long nextScanGameTime;
	/** selfScore cache: the score query is ~10 block reads — far too heavy for a per-tick call. */
	private double cachedSelfScore = -1.0;
	private long selfScoreCachedAt = Long.MIN_VALUE;
	private long navigationStartedAt;
	private int failedNavigations;
	private boolean arrived;
	private boolean gaveUp;

	public CursedSpiritShelterGoal(CursedSpiritEntity mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
	}

	/**
	 * Currently cached shelter target, or null. Observability seam for the GameTests (they cannot
	 * read goal internals any other way): null at night and in combat, non-null while seeking.
	 */
	public BlockPos currentShelterTarget() {
		return shelterTarget;
	}

	@Override
	public boolean canUse() {
		Level level = mob.level();
		if (level.dimensionType().hasFixedTime()) {
			shelterTarget = null;
			return false;
		}
		if (!CursedSpiritSpawnSchedule.isDaytime(level.getDayTime())) {
			shelterTarget = null;
			return false;
		}
		if (mob.currentVictim() != null) {
			shelterTarget = null;
			return false;
		}
		long now = level.getGameTime();
		double selfScore = selfScore(level, now);
		if (!CursedSpiritShelterPolicy.wants(true, false, selfScore)) {
			shelterTarget = null;
			return false;
		}
		if (shelterTarget != null && now < nextScanGameTime) {
			return true;
		}
		if (now < nextScanGameTime) {
			return false;
		}
		nextScanGameTime = now + CursedSpiritShelterPolicy.SCAN_PERIOD_TICKS;
		shelterTarget = scanForShelter(selfScore);
		return shelterTarget != null;
	}

	@Override
	public boolean canContinueToUse() {
		Level level = mob.level();
		if (level.dimensionType().hasFixedTime()
				|| !CursedSpiritSpawnSchedule.isDaytime(level.getDayTime())) {
			return false;
		}
		if (mob.currentVictim() != null || shelterTarget == null) {
			return false;
		}
		// Settle is arrival, not a score threshold: a partial-shade cell (~0.55) is a
		// legal target (picked because it beats the current spot), so demanding
		// SETTLED_AT here made the goal run forever, re-issuing moveTo every tick.
		if (arrived || gaveUp) {
			return false;
		}
		return selfScore(level, level.getGameTime()) < CursedSpiritShelterPolicy.SETTLED_AT;
	}

	@Override
	public void start() {
		arrived = false;
		gaveUp = false;
		failedNavigations = 0;
		navigationStartedAt = mob.level().getGameTime();
		if (shelterTarget != null) {
			mob.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
					shelterTarget.getZ() + 0.5, CursedSpiritShelterPolicy.SEEK_SPEED);
		}
	}

	@Override
	public void tick() {
		if (shelterTarget == null) {
			return;
		}
		// The timeout is evaluated before the isDone check: a path that stays "in progress"
		// without ever completing (blocked, shoved off-node) would otherwise never give up.
		if (mob.level().getGameTime() - navigationStartedAt
				>= CursedSpiritShelterPolicy.NAV_TIMEOUT_TICKS) {
			gaveUp = true;
			return;
		}
		if (!mob.getNavigation().isDone()) {
			return;
		}
		if (reachedTarget()) {
			// Arrived on a cell that scored strictly above the old spot — settle. The goal
			// ends, the MOVE flag frees, and the next canUse re-scans for something better.
			arrived = true;
			return;
		}
		failedNavigations++;
		if (failedNavigations >= CursedSpiritShelterPolicy.MAX_NAV_FAILURES) {
			// Unreachable cell (wall, gap): give up instead of re-pathing every tick.
			// stop() arms the scan cooldown, so the next attempt is SCAN_PERIOD_TICKS out.
			gaveUp = true;
			return;
		}
		mob.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
				shelterTarget.getZ() + 0.5, CursedSpiritShelterPolicy.SEEK_SPEED);
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		shelterTarget = null;
		nextScanGameTime = mob.level().getGameTime() + CursedSpiritShelterPolicy.SCAN_PERIOD_TICKS;
	}

	/**
	 * Nearest-first ring scan for a standable cell scoring strictly above the current spot.
	 * Bounded by {@link CursedSpiritShelterPolicy#MAX_CANDIDATES} so one scan cannot stall a tick.
	 */
	private BlockPos scanForShelter(double selfScore) {
		Level level = mob.level();
		BlockPos origin = mob.blockPosition();
		int radius = CursedSpiritShelterPolicy.SCAN_RADIUS_BLOCKS;
		List<BlockPos> candidates = new ArrayList<>();
		for (int ring = 0; ring <= radius && candidates.size() < CursedSpiritShelterPolicy.MAX_CANDIDATES; ring += 2) {
			for (int dx = -ring; dx <= ring && candidates.size() < CursedSpiritShelterPolicy.MAX_CANDIDATES; dx += 2) {
				for (int dz = -ring; dz <= ring && candidates.size() < CursedSpiritShelterPolicy.MAX_CANDIDATES; dz += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					for (int dy = -1; dy <= 1 && candidates.size() < CursedSpiritShelterPolicy.MAX_CANDIDATES; dy++) {
						BlockPos candidate = origin.offset(dx, dy, dz);
						if (isStandable(level, candidate)) {
							candidates.add(candidate.immutable());
						}
					}
				}
			}
		}
		BlockPos best = CursedSpiritShelterPolicy.pickTarget(candidates,
				spot -> CursedSpiritShelterPolicy.shelterScoreAt(level, spot), mob.getRandom());
		if (best == null
				|| CursedSpiritShelterPolicy.shelterScoreAt(level, best) <= selfScore) {
			return null;
		}
		return best;
	}

	/** Close enough to the cell centre to count the run as done. */
	private boolean reachedTarget() {
		double dx = mob.getX() - (shelterTarget.getX() + 0.5);
		double dz = mob.getZ() - (shelterTarget.getZ() + 0.5);
		double dy = mob.getY() - shelterTarget.getY();
		double radius = CursedSpiritShelterPolicy.ARRIVE_RADIUS_BLOCKS;
		return dx * dx + dz * dz <= radius * radius
				&& Math.abs(dy) <= CursedSpiritShelterPolicy.ARRIVE_RADIUS_Y;
	}

	/**
	 * Current spot's shelter score, cached for {@code SCAN_PERIOD_TICKS}: the query costs
	 * a sky check plus an up-scan of block reads, which must not run per tick per spirit.
	 */
	private double selfScore(Level level, long now) {
		if (cachedSelfScore < 0.0 || now - selfScoreCachedAt
				>= CursedSpiritShelterPolicy.SCAN_PERIOD_TICKS) {
			cachedSelfScore = CursedSpiritShelterPolicy.shelterScoreAt(level, mob.blockPosition());
			selfScoreCachedAt = now;
		}
		return cachedSelfScore;
	}

	private static boolean isStandable(Level level, BlockPos feetPos) {
		return level.getBlockState(feetPos.below()).isSolidRender()
				&& !level.getBlockState(feetPos).isSolidRender()
				&& !level.getBlockState(feetPos.above()).isSolidRender();
	}
}
