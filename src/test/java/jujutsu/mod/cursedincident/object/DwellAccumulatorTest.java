package jujutsu.mod.cursedincident.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import jujutsu.mod.cursedincident.runtime.DwellAccumulator;
import org.junit.jupiter.api.Test;

final class DwellAccumulatorTest {
    private static final BlockPos ORIGIN = new BlockPos(4, 70, -3);

    @Test
    void stationaryPositionAccumulatesAndCrossingReportsItsAnchor() {
        DwellAccumulator.Result first = DwellAccumulator.update(null, ORIGIN, 0L, 100L, 1.0);
        DwellAccumulator.Result stationary = DwellAccumulator.update(first.anchor(), ORIGIN, 40L, 100L, 1.0);
        assertEquals(ORIGIN, stationary.anchor());
        assertEquals(40L, stationary.accumulatedTicks());
        assertNull(stationary.dwellCenter());

        DwellAccumulator.Result crossed = DwellAccumulator.update(stationary.anchor(), ORIGIN, 100L, 100L, 1.0);
        assertEquals(ORIGIN, crossed.dwellCenter());
        assertEquals(0L, crossed.accumulatedTicks());
    }

    @Test
    void movementOutsideRadiusResetsAnchorAndAccumulator() {
        DwellAccumulator.Result moved = DwellAccumulator.update(ORIGIN, ORIGIN.offset(3, 0, 0),
                80L, 100L, 1.0);
        assertEquals(ORIGIN.offset(3, 0, 0), moved.anchor());
        assertEquals(0L, moved.accumulatedTicks());
        assertNull(moved.dwellCenter());
    }

    @Test
    void statefulFormAddsOnlyElapsedGameTime() {
        DwellAccumulator.Result result = DwellAccumulator.update(ORIGIN, ORIGIN, 20L, 30L,
                55L, 100L, 1.0);
        assertEquals(65L, result.accumulatedTicks());
        assertEquals(55L, result.lastGameTime());
    }
}
