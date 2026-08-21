package jujutsu.mod.client.ui;

/**
 * Pure world-to-screen projection for screen-space target overlays.
 *
 * <p>This class deliberately has no Minecraft imports: the mathematics operates on plain primitives
 * so the whole projection is unit-testable without booting the game (contract C3).
 *
 * <p>Convention (pinned by {@code WorldToScreenTest}):
 * <ul>
 *   <li>Forward = {@code Entity.calculateViewVector(pitch, yaw)} exactly: rotate {@code (0,0,1)}
 *       around X by {@code -pitch}, then around Y by {@code -yaw} (radians), i.e.
 *       {@code forward = (-sin(yaw)·cos(pitch), -sin(pitch), cos(yaw)·cos(pitch))}.</li>
 *   <li>Right = normalize(cross(forward, UP=(0,1,0))), falling back to EAST=(1,0,0) when the cross
 *       is degenerate (pitch at ±90°); Up = cross(right, forward).</li>
 *   <li>Camera space: {@code cx = rel·right}, {@code cy = rel·up}, {@code depth = rel·forward}.</li>
 *   <li>Visible iff {@code depth > MIN_DEPTH}.</li>
 *   <li>Screen: {@code x = (ndcX·0.5+0.5)·width}, {@code y = (0.5−ndcY·0.5)·height}, with
 *       {@code ndcX = cx/(depth·tanHalfFov·aspect)}, {@code ndcY = cy/(depth·tanHalfFov)}.</li>
 * </ul>
 */
public final class WorldToScreen {

	/** Targets at or behind this depth (relative to the camera) are not drawn. */
	public static final double MIN_DEPTH = 0.5;

	/** Screen-space outcome of projecting one world-space offset. */
	public record Projection(boolean visible, double x, double y, double depth) {}

	private static final double UP_X = 0.0;
	private static final double UP_Y = 1.0;
	private static final double UP_Z = 0.0;

	private static final double EAST_X = 1.0;
	private static final double EAST_Y = 0.0;
	private static final double EAST_Z = 0.0;

	private WorldToScreen() {}

	/**
	 * Projects the camera-relative offset {@code (relX, relY, relZ)} (world units, +Y up) onto the
	 * screen of the given size, using the player's look {@code pitch}/{@code yaw} (degrees, exactly
	 * {@code Entity.calculateViewVector}) and a symmetric vertical FOV.
	 */
	public static Projection project(double relX, double relY, double relZ,
			float pitchDeg, float yawDeg, float fovDeg, int guiWidth, int guiHeight) {
		// Forward, exactly as Entity.calculateViewVector: f = pitch in radians, g = -yaw in radians,
		// forward = (sin(g)·cos(f), -sin(f), cos(g)·cos(f)).
		double pitchRad = Math.toRadians(pitchDeg);
		double yawRad = Math.toRadians(yawDeg);
		double sinPitch = Math.sin(pitchRad);
		double cosPitch = Math.cos(pitchRad);
		double sinYaw = Math.sin(yawRad);
		double cosYaw = Math.cos(yawRad);
		double fwdX = -sinYaw * cosPitch;
		double fwdY = -sinPitch;
		double fwdZ = cosYaw * cosPitch;

		// Right = normalize(cross(forward, UP)); cross((fwdX,fwdY,fwdZ),(0,1,0)) = (-fwdZ, 0, fwdX).
		double crossX = -fwdZ;
		double crossY = 0.0;
		double crossZ = fwdX;
		double crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
		double rightX;
		double rightY;
		double rightZ;
		if (crossLen < 1.0e-6) {
			rightX = EAST_X;
			rightY = EAST_Y;
			rightZ = EAST_Z;
		} else {
			rightX = crossX / crossLen;
			rightY = crossY / crossLen;
			rightZ = crossZ / crossLen;
		}

		// Up = cross(right, forward).
		double upX = rightY * fwdZ - rightZ * fwdY;
		double upY = rightZ * fwdX - rightX * fwdZ;
		double upZ = rightX * fwdY - rightY * fwdX;

		double depth = relX * fwdX + relY * fwdY + relZ * fwdZ;
		if (depth <= MIN_DEPTH) {
			return new Projection(false, 0.0, 0.0, depth);
		}

		double camX = relX * rightX + relY * rightY + relZ * rightZ;
		double camY = relX * upX + relY * upY + relZ * upZ;

		double tanHalfFov = Math.tan(Math.toRadians(fovDeg) / 2.0);
		double aspect = (double) guiWidth / guiHeight;

		double ndcX = camX / (depth * tanHalfFov * aspect);
		double ndcY = camY / (depth * tanHalfFov);

		double screenX = (ndcX * 0.5 + 0.5) * guiWidth;
		double screenY = (0.5 - ndcY * 0.5) * guiHeight;

		return new Projection(true, screenX, screenY, depth);
	}

	/**
	 * Clamps the top-left corner {@code (x, y)} of a {@code w×h} overlay so the whole box stays on
	 * screen with the given {@code margin}: {@code x ∈ [margin, guiWidth−w−margin]} (y analogously).
	 * When the box is wider than the screen (plus margins) the x coordinate collapses to
	 * {@code margin} (y behaves the same for height).
	 *
	 * @return {@code double[2]} = {@code {x, y}}
	 */
	public static double[] clampToScreen(double x, double y, double w, double h,
			int guiWidth, int guiHeight, double margin) {
		double clampedX;
		if (w + 2.0 * margin > guiWidth) {
			clampedX = margin;
		} else {
			double minX = margin;
			double maxX = guiWidth - w - margin;
			clampedX = Math.max(minX, Math.min(x, maxX));
		}
		double clampedY;
		if (h + 2.0 * margin > guiHeight) {
			clampedY = margin;
		} else {
			double minY = margin;
			double maxY = guiHeight - h - margin;
			clampedY = Math.max(minY, Math.min(y, maxY));
		}
		return new double[] {clampedX, clampedY};
	}
}
