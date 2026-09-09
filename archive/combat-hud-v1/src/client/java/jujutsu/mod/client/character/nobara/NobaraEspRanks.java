package jujutsu.mod.client.character.nobara;

/**
 * Client-side rank classification for the ESP badge.
 *
 * <p>Product assumption: there is no rank system in the mod. This is a deterministic heuristic based
 * on vessel grade (for players) or max health (for mobs), purely for the visual badge.
 */
public final class NobaraEspRanks {
	/** Health threshold for Special Grade classification. */
	public static final float SPECIAL_GRADE_HEALTH = 100.0f;
	/** Health threshold for Grade 1 classification. */
	public static final float GRADE_1_HEALTH = 40.0f;
	/** Health threshold for Grade 2 classification. */
	public static final float GRADE_2_HEALTH = 20.0f;

	private NobaraEspRanks() {}

	/**
	 * Returns the localization key for the rank of the given target.
	 *
	 * <p>Player targets with a known vessel show the vessel's subtitle key (e.g. "Grade 3").
	 * Players without a vessel are classified "civilian". Non-player targets are classified by
	 * max health thresholds.
	 *
	 * @param isPlayer      whether the target is a player
	 * @param vesselGradeKey the vessel's subtitle key from the roster entry, or null if unknown
	 * @param maxHealth     the target's max health
	 * @return a localization key for the rank
	 */
	public static String rankKey(boolean isPlayer, String vesselGradeKey, float maxHealth) {
		if (isPlayer && vesselGradeKey != null) {
			return vesselGradeKey;
		}
		if (isPlayer) {
			return "esp.jujutsumod.rank.civilian";
		}
		if (maxHealth >= SPECIAL_GRADE_HEALTH) {
			return "esp.jujutsumod.rank.special_grade";
		}
		if (maxHealth >= GRADE_1_HEALTH) {
			return "esp.jujutsumod.rank.rank1";
		}
		if (maxHealth >= GRADE_2_HEALTH) {
			return "esp.jujutsumod.rank.rank2";
		}
		return "esp.jujutsumod.rank.rank3";
	}
}
