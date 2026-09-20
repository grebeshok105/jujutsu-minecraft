package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pins the directed cue factory used by the production Nue impact path. */
final class NueArcCueTest {
	private static final double DELTA = 1.0E-6;

	@Test
	void productionShockCueKeepsTargetFixedAndPointsFromNueToTarget() {
		Vec3 nue = new Vec3(4.0, 70.0, -2.0);
		Vec3 target = new Vec3(9.0, 71.5, 3.0);
		VfxCue cue = MegumiNueBrain.shockCue(target, 41, nue, 900L, 1234L);

		assertEquals(MegumiVfxIds.NUE_SHOCK, cue.effectId());
		assertEquals(target, cue.origin(), "impact endpoint is immutable cue origin");
		assertEquals(41, cue.anchorEntityId(), "the live endpoint is the Nue entity");
		assertEquals(target.subtract(nue), cue.anchorOffset());
		Vec3 expectedDirection = target.subtract(nue).normalize();
		assertEquals(expectedDirection.x, cue.direction().x, DELTA);
		assertEquals(expectedDirection.y, cue.direction().y, DELTA);
		assertEquals(expectedDirection.z, cue.direction().z, DELTA);
		assertNotEquals(VfxCue.NO_ANCHOR, cue.anchorEntityId());
	}
}
