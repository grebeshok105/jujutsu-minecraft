package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Pure-seam pins for {@link MegumiOxBrain}: the cue payloads' ids, anchors, and the
 * real-distance intensity the plan pins on the charge trail.
 */
class MegumiOxBrainTest {

	@Test
	void theChargeTrailCarriesRealDistanceAsIntensity() {
		VfxCue first = MegumiOxBrain.chargeCue(new Vec3(0.0, 1.0, 0.0), 7, 0.4, 10L,
				new Vec3(1.0, 0.0, 0.0));
		VfxCue later = MegumiOxBrain.chargeCue(new Vec3(6.0, 1.0, 0.0), 7, 6.6, 40L,
				new Vec3(1.0, 0.0, 0.0));
		assertEquals(MegumiVfxIds.OX_CHARGE, first.effectId());
		assertEquals(1, first.intensity(), "a barely-started charge still flashes the minimum");
		assertEquals(7, later.intensity(), "intensity is the rounded accumulated distance");
	}

	@Test
	void theChargeCueAimsDownTheFrozenLine() {
		Vec3 direction = new Vec3(0.6, 0.0, 0.8);
		VfxCue cue = MegumiOxBrain.chargeCue(new Vec3(0.0, 1.0, 0.0), 7, 3.0, 20L, direction);
		assertEquals(direction.x, cue.direction().x, 1.0E-6);
		assertEquals(direction.z, cue.direction().z, 1.0E-6);
	}

	@Test
	void aNullDirectionStaysZero() {
		VfxCue cue = MegumiOxBrain.chargeCue(new Vec3(0.0, 1.0, 0.0), 7, 3.0, 20L, null);
		assertEquals(Vec3.ZERO, cue.direction());
	}

	@Test
	void theImpactBurstAnchorsOnTheVictim() {
		VfxCue cue = MegumiOxBrain.impactCue(new Vec3(5.0, 1.0, 2.0), 42, 7.0, 20L,
				new Vec3(1.0, 0.0, 0.0));
		assertEquals(MegumiVfxIds.OX_IMPACT, cue.effectId());
		assertEquals(42, cue.anchorEntityId(), "the burst lives where the hit landed");
		assertEquals(4, cue.intensity(), "impact intensity is the rounded power");
	}

	@Test
	void theWallHitKeepsTheFrozenLine() {
		Vec3 direction = new Vec3(-1.0, 0.0, 0.0);
		VfxCue cue = MegumiOxBrain.wallCue(new Vec3(4.0, 1.0, 0.0), 9, 5.0, 30L, direction);
		assertEquals(MegumiVfxIds.OX_WALL_HIT, cue.effectId());
		assertEquals(-1.0, cue.direction().x, 1.0E-6, "the slam faces along the committed charge");
	}

	@Test
	void theWindupTelegraphAnchorsOnTheOx() {
		VfxCue cue = MegumiOxBrain.windupCue(new Vec3(1.0, 1.0, 1.0), 9, 15L);
		assertEquals(MegumiVfxIds.OX_WINDUP, cue.effectId());
		assertEquals(9, cue.anchorEntityId());
		assertEquals(1, cue.intensity());
	}
}
