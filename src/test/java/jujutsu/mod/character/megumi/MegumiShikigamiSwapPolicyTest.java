package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MegumiShikigamiSwapPolicyTest {
	@Test
	void theSelectedShikigamiAlreadyOutIsRecalledByTheKey() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_SELF,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, Set.of(MegumiShikigami.NUE), false));
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_SELF,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.RABBITS,
						Set.of(MegumiShikigami.NUE, MegumiShikigami.RABBITS), true));
		assertEquals(MegumiShikigamiSwapPolicy.Action.RECALL_SELF,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.DOGS, Set.of(), true),
				"the dogs' own toggle executes this row");
	}

	@Test
	void anotherTypeBeingOutNeverTurnsTheKeyIntoARecall() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, Set.of(MegumiShikigami.TOAD), false),
				"issue #107 D1: the types coexist, so a live Toad is not a reason to withhold the Nue");
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.TOAD,
						Set.of(MegumiShikigami.NUE, MegumiShikigami.ELEPHANT), false));
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, Set.of(MegumiShikigami.TOAD), true),
				"an active dog pack no longer takes the field away from the shikigami");
	}

	@Test
	void nothingActiveMeansAPlainSummon() {
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.NUE, Set.of(), false));
		assertEquals(MegumiShikigamiSwapPolicy.Action.SUMMON,
				MegumiShikigamiSwapPolicy.decide(MegumiShikigami.DOGS, Set.of(), false));
	}
}
