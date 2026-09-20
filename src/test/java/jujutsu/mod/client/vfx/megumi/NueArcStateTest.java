package jujutsu.mod.client.vfx.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class NueArcStateTest {
	@Test
	void shockArcExpiresAtEightTicks() {
		NueArcState state = new NueArcState();
		state.registerCue(cue(20L));

		assertEquals(1, state.activeAt(20L).size());
		assertEquals(1, state.activeAt(27L).size());
		assertTrue(state.activeAt(28L).isEmpty(), "ttl is eight ticks, with expiry at start+8");
	}

	@Test
	void targetEndpointStaysFixedWhileLiveNueEndpointMayMove() {
		NueArcState state = new NueArcState();
		Vec3 target = new Vec3(12.5, 65.0, -4.0);
		VfxCue cue = new VfxCue(MegumiVfxIds.NUE_SHOCK, target, 7,
				new Vec3(1.0, 0.5, -2.0), 1, 100L, 44L, new Vec3(1.0, 0.0, 0.0));
		state.registerCue(cue);

		List<NueArcState.Arc> before = state.activeAt(103L);
		List<NueArcState.Arc> after = state.activeAt(105L);
		assertFalse(before.isEmpty());
		assertEquals(target, before.get(0).targetPoint());
		assertEquals(target, after.get(0).targetPoint());
		assertEquals(7, after.get(0).nueEntityId(), "only the live anchor id is retained for per-frame lookup");
	}

	private static VfxCue cue(long startGameTime) {
		return new VfxCue(MegumiVfxIds.NUE_SHOCK, new Vec3(1.0, 2.0, 3.0), 5,
				Vec3.ZERO, 1, startGameTime, 12L, Vec3.ZERO);
	}
}
