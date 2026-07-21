package jujutsu.mod.client.rich.screens.clickgui;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import jujutsu.mod.client.rich.IMinecraft;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.screens.clickgui.impl.DragHandler;
import jujutsu.mod.client.rich.screens.clickgui.impl.background.BackgroundComponent;
import jujutsu.mod.client.rich.screens.clickgui.impl.character.CharacterRosterPanel;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.rich.util.animations.Direction;
import jujutsu.mod.client.rich.util.animations.GuiAnimation;
import jujutsu.mod.client.rich.util.math.FrameRateCounter;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.gif.GifRender;
import jujutsu.mod.client.rich.util.render.shader.Scissor;

/**
 * ClickGui — character vessel menu (N). Neon dashboard removed.
 * Characters tab hosts the roster panel with smooth themed accents.
 */
public class ClickGui extends Screen implements IMinecraft {
	public static ClickGui INSTANCE;
	private static final int FIXED_GUI_SCALE = 2;

	private final BackgroundComponent background = new BackgroundComponent();
	private final CharacterRosterPanel characterRoster = new CharacterRosterPanel();
	private final DragHandler dragHandler = new DragHandler();
	private ModuleCategory selectedCategory = ModuleCategory.COMBAT;

	private final GuiAnimation openAnimation = new GuiAnimation();
	private boolean closing;

	public ClickGui() {
		super(Component.literal("MenuScreen"));
		INSTANCE = this;
	}

	public boolean isClosing() {
		return closing;
	}

	@Override
	protected void init() {
		super.init();
		closing = false;
		openAnimation.setMs(280).setValue(1.0).setDirection(Direction.FORWARDS).reset();
		background.setSearchActive(false);
		characterRoster.syncFromClient();
	}

	public void openGui() {
		if (mc.screen == null) {
			closing = false;
			openAnimation.setMs(280).setValue(1.0).setDirection(Direction.FORWARDS).reset();
			characterRoster.syncFromClient();
			mc.setScreen(this);
		}
	}

	@Override
	public void tick() {
		GifRender.tick();
		float deltaTicks = 1f;
		ClickGuiTheme.tick(deltaTicks);
		characterRoster.tick(deltaTicks);
		super.tick();
	}

	private float scaleFactor() {
		int guiScale = Math.max(1, (int) mc.getWindow().getGuiScale());
		return (float) FIXED_GUI_SCALE / guiScale;
	}

	private boolean isCharactersCategory(ModuleCategory category) {
		return category == ModuleCategory.COMBAT;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		float animValue = openAnimation.getOutput().floatValue();
		float deltaTicks = Math.max(0.05f, Math.min(3f, delta));
		ClickGuiTheme.tick(deltaTicks);
		characterRoster.tick(deltaTicks);

		Render2D.beginFrame();

		int dimAlpha = (int) ((110 + 40 * ClickGuiTheme.warm()) * animValue);
		if (dimAlpha > 0) {
			// Warm scrim when Nobara theme is active.
			int r = Math.round(8 + 18 * ClickGuiTheme.warm());
			int g = Math.round(6 + 8 * ClickGuiTheme.warm());
			int b = Math.round(5 + 2 * (1f - ClickGuiTheme.warm()));
			Render2D.rect(0, 0, 5000, 5000, new Color(r, g, b, dimAlpha).getRGB(), 0);
		}

		float mul = Render2D.getScaleMultiplier();
		float bgX = (this.width - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX() / mul;
		float bgY = (this.height - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY() / mul;

		float mx = mouseX;
		float my = mouseY;
		if (!closing) {
			dragHandler.update(mx, my);
		}

		float yOffset = closing ? (1f - animValue) * 28f : (1f - animValue) * -12f;
		bgY += yOffset;
		float alphaMultiplier = animValue;

		background.render(context, bgX, bgY, selectedCategory, delta, alphaMultiplier);
		background.renderCategoryPanel(bgX, bgY, alphaMultiplier);
		background.renderHeader(bgX, bgY, selectedCategory, alphaMultiplier);
		background.renderCategoryNames(bgX, bgY, selectedCategory, alphaMultiplier);

		// Full content area for Characters roster (no split module/settings columns).
		float contentX = bgX + 92f;
		float contentY = bgY + 38f;
		float contentW = BackgroundComponent.BG_WIDTH - 100f;
		float contentH = BackgroundComponent.BG_HEIGHT - 46f;

		if (isCharactersCategory(selectedCategory)) {
			characterRoster.render(context, contentX, contentY, contentW, contentH, mx, my, alphaMultiplier);
		}

		Scissor.reset();
		Render2D.endFrame();

		if (closing && animValue <= 0.01f) {
			mc.setScreen(null);
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// custom dim in render
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (closing) return false;
		float mx = (float) mouseX;
		float my = (float) mouseY;
		float mul = Render2D.getScaleMultiplier();
		float bgX = (this.width - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX() / mul;
		float bgY = (this.height - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY() / mul;

		ModuleCategory cat = background.getCategoryAtPosition(mx, my, bgX, bgY);
		if (cat != null) {
			selectedCategory = cat;
			if (isCharactersCategory(cat)) {
				characterRoster.syncFromClient();
			}
			return true;
		}

		if (isCharactersCategory(selectedCategory)) {
			if (characterRoster.mouseClicked(mx, my, button)) {
				return true;
			}
		}

		if (button == 2 && dragHandler.startDrag(mx, my, bgX, bgY, BackgroundComponent.BG_WIDTH, BackgroundComponent.BG_HEIGHT)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (closing) return false;
		dragHandler.endDrag();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_N) {
			close();
			return true;
		}
		if (closing) return false;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			openAnimation.setDirection(Direction.BACKWARDS);
			openAnimation.reset();
		}
	}

	public void close() {
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
