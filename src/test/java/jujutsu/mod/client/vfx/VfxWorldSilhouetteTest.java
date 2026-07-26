package jujutsu.mod.client.vfx;

/**
 * The afterimage draws a body out of ribbons from numbers that travelled in a cue. These are the four
 * places where a wrong number stops being a wrong number and becomes something visibly broken on screen.
 */
public final class VfxWorldSilhouetteTest {
	private VfxWorldSilhouetteTest() {}

	public static void main(String[] args) {
		assertMissingDimensionsStillDrawAPerson();
		assertNoBodyBecomesAScarecrow();
		assertTheResidueHoldsBeforeItFades();
		assertFacingWidensAndNarrowsWithoutCollapsing();
		System.out.println("VfxWorldSilhouetteTest passed");
	}

	private static void assertMissingDimensionsStillDrawAPerson() {
		// A cue from an older server, or one whose offset was repurposed, must not render a zero-size figure
		// -- which is invisible, and therefore indistinguishable from the effect not working at all.
		assert VfxWorldChannel.silhouetteWidth(0.0) == 0.6f : "a dimensionless cue must fall back to a person's width";
		assert VfxWorldChannel.silhouetteHeight(0.0) == 1.8f : "a dimensionless cue must fall back to a person's height";
		assert VfxWorldChannel.silhouetteWidth(0.05) == 0.6f : "the fallback threshold is inclusive";
		assert VfxWorldChannel.silhouetteWidth(0.6) == 0.6f : "an ordinary player must be drawn at its own size";
		assert VfxWorldChannel.silhouetteHeight(1.8) == 1.8f : "an ordinary player must be drawn at its own size";
	}

	private static void assertNoBodyBecomesAScarecrow() {
		// Todo can swap with anything eligible. Without the ceiling a large mob leaves a glowing figure
		// several blocks tall standing in the world for four ticks.
		assert VfxWorldChannel.silhouetteWidth(9.0) == 2.0f : "a huge body must be clamped to a readable width";
		assert VfxWorldChannel.silhouetteHeight(9.0) == 4.0f : "a huge body must be clamped to a readable height";
	}

	private static void assertTheResidueHoldsBeforeItFades() {
		assert VfxWorldChannel.silhouetteAlpha(0.0f) == 1.0f : "the residue must be fully there on the first tick";
		// Without the hold the figure is already fading on the frame it appears, and a four-tick effect that
		// starts at three quarters alpha simply is not seen.
		assert VfxWorldChannel.silhouetteAlpha(0.25f) == 1.0f : "the residue must hold for the first quarter";
		assert VfxWorldChannel.silhouetteAlpha(1.0f) == 0.0f : "the residue must be gone at the end of its life";
		assert VfxWorldChannel.silhouetteAlpha(-1.0f) == 1.0f : "a negative age must clamp rather than overshoot";
		assert VfxWorldChannel.silhouetteAlpha(2.0f) == 0.0f : "an overrun age must clamp rather than go negative";

		float previous = Float.MAX_VALUE;
		for (int step = 0; step <= 20; step++) {
			float alpha = VfxWorldChannel.silhouetteAlpha(step / 20.0f);
			assert alpha <= previous : "the residue must never brighten again partway through";
			assert alpha >= 0.0f : "the residue must never go negative";
			previous = alpha;
		}
	}

	private static void assertFacingWidensAndNarrowsWithoutCollapsing() {
		// Cosine, not sine. With sine the figure would be widest edge-on and narrowest facing you, which is
		// exactly inverted and reads as the body turning the wrong way as the camera moves.
		assert Math.abs(VfxWorldChannel.facingScale(0.0f, 0.0f) - 1.0f) < 1.0E-5f
				: "a body facing the camera must be at full shoulder width";
		assert Math.abs(VfxWorldChannel.facingScale(180.0f, 0.0f) - 1.0f) < 1.0E-5f
				: "a body facing away is just as wide as one facing you";
		assert Math.abs(VfxWorldChannel.facingScale(90.0f, 0.0f) - 0.45f) < 1.0E-5f
				: "an edge-on body must narrow to the floor, not to nothing";

		for (int degrees = 0; degrees < 360; degrees++) {
			float scale = VfxWorldChannel.facingScale(degrees, 37.0f);
			assert scale >= 0.45f && scale <= 1.0f
					: "the figure must never collapse to a line nor exceed its own width at " + degrees;
		}
	}
}
