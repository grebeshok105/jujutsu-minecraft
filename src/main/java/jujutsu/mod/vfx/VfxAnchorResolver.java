package jujutsu.mod.vfx;

import java.util.function.IntFunction;
import net.minecraft.world.phys.Vec3;

public final class VfxAnchorResolver {
	private VfxAnchorResolver() {}

	public static Vec3 resolve(VfxCue cue, IntFunction<Vec3> anchorPosition) {
		if (cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return cue.origin();
		}
		Vec3 anchor = anchorPosition.apply(cue.anchorEntityId());
		return anchor == null ? cue.origin() : anchor;
	}
}
