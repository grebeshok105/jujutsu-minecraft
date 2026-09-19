package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import jujutsu.mod.client.character.megumi.MegumiWingsState;
import org.junit.jupiter.api.Test;
import jujutsu.mod.network.MegumiWingsStatePayload;

final class MegumiWingsPhaseTest {
	@Test
	void everyWirePhaseMapsToTheExpectedClip() {
		assertEquals("animation.megumi_nue_wings.materialize",
				MegumiWingsState.clipFor(MegumiWingsStatePayload.MATERIALIZING));
		assertEquals("animation.megumi_nue_wings.folded_idle",
				MegumiWingsState.clipFor(MegumiWingsStatePayload.GROUND_FOLDED));
		assertEquals("animation.megumi_nue_wings.fly",
				MegumiWingsState.clipFor(MegumiWingsStatePayload.FLYING));
		assertEquals("animation.megumi_nue_wings.fold",
				MegumiWingsState.clipFor(MegumiWingsStatePayload.FOLDING));
	}

	@Test
	void unknownPhaseIsSkippedAndNoOwnerRendersWithoutState() {
		assertNull(MegumiWingsState.Phase.fromWire(99));
		assertNull(MegumiWingsState.clipFor(99));
		assertFalse(MegumiWingsState.active(null));
	}
}
