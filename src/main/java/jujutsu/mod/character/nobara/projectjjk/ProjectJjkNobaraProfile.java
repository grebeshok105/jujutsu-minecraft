package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkNobaraProfile {
	public static final int TRIPLE_HOLD_TICKS = 6;
	public static final int BARRAGE_HOLD_TICKS = 16;
	public static final int SINGLE_NAILS = 1;
	public static final int TRIPLE_NAILS = 3;
	public static final int BARRAGE_NAILS = 8;
	public static final int MAX_NAIL_AGE_TICKS = 1200;
	public static final int PREPARED_LAUNCH_DELAY_TICKS = 2;
	public static final double PREPARED_FORWARD_OFFSET = 1.35;
	public static final double PREPARED_VERTICAL_OFFSET = -0.18;
	public static final double LAUNCH_SPEED_BLOCKS_PER_TICK = 3.35;
	public static final double TARGET_RANGE = 36.0;
	public static final double HAIRPIN_SEARCH_RANGE = 18.0;
	public static final double IMPACT_RADIUS = 3.75;
	public static final float NAIL_DAMAGE = 2.0f;
	public static final float HAIRPIN_DAMAGE = 18.0f;
	public static final float HAIRPIN_KNOCKBACK = 1.9f;

	private ProjectJjkNobaraProfile() {}

	public static int nailCountForUseTicks(int useTicks) {
		if (useTicks >= BARRAGE_HOLD_TICKS) {
			return BARRAGE_NAILS;
		}
		if (useTicks >= TRIPLE_HOLD_TICKS) {
			return TRIPLE_NAILS;
		}
		return SINGLE_NAILS;
	}
}
