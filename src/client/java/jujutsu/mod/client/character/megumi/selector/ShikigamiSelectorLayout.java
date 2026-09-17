package jujutsu.mod.client.character.megumi.selector;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.character.megumi.MegumiShikigami;

/**
 * Slot geometry for the quick-selector strip: a bottom-centred horizontal row above the hotbar.
 *
 * <p>Geometry only — no state, no rendering, no Minecraft. That is what makes the design spec's
 * spatial promise testable: slot positions are a function of the entry list's <em>size and index</em>
 * alone, so an unavailable shikigami can never compact the strip or shift its neighbours, and repeated
 * use builds the muscle memory the spec asks for.
 *
 * <p>The strip is laid out at {@link #SLOT_W}×{@link #SLOT_H} with {@link #GAP} between slots and
 * shrinks proportionally — width, height and gap together — when the row would cross {@link #SIDE_MARGIN}
 * of either screen edge, which is what keeps the spec's "readable up to about ten" true on small windows.
 * Its bottom edge always sits {@link #BOTTOM_CLEARANCE} pixels above the screen bottom, clearing the
 * 22-pixel hotbar band.
 */
public final class ShikigamiSelectorLayout {
	/** Comfortable slot width at full size; the near-maximum shikigami name fits at this width. */
	public static final int SLOT_W = 44;
	/** Comfortable slot height at full size: model well on top, name and state label below. */
	public static final int SLOT_H = 56;
	/** Space between neighbouring slots at full size. Must stay non-zero: the gaps are dead zones. */
	public static final int GAP = 6;
	/** The strip's bottom edge never crosses this line above the screen bottom, clearing the hotbar. */
	public static final int BOTTOM_CLEARANCE = 32;
	/** The strip never crosses this many pixels from either side edge before it starts shrinking. */
	public static final int SIDE_MARGIN = 8;
	/** Shrinking stops here; below this a slot would no longer read as a slot. */
	public static final int MIN_SLOT_W = 24;

	private ShikigamiSelectorLayout() {}

	/** One shikigami's fixed rectangle. Screen-space pixels, origin top-left, like every GUI rect. */
	public record Slot(int x, int y, int w, int h, MegumiShikigami type) {
		/** Whether this slot's rectangle contains the point. Right and bottom edges are exclusive. */
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	/**
	 * Lays the whole strip out. The caller passes the canonical entry list — in production
	 * {@code MegumiShikigami.values()} — and gets one slot per entry in the same order, so index
	 * {@code i} is always the same shikigami in the same place.
	 */
	public static List<Slot> layout(int guiW, int guiH, List<MegumiShikigami> entries) {
		int count = entries.size();
		if (count == 0) {
			return List.of();
		}
		int available = Math.max(MIN_SLOT_W * count, guiW - 2 * SIDE_MARGIN);
		int w = SLOT_W;
		int gap = GAP;
		while (count * w + (count - 1) * gap > available && w > MIN_SLOT_W) {
			w--;
			gap = Math.max(2, Math.round(GAP * (w / (float) SLOT_W)));
		}
		int h = Math.max(MIN_SLOT_W, Math.round(SLOT_H * (w / (float) SLOT_W)));
		int total = count * w + (count - 1) * gap;
		int x = (guiW - total) / 2;
		int y = guiH - BOTTOM_CLEARANCE - h;
		List<Slot> slots = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			slots.add(new Slot(x + i * (w + gap), y, w, h, entries.get(i)));
		}
		return List.copyOf(slots);
	}

	/** The slot under the cursor, or {@code null} when the cursor is off the strip (panel, gaps, world). */
	public static Slot slotAt(List<Slot> slots, double mouseX, double mouseY) {
		for (Slot slot : slots) {
			if (slot.contains(mouseX, mouseY)) {
				return slot;
			}
		}
		return null;
	}
}
