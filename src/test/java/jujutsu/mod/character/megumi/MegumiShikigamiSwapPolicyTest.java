package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MegumiShikigamiSwapPolicyTest {
	@Test
	void theDogSelectionKeepsTheDogRuntimeInCharge() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.DELEGATE_DOGS,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.DOGS, null, false));
		assertEquals(MegumiShikigamiSwapPolicy.Action.DELEGATE_DOGS,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.DOGS, MegumiShikigami.NUE, false));
		assertEquals(MegumiShikigamiSwapPolicy.Action.DELEGATE_DOGS,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.DOGS, null, true));
	}

	@Test
	void pressingTheKeyOnTheActiveShikigamiRecallsIt() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_SELF,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, MegumiShikigami.NUE, false));
	}

	@Test
	void anotherActiveShikigamiIsRecalledForFreeBeforeTheSummon() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_OTHER_THEN_SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, MegumiShikigami.TOAD, false));
	}

	@Test
	void activeDogsAreAlsoSwappedAwayBeforeTheSummon() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_OTHER_THEN_SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, null, true));
	}

	@Test
	void nothingActiveMeansAPlainSummon() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, null, false));
	}
}
