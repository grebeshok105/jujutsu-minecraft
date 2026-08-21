package jujutsu.mod.client.character.nobara;

import java.util.Locale;

/**
 * Pure layout math for the Nobara target HUD cards. No Minecraft imports.
 *
 * <p>All cards are {@link #CARD_W} wide and stack vertically below an attach point (left-top of
 * the health card). {@code scale} multiplies both card sizes and the vertical gaps between them.
 */
public final class NobaraTargetLayout {
	private NobaraTargetLayout() {}

	/** Position + size of one HUD card in screen pixels. */
	public record Card(float x, float y, float w, float h) {}

	public static final float CARD_W = 56f;
	public static final float HEALTH_H = 64f;
	public static final float SMALL_H = 44f;
	public static final float GAP = 6f;

	/** Anchors at the attach point exactly; the other cards stack below it. */
	public static Card healthCard(float attachX, float attachY, float scale) {
		return new Card(attachX, attachY, CARD_W * scale, HEALTH_H * scale);
	}

	/** Sits below the health card by (HEALTH_H + GAP) * scale. */
	public static Card gradeCard(float attachX, float attachY, float scale) {
		float y = attachY + (HEALTH_H + GAP) * scale;
		return new Card(attachX, y, CARD_W * scale, SMALL_H * scale);
	}

	/** Sits below the health card plus the grade card, i.e. (HEALTH_H + SMALL_H + 2*GAP) * scale. */
	public static Card nailsCard(float attachX, float attachY, float scale) {
		float y = attachY + (HEALTH_H + SMALL_H + 2 * GAP) * scale;
		return new Card(attachX, y, CARD_W * scale, SMALL_H * scale);
	}

	/** Distance-driven card scale: clamp(9/depth, 0.75, 1.25). */
	public static float attachScale(double depth) {
		return (float) Math.max(0.75, Math.min(1.25, 9.0 / depth));
	}

	/**
	 * Maps an ESP rank key to the large glyph shown on the grade card.
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
		// through by rankKeyFor (C6). Map the known subtitle suffixes to the same compact card glyphs
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

	/** Percentage text: round(hp/maxHp*100) clamped to 0..100, e.g. {@code "40%"}. */
	public static String hpPercentText(float hp, float maxHp) {
		int pct = Math.round((hp / maxHp) * 100f);
		pct = Math.max(0, Math.min(100, pct));
		return pct + "%";
	}

	/** Integer ratio text in Locale.ROOT, e.g. {@code "8/21"}. */
	public static String hpRatioText(float hp, float maxHp) {
		return String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
	}
}
