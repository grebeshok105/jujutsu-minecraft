package jujutsu.mod.character.megumi;

/** Centralized baseline tuning for Megumi's Divine Dogs vertical slice. */
public final class MegumiProfile {
	public static final double DOG_HEALTH = 60.0;
	public static final double DOG_ATTACK_DAMAGE = 3.0;
	public static final double DOG_MOVEMENT_SPEED = 0.34;
	public static final double NAVIGATION_SPEED_MODIFIER = 1.0;
	public static final double FOLLOW_START_DISTANCE = 10.0;
	public static final double FOLLOW_STOP_DISTANCE = 2.0;
	public static final double LEASH_DISTANCE = 32.0;
	public static final double LEASH_SAFE_SEARCH_RADIUS = 3.0;
	public static final int LEASH_RETRY_TICKS = 10;
	public static final double SIC_RANGE = 20.0;
	public static final int DOG_MATERIALIZATION_TICKS = 16;
	public static final int DOG_RECALL_TICKS = 12;
	public static final int RECALL_COOLDOWN_TICKS = 240;
	public static final int PACK_DEATH_COOLDOWN_TICKS = 600;
	public static final int SIC_COOLDOWN_TICKS = 30;
	public static final double POUNCE_MIN_RANGE = 3.0;
	public static final double POUNCE_MAX_RANGE = 8.0;
	public static final int POUNCE_COOLDOWN_TICKS = 80;
	public static final int POUNCE_TIMEOUT_TICKS = 16;
	public static final double POUNCE_HORIZONTAL_SPEED = 0.92;
	public static final double POUNCE_VERTICAL_SPEED = 0.42;
	public static final double POUNCE_MAX_VERTICAL_SPEED = 0.58;
	public static final double POUNCE_GRAVITY = 0.08;
	public static final double POUNCE_EXIT_DAMPING = 0.35;
	public static final float POUNCE_BONUS_DAMAGE = 2.0f;
	public static final double POUNCE_KNOCKBACK = 2.4;
	public static final int POUNCE_STAGGER_TICKS = 6;
	public static final double VFX_CUE_RADIUS = 48.0;
	static final Double VFX_DELIVERY_RADIUS = VFX_CUE_RADIUS;

	// --- Shadow Trap (B) ---
	public static final double SHADOW_TRAP_RANGE = 20.0;
	public static final double SHADOW_TRAP_RADIUS = 2.6;
	/** How far above the pool a body still counts as inside it: one jump, not a pillar. */
	public static final double SHADOW_TRAP_VERTICAL_REACH = 1.6;
	public static final int SHADOW_TRAP_DURATION_TICKS = 100;
	public static final int SHADOW_TRAP_COOLDOWN_TICKS = 200;
	/** Effect length per re-application; leaving the zone lets it expire on its own within this window. */
	public static final int SHADOW_TRAP_GRIP_REFRESH_TICKS = 8;
	public static final int SHADOW_TRAP_ZONE_PULSE_TICKS = 40;
	public static final int SHADOW_TRAP_GRIP_CUE_PERIOD_TICKS = 20;
	/** ADD_MULTIPLIED_TOTAL on MOVEMENT_SPEED: a gripped body keeps a quarter of its speed. */
	public static final double SHADOW_GRIP_SPEED_MULTIPLIER = -0.75;
	/** ADD_MULTIPLIED_TOTAL on JUMP_STRENGTH: jumping is fully suppressed, not merely weakened. */
	public static final double SHADOW_GRIP_JUMP_MULTIPLIER = -1.0;
	/** How far below an airborne target's feet the trap centre may snap to the ground. */
	public static final double SHADOW_TRAP_GROUND_SNAP_BLOCKS = 3.0;

	// --- Shadow Move (Shift+B) ---
	public static final double SHADOW_STEP_TARGET_RANGE = 20.0;
	public static final double SHADOW_STEP_RANGE = 24.0;
	public static final double BACKSTEP_DISTANCE = 1.75;
	/** A hidden target that got farther than this multiple of the cast range cancels the backstep. */
	public static final double BACKSTEP_TARGET_DRIFT_MULTIPLIER = 2.0;
	public static final int SHADOW_SINK_TICKS = 8;
	public static final int SHADOW_HIDDEN_TICKS = 4;
	public static final int SHADOW_EMERGE_TICKS = 6;
	public static final int SUBMERGE_MAX_TICKS = 50;
	public static final int SHADOW_RIPPLE_PERIOD_TICKS = 5;
	public static final int SHADOW_STEP_COOLDOWN_TICKS = 120;
	public static final int SUBMERGE_COOLDOWN_TICKS = 200;
	/** Horizontal ring used when the emerge point itself needs rescuing. */
	public static final double EMERGE_SEARCH_RADIUS = 3.0;
	public static final double SAFE_POSITION_HORIZONTAL_RADIUS = 1.0;
	public static final int SAFE_POSITION_UPWARD_BLOCKS = 3;
	public static final double WORLD_BORDER_MARGIN = 0.05;

	// --- Shadow Drop (V) ---
	public static final double DROP_RANGE = 20.0;
	/** How far above the target's head the zone hangs — also the fall distance that prices the damage. */
	public static final double DROP_ZONE_HEIGHT_BLOCKS = 4.0;
	public static final double DROP_ZONE_RADIUS = 1.2;
	/** The zone telegraphs for a full second before anything falls; outrunning it is real counterplay. */
	public static final int DROP_TELEGRAPH_TICKS = 20;
	/** The hovering disc re-emits (and re-anchors over the moving target) on this period. */
	public static final int DROP_ZONE_PULSE_TICKS = 5;
	public static final int DROP_COOLDOWN_TICKS = 160;
	/** Soft blocks (sand, gravel, clay) crush like light anvils: per-block scaling with a low cap. */
	public static final float DROP_SOFT_DAMAGE_PER_BLOCK = 1.0f;
	public static final int DROP_SOFT_DAMAGE_MAX = 5;
	/** Anvil keeps the vanilla crush numbers so armor/helmet interactions stay familiar. */
	public static final float DROP_ANVIL_DAMAGE_PER_BLOCK = 2.0f;
	public static final int DROP_ANVIL_DAMAGE_MAX = 40;
	/** Weighted block table; weights sum to 100 so each reads as a percent. */
	public static final int DROP_WEIGHT_SAND = 40;
	public static final int DROP_WEIGHT_GRAVEL = 30;
	public static final int DROP_WEIGHT_CLAY = 20;
	public static final int DROP_WEIGHT_ANVIL = 10;

	private MegumiProfile() {}
}
