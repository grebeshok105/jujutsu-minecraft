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

	private TodoVfxIds() {}
}
