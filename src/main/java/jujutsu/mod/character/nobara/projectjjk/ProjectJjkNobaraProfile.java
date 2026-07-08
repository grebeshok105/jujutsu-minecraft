package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkNobaraProfile {
	public static final int TRIPLE_HOLD_TICKS = 6;
	public static final int BARRAGE_HOLD_TICKS = 16;
	public static final int SINGLE_NAILS = 1;
	public static final int TRIPLE_NAILS = 3;
	public static final int BARRAGE_NAILS = 8;
	public static final int MAX_NAIL_AGE_TICKS = 1200;
	public static final int PREPARED_LAUNCH_DELAY_TICKS = 4;
	public static final double PREPARED_FORWARD_OFFSET = 1.35;
	public static final double PREPARED_VERTICAL_OFFSET = -0.18;
	public static final double LAUNCH_SPEED_BLOCKS_PER_TICK = 3.35;
	public static final double TARGET_RANGE = 36.0;
	public static final double HAIRPIN_SEARCH_RANGE = 18.0;
	public static final double PREPARED_LAUNCH_RANGE = 2.0;
	public static final double IMPACT_RADIUS = 3.75;
	public static final double GROUND_IMPACT_RADIUS = 2.25;
	public static final float NAIL_DAMAGE = 2.0f;
	public static final float HAIRPIN_DAMAGE = 18.0f;
	public static final float HAIRPIN_KNOCKBACK = 1.9f;

	// Embedded nail marks (the cursed connection carrier).
	public static final int MARK_MAX_PER_TARGET = 4;
	public static final int MARK_DURATION_TICKS = 900;
	public static final int EMBEDDED_NAIL_AGE_TICKS = MARK_DURATION_TICKS;
	public static final int TARGET_MARK_RENDER_TICKS = MARK_DURATION_TICKS;

	// Hairpin mark detonation.
	public static final double DETONATE_RANGE = 24.0;
	public static final float DETONATE_DAMAGE_BASE = 1.0f;
	public static final float DETONATE_DAMAGE_PER_MARK = 0.0f;
	public static final int HAIRPIN_EXPLOSION_START_DELAY_TICKS = 10;
	public static final double HAIRPIN_EXPLOSION_RADIUS = 1.5;
	public static final float HAIRPIN_EXPLOSION_KNOCKBACK = 0.2f;
	public static final double HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET = 4.0;
	public static final double HAIRPIN_EXPLOSION_DETECT_RANGE = 10.0;
	public static final double HAIRPIN_EXPLOSION_DETECT_RADIUS = 5.0;

	// Hairpin enlargement: ProjectJJK-style delayed snap against a marked looked-at target.
	public static final double HAIRPIN_ENLARGE_RANGE = 20.0;
	public static final int HAIRPIN_ENLARGE_DELAY_TICKS = 20;
	public static final int HAIRPIN_ENLARGE_STUN_TICKS = 50;
	public static final float HAIRPIN_ENLARGE_DAMAGE = 12.0f;

	// Resonance (straw doll remote strike).
	public static final double RESONANCE_RANGE = 96.0;
	public static final double LINK_RANGE = 32.0;
	public static final float RESONANCE_DAMAGE_BASE = 8.0f;
	public static final float RESONANCE_DAMAGE_PER_MARK = 3.0f;
	public static final int RESONANCE_WEAKNESS_TICKS = 80;

	private ProjectJjkNobaraProfile() {}

	public static float detonateDamage(int marks) {
		return DETONATE_DAMAGE_BASE + DETONATE_DAMAGE_PER_MARK * Math.max(0, marks);
	}

	public static float resonanceDamage(int marks) {
		return RESONANCE_DAMAGE_BASE + RESONANCE_DAMAGE_PER_MARK * Math.max(0, marks);
	}

	public static int nailCountForUseTicks(int useTicks) {
		if (useTicks >= BARRAGE_HOLD_TICKS) {
			return BARRAGE_NAILS;
		}
		if (useTicks >= TRIPLE_HOLD_TICKS) {
			return TRIPLE_NAILS;
		}
		return SINGLE_NAILS;
	}

	public static int launchDelayForIndex(int index) {
		return Math.max(0, index) * PREPARED_LAUNCH_DELAY_TICKS;
	}
}
