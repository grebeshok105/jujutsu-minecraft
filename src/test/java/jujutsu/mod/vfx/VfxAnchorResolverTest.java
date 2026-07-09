package jujutsu.mod.vfx;

import net.minecraft.world.phys.Vec3;

public final class VfxAnchorResolverTest {
	private VfxAnchorResolverTest() {}

	public static void main(String[] args) {
		assertStaticCueUsesOrigin();
		assertLiveAnchorOverridesOrigin();
		assertMissingAnchorFallsBackToOrigin();
		System.out.println("VfxAnchorResolverTest passed");
	}

	private static void assertStaticCueUsesOrigin() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, new Vec3(1.0, 2.0, 3.0), VfxCue.NO_ANCHOR, 1, 0L, 1L);
		assert VfxAnchorResolver.resolve(cue, ignored -> new Vec3(9.0, 9.0, 9.0)).equals(cue.origin());
	}

	private static void assertLiveAnchorOverridesOrigin() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, Vec3.ZERO, 17, 1, 0L, 2L);
		Vec3 anchor = new Vec3(4.0, 5.0, 6.0);
		assert VfxAnchorResolver.resolve(cue, id -> id == 17 ? anchor : null).equals(anchor);
	}

	private static void assertMissingAnchorFallsBackToOrigin() {
		Vec3 origin = new Vec3(-3.0, 70.0, 8.0);
		VfxCue cue = new VfxCue(NobaraVfxIds.EXPLOSION, origin, 23, 1, 0L, 3L);
		assert VfxAnchorResolver.resolve(cue, ignored -> null).equals(origin);
	}
}
