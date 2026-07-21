package jujutsu.mod.client.rich.screens.clickgui.impl.background.render;

import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.screens.clickgui.impl.background.search.SearchHandler;
import jujutsu.mod.client.rich.util.render.Render2D;

import java.awt.*;

/** Header chrome only — no search labels, category titles, or module text. */
public class HeaderRenderer {

	public void render(float bgX, float bgY, float bgWidth, ModuleCategory selectedCategory,
			ModuleCategory previousCategory, ModuleCategory currentCategory,
			float headerTransition, SearchHandler searchHandler, float alphaMultiplier) {
		renderHeaderPanel(bgX, bgY, bgWidth, alphaMultiplier);
	}

	private void renderHeaderPanel(float bgX, float bgY, float bgWidth, float alphaMultiplier) {
		int panelAlpha = (int) (25 * alphaMultiplier);
		int outlineAlpha = (int) (255 * alphaMultiplier);

		Render2D.rect(bgX + 92f, bgY + 7.5f, bgWidth - 100f, 25, new Color(128, 128, 128, panelAlpha).getRGB(), 8);
		Render2D.outline(bgX + 92f, bgY + 7.5f, bgWidth - 100f, 25, 0.5f, new Color(55, 55, 55, outlineAlpha).getRGB(), 8);
	}

	public boolean isSearchBoxHovered(double mouseX, double mouseY, float bgX, float bgY) {
		return false;
	}
}
