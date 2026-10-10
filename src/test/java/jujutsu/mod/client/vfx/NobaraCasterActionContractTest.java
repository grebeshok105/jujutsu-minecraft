package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class NobaraCasterActionContractTest {
	@Test
	void casterActionDeliveryWindowCoversLateNetworkCueButHasAnExpiry() {
		VfxCue cue = new VfxCue(
				NobaraVfxIds.CASTER_ACTION, Vec3.ZERO, 17, Vec3.ZERO,
				NobaraVfxIds.CASTER_HAIRPIN_DIRECTED, 100L, 1L, Vec3.ZERO);
		int duration = NobaraVfxRecipes.CASTER_ACTION_DURATION_TICKS;

		assertTrue(duration >= 20, "caster action must tolerate ordinary packet delay");
		assertFalse(VfxTimeline.isExpired(cue, cue.startGameTime() + duration - 1, duration),
				"a cue one tick before expiry must still reach the caster animation hook");
		assertTrue(VfxTimeline.isExpired(cue, cue.startGameTime() + duration, duration),
				"a stale caster cue must be discarded at its expiry boundary");
	}

	@Test
	void casterActionIntensityTableIsThePinnedReworkMapping() {
		// The rework contract pins intensities 1..7 to authored clips; the recipe switch must
		// keep every one of them (a dropped arm silently no-ops the caster animation).
		assertEquals(1, NobaraVfxIds.CASTER_HAIRPIN_DIRECTED);
		assertEquals(2, NobaraVfxIds.CASTER_NAIL_PREPARE);
		assertEquals(3, NobaraVfxIds.CASTER_NAIL_TRAP);
		assertEquals(4, NobaraVfxIds.CASTER_HAMMER_EMBEDDED);
		assertEquals(5, NobaraVfxIds.CASTER_MEGA_NAIL);
		assertEquals(6, NobaraVfxIds.CASTER_REMNANT_EXTRACT);
		assertEquals(7, NobaraVfxIds.CASTER_RESONANCE_RITUAL);
	}
}
