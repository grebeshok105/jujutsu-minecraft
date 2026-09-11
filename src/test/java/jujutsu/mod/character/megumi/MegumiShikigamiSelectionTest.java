package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MegumiShikigamiSelectionTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	@AfterEach
	void clear() {
		MegumiShikigamiSelection.clearAll();
	}

	@Test
	void anUnknownPlayerCommandsTheDogs() {
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.selected(ALICE));
	}

	@Test
	void cyclingWalksTheWholeRosterAndReturnsToTheDogs() {
		assertEquals(MegumiShikigami.NUE, MegumiShikigamiSelection.cycle(ALICE));
		assertEquals(MegumiShikigami.TOAD, MegumiShikigamiSelection.cycle(ALICE));
		assertEquals(MegumiShikigami.RABBITS, MegumiShikigamiSelection.cycle(ALICE));
		assertEquals(MegumiShikigami.ELEPHANT, MegumiShikigamiSelection.cycle(ALICE));
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.cycle(ALICE));
	}

	@Test
	void selectionsArePerPlayer() {
		MegumiShikigamiSelection.cycle(ALICE);
		assertEquals(MegumiShikigami.NUE, MegumiShikigamiSelection.selected(ALICE));
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.selected(BOB));
	}

	@Test
	void settingNullFallsBackToTheDogs() {
		MegumiShikigamiSelection.set(ALICE, MegumiShikigami.ELEPHANT);
		MegumiShikigamiSelection.set(ALICE, null);
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.selected(ALICE));
	}

	@Test
	void clearingAllResetsEveryPlayer() {
		MegumiShikigamiSelection.set(ALICE, MegumiShikigami.TOAD);
		MegumiShikigamiSelection.set(BOB, MegumiShikigami.RABBITS);
		MegumiShikigamiSelection.clearAll();
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.selected(ALICE));
		assertEquals(MegumiShikigami.DOGS, MegumiShikigamiSelection.selected(BOB));
	}
}
