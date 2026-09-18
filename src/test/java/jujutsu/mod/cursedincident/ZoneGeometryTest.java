package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedincident.infection.ZoneGeometry;

/** R49 geometry containment, deterministic samples and edge-gradient oracles. */
final class ZoneGeometryTest {
	@Test
	void sphereAndColumnContainmentDifferOnlyOnVerticalAxis() {
		BlockPos center = new BlockPos(0, 64, 0);
		BlockPos high = new BlockPos(0, 70, 0);
		assertFalse(ZoneGeometry.contains(ZoneGeometry.Shape.SPHERE, center, 4.0, high));
		assertTrue(ZoneGeometry.contains(ZoneGeometry.Shape.COLUMN, center, 4.0, high));
		assertTrue(ZoneGeometry.contains(ZoneGeometry.Shape.SPHERE, center, 4.0, center));
	}

	@Test
	void seededSamplesReplayExactlyAndStayInside() {
		BlockPos center = new BlockPos(10, 64, -4);
		List<BlockPos> first = ZoneGeometry.sampleBlocks(ZoneGeometry.Shape.SPHERE, center, 6.0,
				RandomSource.create(1234L), 40);
		List<BlockPos> second = ZoneGeometry.sampleBlocks(ZoneGeometry.Shape.SPHERE, center, 6.0,
				RandomSource.create(1234L), 40);
		assertEquals(first, second);
		assertTrue(first.stream().allMatch(pos -> ZoneGeometry.contains(ZoneGeometry.Shape.SPHERE, center, 6.0, pos)));
	}

	@Test
	void edgeDistanceIsMonotonicTowardCentre() {
		BlockPos center = new BlockPos(0, 64, 0);
		double edge = ZoneGeometry.edgeDistance(ZoneGeometry.Shape.SPHERE, center, 8.0,
				new BlockPos(8, 64, 0));
		double middle = ZoneGeometry.edgeDistance(ZoneGeometry.Shape.SPHERE, center, 8.0,
				new BlockPos(4, 64, 0));
		double centre = ZoneGeometry.edgeDistance(ZoneGeometry.Shape.SPHERE, center, 8.0, center);
		assertTrue(edge <= middle && middle <= centre);
		assertEquals(0.0, ZoneGeometry.edgeDistance(ZoneGeometry.Shape.SPHERE, center, 8.0,
				new BlockPos(9, 64, 0)));
	}
}
