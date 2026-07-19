package jujutsu.mod.vfx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record VfxCue(
		ResourceLocation effectId,
		Vec3 origin,
		int anchorEntityId,
		Vec3 anchorOffset,
		int intensity,
		long startGameTime,
		long seed,
		Vec3 direction
) {
	public static final int NO_ANCHOR = -1;

	public VfxCue {
		direction = direction.lengthSqr() > 1e-8 ? direction.normalize() : Vec3.ZERO;
	}
}
