package jujutsu.mod.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The shared placement policy's candidate ring: centre first, then the half cross, then the full
 * cross, then the diagonals — one precomputed, unmodifiable list per policy. The order is part of
 * the contract because the first safe candidate wins, so a body prefers the exact requested point
 * and the near ring over a far-flung diagonal.
 */
class SafeBodyPlacementTest {
	@Test
	void horizontalOffsetsStartAtTheCentreAndFanOutInCrossesThenDiagonals() {
		List<Vec3> offsets = new SafeBodyPlacement.Policy(1.0, 3, 0.05, false).horizontalOffsets();
		assertEquals(13, offsets.size());
		assertEquals(Vec3.ZERO, offsets.get(0));
		// Half cross: 0.5 of the radius on each axis.
		assertEquals(new Vec3(0.5, 0.0, 0.0), offsets.get(1));
		assertEquals(new Vec3(-0.5, 0.0, 0.0), offsets.get(2));
		assertEquals(new Vec3(0.0, 0.0, 0.5), offsets.get(3));
		assertEquals(new Vec3(0.0, 0.0, -0.5), offsets.get(4));
		// Full cross: the radius itself.
		assertEquals(new Vec3(1.0, 0.0, 0.0), offsets.get(5));
		assertEquals(new Vec3(-1.0, 0.0, 0.0), offsets.get(6));
		assertEquals(new Vec3(0.0, 0.0, 1.0), offsets.get(7));
		assertEquals(new Vec3(0.0, 0.0, -1.0), offsets.get(8));
		// Diagonals: 0.7 of the radius, one per quadrant.
		assertEquals(new Vec3(0.7, 0.0, 0.7), offsets.get(9));
		assertEquals(new Vec3(0.7, 0.0, -0.7), offsets.get(10));
		assertEquals(new Vec3(-0.7, 0.0, 0.7), offsets.get(11));
		assertEquals(new Vec3(-0.7, 0.0, -0.7), offsets.get(12));
	}

	@Test
	void offsetsScaleWithTheRadius() {
		List<Vec3> offsets = new SafeBodyPlacement.Policy(3.0, 3, 0.05, false).horizontalOffsets();
		assertEquals(13, offsets.size());
		assertEquals(Vec3.ZERO, offsets.get(0));
		assertEquals(new Vec3(1.5, 0.0, 0.0), offsets.get(1));
		assertEquals(new Vec3(3.0, 0.0, 0.0), offsets.get(5));
		// 0.7 of 3.0 is not exactly representable; derive the expected value the same way the
		// policy does so the comparison is exact.
		double diag = 3.0 * 0.7;
		assertEquals(new Vec3(diag, 0.0, diag), offsets.get(9));
	}

	@Test
	void constructorStoresThePlacementParameters() {
		SafeBodyPlacement.Policy policy = new SafeBodyPlacement.Policy(2.0, 5, 0.1, true);
		assertEquals(5, policy.upwardBlocks());
		assertEquals(0.1, policy.borderMargin());
		assertTrue(policy.exactRequestedFallback());
		assertFalse(new SafeBodyPlacement.Policy(2.0, 5, 0.1, false).exactRequestedFallback());
	}

	@Test
	void theCandidateRingIsReadOnly() {
		// Policies are declared static final and shared across casts; a mutable ring would let one
		// caller corrupt another's search order.
		List<Vec3> offsets = new SafeBodyPlacement.Policy(1.0, 3, 0.05, false).horizontalOffsets();
		assertThrows(UnsupportedOperationException.class, () -> offsets.add(Vec3.ZERO));
	}
}
