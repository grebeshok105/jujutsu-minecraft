package jujutsu.mod.client.vfx;

public final class VfxQualityTest {
	private VfxQualityTest() {}

	public static void main(String[] args) {
		assertFullParticlesKeepDensity();
		assertReducedParticlesThinDenseBursts();
		assertMinimalParticlesKeepOneReadableParticle();
		System.out.println("VfxQualityTest passed");
	}

	private static void assertFullParticlesKeepDensity() {
		assert VfxQuality.FULL.scaledCount(10) == 10 : VfxQuality.FULL.scaledCount(10);
	}

	private static void assertReducedParticlesThinDenseBursts() {
		assert VfxQuality.REDUCED.scaledCount(10) == 6 : VfxQuality.REDUCED.scaledCount(10);
	}

	private static void assertMinimalParticlesKeepOneReadableParticle() {
		assert VfxQuality.MINIMAL.scaledCount(10) == 3 : VfxQuality.MINIMAL.scaledCount(10);
		assert VfxQuality.MINIMAL.scaledCount(1) == 1 : VfxQuality.MINIMAL.scaledCount(1);
		assert VfxQuality.MINIMAL.scaledCount(0) == 0 : VfxQuality.MINIMAL.scaledCount(0);
	}
}
