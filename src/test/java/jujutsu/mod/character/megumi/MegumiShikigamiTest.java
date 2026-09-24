package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MegumiShikigamiTest {
	@Test
	void cycleOrderWalksTheRosterAndWraps() {
		assertEquals(MegumiShikigami.NUE, MegumiShikigami.DOGS.next());
		assertEquals(MegumiShikigami.TOAD, MegumiShikigami.NUE.next());
		assertEquals(MegumiShikigami.RABBITS, MegumiShikigami.TOAD.next());
		assertEquals(MegumiShikigami.ELEPHANT, MegumiShikigami.RABBITS.next());
		assertEquals(MegumiShikigami.SERPENT, MegumiShikigami.ELEPHANT.next());
		assertEquals(MegumiShikigami.DEER, MegumiShikigami.SERPENT.next());
		assertEquals(MegumiShikigami.OX, MegumiShikigami.DEER.next());
		assertEquals(MegumiShikigami.TIGER, MegumiShikigami.OX.next());
		assertEquals(MegumiShikigami.DOGS, MegumiShikigami.TIGER.next());
	}

	@Test
	void rosterIdsAreStableAndRoundTrip() {
		Set<String> ids = new HashSet<>();
		for (MegumiShikigami type : MegumiShikigami.values()) {
			assertTrue(ids.add(type.id()), "duplicate roster id: " + type.id());
			assertEquals(type, MegumiShikigami.byId(type.id()));
		}
		assertEquals(
				Set.of("dogs", "nue", "toad", "rabbits", "elephant", "serpent", "deer", "ox", "tiger"),
				ids);
	}

	@Test
	void unknownIdFailsInsteadOfFallingBackToTheDogs() {
		assertThrows(IllegalArgumentException.class, () -> MegumiShikigami.byId("wolf"));
	}
}
