package jujutsu.mod.client.tongue;

/**
 * Compatibility entry point for older client bootstrap code. The tongue is now a world-rendered 3D
 * model; this class deliberately emits no particles and delegates registration to that renderer.
 */
public final class TongueClientFx {
	private TongueClientFx() {}

	/** @deprecated use {@link TongueRenderer#register()} from {@code MegumiPartialClientInit}. */
	@Deprecated
	public static void register() {
		TongueRenderer.register();
	}
}
