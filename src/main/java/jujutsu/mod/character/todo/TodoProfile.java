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
	public static final double BOOGIE_WOOGIE_RANGE = 20.0;
	/** Three seconds at the vanilla 20 TPS baseline. */
	public static final int BOOGIE_WOOGIE_COOLDOWN_TICKS = 60;
	/** Local horizontal nudge only when the exact destination is inside solid blocks. */
	public static final double SAFE_POSITION_HORIZONTAL_RADIUS = 1.0;
	/** Local upward nudge only when the exact destination is inside solid blocks. */
	public static final int SAFE_POSITION_UPWARD_BLOCKS = 3;
	/** Keeps targets comfortably inside the world border. */
	public static final double WORLD_BORDER_MARGIN = 0.05;
	/** Radius the swap cues are broadcast over, around each endpoint. */
	public static final double BOOGIE_WOOGIE_CUE_RADIUS = 64.0;
	/** Clap lands on the swap tick itself, never trailing the visual. */
	public static final float BOOGIE_WOOGIE_CLAP_VOLUME = 0.95f;
	public static final float BOOGIE_WOOGIE_CLAP_PITCH = 1.28f;
	/** Short displacement whoosh at both original positions, one tick behind the clap. */
	public static final int BOOGIE_WOOGIE_MOVE_SOUND_DELAY_TICKS = 1;
	public static final float BOOGIE_WOOGIE_MOVE_SOUND_VOLUME = 0.7f;
	public static final float BOOGIE_WOOGIE_MOVE_SOUND_PITCH = 1.45f;
	/**
	 * Feint clap cooldown: one second, a third of the real swap, on its own slot so a feint never
	 * spends or delays Boogie Woogie. Long enough for the clap to finish before the next one starts.
	 */
	public static final int FAKE_CLAP_COOLDOWN_TICKS = 20;
	/**
	 * Pair swap: longer than Todo's own swap because he takes no personal risk in it — he stays put
	 * while two bystanders trade places.
	 */
	public static final int PAIR_SWAP_COOLDOWN_TICKS = 100;
	/**
	 * How long a marked first participant stays selected. Five seconds is enough to line up a second
	 * target and short enough that a stale mark cannot surprise anyone. The mark visual reads this,
	 * rather than repeating the literal.
	 */
	public static final int PAIR_SELECTION_TTL_TICKS = 100;
	/** Shared Black Flash chance for Todo's vanilla melee bridge. */
	public static final float BLACK_FLASH_CHANCE = 0.10f;
	public static final float BLACK_FLASH_DAMAGE_MULTIPLIER = 1.75f;
	public static final int BLACK_FLASH_STAGGER_TICKS = 14;

	private TodoProfile() {}
}
