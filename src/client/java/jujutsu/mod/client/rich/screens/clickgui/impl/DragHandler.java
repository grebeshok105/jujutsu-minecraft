package jujutsu.mod.client.rich.screens.clickgui.impl;

import org.lwjgl.glfw.GLFW;

/**
 * Panel drag state for the ClickGui. Pure geometry in screen pixels: the screen owns the
 * input events and feeds them in, so this never polls GLFW and never needs a live window.
 */
public class DragHandler {
	private static final float ANIMATION_SPEED = 10f;

	private float offsetX;
	private float offsetY;
	private float targetOffsetX;
	private float targetOffsetY;
	private boolean dragging;
	private double dragStartX;
	private double dragStartY;
	private float dragStartOffsetX;
	private float dragStartOffsetY;
	private long lastUpdateTime = System.currentTimeMillis();

	public float getOffsetX() {
		return offsetX;
	}

	public float getOffsetY() {
		return offsetY;
	}

	public boolean isDragging() {
		return dragging;
	}

	/** Eases a programmatic recenter. A live drag writes the offset directly instead. */
	public void update() {
		long currentTime = System.currentTimeMillis();
		float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
		lastUpdateTime = currentTime;
		if (dragging) {
			return;
		}
		offsetX += (targetOffsetX - offsetX) * Math.min(1f, ANIMATION_SPEED * deltaTime);
		offsetY += (targetOffsetY - offsetY) * Math.min(1f, ANIMATION_SPEED * deltaTime);
	}

	/** Grabs the panel when the press lands inside the given surface. */
	public boolean startDrag(double mouseX, double mouseY, float grabX, float grabY, float grabWidth, float grabHeight) {
		if (mouseX < grabX || mouseX > grabX + grabWidth || mouseY < grabY || mouseY > grabY + grabHeight) {
			return false;
		}
		dragging = true;
		dragStartX = mouseX;
		dragStartY = mouseY;
		dragStartOffsetX = offsetX;
		dragStartOffsetY = offsetY;
		return true;
	}

	/**
	 * Follows the cursor one screen pixel per mouse pixel. The offset is always measured from the
	 * grab point rather than accumulated, so a dropped motion event cannot make the panel creep.
	 */
	public void drag(double mouseX, double mouseY) {
		if (!dragging) {
			return;
		}
		targetOffsetX = dragStartOffsetX + (float) (mouseX - dragStartX);
		targetOffsetY = dragStartOffsetY + (float) (mouseY - dragStartY);
		offsetX = targetOffsetX;
		offsetY = targetOffsetY;
	}

	/** Confines the offset to what the caller considers reachable, for drags and window resizes alike. */
	public void clampTo(float minOffsetX, float maxOffsetX, float minOffsetY, float maxOffsetY) {
		offsetX = clamp(offsetX, minOffsetX, maxOffsetX);
		offsetY = clamp(offsetY, minOffsetY, maxOffsetY);
		targetOffsetX = clamp(targetOffsetX, minOffsetX, maxOffsetX);
		targetOffsetY = clamp(targetOffsetY, minOffsetY, maxOffsetY);
	}

	private static float clamp(float value, float min, float max) {
		if (min > max) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	public void endDrag() {
		dragging = false;
	}

	public void reset() {
		targetOffsetX = 0;
		targetOffsetY = 0;
		offsetX = 0;
		offsetY = 0;
		dragging = false;
	}

	public boolean isResetNeeded(int key, int modifiers) {
		return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL;
	}
}
