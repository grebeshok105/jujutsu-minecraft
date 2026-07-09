package jujutsu.mod.vfx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record VfxCue(
		ResourceLocation effectId,
		Vec3 origin,
		int anchorEntityId,
		int intensity,
		long startGameTime,
		long seed
) {
	public static final int NO_ANCHOR = -1;
}
