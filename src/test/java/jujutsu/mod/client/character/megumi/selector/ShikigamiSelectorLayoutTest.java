package jujutsu.mod.client.character.megumi.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.character.megumi.MegumiShikigami;
import org.junit.jupiter.api.Test;

/**
 * The strip's geometry, checked against the design spec's layout promises: bottom-centred above the
 * hotbar, stable per-index positions, a dead zone in every gap, and readable up to ten entries.
 */
class ShikigamiSelectorLayoutTest {
	private static final int WIDE_W = 960;
	private static final int WIDE_H = 540;
	/** A small window at GUI scale 4; the strip has to survive here by shrinking. */
	private static final int NARROW_W = 320;
	private static final int NARROW_H = 240;

	private static List<MegumiShikigami> entries(int size) {
		MegumiShikigami[] values = MegumiShikigami.values();
		List<MegumiShikigami> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(values[i % values.length]);
		}
		return list;
	}

	@Test
	void emptyEntryListLaysOutNothing() {
		assertTrue(ShikigamiSelectorLayout.layout(WIDE_W, WIDE_H, List.of()).isEmpty());
	}

	@Test
	void stripIsCentredAboveTheHotbarAtEverySize() {
		for (int size : new int[] {1, 5, 7, 8, 10}) {
			List<ShikigamiSelectorLayout.Slot> slots =
					ShikigamiSelectorLayout.layout(WIDE_W, WIDE_H, entries(size));
			assertEquals(size, slots.size(), "one slot per entry");

			ShikigamiSelectorLayout.Slot first = slots.get(0);
			ShikigamiSelectorLayout.Slot last = slots.get(size - 1);
			int total = last.x() + last.w() - first.x();
			assertEquals((WIDE_W - total) / 2, first.x(), "the strip is centred on the screen");
			assertEquals(WIDE_W / 2.0, (first.x() + last.x() + last.w()) / 2.0, 0.5,
					"the strip's midpoint is the screen's midpoint");

			for (ShikigamiSelectorLayout.Slot slot : slots) {
				assertEquals(WIDE_H - ShikigamiSelectorLayout.BOTTOM_CLEARANCE, slot.y() + slot.h(),
						"the strip's bottom edge sits on the clearance line");
				assertTrue(slot.y() + slot.h() <= WIDE_H - 49,
						"the strip clears the status rows too: hearts/hunger occupy guiH-39..-30, armor above");
			}
			assertEquals(ShikigamiSelectorLayout.SLOT_W, first.w(), "a wide window uses the full slot width");
			assertEquals(ShikigamiSelectorLayout.SLOT_H, first.h(), "a wide window uses the full slot height");
		}
	}

	@Test
	void smallWindowsShrinkTheStripInsteadOfOverflowingIt() {
		for (int size : new int[] {5, 7, 8, 10}) {
			List<ShikigamiSelectorLayout.Slot> slots =
					ShikigamiSelectorLayout.layout(NARROW_W, NARROW_H, entries(size));
			ShikigamiSelectorLayout.Slot first = slots.get(0);
			ShikigamiSelectorLayout.Slot last = slots.get(size - 1);
			assertTrue(first.x() >= ShikigamiSelectorLayout.SIDE_MARGIN,
					() -> "size " + size + " left the strip past the side margin: first.x=" + first.x());
			assertTrue(last.x() + last.w() <= NARROW_W - ShikigamiSelectorLayout.SIDE_MARGIN,
					() -> "size " + size + " right edge overflows: " + (last.x() + last.w()));
			assertTrue(first.w() >= ShikigamiSelectorLayout.MIN_SLOT_W, "slots never shrink past readability");
			assertEquals(NARROW_H - ShikigamiSelectorLayout.BOTTOM_CLEARANCE, first.y() + first.h(),
					"shrinking keeps the bottom edge on the clearance line");
		}
		List<ShikigamiSelectorLayout.Slot> ten = ShikigamiSelectorLayout.layout(NARROW_W, NARROW_H, entries(10));
		assertTrue(ten.get(0).w() < ShikigamiSelectorLayout.SLOT_W,
				"ten entries on a small window must actually shrink, not just barely fit by luck");
	}

	@Test
	void slotGeometryFollowsTheIndexAndNeverTheShikigamiInIt() {
		List<ShikigamiSelectorLayout.Slot> canonical =
				ShikigamiSelectorLayout.layout(WIDE_W, WIDE_H, List.of(MegumiShikigami.values()));
		List<MegumiShikigami> swapped = new ArrayList<>(entries(MegumiShikigami.values().length));
		java.util.Collections.reverse(swapped);
		List<ShikigamiSelectorLayout.Slot> other = ShikigamiSelectorLayout.layout(WIDE_W, WIDE_H, swapped);
		assertEquals(canonical.size(), other.size());
		for (int i = 0; i < canonical.size(); i++) {
			assertEquals(canonical.get(i).x(), other.get(i).x(), "x depends on the index alone");
			assertEquals(canonical.get(i).y(), other.get(i).y(), "y depends on the index alone");
			assertEquals(canonical.get(i).w(), other.get(i).w());
			assertEquals(canonical.get(i).h(), other.get(i).h());
		}
		assertEquals(MegumiShikigami.values()[0], canonical.get(0).type(), "canonical order comes from values()");
		assertEquals(MegumiShikigami.values()[4], canonical.get(4).type());
		for (int i = 1; i < canonical.size(); i++) {
			assertTrue(canonical.get(i).x() > canonical.get(i - 1).x(), "slots run left to right");
		}
	}

	@Test
	void hitTestResolvesCentresCornersAndLeavesGapsAlone() {
		List<ShikigamiSelectorLayout.Slot> slots =
				ShikigamiSelectorLayout.layout(WIDE_W, WIDE_H, List.of(MegumiShikigami.values()));
		for (ShikigamiSelectorLayout.Slot slot : slots) {
			assertSame(slot, ShikigamiSelectorLayout.slotAt(slots, slot.x() + slot.w() / 2.0, slot.y() + slot.h() / 2.0),
					"the centre of a slot belongs to that slot");
			assertSame(slot, ShikigamiSelectorLayout.slotAt(slots, slot.x(), slot.y()), "top-left corner");
			assertSame(slot, ShikigamiSelectorLayout.slotAt(slots, slot.x() + slot.w() - 1, slot.y() + slot.h() - 1),
					"bottom-right pixel inside the rect");
		}
		ShikigamiSelectorLayout.Slot first = slots.get(0);
		ShikigamiSelectorLayout.Slot second = slots.get(1);
		assertEquals(ShikigamiSelectorLayout.GAP, second.x() - first.x() - first.w(), "gaps are real");
		int gapX = first.x() + first.w() + ShikigamiSelectorLayout.GAP / 2;
		assertNull(ShikigamiSelectorLayout.slotAt(slots, gapX, first.y() + first.h() / 2.0),
				"the gap between two slots is not a target");
		assertNull(ShikigamiSelectorLayout.slotAt(slots, first.x() - 1, first.y()), "left of the strip");
		assertNull(ShikigamiSelectorLayout.slotAt(slots, second.x() + second.w(), second.y()), "right edge is exclusive");
		assertNull(ShikigamiSelectorLayout.slotAt(slots, first.x() + 1, first.y() - 1), "above the strip");
		assertNull(ShikigamiSelectorLayout.slotAt(slots, first.x() + 1, first.y() + first.h()),
				"below the strip — right where the hotbar sits");
		assertNotNull(ShikigamiSelectorLayout.slotAt(slots, first.x() + 1, first.y() + first.h() - 1));
	}
}
