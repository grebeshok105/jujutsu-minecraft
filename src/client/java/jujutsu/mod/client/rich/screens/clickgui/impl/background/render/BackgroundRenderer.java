package jujutsu.mod.client.rich.screens.clickgui.impl.background.render;

import net.minecraft.client.gui.GuiGraphics;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.font.Fonts;

public class BackgroundRenderer {

	public void render(GuiGraphics context, float bgX, float bgY, float alphaMultiplier) {
		int baseAlpha = (int) (255 * alphaMultiplier);
		int top = ClickGuiTheme.panelTop(baseAlpha);
		int bot = ClickGuiTheme.panelBottom(baseAlpha);
		int mid = ClickGuiTheme.raised((int) (230 * alphaMultiplier));
		int[] gradientColors = {top, bot, mid, bot, top};
		Render2D.gradientRect(bgX, bgY, 400, 250, gradientColors, 15);
		// Soft accent edge
		Render2D.outline(bgX, bgY, 400, 250, 0.8f, ClickGuiTheme.accent((int) (90 * alphaMultiplier + 40 * ClickGuiTheme.warm())), 15);
	}

	public void renderCategoryPanel(float bgX, float bgY, float bgHeight, float alphaMultiplier) {
		int panelAlpha = (int) (30 * alphaMultiplier);
		int outlineAlpha = (int) (255 * alphaMultiplier);

		Render2D.rect(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15,
				ClickGuiTheme.raised(panelAlpha + 20), 10);
		Render2D.outline(bgX + 7.5f, bgY + 7.5f, 80, bgHeight - 15, 0.5f,
				ClickGuiTheme.outline(outlineAlpha), 10);

		// Footer brand strip
		Render2D.outline(bgX + 12.5f, bgY + 220.5f, 70, 17, 0.5f,
				ClickGuiTheme.outline(outlineAlpha), 5);
		Render2D.rect(bgX + 12.5f, bgY + 220.5f, 70, 17,
				ClickGuiTheme.raised((int) (40 * alphaMultiplier)), 5);

		float textSize = 5.5f;
		String label = "Vessel";
		float textWidth = Fonts.BOLD.getWidth(label, textSize);
		float textHeight = Fonts.BOLD.getHeight(textSize);
		float centerX = bgX + 12.5f + (70 - textWidth) / 2f;
		float centerY = bgY + 220.5f + (17 - textHeight) / 2f;
		Fonts.BOLD.draw(label, centerX, centerY, textSize,
				ClickGuiTheme.accent((int) (210 * alphaMultiplier)));
	}
}
