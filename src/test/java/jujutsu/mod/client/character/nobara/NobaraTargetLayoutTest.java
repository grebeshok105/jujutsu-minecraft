package jujutsu.mod.client.character.nobara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure geometry contract for {@link NobaraTargetLayout} and {@link NobaraTargetAnim}.
 *
 * <p>Both classes must stay free of Minecraft imports, so everything they expose is testable as
 * plain math. The block geometry, HP-segment math, grade-mapping, ratio text and easing tests pin
 * the reference layout exactly.
 */
final class NobaraTargetLayoutTest {

	private static final float EPS = 1e-4f;

	// --- block geometry ---

	@Test
	void blockAnchorsRowsBelowEachOther() {
		NobaraTargetLayout.Block b = NobaraTargetLayout.block(100f, 200f, 1f, 40f, 12f, 8f);
		assertEquals(100f, b.x(), EPS, "block x is the bracket edge");
		assertEquals(200f, b.y(), EPS, "block y");
		float nameY = b.nameY();
		assertEquals(200f, nameY, EPS);
		assertEquals(nameY + NobaraTargetLayout.NAME_H + NobaraTargetLayout.ROW_GAP, b.dividerY(), EPS);
		assertEquals(b.dividerY() + NobaraTargetLayout.DIVIDER_H + NobaraTargetLayout.ROW_GAP, b.hpY(), EPS);
		assertEquals(b.hpY() + NobaraTargetLayout.HP_H + NobaraTargetLayout.ROW_GAP, b.ratioY(), EPS);
		assertEquals(b.ratioY() + NobaraTargetLayout.RATIO_H + NobaraTargetLayout.GROUP_GAP, b.nailY(), EPS);
		assertEquals(b.nailY() + NobaraTargetLayout.NAIL_H + NobaraTargetLayout.GROUP_GAP, b.rankY(), EPS);
		assertEquals(b.rankY() + NobaraTargetLayout.RANK_H - b.y(), b.h(), EPS, "block height covers every row");
	}

	@Test
	void blockWidthCoversTheWidestRowAndBracketGap() {
		NobaraTargetLayout.Block b = NobaraTargetLayout.block(100f, 200f, 1f, 10f, 8f, 5f);
		float expectedW = NobaraTargetLayout.BRACKET_GAP + Math.max(10f, Math.max(
				NobaraTargetLayout.hpBarWidth(),
				Math.max(NobaraTargetLayout.NAIL_H + NobaraTargetLayout.COUNT_GAP + 8f,
						NobaraTargetLayout.RANK_CELL + NobaraTargetLayout.ROW_GAP + 5f)));
		assertEquals(expectedW, b.w(), EPS, "block width = widest row + bracket gap");
		assertEquals(100f + NobaraTargetLayout.BRACKET_GAP, b.contentX(), EPS);
	}

	@Test
	void scaleMultipliesEveryRowAndWidth() {
		NobaraTargetLayout.Block b1 = NobaraTargetLayout.block(100f, 200f, 1f, 40f, 12f, 8f);
		NobaraTargetLayout.Block b2 = NobaraTargetLayout.block(100f, 200f, 2f, 40f, 12f, 8f);
		assertEquals(200f, b2.nameY(), EPS, "the anchor top never scales");
		assertEquals(200f + 2f * (b1.dividerY() - 200f), b2.dividerY(), EPS, "row offsets below the anchor double");
		assertEquals(2f * NobaraTargetLayout.hpBarWidth(), b2.hpW(), EPS);
		assertEquals(b1.hpW() * 2f, b2.hpW(), EPS);
		assertEquals(b1.h() * 2f, b2.h(), EPS);
	}

	@Test
	void hpBarWidthMatchesSegmentArithmetic() {
		float expected = NobaraTargetLayout.HP_SEGMENTS * NobaraTargetLayout.HP_SEGMENT_W
				+ (NobaraTargetLayout.HP_SEGMENTS - 1) * NobaraTargetLayout.HP_SEGMENT_GAP;
		assertEquals(expected, NobaraTargetLayout.hpBarWidth(), EPS);
	}

	// --- filled segments ---

	@Test
	void filledSegmentsFollowClampedHealthRatio() {
		assertEquals(NobaraTargetLayout.HP_SEGMENTS, NobaraTargetLayout.filledSegments(100f, 100f));
		assertEquals(0, NobaraTargetLayout.filledSegments(0f, 100f));
		assertEquals(NobaraTargetLayout.HP_SEGMENTS, NobaraTargetLayout.filledSegments(150f, 100f),
				"over-heal clamps to full bar");
		assertEquals(0, NobaraTargetLayout.filledSegments(-5f, 100f), "negative health clamps to empty");
	}

	@Test
	void filledSegmentsHandleDegenerateHealth() {
		assertEquals(0, NobaraTargetLayout.filledSegments(50f, 0f), "max health 0 draws nothing");
		assertEquals(0, NobaraTargetLayout.filledSegments(50f, Float.NaN));
		assertEquals(0, NobaraTargetLayout.filledSegments(Float.NaN, 50f));
		assertEquals(0, NobaraTargetLayout.filledSegments(Float.POSITIVE_INFINITY, 50f));
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

	@Test
	void gradeDisplayMapsVesselSubtitleKeys() {
		assertEquals("3", NobaraTargetLayout.gradeDisplay("screen.jujutsumod.character_select.nobara.grade"));
		assertEquals("B", NobaraTargetLayout.gradeDisplay("screen.jujutsumod.character_select.todo.technique"));
		assertEquals("T", NobaraTargetLayout.gradeDisplay("screen.jujutsumod.character_select.megumi.technique"));
	}

	// --- hp texts ---

	@Test
	void hpRatioTextRoundsInLocaleRoot() {
		assertEquals("8/21", NobaraTargetLayout.hpRatioText(8.4f, 20.7f));
		assertEquals("0/100", NobaraTargetLayout.hpRatioText(0f, 100f));
		assertEquals("100/100", NobaraTargetLayout.hpRatioText(100f, 100f));
	}

	// --- animations ---

	@Test
	void appearAlphaEasesMonotonicallyFromZeroToOne() {
		assertEquals(0f, NobaraTargetAnim.appearAlpha(0, 0f), EPS);
		assertTrue(NobaraTargetAnim.appearAlpha(1, 0f) > NobaraTargetAnim.appearAlpha(0, 0f));
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
	void approachValueChasesTargetWithoutOvershoot() {
		assertEquals(50f, NobaraTargetAnim.approachValue(50f, 100f, 0f), EPS, "a zero-delta frame changes nothing");
		float stepped = NobaraTargetAnim.approachValue(50f, 100f, 10f);
		assertTrue(stepped > 50f && stepped < 100f, "one step moves toward but never exceeds the target");
		float settled = NobaraTargetAnim.approachValue(50f, 100f, 1000f);
		assertEquals(100f, settled, 1e-2f, "enough ticks settle onto the target");
	}
}