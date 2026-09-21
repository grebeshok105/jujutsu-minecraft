package jujutsu.mod.cursedincident.runtime;

import net.minecraft.core.BlockPos;

/**
 * World-free dwell math.  A position outside the radius starts a fresh dwell;
 * crossing the threshold reports the anchor once and resets the running counter.
 */
public final class DwellAccumulator {
    public record Result(BlockPos anchor, long accumulatedTicks, long lastGameTime, BlockPos dwellCenter) {
        public boolean thresholdCrossed() {
            return dwellCenter != null;
        }

        public BlockPos center() {
            return dwellCenter;
        }

        public BlockPos newAnchor() {
            return anchor;
        }
    }

    private DwellAccumulator() {
    }

    /**
     * Stateless convenience form: {@code nowGameTime} is treated as elapsed time
     * since the anchor was first observed.  This keeps the helper useful in JUnit
     * without a fake clock object.
     */
    public static Result update(BlockPos anchor, BlockPos pos, long nowGameTime,
            long dwellTicksRequired, double dwellRadius) {
        if (pos == null) {
            return new Result(anchor, 0L, nowGameTime, null);
        }
        if (anchor == null || outside(anchor, pos, dwellRadius)) {
            return new Result(pos, 0L, nowGameTime, null);
        }
        long elapsed = Math.max(0L, nowGameTime);
        long required = Math.max(1L, dwellTicksRequired);
        if (elapsed >= required) {
            return new Result(anchor, 0L, nowGameTime, anchor);
        }
        return new Result(anchor, elapsed, nowGameTime, null);
    }

    /** Stateful form used by the server tracker. */
    public static Result update(BlockPos anchor, BlockPos pos, long previousGameTime,
            long accumulatedTicks, long nowGameTime, long dwellTicksRequired, double dwellRadius) {
        if (pos == null) {
            return new Result(anchor, Math.max(0L, accumulatedTicks), nowGameTime, null);
        }
        if (anchor == null || outside(anchor, pos, dwellRadius)) {
            return new Result(pos, 0L, nowGameTime, null);
        }
        long elapsed = Math.max(0L, nowGameTime - previousGameTime);
        long accumulated = Math.max(0L, accumulatedTicks) + elapsed;
        long required = Math.max(1L, dwellTicksRequired);
        if (accumulated >= required) {
            return new Result(anchor, 0L, nowGameTime, anchor);
        }
        return new Result(anchor, accumulated, nowGameTime, null);
    }

    public static boolean outside(BlockPos anchor, BlockPos pos, double dwellRadius) {
        double radius = Math.max(0.0, dwellRadius);
        return anchor.distSqr(pos) > radius * radius;
    }
}
