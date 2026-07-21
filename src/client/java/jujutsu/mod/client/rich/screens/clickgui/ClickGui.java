package jujutsu.mod.client.rich.screens.clickgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import jujutsu.mod.client.rich.IMinecraft;
import jujutsu.mod.client.rich.Initialization;
import jujutsu.mod.client.rich.modules.module.ModuleStructure;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.screens.clickgui.impl.DragHandler;
import jujutsu.mod.client.rich.screens.clickgui.impl.autobuy.autobuyui.AutoBuyRenderer;
import jujutsu.mod.client.rich.screens.clickgui.impl.background.BackgroundComponent;
import jujutsu.mod.client.rich.screens.clickgui.impl.configs.ConfigsRenderer;
import jujutsu.mod.client.rich.screens.clickgui.impl.module.ModuleComponent;
import jujutsu.mod.client.rich.screens.clickgui.impl.settingsrender.BindComponent;
import jujutsu.mod.client.rich.util.animations.Direction;
import jujutsu.mod.client.rich.util.animations.GuiAnimation;
import jujutsu.mod.client.rich.util.interfaces.AbstractSettingComponent;
import jujutsu.mod.client.rich.util.math.FrameRateCounter;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.gif.GifRender;
import jujutsu.mod.client.rich.util.render.shader.Scissor;

/**
 * Port of rich.screens.clickgui.ClickGui (1.21.8 input API).
 * Visual structure is the original Rich clickgui.
 */
public class ClickGui extends Screen implements IMinecraft {
	public static ClickGui INSTANCE;
	private static final int FIXED_GUI_SCALE = 2;

	private final BackgroundComponent background = new BackgroundComponent();
	private final ModuleComponent moduleComponent = new ModuleComponent();
	private final AutoBuyRenderer autoBuyRenderer = new AutoBuyRenderer();
	private final ConfigsRenderer configsRenderer = new ConfigsRenderer();
	private final DragHandler dragHandler = new DragHandler();
	private ModuleCategory selectedCategory = ModuleCategory.COMBAT;

