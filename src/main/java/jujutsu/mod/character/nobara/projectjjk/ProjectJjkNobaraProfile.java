package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkNobaraProfile {
	public static final int TRIPLE_HOLD_TICKS = 6;
	public static final int BARRAGE_HOLD_TICKS = 16;
	public static final int EXTRA_NAIL_HOLD_TICKS = 10;
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
	public static final int EMBEDDED_NAIL_AGE_TICKS = 0;
	public static final int TARGET_MARK_RENDER_TICKS = MARK_DURATION_TICKS;

	// Hairpin mark detonation.
	public static final double DETONATE_RANGE = 24.0;
	public static final float HAIRPIN_BOOM_DAMAGE_PER_NAIL = 3.0f;
	public static final float DETONATE_DAMAGE_BASE = HAIRPIN_BOOM_DAMAGE_PER_NAIL;
	public static final float DETONATE_DAMAGE_PER_MARK = 0.0f;
	public static final int HAIRPIN_EXPLOSION_START_DELAY_TICKS = 10;
	public static final double HAIRPIN_EXPLOSION_RADIUS = 1.5;
	public static final float HAIRPIN_EXPLOSION_KNOCKBACK = 0.2f;
	public static final double HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET = 0.0;
	public static final double HAIRPIN_EXPLOSION_DETECT_RANGE = 14.0;
	public static final double HAIRPIN_EXPLOSION_DETECT_RADIUS = 5.0;

	// Hairpin enlargement: ProjectJJK-style delayed snap against a marked looked-at target.
	public static final double HAIRPIN_ENLARGE_RANGE = 20.0;
	public static final int HAIRPIN_ENLARGE_DELAY_TICKS = 20;
	public static final int HAIRPIN_ENLARGE_STUN_TICKS = 50;
	public static final float HAIRPIN_ENLARGE_DAMAGE_PER_NAIL = 4.0f;
	public static final float HAIRPIN_ENLARGE_DAMAGE = HAIRPIN_ENLARGE_DAMAGE_PER_NAIL;

	// Resonance (straw doll remote strike).
	public static final float RESONANCE_DAMAGE = 28.0f;
	public static final float RESONANCE_SERVER_TICK_RATE = 6.0f;
	public static final int RESONANCE_SERVER_SLOW_TICKS = 4;
	public static final float SELF_RESONANCE_SELF_DAMAGE = 6.0f;
	public static final float SELF_RESONANCE_LINKED_DAMAGE = 18.0f;
	public static final float BLACK_FLASH_DAMAGE_MULTIPLIER = 1.75f;
	public static final float HAMMER_HORIZONTAL_DAMAGE = 5.0f;
	public static final float HAMMER_OVERHEAD_DAMAGE = 8.0f;
	public static final float EMBEDDED_NAIL_DRIVE_DAMAGE = 4.0f;
	public static final double HAMMER_MELEE_RANGE = 3.5;
	public static final double HAMMER_SWEEP_RADIUS = 3.25;
	public static final double NAIL_CONTEXT_RANGE = 4.5;
	public static final double EMBEDDED_NAIL_DRIVE_DEPTH = 0.10;
	public static final double HAMMER_RANGE_TOLERANCE = 0.75;
	public static final double NAIL_CONTEXT_SCAN_INFLATE = 1.0;
	public static final double HAMMER_SWEEP_REAR_DOT = -0.15;
	public static final double PREPARED_NAIL_LATERAL_SPACING = 0.22;
	public static final int HORIZONTAL_IMPACT_TICK = 3;
	public static final int HORIZONTAL_RECOVERY_TICKS = 8;
	public static final int OVERHEAD_IMPACT_TICK = 8;
	public static final int OVERHEAD_RECOVERY_TICKS = 16;
	public static final int BLACK_FLASH_WINDOW_EARLY_TICKS = 0;
	public static final int BLACK_FLASH_WINDOW_LATE_TICKS = 2;
	public static final int LIGHT_STAGGER_TICKS = 5;
	public static final int HEAVY_STAGGER_TICKS = 14;
	public static final int SELF_RESONANCE_WINDUP_TICKS = 14;
	public static final int RITUAL_RECOVERY_TICKS = 20;
	public static final int NAIL_LAUNCH_RECOVERY_TICKS = 10;

	private ProjectJjkNobaraProfile() {}

	public static float detonateDamage(int marks) {
		return DETONATE_DAMAGE_BASE + DETONATE_DAMAGE_PER_MARK * Math.max(0, marks);
	}

	public static int nailCountForUseTicks(int useTicks) {
		return Math.min(BARRAGE_NAILS, SINGLE_NAILS + Math.max(0, useTicks) / EXTRA_NAIL_HOLD_TICKS);
	}

	public static int launchDelayForIndex(int index) {
		return Math.max(0, index) * PREPARED_LAUNCH_DELAY_TICKS;
	}

	public static int preparationDelayForIndex(int index) {
		return Math.max(0, index) * EXTRA_NAIL_HOLD_TICKS;
	}
}
