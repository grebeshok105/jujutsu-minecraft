package jujutsu.mod.client.vfx;

import jujutsu.mod.vfx.VfxCue;

@FunctionalInterface
public interface VfxRecipe {
	VfxInstance create(VfxCue cue);
}
