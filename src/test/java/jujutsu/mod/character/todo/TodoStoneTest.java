package jujutsu.mod.character.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Pure pins of the stone kit: profile numbers, the single-stone policy, the swap eligibility gate and
 * the one-destination preflight. Entity-level behavior — collision, sync, teleportation, the swap
 * commit itself — is manual smoke only, per the wave's SESSION.md checklist.
 *
 * <p>The target-side eligibility family (alive, not spectator, not removed, not an armor stand,
 * transport-safe, finite position, same level) is delegated to
 * {@link TodoBoogieWoogieRuntime#isEligibleTarget} and is pinned by that family's own tests; this
 * test tables the stone-side gate: present, in the caster's level, within swap range.
 */
class TodoStoneTest {
	@Test
	void stoneSpeedSitsInTheReadableBand() {
		double blocksPerSecond = TodoProfile.STONE_SPEED_BLOCKS_PER_TICK * 20.0;
		assertTrue(blocksPerSecond >= 3.0 && blocksPerSecond <= 4.0,
				"stone speed " + blocksPerSecond + " b/s must stay in the readable 3-4 band");
	}

	@Test
	void stoneLivesFiveSecondsInFlight() {
		assertEquals(100, TodoProfile.STONE_LIFETIME_TICKS, "lifetime must be 100 ticks (5 s)");
	}

	@Test
	void throwCooldownIsAntiDoubleClickNotAGate() {
		assertTrue(TodoProfile.STONE_THROW_COOLDOWN_TICKS * 4 < TodoProfile.STONE_LIFETIME_TICKS,
				"the throw cooldown must be a tiny fraction of the flight so V never locks the follow-up swap");
	}

	@Test
	void theThreeStoneCooldownsAreDistinctAndEscalate() {
		assertNotEquals(TodoProfile.STONE_THROW_COOLDOWN_TICKS, TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS);
		assertNotEquals(TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS, TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS);
		assertNotEquals(TodoProfile.STONE_THROW_COOLDOWN_TICKS, TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS);
		assertTrue(TodoProfile.STONE_THROW_COOLDOWN_TICKS < TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS,
				"the throw is free, the self-swap is the first real price");
		assertTrue(TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS < TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS,
				"moving a bystander costs more than moving himself");
	}

	@Test
	void selfSwapCooldownOutlivesTheMomentumWindowItGrants() {
		assertTrue(TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS > TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS,
				"the self-swap grants momentum, so its cooldown must outlive the window or two grants overlap");
	}

	@Test
	void swapRangesPriceTheStoneAndTheCrosshairSeparately() {
		assertEquals(32.0, TodoProfile.STONE_SWAP_RANGE, "Todo-to-stone reach for either swap");
		assertEquals(20.0, TodoProfile.STONE_TARGET_RANGE, "Shift+V crosshair reach");
		assertEquals(TodoProfile.BOOGIE_WOOGIE_RANGE, TodoProfile.STONE_TARGET_RANGE,
				"Shift+V must reach exactly as far as the aimed swap");
		assertTrue(TodoProfile.STONE_SWAP_RANGE > TodoProfile.STONE_TARGET_RANGE);
	}

	@Test
	void liveStoneNeverThrowsASecondOne() {
		TodoStoneRef ref = sampleRef();
		assertTrue(TodoStoneRuntime.shouldThrow(Optional.empty()), "no stone -> V throws");
		assertFalse(TodoStoneRuntime.shouldThrow(Optional.of(ref)),
				"live stone -> V is the self-swap, never a second throw");
	}

	@Test
	void stoneSwapEligibilityTable() {
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(false, false, false));
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(false, true, true));
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(true, false, true));
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(true, true, false));
		assertTrue(TodoStoneRuntime.stoneEligibleForSwap(true, true, true),
				"a present, same-level stone within range is the only eligible swap partner");
	}

	@Test
	void unsafeDestinationCancelsTheWholePlan() {
		assertTrue(TodoStonePlan.preflight(new Vec3(3.0, 70.0, 3.0)).isPresent(),
				"a safe destination must produce a committable plan");
		assertTrue(TodoStonePlan.preflight(null).isEmpty(),
				"an unsafe destination must cancel the cast before anything moves");
	}

	@Test
	void swapRangeBoundarySitsAtThirtyTwoBlocks() {
		double range = TodoProfile.STONE_SWAP_RANGE;
		assertTrue(TodoStoneRuntime.stoneEligibleForSwap(true, true, range * range),
				"exactly at range must remain eligible");
		assertFalse(TodoStoneRuntime.stoneEligibleForSwap(true, true, range * range + 1.0E-4),
				"just past range must refuse");
	}

	private static TodoStoneRef sampleRef() {
		return new TodoStoneRef(UUID.randomUUID(), 1,
				ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")), 0L);
	}
}
