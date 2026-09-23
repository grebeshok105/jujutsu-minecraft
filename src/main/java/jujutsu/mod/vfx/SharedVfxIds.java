package jujutsu.mod.vfx;

import java.util.Set;
import jujutsu.mod.JujutsuMod;
import net.minecraft.resources.ResourceLocation;

/** VFX ids owned by mechanics shared across vessel implementations. */
public final class SharedVfxIds {
	public static final ResourceLocation BLACK_FLASH = JujutsuMod.id("black_flash");
	public static final Set<ResourceLocation> LIVE = Set.of(BLACK_FLASH);
	public static final Set<ResourceLocation> PLANNED = Set.of();

	private SharedVfxIds() {}
}
