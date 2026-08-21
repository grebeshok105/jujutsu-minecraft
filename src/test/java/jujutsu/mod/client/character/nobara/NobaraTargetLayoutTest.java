package jujutsu.mod.client.character.nobara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure geometry + animation contract for {@link NobaraTargetLayout} and {@link NobaraTargetAnim}.
 *
 * <p>Both classes must stay free of Minecraft imports, so everything they expose is testable as
 * plain math. The stack-offset, clamp, grade-mapping, hp-text and easing tests pin C4 exactly.
 */
final class NobaraTargetLayoutTest {

	private static final float EPS = 1e-4f;

	private static void assertCard(NobaraTargetLayout.Card card, float x, float y, float w, float h) {
		assertEquals(x, card.x(), EPS, "card x");
		assertEquals(y, card.y(), EPS, "card y");
		assertEquals(w, card.w(), EPS, "card w");
		assertEquals(h, card.h(), EPS, "card h");
	}

	// --- stack geometry ---

	@Test
	void healthCardAnchorsExactlyAtAttachPoint() {
		assertCard(NobaraTargetLayout.healthCard(100f, 200f, 1f), 100f, 200f,
				NobaraTargetLayout.CARD_W, NobaraTargetLayout.HEALTH_H);
	}

	@Test
	void gradeCardStacksBelowHealthByHealthPlusGap() {
		float y = 200f + (NobaraTargetLayout.HEALTH_H + NobaraTargetLayout.GAP);
		assertCard(NobaraTargetLayout.gradeCard(100f, 200f, 1f), 100f, y,
				NobaraTargetLayout.CARD_W, NobaraTargetLayout.SMALL_H);
	}

	@Test
	void nailsCardStacksBelowHealthAndGrade() {
		float y = 200f + (NobaraTargetLayout.HEALTH_H + NobaraTargetLayout.SMALL_H + 2 * NobaraTargetLayout.GAP);
		assertCard(NobaraTargetLayout.nailsCard(100f, 200f, 1f), 100f, y,
				NobaraTargetLayout.CARD_W, NobaraTargetLayout.SMALL_H);
	}

	@Test
	void scaleDoublesEverySizeAndStackOffset() {
		float y = 200f + 2f * (NobaraTargetLayout.HEALTH_H + NobaraTargetLayout.SMALL_H + 2 * NobaraTargetLayout.GAP);
		float gY = 200f + 2f * (NobaraTargetLayout.HEALTH_H + NobaraTargetLayout.GAP);
		assertCard(NobaraTargetLayout.healthCard(100f, 200f, 2f), 100f, 200f,
				2f * NobaraTargetLayout.CARD_W, 2f * NobaraTargetLayout.HEALTH_H);
		assertCard(NobaraTargetLayout.gradeCard(100f, 200f, 2f), 100f, gY,
				2f * NobaraTargetLayout.CARD_W, 2f * NobaraTargetLayout.SMALL_H);
		assertCard(NobaraTargetLayout.nailsCard(100f, 200f, 2f), 100f, y,
				2f * NobaraTargetLayout.CARD_W, 2f * NobaraTargetLayout.SMALL_H);
	}

	// --- attachScale ---

	@Test
	void attachScaleClampsNineOverDepth() {
		assertEquals(1.25f, NobaraTargetLayout.attachScale(2.0), EPS, "depth 2 -> 4.5 clamped to 1.25");
		assertEquals(0.75f, NobaraTargetLayout.attachScale(12.0), EPS, "depth 12 -> 0.75");
		assertEquals(1.0f, NobaraTargetLayout.attachScale(9.0), EPS, "depth 9 -> 1.0");
		assertEquals(1.25f, NobaraTargetLayout.attachScale(6.0), EPS, "depth 6 -> 1.5 clamped to 1.25");
		assertEquals(0.75f, NobaraTargetLayout.attachScale(18.0), EPS, "depth 18 -> 0.5 clamped to 0.75");
	}

	// --- gradeDisplay ---

