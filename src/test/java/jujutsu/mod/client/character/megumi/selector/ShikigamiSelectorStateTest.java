package jujutsu.mod.client.character.megumi.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;
import org.junit.jupiter.api.Test;

/**
 * The strip's click contract and its visual precedence table, plus the multi-selection sequence the
 * spec allows inside one hold.
 */
class ShikigamiSelectorStateTest {
	private static final List<Path> LANG_FILES = List.of(
			Path.of("src/main/resources/assets/jujutsumod/lang/en_us.json"),
			Path.of("src/main/resources/assets/jujutsumod/lang/ru_ru.json"));

	@Test
	void availableStatesSelectAndEveryOtherStateRejects() {
		assertEquals(ShikigamiSelectorState.ClickResult.SELECT,
				ShikigamiSelectorState.resolveClick(MegumiShikigamiSlotState.SUMMONED),
				"an already-summoned shikigami stays selectable — summoned is a marker, not a block");
		for (MegumiShikigamiSlotState state : MegumiShikigamiSlotState.values()) {
			ShikigamiSelectorState.ClickResult result = ShikigamiSelectorState.resolveClick(state);
			assertNotNull(result, () -> "every state needs a verdict, including " + state);
			boolean selectable = state == MegumiShikigamiSlotState.READY
					|| state == MegumiShikigamiSlotState.SUMMONED;
			assertEquals(selectable ? ShikigamiSelectorState.ClickResult.SELECT
							: ShikigamiSelectorState.ClickResult.REJECT,
					result, () -> state + " disagrees with the spec's availability rule");
		}
	}

	@Test
	void noClickVerdictCanCloseTheStrip() {
		Set<String> verdicts = Arrays.stream(ShikigamiSelectorState.ClickResult.values())
				.map(Enum::name)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(new TreeSet<>(Set.of("SELECT", "REJECT", "NONE")), verdicts,
				"a click verdict that closes the strip would be a second close gesture; the spec allows one");
	}

	@Test
	void flagsNeverHideTheReasonAndNeverInventAvailability() {
		assertEquals(Set.of(ShikigamiSelectorState.AVAILABLE),
				ShikigamiSelectorState.flags(false, false, MegumiShikigamiSlotState.READY));
		assertEquals(Set.of(ShikigamiSelectorState.ACTIVE, ShikigamiSelectorState.AVAILABLE,
						ShikigamiSelectorState.SUMMONED),
				ShikigamiSelectorState.flags(true, false, MegumiShikigamiSlotState.SUMMONED),
				"active and summoned coexist: the state marker survives the active frame");
		assertEquals(Set.of(ShikigamiSelectorState.HOVERED, ShikigamiSelectorState.COOLDOWN),
				ShikigamiSelectorState.flags(false, true, MegumiShikigamiSlotState.COOLDOWN),
				"hovering an unavailable slot must not report it as available");
		assertEquals(Set.of(ShikigamiSelectorState.ACTIVE, ShikigamiSelectorState.HOVERED,
						ShikigamiSelectorState.LOCKED),
				ShikigamiSelectorState.flags(true, true, MegumiShikigamiSlotState.LOCKED),
				"all three at once still keep the blocking reason");
		assertEquals(Set.of(ShikigamiSelectorState.DESTROYED),
				ShikigamiSelectorState.flags(false, false, MegumiShikigamiSlotState.DESTROYED));
		assertEquals(Set.of(ShikigamiSelectorState.TEMPORARY),
				ShikigamiSelectorState.flags(false, false, MegumiShikigamiSlotState.TEMPORARY));

		int selectable = 0;
		for (MegumiShikigamiSlotState state : MegumiShikigamiSlotState.values()) {
			Set<String> flags = ShikigamiSelectorState.flags(false, false, state);
			boolean available = flags.contains(ShikigamiSelectorState.AVAILABLE);
			assertEquals(state == MegumiShikigamiSlotState.READY || state == MegumiShikigamiSlotState.SUMMONED,
					available, () -> state + " availability disagrees with the click verdict");
			assertFalse(flags.isEmpty(), "every state paints something, not a blank slot");
			selectable += available ? 1 : 0;
		}
		assertEquals(2, selectable, "exactly READY and SUMMONED are selectable");
	}

	@Test
	void everyNonReadyStateHasItsOwnLabelInBothLocales() {
		Set<String> keys = new TreeSet<>();
		for (MegumiShikigamiSlotState state : MegumiShikigamiSlotState.values()) {
			String key = ShikigamiSelectorState.stateLabelKey(state);
			if (state == MegumiShikigamiSlotState.READY) {
				assertNull(key, "the selectable-by-default state needs no excuse label");
				continue;
			}
			assertNotNull(key, () -> state + " needs a label naming why it looks the way it does");
			assertTrue(keys.add(key), () -> state + " shares a label key with another state");
			for (Path lang : LANG_FILES) {
				assertTrue(read(lang).contains("\"" + key + "\""),
						() -> lang.getFileName() + " is missing " + key);
			}
		}
	}

