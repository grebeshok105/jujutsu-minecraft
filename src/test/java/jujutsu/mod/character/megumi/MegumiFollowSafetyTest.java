package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiFollowSafetyTest {
	@Test
	void candidatesUseCenterThenDistanceXZSortedRingsAndApprovedYOrder() {
		List<MegumiGroundSafety.HorizontalOffset> radiusOne = MegumiGroundSafety.buildLeashOffsets(1);
		assertEquals(List.of(
				new MegumiGroundSafety.HorizontalOffset(0, 0),
				new MegumiGroundSafety.HorizontalOffset(-1, 0),
				new MegumiGroundSafety.HorizontalOffset(0, -1),
				new MegumiGroundSafety.HorizontalOffset(0, 1),
				new MegumiGroundSafety.HorizontalOffset(1, 0),
				new MegumiGroundSafety.HorizontalOffset(-1, -1),
				new MegumiGroundSafety.HorizontalOffset(-1, 1),
				new MegumiGroundSafety.HorizontalOffset(1, -1),
				new MegumiGroundSafety.HorizontalOffset(1, 1)), radiusOne);
		assertEquals(49, MegumiGroundSafety.buildLeashOffsets(3).size());
		assertEquals(List.of(0, 1, -1, 2, -2, 3, -3), MegumiGroundSafety.verticalOffsets());
	}

	@Test
	void safeGroundRequiresLoadedFloorAndFreeNonHazardousSpace() {
		assertTrue(MegumiGroundSafety.accepts(facts(true, true, false, true, false)),
				"water is allowed when it is not lava and the floor and AABB are safe");
		assertFalse(MegumiGroundSafety.accepts(facts(false, true, false, true, false)));
		assertFalse(MegumiGroundSafety.accepts(facts(true, false, false, true, false)));
		assertFalse(MegumiGroundSafety.accepts(facts(true, true, true, true, false)),
				"fire and lava are hazardous");
		assertFalse(MegumiGroundSafety.accepts(facts(true, true, false, false, false)));
		assertFalse(MegumiGroundSafety.accepts(facts(true, true, false, true, true)));
	}

	@Test
	void exhaustedCandidatesReturnEmptyWithoutAnExactPositionFallback() {
		List<Vec3> candidates = List.of(new Vec3(1.0, 2.0, 3.0), new Vec3(4.0, 5.0, 6.0));
		assertTrue(MegumiGroundSafety.firstSafe(candidates, candidate -> false).isEmpty());
		assertEquals(candidates.get(1),
				MegumiGroundSafety.firstSafe(candidates, candidate -> candidate == candidates.get(1)).orElseThrow());
	}

	private static MegumiGroundSafety.SafetyFacts facts(
			boolean loaded, boolean sturdyFloor, boolean hazard,
			boolean collisionFree, boolean entityCollision) {
		return new MegumiGroundSafety.SafetyFacts(
				loaded, sturdyFloor, hazard, collisionFree, entityCollision);
	}
}
