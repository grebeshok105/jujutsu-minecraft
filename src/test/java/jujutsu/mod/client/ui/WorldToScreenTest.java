package jujutsu.mod.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Projection contract for {@link WorldToScreen}.
 *
 * <p>Pins the C3 convention with no Minecraft boot: forward follows
 * {@code Entity.calculateViewVector} (identity pitch=0,yaw=0 → {@code (0,0,1)}; yaw=90 →
 * {@code (-1,0,0)}), right = {@code cross(forward, UP)} (at identity = west, {@code (-1,0,0)}),
 * up = {@code cross(right, forward)}. Screen X grows to the right, screen Y grows downward, so a
 * target to the east of a south-facing player lands on the screen's left half.
 *
 * <p>The GUI size is 854×480, FOV 70 — the exact fov=70 projection:
 * {@code tanHalfFov = tan(35°)}.
 */
final class WorldToScreenTest {

	private static final int GUI_WIDTH = 854;
	private static final int GUI_HEIGHT = 480;
	private static final float FOV = 70.0f;
	private static final double EPS = 1.0e-3;

	@Test
	void identityPitchYawAndOnAxisCenter() {
		WorldToScreen.Projection p = WorldToScreen.project(0.0, 0.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible(), "a point 10 blocks ahead on the view axis must be visible");
		assertEquals(427.0, p.x(), EPS, "on-axis point projects to the horizontal center");
		assertEquals(240.0, p.y(), EPS, "on-axis point projects to the vertical center");
		assertEquals(10.0, p.depth(), EPS, "depth is the forward component of the offset");
	}

	@Test
	void eastOfCameraProjectsToScreenLeft() {
		// C3-locked convention: right = cross(forward, UP) = (-1,0,0) at identity, so an east offset
		// (positive relX) has a negative camera-space X and lands on the screen's left half.
		WorldToScreen.Projection p = WorldToScreen.project(10.0, 0.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible());
		assertTrue(p.x() < 427.0, "east of a south-facing player must appear on the screen left");
	}

	@Test
	void westOfCameraProjectsToScreenRight() {
		WorldToScreen.Projection p = WorldToScreen.project(-10.0, 0.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible());
		assertTrue(p.x() > 427.0, "west of a south-facing player must appear on the screen right");
	}

	@Test
	void aboveCameraProjectsToScreenTopHalf() {
		WorldToScreen.Projection p = WorldToScreen.project(0.0, 5.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible());
		assertTrue(p.y() < 240.0, "a point above the eye must appear in the screen's top half");
	}

	@Test
	void behindCameraIsInvisible() {
		WorldToScreen.Projection p = WorldToScreen.project(0.0, 0.0, -10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertFalse(p.visible(), "a point behind the camera must not be drawn");
	}

	@Test
	void depthAtOrBelowMinDepthIsInvisible() {
		assertFalse(WorldToScreen.project(0.0, 0.0, 0.25, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT).visible(),
				"depth 0.25 is below MIN_DEPTH and must be culled");
		assertFalse(WorldToScreen.project(0.0, 0.0, 0.5, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT).visible(),
				"depth exactly MIN_DEPTH is not > MIN_DEPTH and must be culled");
	}

	@Test
	void yawNinetyForwardsNegativeX() {
		// forward = (-1,0,0): a point 10 blocks along -X is dead center and visible; +X is behind.
		WorldToScreen.Projection p = WorldToScreen.project(-10.0, 0.0, 0.0, 0.0f, 90.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible(), "a point on the forward axis at yaw 90 must be visible");
		assertEquals(427.0, p.x(), EPS, "on the forward axis the point is centered horizontally");
		assertEquals(240.0, p.y(), EPS, "on the forward axis the point is centered vertically");
		assertFalse(WorldToScreen.project(10.0, 0.0, 0.0, 0.0f, 90.0f, FOV, GUI_WIDTH, GUI_HEIGHT).visible(),
				"at yaw 90 a point toward +X is behind the camera");
	}

	@Test
	void pitchNinetyForwardsDownwardsAndCullsUpwardPoint() {
		// forward = (0,-1,0) at pitch=+90: looking straight down. A point 10 blocks down is at the
		// on-axis center; a point 10 blocks up has depth -10 and must be culled.
		WorldToScreen.Projection p = WorldToScreen.project(0.0, -10.0, 0.0, 90.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		assertTrue(p.visible());
		assertEquals(427.0, p.x(), EPS);
		assertEquals(240.0, p.y(), EPS);
		assertFalse(WorldToScreen.project(0.0, 10.0, 0.0, 90.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT).visible(),
				"a point above the eye while looking straight down is behind");
	}

	@Test
	void clampToScreenKeepsLeftTopInside() {
		double[] clamped = WorldToScreen.clampToScreen(-50.0, -50.0, 100.0, 100.0, GUI_WIDTH, GUI_HEIGHT, 4.0);
		assertEquals(4.0, clamped[0], EPS, "x below the left margin clamps to the margin");
		assertEquals(4.0, clamped[1], EPS, "y above the top margin clamps to the margin");
	}

	@Test
	void clampToScreenKeepsRightBottomInside() {
		double[] clamped = WorldToScreen.clampToScreen(1000.0, 1000.0, 100.0, 100.0, GUI_WIDTH, GUI_HEIGHT, 4.0);
		assertEquals(GUI_WIDTH - 100.0 - 4.0, clamped[0], EPS, "x beyond the right edge clamps inside");
		assertEquals(GUI_HEIGHT - 100.0 - 4.0, clamped[1], EPS, "y beyond the bottom edge clamps inside");
	}

	@Test
	void clampToScreenCollapsesOverwideBoxToMargin() {
		// w + 2*margin > guiWidth → x collapses to margin regardless of the requested x.
		double[] clamped = WorldToScreen.clampToScreen(200.0, 200.0, 900.0, 100.0, GUI_WIDTH, GUI_HEIGHT, 4.0);
		assertEquals(4.0, clamped[0], EPS, "an over-wide box is pinned to the left margin");
	}

	@Test
	void widerAspectShiftsSamePointRight() {
		// Same offset, doubled width: aspect doubles, ndcX halves, and the point lands further
		// right on the larger viewport.
		WorldToScreen.Projection narrow = WorldToScreen.project(10.0, 0.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH, GUI_HEIGHT);
		WorldToScreen.Projection wide = WorldToScreen.project(10.0, 0.0, 10.0, 0.0f, 0.0f, FOV, GUI_WIDTH * 2, GUI_HEIGHT);
		assertTrue(narrow.visible() && wide.visible());
		assertTrue(wide.x() > narrow.x(),
				"widening the aspect ratio must move the same world point further right on screen");
	}
}
