package jujutsu.mod.client.vfx;

public interface VfxInstance {
	int durationTicks();

	void start(VfxContext context, float initialAgeTicks);
}
