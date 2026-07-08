package jujutsu.mod.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for hand-drawn screens. Manages a list of {@link UiElement}s, a global open/close animation
 * factor, and a scrim, so concrete screens focus purely on layout + painting. Deliberately does NOT
 * use vanilla widgets — Minecraft is only the render + input host.
 */
public abstract class UiScreen extends Screen {
	protected final List<UiElement> elements = new ArrayList<>();
	protected float openAnim;
	private boolean closing;
	private long closeStartedMillis;
	private long lastFrameNanos;

	protected UiScreen(Component title) {
		super(title);
	}

	protected void add(UiElement element) {
		elements.add(element);
	}

	protected abstract void layout();

	protected abstract void paint(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks, float anim);

	@Override
	protected void init() {
		elements.clear();
		openAnim = 0.0f;
		closing = false;
		lastFrameNanos = System.nanoTime();
		layout();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float delta = frameDeltaTicks();
		float target = closing ? 0.0f : 1.0f;
		openAnim = UiEase.approach(openAnim, target, 0.4f, delta);
		if (closing && System.currentTimeMillis() - closeStartedMillis > UiTheme.OPEN_MS) {
			super.onClose();
			return;
		}
		float anim = closing ? UiEase.inOutCubic(openAnim) : UiEase.outCubic(openAnim);

		int scrimAlpha = (int) (((UiTheme.SCRIM >>> 24) & 0xff) * anim) << 24;
		graphics.fill(0, 0, this.width, this.height, scrimAlpha | (UiTheme.SCRIM & 0x00FFFFFF));

		paint(graphics, mouseX, mouseY, delta, anim);
		for (UiElement element : elements) {
			element.render(graphics, mouseX, mouseY, delta);
		}
	}

	private float frameDeltaTicks() {
		long now = System.nanoTime();
		long elapsed = lastFrameNanos == 0L ? 0L : now - lastFrameNanos;
		lastFrameNanos = now;
		float delta = elapsed <= 0L ? 1.0f : elapsed / 50_000_000.0f;
		return Math.max(0.05f, Math.min(3.0f, delta));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (UiElement element : elements) {
			if (element.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean handled = false;
		for (UiElement element : elements) {
			handled |= element.mouseReleased(mouseX, mouseY, button);
		}
		return handled || super.mouseReleased(mouseX, mouseY, button);
	}

	protected void animateClose() {
		if (!closing) {
			closing = true;
			closeStartedMillis = System.currentTimeMillis();
		}
	}

	@Override
	public void onClose() {
		animateClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
