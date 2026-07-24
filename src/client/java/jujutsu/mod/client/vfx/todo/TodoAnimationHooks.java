package jujutsu.mod.client.vfx.todo;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.vfx.VfxCue;

/** Safe integration seam for Todo's future GeckoLib clap animation. */
public final class TodoAnimationHooks {
	public static final ResourceLocation BOOGIE_WOOGIE = JujutsuMod.id("ability.boogie_woogie");

	private TodoAnimationHooks() {}

	/** No-op until Todo has a model and animation JSON; VFX must never depend on that future asset. */
	public static void triggerBoogieWoogie(VfxCue cue) {
	}
}
