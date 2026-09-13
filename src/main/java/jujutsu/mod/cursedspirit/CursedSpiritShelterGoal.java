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
		double selfScore = CursedSpiritShelterPolicy.shelterScoreAt(level, mob.blockPosition());
		if (!CursedSpiritShelterPolicy.wants(true, false, selfScore)) {
			shelterTarget = null;
			return false;
		}
		long now = level.getGameTime();
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
		double selfScore = CursedSpiritShelterPolicy.shelterScoreAt(level, mob.blockPosition());
		return selfScore < CursedSpiritShelterPolicy.SETTLED_AT;
	}

	@Override
	public void start() {
		if (shelterTarget != null) {
			mob.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
					shelterTarget.getZ() + 0.5, CursedSpiritShelterPolicy.SEEK_SPEED);
		}
	}

	@Override
	public void tick() {
		if (shelterTarget != null && mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
					shelterTarget.getZ() + 0.5, CursedSpiritShelterPolicy.SEEK_SPEED);
		}
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

	private static boolean isStandable(Level level, BlockPos feetPos) {
		return level.getBlockState(feetPos.below()).isSolidRender()
				&& !level.getBlockState(feetPos).isSolidRender()
				&& !level.getBlockState(feetPos.above()).isSolidRender();
	}
}
