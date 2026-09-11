package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;
import org.junit.jupiter.api.Test;

class MegumiDogAnimationPolicyTest {
	@Test
	void transientPhasesBeatAttackAndMovement() {
		assertEquals(MegumiDogAnimationPolicy.Clip.RISE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.MATERIALIZING, true, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.RISE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.MATERIALIZING, false, false, false));
		assertEquals(MegumiDogAnimationPolicy.Clip.SINK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.RECALLING, true, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.SINK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.RECALLING, false, false, false));
	}

	@Test
	void attackBeatsMovement() {
		assertEquals(MegumiDogAnimationPolicy.Clip.ATTACK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, true, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.ATTACK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, false, true));
		assertEquals(MegumiDogAnimationPolicy.Clip.ATTACK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, false, false, true));
	}

	@Test
	void runningSelectsSprintWhileWalkingSelectsWalk() {
		assertEquals(MegumiDogAnimationPolicy.Clip.SPRINT,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, true, false));
		assertEquals(MegumiDogAnimationPolicy.Clip.WALK,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, true, false, false));
	}

	@Test
	void standingStillSelectsIdle() {
		assertEquals(MegumiDogAnimationPolicy.Clip.IDLE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, false, false, false));
	}

	@Test
	void runningWithoutMovingStillIdles() {
		// Callers never set running without moving (running derives from the same velocity);
		// the moving gate runs first, so a stray running flag cannot select SPRINT.
		assertEquals(MegumiDogAnimationPolicy.Clip.IDLE,
				MegumiDogAnimationPolicy.decide(MegumiDogPresentationPolicy.Phase.ACTIVE, false, true, false));
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
}
