package jujutsu.mod.client.vfx;

public interface VfxInstance {
	int durationTicks();

	void start(VfxContext context, float initialAgeTicks);

	static VfxInstance of(int durationTicks, Starter starter) {
		return new VfxInstance() {
			@Override
			public int durationTicks() {
				return durationTicks;
			}

			@Override
			public void start(VfxContext context, float initialAgeTicks) {
				starter.start(context, initialAgeTicks);
			}
		};
	}

	@FunctionalInterface
	interface Starter {
		void start(VfxContext context, float initialAgeTicks);
	}
}
