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
	/** Todo's Black Flash reuses the shared visual recipe but has its own delivery site. */
	static final Double BLACK_FLASH_VFX_DELIVERY_RADIUS = 64.0;
	static final Double VFX_DELIVERY_RADIUS = BOOGIE_WOOGIE_CUE_RADIUS;
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
	 * Triple cycle: deliberately above the pair, because it moves three bodies including Todo's own and
	 * it runs on its own slot, so the pair and the cycle never crowd each other.
	 */
	public static final int TRIPLE_SWAP_COOLDOWN_TICKS = 160;
	/**
	 * How long a marked first participant stays selected. Five seconds is enough to line up a second
	 * target and short enough that a stale mark cannot surprise anyone. The mark visual reads this,
	 * rather than repeating the literal.
	 */
	public static final int PAIR_SELECTION_TTL_TICKS = 100;
	/**
	 * Server re-emit period for the pair-swap selection mark while it lives. One second, a fifth of the
	 * selection TTL; the re-emit is the silent trap-boundary pulse pattern, carrying intensity 0 so the
	 * recipe can tell it from the audible mark. Each pulse re-arms the caster's HUD chip hold and
	 * re-draws a quiet ring at the marked body's current position — the caster alone receives it.
	 */
	public static final int PAIR_MARK_PULSE_TICKS = 20;
	/**
	 * The stone's flight speed: 3.5 blocks per second — slow, readable, and a flat line with no arc, so
	 * the swap is a plan the opponent can see coming.
	 */
	public static final double STONE_SPEED_BLOCKS_PER_TICK = 0.175;
	/** Five seconds of flight. The stone never anchors: this clock is its only end. */
	public static final int STONE_LIFETIME_TICKS = 100;
	/** The stone's hitbox, a third of a block, sized for a small thrown rock. */
	public static final float STONE_HITBOX_SIZE = 0.35f;
	/**
	 * Anti-double-click only: throwing must never lock the follow-up self-swap behind a long cooldown.
	 * The self-swap carries the real price.
	 */
	public static final int STONE_THROW_COOLDOWN_TICKS = 10;
	/** Three seconds — the same price as Todo's own aimed swap, because the self-swap is one of those. */
	public static final int STONE_SELF_SWAP_COOLDOWN_TICKS = 60;
	/** Five seconds — the same price as the pair swap, because he moves a bystander, not himself. */
	public static final int STONE_TARGET_SWAP_COOLDOWN_TICKS = 100;
	/** How far Todo may be from the stone for either swap (V or Shift+V). */
	public static final double STONE_SWAP_RANGE = 32.0;
	/** Crosshair reach for the Shift+V target, matching the aimed swap. */
	public static final double STONE_TARGET_RANGE = 20.0;
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
