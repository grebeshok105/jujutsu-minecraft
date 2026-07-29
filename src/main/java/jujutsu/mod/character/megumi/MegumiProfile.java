package jujutsu.mod.character.megumi;

/** Centralized baseline tuning for Megumi's Divine Dogs vertical slice. */
public final class MegumiProfile {
	public static final double DOG_HEALTH = 60.0;
	public static final double DOG_ATTACK_DAMAGE = 3.0;
	public static final double DOG_MOVEMENT_SPEED = 0.34;
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
	public static final float POUNCE_BONUS_DAMAGE = 2.0f;
	public static final double POUNCE_KNOCKBACK = 2.4;
	public static final int POUNCE_STAGGER_TICKS = 6;
	public static final double VFX_CUE_RADIUS = 48.0;
	static final Double VFX_DELIVERY_RADIUS = VFX_CUE_RADIUS;

	private MegumiProfile() {}
}
