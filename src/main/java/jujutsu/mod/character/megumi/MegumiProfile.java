package jujutsu.mod.character.megumi;

/** Centralized baseline tuning for Megumi's Divine Dogs vertical slice. */
public final class MegumiProfile {
	public static final double DOG_HEALTH = 20.0;
	public static final double DOG_ATTACK_DAMAGE = 3.0;
	public static final double DOG_MOVEMENT_SPEED = 0.30;
	public static final double FOLLOW_START_DISTANCE = 10.0;
	public static final double FOLLOW_STOP_DISTANCE = 2.0;
	public static final double LEASH_DISTANCE = 32.0;
	public static final double LEASH_SAFE_SEARCH_RADIUS = 3.0;
	public static final int LEASH_RETRY_TICKS = 10;
	public static final double SIC_RANGE = 20.0;
	public static final int RECALL_COOLDOWN_TICKS = 240;
	public static final int PACK_DEATH_COOLDOWN_TICKS = 600;
	public static final int SIC_COOLDOWN_TICKS = 30;

	private MegumiProfile() {}
}
