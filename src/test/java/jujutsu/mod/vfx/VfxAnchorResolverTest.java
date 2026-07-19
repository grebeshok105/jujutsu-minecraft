package jujutsu.mod.vfx;

import net.minecraft.world.phys.Vec3;

public final class VfxAnchorResolverTest {
	private VfxAnchorResolverTest() {}

	public static void main(String[] args) {
		assertStaticCueUsesOrigin();
		assertZeroOffsetUsesLiveAnchor();
		assertLiveAnchorAppliesOffset();
		assertMissingAnchorFallsBackToOrigin();
		System.out.println("VfxAnchorResolverTest passed");
	}

	private static void assertStaticCueUsesOrigin() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, new Vec3(1.0, 2.0, 3.0), VfxCue.NO_ANCHOR, Vec3.ZERO, 1, 0L, 1L, Vec3.ZERO);
		assert VfxAnchorResolver.resolve(cue, ignored -> new Vec3(9.0, 9.0, 9.0)).equals(cue.origin());
	}

	private static void assertZeroOffsetUsesLiveAnchor() {
		VfxCue cue = new VfxCue(NobaraVfxIds.HAMMER, Vec3.ZERO, 17, Vec3.ZERO, 1, 0L, 2L, Vec3.ZERO);
		Vec3 anchor = new Vec3(4.0, 5.0, 6.0);
		assert VfxAnchorResolver.resolve(cue, id -> id == 17 ? anchor : null).equals(anchor);
	}

	private static void assertLiveAnchorAppliesOffset() {
		Vec3 eyeOffset = new Vec3(0.0, 1.62, 0.0);
		VfxCue cue = new VfxCue(NobaraVfxIds.DETONATE, eyeOffset, 17, eyeOffset, 1, 0L, 3L, Vec3.ZERO);
		Vec3 movedFeet = new Vec3(10.0, 64.0, 10.0);
		Vec3 expectedEye = movedFeet.add(eyeOffset);
		assert VfxAnchorResolver.resolve(cue, id -> id == 17 ? movedFeet : null).equals(expectedEye)
				: "live anchor must preserve the cue's eye-height offset";
	}

	private static void assertMissingAnchorFallsBackToOrigin() {
		Vec3 origin = new Vec3(-3.0, 70.0, 8.0);
		VfxCue cue = new VfxCue(NobaraVfxIds.EXPLOSION, origin, 23, new Vec3(0.0, 1.2, 0.0), 1, 0L, 4L, Vec3.ZERO);
		assert VfxAnchorResolver.resolve(cue, ignored -> null).equals(origin);
	}
}
