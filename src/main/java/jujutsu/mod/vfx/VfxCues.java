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
		// The offset owns the full travel vector; VfxCue derives normalized orientation from it.
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
		return create(effectId, origin, requireAnchor(anchorEntityId), origin.subtract(anchorPosition), intensity, startGameTime, seed, Vec3.ZERO);
	}

	/**
	 * A silent re-emission of an already-announced anchored cue. This is the one factory whose
	 * intensity is deliberately zero: recipes treat 0 as "re-arm the presentation, sound nothing",
	 * which is how a server pulse keeps a mark readable without re-playing its announcement. The
	 * generic factories clamp intensity to 1 so an accidental zero cannot mute a real cue.
	 */
	public static VfxCue anchoredSilentRepeat(
			ResourceLocation effectId,
			Vec3 origin,
			int anchorEntityId,
			Vec3 anchorPosition,
			long startGameTime,
			long seed
	) {
		return new VfxCue(effectId, origin, requireAnchor(anchorEntityId), origin.subtract(anchorPosition), 0,
				startGameTime, seed, Vec3.ZERO);
	}

	public static VfxCue anchoredWithOffset(
			ResourceLocation effectId,
			Vec3 origin,
			int anchorEntityId,
			Vec3 anchorOffset,
			int intensity,
			long startGameTime,
			long seed
	) {
		return create(effectId, origin, requireAnchor(anchorEntityId), anchorOffset, intensity, startGameTime, seed, Vec3.ZERO);
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
		return create(effectId, origin, requireAnchor(anchorEntityId), origin.subtract(anchorPosition), intensity, startGameTime, seed, direction);
	}

	private static int requireAnchor(int anchorEntityId) {
		if (anchorEntityId == VfxCue.NO_ANCHOR) {
			throw new IllegalArgumentException("Anchored VFX cues require an entity anchor");
		}
		return anchorEntityId;
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
