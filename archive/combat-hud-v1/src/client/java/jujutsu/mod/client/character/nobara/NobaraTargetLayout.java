package jujutsu.mod.client.character.nobara;

import java.util.Locale;

/**
 * Pure layout math for the Nobara target HUD block (bracket line + rows). No Minecraft imports.
 *
 * <p>The block is anchored at its top-left corner: {@code x} is the left edge of the vertical
 * bracket line, {@code y} its top. Every {@link Block} value is in already-scaled pixels; text
 * widths ({@code nameW}, {@code countW}, {@code rankW}) are unscaled font widths passed by the
 * renderer at scale 1 and multiplied by their own text scale and the HUD scale here, so geometry
 * and text always agree.
 */
public final class NobaraTargetLayout {
	private NobaraTargetLayout() {}

	// --- scale ---
	public static final float SCALE_MIN = 0.75f;
	public static final float SCALE_MAX = 1.25f;

	/** Distance-driven HUD scale: clamp(9/depth, 0.75, 1.25). */
	public static float attachScale(double depth) {
		return (float) Math.max(SCALE_MIN, Math.min(SCALE_MAX, 9.0 / depth));
	}

	// --- vertical rhythm (units at scale 1) ---
	public static final float NAME_H = 9f;
	public static final float ROW_GAP = 2f;
	public static final float DIVIDER_H = 1f;
	public static final float HP_H = 5f;
	public static final float RATIO_H = 7f;
	public static final float NAIL_H = 16f;
	public static final float RANK_H = 9f;
	/** Gap between row groups (ratio -> nails, nails -> rank). */
	public static final float GROUP_GAP = 3f;

	// --- horizontal rhythm ---
	/** Gap between the bracket line and the first content column. */
	public static final float BRACKET_GAP = 4f;
	/** Length of the bracket's top/bottom caps, extending right from the vertical line. */
	public static final float BRACKET_CAP = 3f;
	public static final int HP_SEGMENTS = 10;
	public static final float HP_SEGMENT_W = 3f;
	public static final float HP_SEGMENT_GAP = 1f;
	/** Gap between the nail icon and its count text. */
	public static final float COUNT_GAP = 3f;
	/** Rank cell square size (outline box next to the rank token). */
	public static final float RANK_CELL = 7f;

	/** Width of the segmented HP bar at scale 1, in pixels. */
	public static float hpBarWidth() {
		return HP_SEGMENTS * HP_SEGMENT_W + (HP_SEGMENTS - 1) * HP_SEGMENT_GAP;
	}

	/** Position + row anchors for one HUD block; all values in scaled pixels. */
	public record Block(float x, float y, float w, float h, float contentX,
			float nameY, float dividerY, float hpY, float hpW, float hpH,
			float ratioY, float nailY, float rankY) {}

	/**
	 * Resolves the full block geometry for one target.
	 *
	 * @param x       left edge of the bracket line (scaled pixels)
	 * @param y       top of the block (scaled pixels)
	 * @param scale   HUD scale from {@link #attachScale}
	 * @param nameW   unscaled width of the target name in the font used
	 * @param countW  unscaled width of the nail-count text
	 * @param rankW   unscaled width of the rank token text
	 * @return        the block with every row anchor in scaled pixels
	 */
	public static Block block(float x, float y, float scale, float nameW, float countW, float rankW) {
		float hpW = hpBarWidth() * scale;
		float hpH = HP_H * scale;
		float contentX = x + BRACKET_GAP * scale;

		float nameY = y;
		float dividerY = nameY + NAME_H * scale + ROW_GAP * scale;
		float hpY = dividerY + DIVIDER_H * scale + ROW_GAP * scale;
		float ratioY = hpY + hpH + ROW_GAP * scale;
		float nailY = ratioY + RATIO_H * scale + GROUP_GAP * scale;
		float rankY = nailY + NAIL_H * scale + GROUP_GAP * scale;
		float h = rankY + RANK_H * scale - y;

		float nameWx = nameW * scale;
		float countWx = countW * scale;
		float rankWx = rankW * scale;
		float nailRowW = NAIL_H * scale + COUNT_GAP * scale + countWx;
		float rankRowW = (RANK_CELL + ROW_GAP) * scale + rankWx;
		float contentW = Math.max(nameWx, Math.max(hpW, Math.max(nailRowW, rankRowW)));
		float w = contentW + BRACKET_GAP * scale;

		return new Block(x, y, w, h, contentX, nameY, dividerY, hpY, hpW, hpH, ratioY, nailY, rankY);
	}

	/** Integer ratio text in Locale.ROOT, e.g. {@code "8/21"}. */
	public static String hpRatioText(float hp, float maxHp) {
		return String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
	}

	/**
	 * Filled HP segments: clamp(hp / maxHealth) rounded onto {@link #HP_SEGMENTS}. Degenerate
	 * health (non-positive max or non-finite values) yields 0 so the caller simply draws no bar.
	 */
	public static int filledSegments(float hp, float maxHp) {
		if (!(maxHp > 0f) || !Float.isFinite(hp) || !Float.isFinite(maxHp)) {
			return 0;
		}
		float fraction = Math.max(0f, Math.min(1f, hp / maxHp));
		return Math.round(fraction * HP_SEGMENTS);
	}

	/**
	 * Maps an ESP rank key to the compact token shown in the rank row.
	 *
	 * <p>{@code "*special_grade*"} and keys containing {@code "special_grade"} yield {@code "S"};
	 * rank/grade 1..3 map to {@code "1".."3"}; {@code "*civilian*"} maps to {@code "-"}; null or any
	 * unknown key maps to {@code "?"}.
	 */
	public static String gradeDisplay(String rankKey) {
		if (rankKey == null) {
			return "?";
		}
		if (rankKey.contains("special_grade")) {
			return "S";
		}
		if (rankKey.contains("rank1") || rankKey.contains("grade_1")) {
			return "1";
		}
		if (rankKey.contains("rank2") || rankKey.contains("grade_2")) {
			return "2";
		}
		if (rankKey.contains("rank3") || rankKey.contains("grade_3")) {
			return "3";
		}
		if (rankKey.contains("civilian")) {
			return "-";
		}
		// Player targets: the key is a vessel roster subtitle (grade or technique), passed straight
		// through by rankKeyFor (C6). Map the known subtitle suffixes to the same compact tokens
		// so a sorcerer target never falls back to "?". Display-only, from existing roster data.
		if (rankKey.endsWith(".nobara.grade") || rankKey.contains(".character_select.nobara.")) {
			return "3";
		}
		if (rankKey.endsWith(".todo.technique") || rankKey.contains(".character_select.todo.")) {
			return "B";
		}
		if (rankKey.endsWith(".megumi.technique") || rankKey.contains(".character_select.megumi.")) {
			return "T";
		}
		return "?";
	}
}