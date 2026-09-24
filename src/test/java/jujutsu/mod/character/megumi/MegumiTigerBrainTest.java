package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pins the pure seams of the production Tiger combo paths: the strike cue and the yaw math. */
final class MegumiTigerBrainTest {
	private static final double DELTA = 1.0E-6;

	@Test
	void productionStrikeCueKeepsImpactFixedAndCarriesTheBeatInIntensity() {
		Vec3 tiger = new Vec3(2.0, 64.0, 2.0);
		Vec3 target = new Vec3(4.0, 64.5, 4.0);
		Vec3 facing = new Vec3(Math.sqrt(0.5), 0.0, Math.sqrt(0.5));
		VfxCue cue = MegumiTigerBrain.strikeCue(target, 77, tiger, 3, 900L, 1234L, facing);

		assertEquals(MegumiVfxIds.TIGER_STRIKE, cue.effectId());
		assertEquals(target, cue.origin(), "the impact point is the immutable cue origin");
		assertEquals(77, cue.anchorEntityId(), "the live endpoint is the tiger body");
		assertEquals(target.subtract(tiger), cue.anchorOffset());
		assertEquals(3, cue.intensity(), "the finisher's beat index — the client sizes the slash by it");
		assertEquals(facing.x, cue.direction().x, DELTA);
		assertEquals(facing.y, cue.direction().y, DELTA);
		assertEquals(facing.z, cue.direction().z, DELTA);
		assertNotEquals(VfxCue.NO_ANCHOR, cue.anchorEntityId());
	}

	@Test
	void bearingDegMatchesTheVanillaYawConvention() {
		assertEquals(0.0, MegumiTigerBrain.bearingDeg(new Vec3(0.0, 0.0, 4.0)), DELTA,
				"due south is yaw zero");
		assertEquals(-90.0, MegumiTigerBrain.bearingDeg(new Vec3(4.0, 0.0, 0.0)), DELTA,
				"due east is yaw -90");
		assertEquals(90.0, MegumiTigerBrain.bearingDeg(new Vec3(-4.0, 0.0, 0.0)), DELTA);
		assertEquals(180.0, Math.abs(MegumiTigerBrain.bearingDeg(new Vec3(0.0, 0.0, -4.0))), DELTA,
				"due north is yaw ±180");
	}
}
