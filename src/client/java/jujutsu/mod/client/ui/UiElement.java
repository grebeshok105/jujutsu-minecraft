package jujutsu.mod.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base of the custom, self-contained widget system. Not a vanilla {@code AbstractWidget}: the owning
 * screen simply holds a list of these and forwards render/mouse events. Each element tracks its own
 * hover + press animation so subclasses only describe how to paint a given state.
 */
public abstract class UiElement {
	protected float x;
	protected float y;
	protected float width;
	protected float height;
	protected boolean enabled = true;
	protected boolean visible = true;

	protected float hover;
	protected float press;
	private boolean hovered;
	private boolean pressed;

	protected UiElement(float x, float y, float width, float height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
		if (!visible) {
			return;
		}
		hovered = enabled && contains(mouseX, mouseY);
		hover = UiEase.approach(hover, hovered ? 1.0f : 0.0f, 0.35f, deltaTicks);
		press = UiEase.approach(press, pressed && hovered ? 1.0f : 0.0f, 0.5f, deltaTicks);
		draw(graphics, mouseX, mouseY, deltaTicks);
	}

	protected abstract void draw(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks);

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (visible && enabled && button == 0 && contains(mouseX, mouseY)) {
			pressed = true;
			return true;
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressed && button == 0) {
			pressed = false;
			if (visible && enabled && contains(mouseX, mouseY)) {
				onClick();
				return true;
			}
		}
		return false;
	}

	protected void onClick() {}

	public boolean contains(double px, double py) {
		return px >= x && px < x + width && py >= y && py < y + height;
	}

	public boolean isHovered() {
		return hovered;
	}

	public void setEnabled(boolean value) {
		this.enabled = value;
	}

	public void setVisible(boolean value) {
		this.visible = value;
	}
}