	@Test
	void everyShikigamiNameIsPinnedInBothLocales() {
		// The slot draws its name from the roster id, so a renamed id silently turns into a raw
		// translation key on screen. The nine literal keys are the contract; the derived set makes a
		// tenth shikigami without its two lang rows fail here instead of in game.
		Set<String> contracted = Set.of(
				"jujutsumod.megumi.shikigami.dogs",
				"jujutsumod.megumi.shikigami.nue",
				"jujutsumod.megumi.shikigami.toad",
				"jujutsumod.megumi.shikigami.rabbits",
				"jujutsumod.megumi.shikigami.elephant",
				"jujutsumod.megumi.shikigami.serpent",
				"jujutsumod.megumi.shikigami.deer",
				"jujutsumod.megumi.shikigami.ox",
				"jujutsumod.megumi.shikigami.tiger");
		for (Path lang : LANG_FILES) {
			String json = read(lang);
			for (String key : contracted) {
				assertTrue(json.contains("\"" + key + "\""), () -> lang.getFileName() + " is missing " + key);
			}
		}
		Set<String> derived = new TreeSet<>();
		for (MegumiShikigami type : MegumiShikigami.values()) {
			derived.add("jujutsumod.megumi.shikigami." + type.id());
		}
		assertEquals(new TreeSet<>(contracted), derived,
				"the roster and the shipped name keys must cover each other");
	}

	/**
	 * Several selections inside one hold. The sequence mirrors the screen's click path exactly:
	 * {@link ShikigamiSelectorLayout#slotAt} for the hit, then
	 * {@link ShikigamiSelectorState#resolveClick} for the verdict — the screen's {@code mouseClicked}
	 * contains no other decision — and each accepted click updates the selection the way the screen's
	 * optimistic mark-selected call does.
	 */
	@Test
	void rapidSelectionsLeaveTheLastOneActiveAndTheStripOpen() {
		List<ShikigamiSelectorLayout.Slot> slots =
				ShikigamiSelectorLayout.layout(960, 540, List.of(MegumiShikigami.values()));
		MegumiShikigami selection = MegumiShikigami.DOGS;
		for (MegumiShikigami clicked : List.of(MegumiShikigami.NUE, MegumiShikigami.RABBITS,
				MegumiShikigami.ELEPHANT)) {
			ShikigamiSelectorLayout.Slot expected = slotOf(slots, clicked);
			ShikigamiSelectorLayout.Slot hit = ShikigamiSelectorLayout.slotAt(slots,
					expected.x() + expected.w() / 2.0, expected.y() + expected.h() / 2.0);
			assertNotNull(hit, "the click landed on a slot");
			assertEquals(clicked, hit.type(), "the hit is the shikigami under the cursor");
			assertEquals(ShikigamiSelectorState.ClickResult.SELECT,
					ShikigamiSelectorState.resolveClick(MegumiShikigamiSlotState.READY),
					() -> "clicking " + clicked + " mid-hold must select, not close");
			selection = hit.type();
		}
		assertEquals(MegumiShikigami.ELEPHANT, selection, "the last successful click stays active");
	}

	@Test
	void aRefusedClickLeavesTheSelectionAndTheStripAlone() {
		List<ShikigamiSelectorLayout.Slot> slots =
				ShikigamiSelectorLayout.layout(960, 540, List.of(MegumiShikigami.values()));
		ShikigamiSelectorLayout.Slot toad = slotOf(slots, MegumiShikigami.TOAD);
		ShikigamiSelectorLayout.Slot hit = ShikigamiSelectorLayout.slotAt(slots,
				toad.x() + toad.w() / 2.0, toad.y() + toad.h() / 2.0);
		assertNotNull(hit);
		MegumiShikigami selection = MegumiShikigami.DOGS;
		ShikigamiSelectorState.ClickResult verdict =
				ShikigamiSelectorState.resolveClick(MegumiShikigamiSlotState.COOLDOWN);
		assertEquals(ShikigamiSelectorState.ClickResult.REJECT, verdict);
		if (verdict == ShikigamiSelectorState.ClickResult.SELECT) {
			selection = hit.type();
		}
		assertEquals(MegumiShikigami.DOGS, selection, "a refused click leaves the active shikigami alone");
	}

	@Test
	void aClickOffTheStripHitsNothing() {
		List<ShikigamiSelectorLayout.Slot> slots =
				ShikigamiSelectorLayout.layout(960, 540, List.of(MegumiShikigami.values()));
		ShikigamiSelectorLayout.Slot first = slots.get(0);
		assertNull(ShikigamiSelectorLayout.slotAt(slots, first.x() - 4, first.y() - 4),
				"a click outside the strip has no slot to resolve");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException failure) {
			throw new AssertionError("cannot read " + path, failure);
		}
	}

	private static ShikigamiSelectorLayout.Slot slotOf(List<ShikigamiSelectorLayout.Slot> slots,
			MegumiShikigami type) {
		for (ShikigamiSelectorLayout.Slot slot : slots) {
			if (slot.type() == type) {
				return slot;
			}
		}
		throw new AssertionError("no slot for " + type);
	}
}
