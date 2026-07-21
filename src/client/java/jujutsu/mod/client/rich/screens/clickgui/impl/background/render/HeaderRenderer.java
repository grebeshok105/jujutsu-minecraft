package jujutsu.mod.client.rich.screens.clickgui.impl.background.render;

import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.screens.clickgui.impl.background.search.SearchHandler;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.font.Fonts;

/**
 * Header chrome + smooth category title. Search box removed (roster search not needed).
 */
public class HeaderRenderer {

	private static final float HEADER_SLIDE_DISTANCE = 8f;

	public void render(float bgX, float bgY, float bgWidth, ModuleCategory selectedCategory,
			ModuleCategory previousCategory, ModuleCategory currentCategory,
			float headerTransition, SearchHandler searchHandler, float alphaMultiplier) {
		renderHeaderPanel(bgX, bgY, bgWidth, alphaMultiplier);
		renderCategoryLabel(bgX, bgY, previousCategory, currentCategory, headerTransition, alphaMultiplier);
	}

	private void renderHeaderPanel(float bgX, float bgY, float bgWidth, float alphaMultiplier) {
		Render2D.rect(bgX + 92f, bgY + 7.5f, bgWidth - 100f, 25,
				ClickGuiTheme.raised((int) (35 * alphaMultiplier)), 8);
		Render2D.outline(bgX + 92f, bgY + 7.5f, bgWidth - 100f, 25, 0.5f,
				ClickGuiTheme.outline((int) (255 * alphaMultiplier)), 8);
		// Accent underline
		Render2D.rect(bgX + 100f, bgY + 28.5f, bgWidth - 116f, 1.2f,
				ClickGuiTheme.accent((int) (90 * alphaMultiplier + 80 * ClickGuiTheme.warm())), 0);
	}

	private void renderCategoryLabel(float bgX, float bgY, ModuleCategory previousCategory,
			ModuleCategory currentCategory, float headerTransition, float alphaMultiplier) {
		float baseX = bgX + 100f;
		float baseY = bgY + 15.5f;
		float eased = easeOutQuart(Math.max(0f, Math.min(1f, headerTransition)));

		if (previousCategory != null && headerTransition < 1f) {
			float oldAlpha = (1f - eased) * alphaMultiplier;
			float oldOffsetY = eased * HEADER_SLIDE_DISTANCE;
			int a = (int) (180 * oldAlpha);
			if (a > 0) {
				Fonts.BOLD.draw(previousCategory.getReadableName(), baseX, baseY + oldOffsetY, 7.5f,
						ClickGuiTheme.accent(a));
			}
		}

		if (currentCategory != null) {
			float newAlpha = eased * alphaMultiplier;
			float newOffsetY = (1f - eased) * -HEADER_SLIDE_DISTANCE;
			int a = (int) (230 * newAlpha);
			if (a > 0) {
				Fonts.BOLD.draw(currentCategory.getReadableName(), baseX, baseY + newOffsetY, 7.5f,
						ClickGuiTheme.accent(a));
			}
		}
	}

	private float easeOutQuart(float x) {
		return 1f - (float) Math.pow(1 - x, 4);
	}

	public boolean isSearchBoxHovered(double mouseX, double mouseY, float bgX, float bgY) {
		return false;
	}
}