	private final GuiAnimation openAnimation = new GuiAnimation();
	private boolean closing;
	private float lastDelta;
	private int lastMouseX;
	private int lastMouseY;

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
		openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();
		background.setSearchActive(false);
		autoBuyRenderer.resetForClose();
		updateModules();
	}

	private void updateModules() {
		List<ModuleStructure> modules = new ArrayList<>();
		try {
			var repo = Initialization.getInstance().getManager().getModuleRepository();
			if (repo != null) {
				for (ModuleStructure m : repo.modules()) {
					if (m.getCategory() == selectedCategory) {
						modules.add(m);
					}
				}
			}
		} catch (Exception ignored) {}
		moduleComponent.updateModules(modules, selectedCategory);
	}

	public void openGui() {
		if (mc.screen == null) {
			closing = false;
			openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();
			mc.setScreen(this);
		}
	}

	@Override
	public void tick() {
		GifRender.tick();
		moduleComponent.tick();
		super.tick();
	}

	private float[] calculateBackground(float scale) {
		int vw = mc.getWindow().getWidth() / FIXED_GUI_SCALE;
		int vh = mc.getWindow().getHeight() / FIXED_GUI_SCALE;
		float bgX = (vw - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX();
		float bgY = (vh - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY();
		return new float[] {bgX, bgY, vw, vh};
	}

	private float scaleFactor() {
		int guiScale = Math.max(1, (int) mc.getWindow().getGuiScale());
		return (float) FIXED_GUI_SCALE / guiScale;
	}

	private double[] toFixed(double mouseX, double mouseY) {
		float scale = scaleFactor();
		return new double[] {mouseX / scale, mouseY / scale};
	}

	private boolean isAnyBindListening() {
		for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
			if (c instanceof BindComponent bind && bind.isListening()) {
				return true;
			}
		}
		return false;
	}

	private boolean isModuleCategory(ModuleCategory category) {
		return category != ModuleCategory.AUTOBUY;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		lastDelta = delta;
		lastMouseX = mouseX;
		lastMouseY = mouseY;

		float scrollSpeed = Math.min(1f, 60f / Math.max(FrameRateCounter.INSTANCE.getFps(), 1));
		float animValue = openAnimation.getOutput().floatValue();

		Render2D.beginFrame();

		int dimAlpha = (int) (125 * animValue);
		if (dimAlpha > 0) {
			Render2D.rect(0, 0, 5000, 5000, new Color(0, 0, 0, dimAlpha).getRGB(), 0);
		}

		float scale = scaleFactor();
		double[] m = toFixed(mouseX, mouseY);
		float mx = (float) m[0];
		float my = (float) m[1];

		if (!closing) {
			dragHandler.update(mx, my);
		}

		// scale matrix not available the same way — draw in fixed-scale coords mapped via getScaleMultiplier
		// Rich divides mouse by scale and draws in FIXED_GUI_SCALE space; our window uses gui-scaled coords.
		// Convert: fixed coords * (guiScale/FIXED) = gui coords
		float toGui = 1f / scale; // FIXED/gui -> wait: scale = FIXED/gui, so fixed * (gui/FIXED) = fixed / scale? 
		// mouse: mx_fixed = mouseX_gui / scale where scale = FIXED/guiScale
		// draw at fixed coords on screen that is gui-scaled: x_gui = x_fixed * (FIXED/guiScale)? 
		// Actually: framebuffer / guiScale = gui width. Rich uses framebuffer/2 = fixed width.
		// If guiScale==2, fixed==gui. If guiScale==3, fixed coords are larger than gui.
		// For simplicity when guiScale is 2 (common), coords match. Apply multiplier:
		float mul = Render2D.getScaleMultiplier(); // FIXED / currentGuiScale
		// Draw positions from Rich are in fixed space. Map to gui: fixed * (guiScale/FIXED) = fixed / mul? 
		// mul = FIXED/guiScale, so fixed * (1/mul) = fixed * guiScale/FIXED = gui coords.
		// Actually Render2D draws in current GuiGraphics space which is gui-scaled.
		// So we need to scale all bg coords: x_gui = x_fixed * (guiScale / FIXED) = x_fixed / mul

		float[] bg = calculateBackground(scale);
		float bgX = bg[0] / mul;
		float bgY = bg[1] / mul;
		// Wait - if we divide mouse by scale to get fixed, and draw fixed as if gui when mul=1...
		// Simpler approach: use gui-scaled dimensions equal to BG when window large enough
		// Force draw in gui space with fixed BG size centered:
		bgX = (this.width - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX() / mul;
		bgY = (this.height - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY() / mul;
		// mouse in gui space already:
		mx = mouseX;
		my = mouseY;

		float yOffset = closing ? (1f - animValue) * 30f : (1f - animValue) * -15f;
		bgY += yOffset;
		float alphaMultiplier = animValue;

		background.render(context, bgX, bgY, selectedCategory, delta, alphaMultiplier);
		background.renderCategoryPanel(bgX, bgY, alphaMultiplier);
		background.renderHeader(bgX, bgY, selectedCategory, alphaMultiplier);
		background.renderCategoryNames(bgX, bgY, selectedCategory, alphaMultiplier);

		float mlX = bgX + 92f, mlY = bgY + 38f, mlW = 120f, mlH = BackgroundComponent.BG_HEIGHT - 46f;
		float spX = bgX + 218f, spY = bgY + 38f, spW = 172f, spH = BackgroundComponent.BG_HEIGHT - 46f;

		if (isModuleCategory(selectedCategory)) {
			moduleComponent.updateScroll(delta, scrollSpeed);
			moduleComponent.updateScrollFades(delta, scrollSpeed, mlH, spH);
			moduleComponent.renderModuleList(context, mlX, mlY, mlW, mlH, mx, my, FIXED_GUI_SCALE, alphaMultiplier);
			moduleComponent.renderSettingsPanel(context, spX, spY, spW, spH, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier);
		}

		autoBuyRenderer.render(context, bgX, bgY, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier, selectedCategory);
		configsRenderer.render(context, bgX, bgY, mx, my, delta, FIXED_GUI_SCALE, alphaMultiplier, selectedCategory);

		if (background.getSearchPanelAlpha() > 0.01f) {
			background.renderSearchResults(context, bgX, bgY, mx, my, FIXED_GUI_SCALE, alphaMultiplier);
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
		float bgX = (this.width - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX();
		float bgY = (this.height - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY();

		if (background.isSearchBoxHovered(mx, my, bgX, bgY) && button == 0) {
			background.setSearchActive(true);
			return true;
		}

		if (background.isSearchActive()) {
			if (button == 0) {
				ModuleStructure searchModule = background.getSearchModuleAtPosition(mx, my, bgX, bgY);
				if (searchModule != null) {
					searchModule.switchState();
					return true;
				}
				if (!background.isSearchBoxHovered(mx, my, bgX, bgY)) {
					background.setSearchActive(false);
				}
			}
			return true;
		}

		ModuleCategory cat = background.getCategoryAtPosition(mx, my, bgX, bgY);
		if (cat != null) {
			selectedCategory = cat;
			updateModules();
			return true;
		}

		float mlX = bgX + 92f, mlY = bgY + 38f, mlW = 120f, mlH = BackgroundComponent.BG_HEIGHT - 48f;
		if (isModuleCategory(selectedCategory)) {
			ModuleStructure starModule = moduleComponent.getModuleForStarClick(mx, my, mlX, mlY, mlW, mlH);
			if (starModule != null && button == 0) {
				moduleComponent.toggleFavorite(starModule);
				return true;
			}
			ModuleStructure module = moduleComponent.getModuleAtPosition(mx, my, mlX, mlY, mlW, mlH);
			if (module != null) {
				if (button == 0) module.switchState();
				else if (button == 1) moduleComponent.selectModule(module);
				return true;
			}
			float spX = bgX + 218f, spY = bgY + 38f, spW = 172f, spH = BackgroundComponent.BG_HEIGHT - 48f;
			if (mx >= spX && mx <= spX + spW && my >= spY && my <= spY + spH) {
				for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
					if (c.getSetting().isVisible() && c.mouseClicked(mx, my, button)) return true;
				}
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
		for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
			if (c.getSetting().isVisible() && c.mouseReleased(mouseX, mouseY, button)) return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (closing) return false;
		float bgX = (this.width - BackgroundComponent.BG_WIDTH) / 2f + dragHandler.getOffsetX();
		float bgY = (this.height - BackgroundComponent.BG_HEIGHT) / 2f + dragHandler.getOffsetY();
		float mlX = bgX + 92f, mlY = bgY + 38f, mlW = 120f, mlH = BackgroundComponent.BG_HEIGHT - 48f;
		if (mouseX >= mlX && mouseX <= mlX + mlW && mouseY >= mlY && mouseY <= mlY + mlH) {
			moduleComponent.handleModuleScroll(vertical, mlH);
			return true;
		}
		float spX = bgX + 218f, spY = bgY + 38f, spW = 172f, spH = BackgroundComponent.BG_HEIGHT - 48f;
		if (mouseX >= spX && mouseX <= spX + spW && mouseY >= spY && mouseY <= spY + spH) {
			moduleComponent.handleSettingScroll(vertical, spH);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_N) {
			if (background.isSearchActive()) {
				background.setSearchActive(false);
				return true;
			}
			close();
			return true;
		}
		if (closing) return false;
		if (background.isSearchActive() && background.handleSearchKey(keyCode)) {
			return true;
		}
		ModuleStructure binding = moduleComponent.getBindingModule();
		if (binding != null) {
			binding.setKey(keyCode == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
			moduleComponent.setBindingModule(null);
			return true;
		}
		for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
			if (c.getSetting().isVisible() && c.keyPressed(keyCode, scanCode, modifiers)) return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (closing) return false;
		if (background.isSearchActive() && background.handleSearchChar(codePoint)) {
			return true;
		}
		for (AbstractSettingComponent c : moduleComponent.getSettingComponents()) {
			if (c.getSetting().isVisible() && c.charTyped(codePoint, modifiers)) return true;
		}
		return super.charTyped(codePoint, modifiers);
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
