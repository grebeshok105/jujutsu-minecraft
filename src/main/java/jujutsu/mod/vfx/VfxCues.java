package jujutsu.mod.vfx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Common transport factories for server-produced VFX cues. */
public final class VfxCues {
	private VfxCues() {}

	public static VfxCue worldFixed(
			ResourceLocation effectId,
			Vec3 origin,
			int intensity,
			long startGameTime,
			long seed
	) {
		return create(effectId, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, intensity, startGameTime, seed, Vec3.ZERO);
	}

	public static VfxCue worldFixedDirected(
			ResourceLocation effectId,
			Vec3 origin,
			int intensity,
			long startGameTime,
			long seed,
			Vec3 direction
	) {
		return create(effectId, origin, VfxCue.NO_ANCHOR, Vec3.ZERO, intensity, startGameTime, seed, direction);
	}

	public static VfxCue worldFixedDisplacement(
			ResourceLocation effectId,
			Vec3 origin,
			int intensity,
			long startGameTime,
			long seed,
			Vec3 displacement
	) {
		return create(effectId, origin, VfxCue.NO_ANCHOR, displacement, intensity, startGameTime, seed, displacement);
	}

	public static VfxCue anchored(
			ResourceLocation effectId,
			Vec3 origin,
			int anchorEntityId,
			Vec3 anchorPosition,
			int intensity,
			long startGameTime,
			long seed
	) {
		return create(effectId, origin, anchorEntityId, origin.subtract(anchorPosition), intensity, startGameTime, seed, Vec3.ZERO);
	}

	public static VfxCue anchoredDirected(
			ResourceLocation effectId,
			Vec3 origin,
			int anchorEntityId,
			Vec3 anchorPosition,
			int intensity,
			long startGameTime,
			long seed,
			Vec3 direction
	) {
		return create(effectId, origin, anchorEntityId, origin.subtract(anchorPosition), intensity, startGameTime, seed, direction);
	}

	private static VfxCue create(
			ResourceLocation effectId,
			Vec3 origin,
			int anchorEntityId,
			Vec3 anchorOffset,
			int intensity,
			long startGameTime,
			long seed,
			Vec3 direction
	) {
		return new VfxCue(effectId, origin, anchorEntityId, anchorOffset, Math.max(1, intensity), startGameTime, seed, direction);
	}
}
