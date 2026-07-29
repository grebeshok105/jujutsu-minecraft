package jujutsu.mod.client.vfx.todo;

import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.VfxCue;

/** Named client read model for the overloaded SWAP_ARRIVAL offset convention. */
public record TodoSwapArrivalPayload(double speed, double bodyWidth, double bodyHeight, Vec3 direction) {
	public static TodoSwapArrivalPayload from(VfxCue cue) {
		Vec3 offset = cue.anchorOffset();
		return new TodoSwapArrivalPayload(offset.x, offset.y, offset.z, cue.direction());
	}
}
