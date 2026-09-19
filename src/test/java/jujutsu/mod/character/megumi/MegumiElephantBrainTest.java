package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiElephantBrainTest {
	@Test
	void closeSicDistancesMayStartTheJet() {
		assertTrue(MegumiElephantBrain.jetTriggerInReach(0.0));
		assertTrue(MegumiElephantBrain.jetTriggerInReach(5.0));
		assertTrue(MegumiElephantBrain.jetTriggerInReach(
				MegumiShikigamiProfile.ELEPHANT_JET_LENGTH
						+ MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD),
				"the corridor's far edge is still hosed");
	}

	@Test
	void theOldSixteenBlockTriggerRefusesToFireABlankVolley() {
		assertFalse(MegumiElephantBrain.jetTriggerInReach(16.0),
				"16 blocks outruns the 12-block corridor from a trunk 1.2 ahead: a jet from there "
						+ "spends the windup, twenty pulses, and a 220-tick lockout on empty air");
		assertFalse(MegumiElephantBrain.jetTriggerInReach(
				MegumiShikigamiProfile.SIC_RANGE),
				"the sic acquisition range must stay wider than the jet's real reach");
	}
	@Test
	void elephantJetCueCarriesFacingForDirectionalRibbon() {
		Vec3 trunk = new Vec3(3.0, 70.0, 4.0);
		Vec3 elephant = new Vec3(3.0, 68.8, 4.0);
		Vec3 facing = new Vec3(1.0, 0.1, 0.0);
		VfxCue cue = MegumiShikigamiRuntime.directedCue(MegumiVfxIds.ELEPHANT_JET,
				trunk, 12, elephant, 1, 20L, 99L, facing);

		assertEquals(MegumiVfxIds.ELEPHANT_JET, cue.effectId());
		assertEquals(trunk.subtract(elephant), cue.anchorOffset());
		assertEquals(facing.normalize().x, cue.direction().x, 1.0E-6);
		assertEquals(facing.normalize().y, cue.direction().y, 1.0E-6);
		assertEquals(facing.normalize().z, cue.direction().z, 1.0E-6);
	}
}
