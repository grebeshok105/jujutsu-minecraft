package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/** Typed VFX ids owned by Todo's character slice. */
public final class TodoVfxIds {
	public static final ResourceLocation BOOGIE_WOOGIE = JujutsuMod.id("todo/boogie_woogie");
	/** World-fixed (NO_ANCHOR) burst at one absolute swap endpoint; one cue per moved body. */
	public static final ResourceLocation SWAP_ENDPOINT = JujutsuMod.id("todo/swap_endpoint");
	/**
	 * Caster-only confirmation that a feint clap registered. Sent to one player, never broadcast, and
	 * deliberately quiet: anything an observer could see or hear would give the feint away.
	 */
	public static final ResourceLocation FEINT_TELL = JujutsuMod.id("todo/feint_tell");
	/**
	 * Confirmation that a body is marked for the pair swap, anchored to that body so it lands on the
	 * right one. Sent to the caster only: nothing about who is marked has reached the world yet, so the
	 * marked body must not learn it is next. The server re-emits it as a silent pulse while the
	 * selection lives; each pulse feeds the caster's HUD chip hold and re-draws a quiet ring at the
	 * body's position, and observers receive neither cue.
	 */
	public static final ResourceLocation PAIR_MARK = JujutsuMod.id("todo/pair_mark");
	/**
	 * The residue one body leaves where it used to be. World-fixed, one cue per body that actually moved,
	 * which is not the same as one per swap endpoint — a swap onto a landed mark moves a single body.
	 *
	 * <p>{@code anchorOffset} carries {@code (bbWidth, bbHeight, yawDegrees)} and {@code direction} points
	 * at where the body went. The dimensions travel rather than being read off the live entity, because by
	 * render time that entity is standing somewhere else in a different pose.
	 */
	public static final ResourceLocation SWAP_AFTERIMAGE = JujutsuMod.id("todo/swap_afterimage");
	/**
	 * The landing, at the position a body arrived in. World-fixed, one cue per moved body.
	 *
	 * <p>{@code anchorOffset} carries {@code (speed, bbWidth, bbHeight)} and {@code direction} carries the
	 * preserved velocity. The speed rides in {@code anchorOffset} because {@link VfxCue} normalizes
	 * {@code direction}, so magnitude cannot survive there — one vector, two useful forms.
	 */
	public static final ResourceLocation SWAP_ARRIVAL = JujutsuMod.id("todo/swap_arrival");
	/**
	 * The hit a landed swap bought. Its own id so a heavier strike is visibly not an ordinary one, and
	 * deliberately cheap: it can land on the same tick as a Black Flash, and it must not compete with it.
	 */
	public static final ResourceLocation MOMENTUM_STRIKE = JujutsuMod.id("todo/momentum_strike");
	/**
	 * The stone leaving Todo's hand. Anchored at the caster; a short flick, deliberately lighter than
	 * any clap so a throw never reads as a swap.
	 */
	public static final ResourceLocation STONE_THROW = JujutsuMod.id("todo/stone_throw");
	/**
	 * The stone ending — lifetime expiry, terminal block collision, or state cleanup. World-fixed at
	 * the stone's last position; one compact puff, no lingering residue.
	 */
	public static final ResourceLocation STONE_VANISH = JujutsuMod.id("todo/stone_vanish");
	/**
	 * One edge of the triple cyclic swap. Emitted three times per cast — Todo→A, A→T, T→Todo — with
	 * {@code anchorOffset} owning the full travel vector (its length is the edge length; {@link VfxCue}
	 * normalizes {@code direction}, so magnitude cannot survive there). World-fixed at the edge's start.
	 */
	public static final ResourceLocation TRIPLE_SWAP = JujutsuMod.id("todo/triple_swap");
	public static final Set<ResourceLocation> LIVE = Set.of(
			BOOGIE_WOOGIE, SWAP_ENDPOINT, FEINT_TELL, PAIR_MARK, SWAP_AFTERIMAGE, SWAP_ARRIVAL, MOMENTUM_STRIKE,
			STONE_THROW, STONE_VANISH, TRIPLE_SWAP);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	private TodoVfxIds() {}
}
