package jujutsu.mod.client.rich.screens.clickgui.impl.background.render;

import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CategoryRenderer {

	private static final ModuleCategory[] MAIN_CATEGORIES = {
			ModuleCategory.COMBAT
	};
	private static final String[] MAIN_CATEGORY_NAMES = {"Characters"};
	private static final String[] MAIN_CATEGORY_ICONS = {"a"};

	private static final ModuleCategory[] EXTRA_CATEGORIES = {};
	private static final String[] EXTRA_CATEGORY_NAMES = {};
	private static final String[] EXTRA_CATEGORY_ICONS = {};

	private final Map<ModuleCategory, Float> categoryAnimations = new HashMap<>();

	private static final float ANIMATION_SPEED = 8f;
	private static final float MAX_OFFSET = 5f;
	private static final float BALL_SIZE = 3f;
	private static final float TEXT_SIZE = 6f;
	private static final float ICON_SIZE = 6f;
	private static final float ICON_SPACING = 4f;
	private static final float SECTION_TEXT_SIZE = 5f;

	public CategoryRenderer() {
		for (ModuleCategory cat : MAIN_CATEGORIES) {
			categoryAnimations.put(cat, 0f);
		}
	}

	public void updateAnimations(ModuleCategory selectedCategory, float deltaTime) {
		for (ModuleCategory cat : MAIN_CATEGORIES) {
			updateCategoryAnimation(cat, selectedCategory, deltaTime);
		}
	}

	private void updateCategoryAnimation(ModuleCategory cat, ModuleCategory selected, float deltaTime) {
		float target = cat == selected ? 1f : 0f;
		float current = categoryAnimations.getOrDefault(cat, 0f);
		float diff = target - current;
		float change = diff * ANIMATION_SPEED * deltaTime;
		if (Math.abs(diff) < 0.001f) {
			categoryAnimations.put(cat, target);
		} else {
			categoryAnimations.put(cat, current + change);
		}
	}

	public void render(float bgX, float bgY, ModuleCategory selectedCategory, float alphaMultiplier) {
		if (MAIN_CATEGORIES.length == 0) return;
		renderSectionHeader(bgX, bgY + 52f, "Основные", alphaMultiplier);
		renderMainCategories(bgX, bgY, alphaMultiplier);
	}

	private void renderSectionHeader(float bgX, float sectionY, String title, float alphaMultiplier) {
		float lineWidth = 18f;
		float textWidth = Fonts.BOLD.getWidth(title, SECTION_TEXT_SIZE);
		float totalWidth = 65f;
		float textX = bgX + 15f + (totalWidth - textWidth) / 2f;
		float lineY = sectionY + 3f;
		int lineAlpha = (int) (40 * alphaMultiplier);
		int textAlpha = (int) (100 * alphaMultiplier);
		Render2D.rect(bgX + 15f, lineY, lineWidth, 0.5f, new Color(255, 255, 255, lineAlpha).getRGB(), 0);
		Render2D.rect(bgX + 15f + totalWidth - lineWidth, lineY, lineWidth, 0.5f, new Color(255, 255, 255, lineAlpha).getRGB(), 0);
		Fonts.BOLD.draw(title, textX, sectionY, SECTION_TEXT_SIZE, new Color(150, 150, 150, textAlpha).getRGB());
	}

	private void renderMainCategories(float bgX, float bgY, float alphaMultiplier) {
		for (int i = 0; i < MAIN_CATEGORY_NAMES.length; i++) {
			ModuleCategory cat = MAIN_CATEGORIES[i];
			float animation = categoryAnimations.getOrDefault(cat, 0f);
			float textY = bgY + 65f + i * 15f;
			renderCategoryItem(bgX, textY, MAIN_CATEGORY_NAMES[i], MAIN_CATEGORY_ICONS[i], animation, alphaMultiplier);
		}
	}

	private void renderCategoryItem(float bgX, float textY, String name, String icon, float animation, float alphaMultiplier) {
		float offsetX = animation * MAX_OFFSET;

		int baseGray = 128;
		int targetWhite = 255;
		int colorValue = (int) (baseGray + (targetWhite - baseGray) * animation);
		int alpha = (int) ((128 + 127 * animation) * alphaMultiplier);

		// Blend selected category text toward theme accent.
		int plain = new Color(colorValue, colorValue, colorValue, alpha).getRGB();
		int accent = ClickGuiTheme.accent(alpha);
		int textColor = lerpColor(plain, accent, animation * 0.85f);

		float iconX = bgX + 17f + offsetX;
		float iconWidth = Fonts.CATEGORY_ICONS.getWidth(icon, ICON_SIZE);
		float textX = iconX + iconWidth + ICON_SPACING;
		float textWidth = Fonts.BOLD.getWidth(name, TEXT_SIZE);

		Fonts.CATEGORY_ICONS.draw(icon, iconX, textY + 0.5f, ICON_SIZE, textColor);

		if (animation > 0.01f) {
			float lineWidth = (iconWidth + ICON_SPACING + textWidth) * animation;
			float lineAlpha = animation * 120 * alphaMultiplier;
			Render2D.rect(iconX, textY + 9f, lineWidth, 0.5f, ClickGuiTheme.accent((int) lineAlpha), 0);

			float ballAlpha = animation * 220 * alphaMultiplier;
			float ballX = bgX + 12f;
			float ballY = textY + 2.5f;
			Render2D.rect(ballX, ballY, BALL_SIZE, BALL_SIZE, ClickGuiTheme.accent((int) ballAlpha), BALL_SIZE / 2f);
		}

		Fonts.BOLD.draw(name, textX, textY, TEXT_SIZE, textColor);
	}

	public ModuleCategory getCategoryAtPosition(double mouseX, double mouseY, float bgX, float bgY) {
		if (mouseX < bgX + 10f || mouseX > bgX + 95f) return null;

		for (int i = 0; i < MAIN_CATEGORY_NAMES.length; i++) {
			float catY = 65f + i * 15f;
			if (mouseY >= bgY + catY && mouseY <= bgY + catY + 13f) {
				return MAIN_CATEGORIES[i];
			}
		}
		return null;
	}

	private static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}
}
