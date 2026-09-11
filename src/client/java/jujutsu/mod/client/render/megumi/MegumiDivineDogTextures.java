package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

/** Shipped Dire Wolf texture set for the Divine Dog visual, keyed by the synced wolf variant. */
public final class MegumiDivineDogTextures {
	public static final ResourceLocation WHITE = JujutsuMod.id("textures/entity/megumi_divine_dog_white.png");
	public static final ResourceLocation BLACK = JujutsuMod.id("textures/entity/megumi_divine_dog_black.png");

	private MegumiDivineDogTextures() {}

	/** The black variant serves the dark Dire Wolf sheet; every other variant stays on the light one. */
	public static ResourceLocation forVariant(boolean blackVariant) {
		return blackVariant ? BLACK : WHITE;
	}
}
