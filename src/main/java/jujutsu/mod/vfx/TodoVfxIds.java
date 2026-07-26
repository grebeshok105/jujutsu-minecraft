package jujutsu.mod.vfx;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

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
	 * Caster-only confirmation that a pair-swap participant is marked, anchored to that participant so it
	 * lands on the right body. Sent to one player: only the caster may know who is marked.
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

	private TodoVfxIds() {}
}
