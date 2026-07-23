package jujutsu.mod.client.rich.screens.clickgui.impl;

import org.lwjgl.glfw.GLFW;
import jujutsu.mod.client.rich.IMinecraft;

public class DragHandler implements IMinecraft {
	private float offsetX;
	private float offsetY;
	private float targetOffsetX;
	private float targetOffsetY;
	private boolean dragging;
	private double dragStartX;
	private double dragStartY;
	private float dragStartOffsetX;
	private float dragStartOffsetY;
	private static final float ANIMATION_SPEED = 10f;
	private long lastUpdateTime = System.currentTimeMillis();

	public float getOffsetX() {
		return offsetX;
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void update(double mouseX, double mouseY) {
		long currentTime = System.currentTimeMillis();
		float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
		lastUpdateTime = currentTime;

		if (dragging) {
			if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) {
				dragging = false;
			} else {
				targetOffsetX = dragStartOffsetX + (float) (mouseX - dragStartX);
				targetOffsetY = dragStartOffsetY + (float) (mouseY - dragStartY);
				offsetX = targetOffsetX;
				offsetY = targetOffsetY;
			}
		} else {
			offsetX += (targetOffsetX - offsetX) * Math.min(1f, ANIMATION_SPEED * deltaTime);
			offsetY += (targetOffsetY - offsetY) * Math.min(1f, ANIMATION_SPEED * deltaTime);
		}
	}

	public boolean startDrag(double mouseX, double mouseY, float bgX, float bgY, float bgW, float bgH) {
		if (mouseX >= bgX && mouseX <= bgX + bgW && mouseY >= bgY && mouseY <= bgY + bgH) {
			dragging = true;
			dragStartX = mouseX;
			dragStartY = mouseY;
			dragStartOffsetX = offsetX;
			dragStartOffsetY = offsetY;
			return true;
		}
		return false;
	}

	public void endDrag() {
		dragging = false;
	}

	public void reset() {
		targetOffsetX = 0;
		targetOffsetY = 0;
		offsetX = 0;
		offsetY = 0;
	}

	public boolean isResetNeeded(int key, int modifiers) {
		return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL;
	}
}
