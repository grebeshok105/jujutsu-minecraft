package jujutsu.mod.client.character.megumi.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Timing and palette contract for the shikigami quick selector (issue #109, spec sections "Motion"
 * and "Visual language").
 *
 * <p>The theme is what keeps the selector feeling like a fast physical control rather than a menu:
 * the spec caps the entrance/exit presentation around 100-150 ms so animation can never delay input
 * readiness, and it demands a dark-red / caramel-warm accent that never turns neon. It also demands
 * that current / hovered / summoned / unavailable stay distinguishable when states overlap, which
 * means the state markers must not reuse the accent hue. Those are the four things asserted here --
 * the exact RGB values stay free for visual tuning inside those bands.
 */
final class SelectorThemeTest {
	/** Spec: "the main entrance/exit presentation stays around 100-150 ms maximum". */
	private static final float MOTION_BUDGET_MS = 150f;
	/** "Dark rather than neon": the brightest channel of any accent stays well below an alert red. */
	private static final int NEON_VALUE_CAP = 0xD0;
	private static final int OPAQUE_ALPHA = 0xFF000000;

	@Test
	void everyPresentationDurationFitsTheFastBudget() {
		assertWithinBudget("OPEN_MS", SelectorTheme.OPEN_MS);
		assertWithinBudget("CLOSE_MS", SelectorTheme.CLOSE_MS);
		assertWithinBudget("HOVER_MS", SelectorTheme.HOVER_MS);
		assertWithinBudget("SELECT_MS", SelectorTheme.SELECT_MS);
	}

	@Test
	void theFourFeedbackRolesUseDistinctDurations() {
		HashSet<Float> durations = new HashSet<>(List.of(
				SelectorTheme.OPEN_MS, SelectorTheme.CLOSE_MS, SelectorTheme.HOVER_MS, SelectorTheme.SELECT_MS));
		assertEquals(4, durations.size(),
				"open/close/hover/select must be distinguishable by duration, got " + durations);
	}

	@Test
	void accentPaletteStaysDarkRedAndCaramelWarm() {
		assertDarkRedAccent("ACCENT_DEEP", SelectorTheme.ACCENT_DEEP);
		assertDarkRedAccent("ACCENT_GLOW", SelectorTheme.ACCENT_GLOW);
		assertDarkRedAccent("ACCENT_HOVER", SelectorTheme.ACCENT_HOVER);
	}

	@Test
	void stateMarkersAreDistinctAndNeverWearTheAccentHue() {
		Map<String, Integer> markers = new LinkedHashMap<>();
		markers.put("STATE_SUMMONED", SelectorTheme.STATE_SUMMONED);
		markers.put("STATE_COOLDOWN", SelectorTheme.STATE_COOLDOWN);
		markers.put("STATE_LOCKED", SelectorTheme.STATE_LOCKED);
		markers.put("STATE_DESTROYED", SelectorTheme.STATE_DESTROYED);
		assertEquals(markers.size(), new HashSet<>(markers.values()).size(),
				"state markers must be visually distinct, got " + markers);

		for (Map.Entry<String, Integer> marker : markers.entrySet()) {
			int argb = marker.getValue();
			String name = marker.getKey();
			assertEquals(OPAQUE_ALPHA, argb & 0xFF000000, name + " must be fully opaque");
			assertTrue(!isRedDominant(argb),
					name + " reuses the red accent band, so overlapping states blur together: " + hex(argb));
		}
	}

	@Test
	void unavailableMarkersReadAsDimmedWhileSummonedCarriesItsOwnHue() {
		for (Map.Entry<String, Integer> marker : Map.of(
				"STATE_COOLDOWN", SelectorTheme.STATE_COOLDOWN,
				"STATE_LOCKED", SelectorTheme.STATE_LOCKED,
				"STATE_DESTROYED", SelectorTheme.STATE_DESTROYED).entrySet()) {
			int argb = marker.getValue();
			assertTrue(spread(argb) <= 0x20,
					marker.getKey() + " must read as desaturated/unavailable, not as a competing colour: "
							+ hex(argb));
		}
		assertTrue(green(SelectorTheme.STATE_SUMMONED) > red(SelectorTheme.STATE_SUMMONED),
				"already-summoned is a presence marker with its own hue, never a red alarm: "
						+ hex(SelectorTheme.STATE_SUMMONED));
	}

	private static void assertWithinBudget(String name, float milliseconds) {
		assertTrue(milliseconds > 0f, name + " must be a positive duration, was " + milliseconds);
		assertTrue(milliseconds <= MOTION_BUDGET_MS,
				name + " is " + milliseconds + " ms, outside the " + MOTION_BUDGET_MS + " ms presentation budget");
	}

	private static void assertDarkRedAccent(String name, int argb) {
		assertEquals(OPAQUE_ALPHA, argb & 0xFF000000, name + " must be fully opaque");
		assertTrue(isRedDominant(argb),
				name + " must sit in the warm red band (red clearly dominant), was " + hex(argb));
		assertTrue(red(argb) <= NEON_VALUE_CAP, name + " is brighter than a dark accent may be: " + hex(argb));
		assertTrue(blue(argb) < red(argb) / 2,
				name + " lost its warm drop-off, blue competes with red in " + hex(argb));
	}

	/** True when red is the strongest channel and blue does not lead green — the warm accent band. */
	private static boolean isRedDominant(int argb) {
		return red(argb) > green(argb) && red(argb) > blue(argb) && green(argb) >= blue(argb);
	}

	private static int spread(int argb) {
		int high = Math.max(red(argb), Math.max(green(argb), blue(argb)));
		int low = Math.min(red(argb), Math.min(green(argb), blue(argb)));
		return high - low;
	}

	private static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	private static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	private static int blue(int argb) {
		return argb & 0xFF;
	}

	private static String hex(int argb) {
		return String.format("#%08X", argb);
	}
}
