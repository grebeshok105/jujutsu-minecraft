package jujutsu.mod.client.vfx;

public final class VfxTimeChannelTest {
	private VfxTimeChannelTest() {}

	public static void main(String[] args) {
		assert VfxTimeChannel.clampScale(0.2f) == 0.45f : "slow motion must not freeze the client";
		assert VfxTimeChannel.clampScale(0.65f) == 0.65f : "normal Resonance scale must be preserved";
		assert VfxTimeChannel.clampScale(1.4f) == 1.0f : "VFX must never speed up render time";
		VfxTimeChannel channel = new VfxTimeChannel();
		assert channel.timeScale() == 1.0f : "idle time channel must be neutral";
		channel.clear();
		assert channel.timeScale() == 1.0f : "cleared time channel must be neutral";
		System.out.println("VfxTimeChannelTest passed");
	}
}
