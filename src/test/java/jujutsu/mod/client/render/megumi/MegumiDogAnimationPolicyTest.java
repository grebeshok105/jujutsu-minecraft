package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;
import org.junit.jupiter.api.Test;

class MegumiDogAnimationPolicyTest {
	@Test
	void transientPhasesBeatMovement() {
		assertEquals(MegumiDogAnimationPolicy.Clip.RISE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.MATERIALIZING, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.RISE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.MATERIALIZING, false, false));
		assertEquals(MegumiDogAnimationPolicy.Clip.SINK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.RECALLING, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.SINK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.RECALLING, false, false));
	}

	@Test
	void runningSelectsSprintWhileWalkingSelectsWalk() {
		assertEquals(MegumiDogAnimationPolicy.Clip.SPRINT,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.WALK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, false));
	}

	@Test
	void standingStillSelectsIdle() {
		assertEquals(MegumiDogAnimationPolicy.Clip.IDLE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, false, false));
	}

	@Test
	void runningWithoutMovingStillIdles() {
		// Callers never set running without moving (running derives from the same velocity);
		// the moving gate runs first, so a stray running flag cannot select SPRINT.
		assertEquals(MegumiDogAnimationPolicy.Clip.IDLE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, false, true));
	}

	@Test
	void horizontalSpeedDecidesRunning() {
		assertFalse(MegumiDogAnimationPolicy.isRunning(0.0, 0.0));
		assertFalse(MegumiDogAnimationPolicy.isRunning(0.1, 0.0), "0.01 squared is still walking speed");
		assertTrue(MegumiDogAnimationPolicy.isRunning(0.15, 0.0), "0.0225 squared is running speed");
		assertTrue(MegumiDogAnimationPolicy.isRunning(0.0, -0.2), "direction is irrelevant, only horizontal speed counts");
	}

	@Test
	void aRestingSwingIsNotAnAttack() {
		assertFalse(MegumiDogAnimationPolicy.isAttacking(0.0f));
		assertFalse(MegumiDogAnimationPolicy.isAttacking(0.01f), "the threshold is exclusive");
		assertTrue(MegumiDogAnimationPolicy.isAttacking(0.5f));
	}

	@Test
	void theJawLayerOutlivesTheSwingWindowButNotItsClip() {
		assertTrue(MegumiDogAnimationPolicy.biteOwnsJaw(true, false, false),
				"A fresh swing must start the bite clip");
		assertTrue(MegumiDogAnimationPolicy.biteOwnsJaw(false, true, false),
				"The 0.92 s clip keeps the jaw after the six-tick swing that started it is over");
		assertTrue(MegumiDogAnimationPolicy.biteOwnsJaw(true, true, false), "A repeat bite rides the running clip");
		assertFalse(MegumiDogAnimationPolicy.biteOwnsJaw(false, true, true), "A finished bite releases the jaw");
		assertFalse(MegumiDogAnimationPolicy.biteOwnsJaw(false, false, false), "No swing and no clip: the layer stays silent");
	}

	@Test
	void aFinishedBiteRestartsInsteadOfHoldingItsLastFrame() {
		assertTrue(MegumiDogAnimationPolicy.biteNeedsRestart(true, true, true),
				"A new swing on a finished clip must reset it, or the controller holds the last frame");
		assertFalse(MegumiDogAnimationPolicy.biteNeedsRestart(true, false, false), "A first bite has nothing to reset");
		assertFalse(MegumiDogAnimationPolicy.biteNeedsRestart(false, true, true), "Only a swing restarts the clip");
	}
}