	@Test
	void gradeDisplayMapsKnownRankKeys() {
		assertEquals("S", NobaraTargetLayout.gradeDisplay("esp.jujutsumod.rank.special_grade"));
		assertEquals("1", NobaraTargetLayout.gradeDisplay("esp.jujutsumod.rank.rank1"));
		assertEquals("2", NobaraTargetLayout.gradeDisplay("esp.jujutsumod.rank.rank2"));
		assertEquals("3", NobaraTargetLayout.gradeDisplay("esp.jujutsumod.rank.rank3"));
		assertEquals("-", NobaraTargetLayout.gradeDisplay("esp.jujutsumod.rank.civilian"));
	}

	@Test
	void gradeDisplayFallsBackToQuestionForUnknownKeys() {
		assertEquals("?", NobaraTargetLayout.gradeDisplay(null));
		assertEquals("?", NobaraTargetLayout.gradeDisplay("garbage"));
	}

	// --- hp texts ---

	@Test
	void hpPercentTextRoundsAndClampsToPercent() {
		assertEquals("40%", NobaraTargetLayout.hpPercentText(8f, 20f));
		assertEquals("0%", NobaraTargetLayout.hpPercentText(0f, 20f));
		assertEquals("100%", NobaraTargetLayout.hpPercentText(20f, 20f));
		assertEquals("100%", NobaraTargetLayout.hpPercentText(25f, 20f), "over-heal clamps to 100%");
	}

	@Test
	void hpRatioTextRoundsInLocaleRoot() {
		assertEquals("8/21", NobaraTargetLayout.hpRatioText(8.4f, 20.7f));
	}

	// --- animations ---

	@Test
	void appearAlphaEasesMonotonicallyFromZeroToOne() {
		assertEquals(0f, NobaraTargetAnim.appearAlpha(0, 0f), EPS);
		assertTrue(NobaraTargetAnim.appearAlpha(1, 0f) > NobaraTargetAnim.appearAlpha(0, 0f));
		assertTrue(NobaraTargetAnim.appearAlpha(2, 0f) > NobaraTargetAnim.appearAlpha(1, 0f));
		assertEquals(1f, NobaraTargetAnim.appearAlpha(3, 0f), EPS);
		assertEquals(1f, NobaraTargetAnim.appearAlpha(10, 0f), EPS, "settled appears stay fully opaque");
	}

	@Test
	void slideOffsetFadesFromFullSlideToZero() {
		assertEquals(NobaraTargetAnim.SLIDE_PX, NobaraTargetAnim.slideOffsetPx(0, 0f), EPS);
		assertTrue(NobaraTargetAnim.slideOffsetPx(1, 0f) < NobaraTargetAnim.slideOffsetPx(0, 0f));
		assertEquals(0f, NobaraTargetAnim.slideOffsetPx(3, 0f), EPS);
	}

	@Test
	void popScaleOvershootsThenSettlesToOne() {
		assertTrue(NobaraTargetAnim.popScale(1, 0.5f) > 1f, "the pop must overshoot in the middle of its window");
		assertEquals(1f, NobaraTargetAnim.popScale(3, 0f), EPS, "window end snaps to exactly 1");
		assertEquals(1f, NobaraTargetAnim.popScale(10, 0f), EPS, "long after the change stays at 1");
	}

	@Test
	void pulseAlphaDecaysFromOneToZero() {
		assertEquals(1f, NobaraTargetAnim.pulseAlpha(0, 0f), EPS);
		assertEquals(0f, NobaraTargetAnim.pulseAlpha(4, 0f), EPS);
		assertEquals(0f, NobaraTargetAnim.pulseAlpha(10, 0f), EPS, "pulse clamps, never goes negative");
	}

	@Test
	void approachValueChasesTargetWithoutOvershoot() {
		assertEquals(50f, NobaraTargetAnim.approachValue(50f, 100f, 0f), EPS, "a zero-delta frame changes nothing");
		float stepped = NobaraTargetAnim.approachValue(50f, 100f, 10f);
		assertTrue(stepped > 50f && stepped < 100f, "one step moves toward but never exceeds the target");
		float settled = NobaraTargetAnim.approachValue(50f, 100f, 1000f);
		assertEquals(100f, settled, 1e-2f, "enough ticks settle onto the target");
	}
}
