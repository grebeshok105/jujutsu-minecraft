package jujutsu.mod.character.todo;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure stone profile, eligibility, and shared-plan contracts. */
class TodoStoneTest {
	@Test
	void stoneProfileRemainsReadableAndFinite() {
		assertTrue(TodoProfile.STONE_SPEED_BLOCKS_PER_TICK >= 0.20);
		assertTrue(TodoProfile.STONE_SPEED_BLOCKS_PER_TICK <= 0.28);
		assertEquals(100, TodoProfile.STONE_LIFETIME_TICKS);
	}

	@Test
	void liveStoneNeverThrowsAgain() {
		TodoStoneRef ref = new TodoStoneRef(java.util.UUID.randomUUID(),
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
						net.minecraft.resources.ResourceLocation.parse("minecraft:overworld")), 0);
		assertTrue(TodoStoneRuntime.shouldThrow(Optional.empty()));
		assertFalse(TodoStoneRuntime.shouldThrow(Optional.of(ref)));
	}

	@Test
	void stoneEligibilityAndRangeBoundaryAreStable() {
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(false, true, true));
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(true, false, true));
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(true, true, false));
		assertTrue(TodoStoneRuntime.stoneEligibleForSwap(true, true, true));
		double range = TodoProfile.STONE_SWAP_RANGE;
		assertTrue(TodoStoneRuntime.withinSwapRange(range * range));
		assertFalse(TodoStoneRuntime.withinSwapRange(range * range + 1.0E-4));
	}

	@Test
	void stonePlanUsesUnifiedAllOrNothingPreflight() {
		assertTrue(SwapPlan.preflight(new SwapMove(null, new Vec3(3, 70, 3))).isPresent());
		assertTrue(SwapPlan.preflight(new SwapMove(null, null)).isEmpty());
	}

	@Test
	void stoneKindsKeepCooldownAndMomentumSeparation() {
		assertEquals(60, SwapKind.STONE_SELF.baseCooldownTicks());
		assertEquals(100, SwapKind.STONE_TARGET.baseCooldownTicks());
		assertTrue(SwapKind.STONE_SELF.grantsMomentum());
		assertFalse(SwapKind.STONE_TARGET.grantsMomentum());
	}
}

// Red: before the migration, stone preflight lived in a deleted record;
// `./gradlew.bat testTodoStone` failed once the unified `SwapPlan` contract was absent.
// Run: ./gradlew.bat testTodoStone
// Expected: stone profile, boundary, and unified-plan assertions pass.
