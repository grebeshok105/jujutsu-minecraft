package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy.Phase;
import org.junit.jupiter.api.Test;

/**
 * Pure clip-choice pins for the new shikigami bodies: transient phases outrank everything, an
 * action outranks travel, and rest maps to the renderer's stop pose. No Minecraft bootstrap —
 * the policy is static math over the presentation phase.
 */
class MegumiShikigamiAnimationPolicyTest {
	@Test
	void transientPhasesBeatActionAndTravel() {
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.RISE,
				MegumiShikigamiAnimationPolicy.toad(Phase.MATERIALIZING, true, 5, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.SINK,
				MegumiShikigamiAnimationPolicy.toad(Phase.RECALLING, true, 5, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.RISE,
				MegumiShikigamiAnimationPolicy.elephant(Phase.MATERIALIZING, true, true, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.SINK,
				MegumiShikigamiAnimationPolicy.elephant(Phase.RECALLING, true, true, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.RISE,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.MATERIALIZING, true, true));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.SINK,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.RECALLING, true, true));
	}

	@Test
	void toadActionBeatsWalkAndRestIdles() {
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.ACTION,
				MegumiShikigamiAnimationPolicy.toad(Phase.ACTIVE, true, 3, 0.0f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.ACTION,
				MegumiShikigamiAnimationPolicy.toad(Phase.ACTIVE, false, 0, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.FLY,
				MegumiShikigamiAnimationPolicy.toad(Phase.ACTIVE, true, 0, 0.0f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.IDLE,
				MegumiShikigamiAnimationPolicy.toad(Phase.ACTIVE, false, 0, 0.0f));
	}

	@Test
	void elephantJetAndSwingBothReadAsActionOverTravel() {
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.ACTION,
				MegumiShikigamiAnimationPolicy.elephant(Phase.ACTIVE, true, true, 0.0f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.ACTION,
				MegumiShikigamiAnimationPolicy.elephant(Phase.ACTIVE, true, false, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.ACTION,
				MegumiShikigamiAnimationPolicy.elephant(Phase.ACTIVE, false, true, 0.5f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.FLY,
				MegumiShikigamiAnimationPolicy.elephant(Phase.ACTIVE, true, false, 0.0f));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.IDLE,
				MegumiShikigamiAnimationPolicy.elephant(Phase.ACTIVE, false, false, 0.0f));
	}

	@Test
	void rabbitsHaveNoActionClipSoMovementDecides() {
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.RUN,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.ACTIVE, true, true));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.FLY,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.ACTIVE, true, false));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.IDLE,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.ACTIVE, false, false));
		assertEquals(MegumiShikigamiAnimationPolicy.Clip.IDLE,
				MegumiShikigamiAnimationPolicy.rabbits(Phase.ACTIVE, false, true));
	}

	@Test
	void horizontalSpeedDecidesRunning() {
		assertFalse(MegumiShikigamiAnimationPolicy.isRunning(0.0, 0.0));
		assertFalse(MegumiShikigamiAnimationPolicy.isRunning(0.09, 0.09),
				"0.0162 squared sits below the 0.02 threshold");
		assertTrue(MegumiShikigamiAnimationPolicy.isRunning(0.15, 0.0), "0.0225 squared is running speed");
		assertTrue(MegumiShikigamiAnimationPolicy.isRunning(0.0, -0.2),
				"direction is irrelevant, only horizontal speed counts");
	}

	@Test
	void aRestingSwingIsNotAnAttack() {
		assertFalse(MegumiShikigamiAnimationPolicy.isAttacking(0.0f));
		assertFalse(MegumiShikigamiAnimationPolicy.isAttacking(0.01f), "the threshold is exclusive");
		assertTrue(MegumiShikigamiAnimationPolicy.isAttacking(0.5f));
	}
}
