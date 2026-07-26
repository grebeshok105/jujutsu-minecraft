package jujutsu.mod.client.rich.screens.clickgui.impl;

/** ClickGui panel drag geometry: grab region, one-to-one travel, release, regrab, and clamping. */
public final class DragHandlerTest {
	private static final float HANDLE_X = 100f;
	private static final float HANDLE_Y = 50f;
	private static final float HANDLE_WIDTH = 400f;
	private static final float HANDLE_HEIGHT = 38f;

	public static void main(String[] args) {
		assertPressOutsideTheHandleNeverGrabs();
		assertDragFollowsTheCursorOneToOne();
		assertReleaseFreezesThePanel();
		assertRegrabContinuesWithoutJumping();
		assertClampConfinesBothAxes();
		assertResetRecentersAndDropsTheGrab();
		System.out.println("DragHandlerTest passed");
	}

	private static void assertPressOutsideTheHandleNeverGrabs() {
		DragHandler handler = handler();
		assert !grab(handler, HANDLE_X - 1f, HANDLE_Y + 10f) : "A press left of the handle must not grab";
		assert !grab(handler, HANDLE_X + HANDLE_WIDTH + 1f, HANDLE_Y + 10f) : "A press right of the handle must not grab";
		assert !grab(handler, HANDLE_X + 10f, HANDLE_Y - 1f) : "A press above the handle must not grab";
		// The band below the handle is where the tabs, cards and confirm button live.
		assert !grab(handler, HANDLE_X + 10f, HANDLE_Y + HANDLE_HEIGHT + 1f) : "A press below the handle must not grab";
		assert !handler.isDragging() : "A refused press must leave the handler idle";
		handler.drag(HANDLE_X + 200f, HANDLE_Y + 200f);
		assert handler.getOffsetX() == 0f && handler.getOffsetY() == 0f : "Motion without a grab must not move the panel";
	}

	private static void assertDragFollowsTheCursorOneToOne() {
		DragHandler handler = handler();
		assert grab(handler, HANDLE_X + 5f, HANDLE_Y + 5f) : "A press inside the handle must grab";
		assert handler.isDragging() : "A successful grab must report as dragging";
		handler.drag(HANDLE_X + 5f + 37f, HANDLE_Y + 5f - 21f);
		assert handler.getOffsetX() == 37f : "Horizontal travel must match the cursor exactly, got " + handler.getOffsetX();
		assert handler.getOffsetY() == -21f : "Vertical travel must match the cursor exactly, got " + handler.getOffsetY();
		// Measured from the grab point, so a skipped motion event cannot make the panel creep.
		handler.drag(HANDLE_X + 5f + 37f, HANDLE_Y + 5f - 21f);
		assert handler.getOffsetX() == 37f && handler.getOffsetY() == -21f : "A repeated position must not double-apply";
	}

	private static void assertReleaseFreezesThePanel() {
		DragHandler handler = handler();
		grab(handler, HANDLE_X + 5f, HANDLE_Y + 5f);
		handler.drag(HANDLE_X + 15f, HANDLE_Y + 5f);
		handler.endDrag();
		assert !handler.isDragging() : "Release must clear the grab";
		handler.drag(HANDLE_X + 900f, HANDLE_Y + 900f);
		assert handler.getOffsetX() == 10f && handler.getOffsetY() == 0f : "Motion after release must be ignored";
	}

	private static void assertRegrabContinuesWithoutJumping() {
		DragHandler handler = handler();
		grab(handler, HANDLE_X, HANDLE_Y);
		handler.drag(HANDLE_X + 60f, HANDLE_Y);
		handler.endDrag();
		// Second grab from a far-away cursor: the panel must stay put until the cursor actually moves.
		assert grab(handler, HANDLE_X + 360f, HANDLE_Y + 20f) : "A second press inside the handle must grab again";
		assert handler.getOffsetX() == 60f : "A regrab must not move the panel by itself";
		handler.drag(HANDLE_X + 365f, HANDLE_Y + 20f);
		assert handler.getOffsetX() == 65f : "A regrab must continue from the current offset, got " + handler.getOffsetX();
	}

	private static void assertClampConfinesBothAxes() {
		DragHandler handler = handler();
		grab(handler, HANDLE_X, HANDLE_Y);
		handler.drag(HANDLE_X + 5000f, HANDLE_Y - 5000f);
		handler.clampTo(-120f, 140f, -80f, 160f);
		assert handler.getOffsetX() == 140f : "Rightward travel must stop at the maximum, got " + handler.getOffsetX();
		assert handler.getOffsetY() == -80f : "Upward travel must stop at the minimum, got " + handler.getOffsetY();
		// Clamping the live target too, so the eased recenter cannot spring back out of bounds.
		handler.endDrag();
		handler.update();
		assert handler.getOffsetX() <= 140f && handler.getOffsetY() >= -80f : "Easing must not leave the clamped range";
		DragHandler inverted = handler();
		inverted.clampTo(30f, -30f, 40f, -40f);
		assert inverted.getOffsetX() == 30f && inverted.getOffsetY() == 40f : "An empty range must collapse to its minimum";
	}

	private static void assertResetRecentersAndDropsTheGrab() {
		DragHandler handler = handler();
		grab(handler, HANDLE_X, HANDLE_Y);
		handler.drag(HANDLE_X + 42f, HANDLE_Y + 42f);
		handler.reset();
		assert handler.getOffsetX() == 0f && handler.getOffsetY() == 0f : "Reset must recenter the panel";
		assert !handler.isDragging() : "Reset must drop the grab";
	}

	private static DragHandler handler() {
		return new DragHandler();
	}

	private static boolean grab(DragHandler handler, double mouseX, double mouseY) {
		return handler.startDrag(mouseX, mouseY, HANDLE_X, HANDLE_Y, HANDLE_WIDTH, HANDLE_HEIGHT);
	}
}
