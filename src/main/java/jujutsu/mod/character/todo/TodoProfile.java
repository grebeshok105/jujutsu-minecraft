package jujutsu.mod.character.todo;

/** Centralized baseline tuning for Aoi Todo's first playable slice. */
public final class TodoProfile {
	/** Vanilla melee damage is multiplied by 1.50 through an attribute modifier. */
	public static final double MELEE_DAMAGE_MULTIPLIER = 1.50;
	/** Vanilla attack speed is multiplied by 0.85 through an attribute modifier. */
	public static final double ATTACK_SPEED_MULTIPLIER = 0.85;
	/** Incoming stagger durations are multiplied by 0.50. */
	public static final double STAGGER_DURATION_MULTIPLIER = 0.50;
	/** Boogie Woogie server-side directed target reach. */
	public static final double BOOGIE_WOOGIE_RANGE = 24.0;
	/** Three seconds at the vanilla 20 TPS baseline. */
	public static final int BOOGIE_WOOGIE_COOLDOWN_TICKS = 60;
	/** Deterministic local horizontal fallback around a blocked exact destination. */
	public static final double SAFE_POSITION_HORIZONTAL_RADIUS = 1.0;
	/** Maximum upward fallback for a blocked exact destination. */
	public static final int SAFE_POSITION_UPWARD_BLOCKS = 3;
	/** Keeps targets comfortably inside the world border. */
	public static final double WORLD_BORDER_MARGIN = 0.05;
	/** Shared Black Flash chance for Todo's vanilla melee bridge. */
	public static final float BLACK_FLASH_CHANCE = 0.10f;
	public static final float BLACK_FLASH_DAMAGE_MULTIPLIER = 1.75f;
	public static final int BLACK_FLASH_STAGGER_TICKS = 14;

	private TodoProfile() {}
}
