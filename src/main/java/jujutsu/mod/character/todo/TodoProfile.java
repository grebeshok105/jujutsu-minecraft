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
	 * One low report where the bodies landed, two ticks behind the whoosh so the pair reads as a single
	 * impact rather than a flam. Deliberately one sound at the midpoint and not one per endpoint: Minecraft
	 * audio has no propagation delay, so two of these arrive together and only muddy each other.
	 */
	public static final int BOOGIE_WOOGIE_IMPACT_SOUND_DELAY_TICKS = 3;
	public static final float BOOGIE_WOOGIE_IMPACT_SOUND_VOLUME = 0.85f;
	public static final float BOOGIE_WOOGIE_IMPACT_SOUND_PITCH = 0.8f;
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
	/** Thrown marker: a little slower than an ender pearl, so it can be placed rather than launched. */
	public static final float MARKER_THROW_POWER = 1.35f;
	public static final float MARKER_THROW_VOLUME = 0.6f;
	public static final float MARKER_THROW_PITCH = 0.85f;
	/** Three seconds of flight is more than enough for a 32-block throw; after that it is a stray. */
	public static final int MARKER_FLIGHT_TICKS = 60;
	/**
	 * Ten seconds of mark on a <em>body</em>, about two swap cooldowns, so following someone is worth at
	 * most that much tempo. A landed mark has no clock at all — the name says "body" because the scope of
	 * this number shrank to one of the two forms.
	 */
	public static final int MARKER_BODY_MARK_TTL_TICKS = 200;
	/**
	 * Cooldown for marking a body by hand. Short: the cast costs no item and moves nobody, and its whole
	 * value is set-up. Long enough that a held right click cannot repaint the mark every tick.
	 */
	public static final int ENTITY_MARK_COOLDOWN_TICKS = 20;
	/**
	 * Cooldown for the swap onto a mark. Equal to the aimed swap's today, and separate from it on purpose:
	 * a reusable anchor is the strongest thing in the kit, and pricing it differently must not mean
	 * rewriting the runtime.
	 */
	public static final int MARKER_SWAP_COOLDOWN_TICKS = 60;
	/** Nudge out of the struck face so the resting marker is visible against the surface. */
	public static final double MARKER_SURFACE_OFFSET = 0.15;
	/**
	 * Longer reach than the aimed swap: the player paid an item, a throw and a visible telegraph for it,
	 * and a public mark is something an opponent can play around.
	 */
	public static final double MARKER_SWAP_RANGE = 32.0;
	/**
	 * What a landed swap buys: one heavier hit, taken through an ATTACK_DAMAGE modifier so the vanilla
	 * swing is simply bigger and no second damage instance exists to double-count or double-consume.
	 *
	 * <p>Read the multiplier honestly. Both hands must be empty to clap, so a fist that swings inside the
	 * window gains 0.375 damage — a fist is 1.0, Todo's is 1.5, boosted 1.875 — which against a two-point
	 * heart is under a fifth of one. <b>The stagger is the payload</b>; the damage only matters if
	 * the player deliberately draws a weapon inside the window, which is the intended loop — displace,
	 * arm, hit. The window is shorter than the swap's own cooldown, so two grants can never overlap.
	 */
	public static final double SWAP_MOMENTUM_DAMAGE_MULTIPLIER = 1.25;
	public static final int SWAP_MOMENTUM_WINDOW_TICKS = 24;
	/** Between Nobara's light stagger and the Black Flash's: an opening, not a free hit. */
	public static final int SWAP_MOMENTUM_STAGGER_TICKS = 8;
	/** Shared Black Flash chance for Todo's vanilla melee bridge. */
	public static final float BLACK_FLASH_CHANCE = 0.10f;
	public static final float BLACK_FLASH_DAMAGE_MULTIPLIER = 1.75f;
	public static final int BLACK_FLASH_STAGGER_TICKS = 14;

	private TodoProfile() {}
}
