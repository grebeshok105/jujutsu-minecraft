package jujutsu.mod.client.curse.fear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Fear key matrix (Block 3, Step 7): the pure {@link FearInputMap} behind the keyboard
 * mixin. Movement pairs swap, hotbar mirrors, hands/drop swap, everything else is
 * identity — and every remap is an involution, so the fear ending mid-hold cannot stick
 * a key (applying the map twice is the identity, except both Shifts pairing with Space
 * by design).
 *
 * <p>Red-proofs: unswap any pair → involution red; remap E or Esc → identity red.
 */
final class FearInputMapTest {
	@Test
	void movementPairsSwap() {
		assertEquals(FearInputMap.KEY_S, FearInputMap.remapKey(FearInputMap.KEY_W));
		assertEquals(FearInputMap.KEY_W, FearInputMap.remapKey(FearInputMap.KEY_S));
		assertEquals(FearInputMap.KEY_D, FearInputMap.remapKey(FearInputMap.KEY_A));
		assertEquals(FearInputMap.KEY_A, FearInputMap.remapKey(FearInputMap.KEY_D));
		assertEquals(FearInputMap.KEY_LEFT_SHIFT, FearInputMap.remapKey(FearInputMap.KEY_SPACE));
		assertEquals(FearInputMap.KEY_SPACE, FearInputMap.remapKey(FearInputMap.KEY_LEFT_SHIFT));
		assertEquals(FearInputMap.KEY_SPACE, FearInputMap.remapKey(FearInputMap.KEY_RIGHT_SHIFT));
	}

	@Test
	void hotbarMirrorsAroundFive() {
		assertEquals(57, FearInputMap.remapKey(49));
		assertEquals(49, FearInputMap.remapKey(57));
		assertEquals(56, FearInputMap.remapKey(50));
		assertEquals(55, FearInputMap.remapKey(51));
		assertEquals(54, FearInputMap.remapKey(52));
		assertEquals(53, FearInputMap.remapKey(53));
	}

	@Test
	void swapHandsAndDropSwap() {
		assertEquals(FearInputMap.KEY_Q, FearInputMap.remapKey(FearInputMap.KEY_F));
		assertEquals(FearInputMap.KEY_F, FearInputMap.remapKey(FearInputMap.KEY_Q));
	}

	@Test
	void everythingElseIsIdentity() {
		assertEquals(69, FearInputMap.remapKey(69));
		assertEquals(256, FearInputMap.remapKey(256));
		assertEquals(290, FearInputMap.remapKey(290));
	}

	/**
	 * Look deltas (yaw/pitch args of {@code LocalPlayer.turn}) mirror under fear and
	 * pass through untouched when it is inactive. Red-proofs: dropping the negation or
	 * flipping the predicate → both sides red.
	 */
	@Test
	void lookDeltaMirrorsUnderFear() {
		assertEquals(-12.5, FearInputMap.lookDelta(12.5, true));
		assertEquals(3.0, FearInputMap.lookDelta(-3.0, true));
		assertEquals(-0.0, FearInputMap.lookDelta(0.0, true));
	}

	@Test
	void lookDeltaIsIdentityWithoutFear() {
		assertEquals(12.5, FearInputMap.lookDelta(12.5, false));
		assertEquals(-3.0, FearInputMap.lookDelta(-3.0, false));
		assertEquals(0.0, FearInputMap.lookDelta(0.0, false));
	}
}
