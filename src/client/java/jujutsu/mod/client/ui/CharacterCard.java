package jujutsu.mod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import jujutsu.mod.JujutsuMod;

/**
 * Selectable character card for the roster screen. Fully hand-painted: glass body, animated accent
 * glow on hover/selection, a procedural portrait emblem, name, role tagline and a selected checkmark.
 */
public final class CharacterCard extends UiElement {
	private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
	private final Component name;
	private final Component role;
	private final int accentRgb;
	private final Portrait portrait;
	private final Runnable action;
	private boolean selected;
	private float selectAnim;

	public enum Portrait { NOBARA, NONE }

	public CharacterCard(float x, float y, float w, float h, Component name, Component role, int accentRgb, Portrait portrait, Runnable action) {
		super(x, y, w, h);
		this.name = name;
		this.role = role;
		this.accentRgb = accentRgb;
		this.portrait = portrait;
		this.action = action;
	}

	public void setSelected(boolean value) {
		this.selected = value;
	}

	@Override
	protected void draw(GuiGraphics g, int mouseX, int mouseY, float deltaTicks) {
		selectAnim = UiEase.approach(selectAnim, selected ? 1.0f : 0.0f, 0.3f, deltaTicks);
		float lift = hover * 3.0f + selectAnim * 2.0f;
		int bx = Math.round(x);
		int by = Math.round(y - lift);
		int bw = Math.round(width);
		int bh = Math.round(height);

		float energy = Math.max(hover, selectAnim);
		UiRender.shadow(g, bx, by + 2, bw, bh, 8, 0x66000000);
		if (energy > 0.01f) {
			UiRender.glow(g, bx, by, bw, bh, 5, accentRgb, energy * 0.72f);
		}

		// Body with a subtle vertical gradient.
		UiRender.roundedRect(g, bx, by, bw, bh, 8, 0xFF000000);
		paintBody(g, bx, by, bw, bh, energy);
		int border = UiRender.lerpColor(UiTheme.BORDER, UiRender.withAlpha(accentRgb, 1.0f), energy);
		UiRender.roundedRect(g, bx, by, bw, bh, 8, 0x00000000, border);

		// Portrait panel.
		int pad = 10;
		int portraitH = bh - 44;
		int px = bx + pad;
		int py = by + pad;
		int pw = bw - pad * 2;
		UiRender.roundedRect(g, px, py, pw, portraitH, 6, 0xFF0B080E, UiRender.withAlpha(accentRgb, 0.18f + energy * 0.22f));
		drawPortrait(g, px, py, pw, portraitH, energy);

		// Name + role.
		Font font = Minecraft.getInstance().font;
		int nameY = by + bh - 32;
		int nameColor = UiRender.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, energy);
		g.drawString(font, name, bx + (bw - font.width(name)) / 2, nameY, nameColor, false);
		g.drawString(font, role, bx + (bw - font.width(role)) / 2, nameY + 12, UiTheme.TEXT_DIM, false);

		// Selected checkmark badge.
		if (selectAnim > 0.05f) {
			int badge = 12;
			int bxr = bx + bw - badge - 6;
			int byr = by + 6;
			UiRender.roundedRect(g, bxr, byr, badge, badge, badge / 2, UiRender.withAlpha(accentRgb, selectAnim), 0);
			int cx = bxr + badge / 2;
			int cy = byr + badge / 2;
			g.fill(cx - 2, cy, cx - 1, cy + 2, UiTheme.TEXT_ON_ACCENT);
			g.fill(cx - 1, cy + 1, cx, cy + 3, UiTheme.TEXT_ON_ACCENT);
			g.fill(cx, cy, cx + 3, cy + 1, UiTheme.TEXT_ON_ACCENT);
			g.fill(cx + 1, cy - 1, cx + 3, cy, UiTheme.TEXT_ON_ACCENT);
		}
	}

	private void paintBody(GuiGraphics g, int x, int y, int w, int h, float energy) {
		int top = UiRender.lerpColor(UiTheme.PANEL_RAISED, UiRender.lerpColor(UiTheme.PANEL_RAISED, accentRgb | 0xFF000000, 0.16f), energy);
		int bottom = UiTheme.PANEL_INSET;
		for (int row = 1; row < h - 1; row++) {
			float t = row / (float) (h - 1);
			int color = UiRender.lerpColor(top, bottom, t);
			int inset = row < 8 ? (8 - row) : (row >= h - 8 ? row - (h - 8) + 1 : 0);
			inset = Math.max(0, inset);
			UiRender.fill(g, x + inset, y + row, w - inset * 2, 1, color);
		}
	}

	private void drawPortrait(GuiGraphics g, int x, int y, int w, int h, float energy) {
		int cx = x + w / 2;
		if (portrait == Portrait.NOBARA) {
			drawNobaraPortrait(g, x, y, w, h, energy);
		} else {
			drawNonePortrait(g, cx, y, h, energy);
		}
	}

	private void drawNobaraPortrait(GuiGraphics g, int x, int y, int w, int h, float energy) {
		int cx = x + w / 2;
		int head = 46;
		int headX = cx - head / 2;
		int headY = y + 14;
		UiRender.glow(g, headX - 4, headY - 4, head + 8, head + 8, 6, accentRgb, 0.28f + energy * 0.42f);
		UiRender.roundedRect(g, headX - 5, headY - 5, head + 10, head + 10, 8, 0xAA080508, UiRender.withAlpha(accentRgb, 0.28f + energy * 0.28f));
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, headX, headY, 8.0f, 8.0f, head, head, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, headX, headY, 40.0f, 8.0f, head, head, 8, 8, 64, 64);

		int coatY = y + h - 24;
		UiRender.roundedRect(g, cx - 23, coatY, 46, 24, 7, 0xFF211928, UiRender.withAlpha(accentRgb, 0.18f));
		UiRender.roundedRect(g, cx - 4, coatY + 2, 8, 22, 3, UiRender.withAlpha(accentRgb, 0.46f + energy * 0.18f));
		int nailX = x + w - 12;
		int nailY = y + 12 + (int) (Math.sin(System.currentTimeMillis() / 320.0) * 2);
		g.fill(nailX, nailY, nailX + 2, nailY + 12, 0xFFD8D2C0);
		g.fill(nailX - 2, nailY, nailX + 4, nailY + 2, 0xFF8E867C);
	}

	private void drawNonePortrait(GuiGraphics g, int cx, int y, int h, float energy) {
		int accent = UiRender.withAlpha(accentRgb, 0.26f + energy * 0.25f);
		UiRender.glow(g, cx - 18, y + 16, 36, 42, 5, accentRgb, 0.12f + energy * 0.22f);
		UiRender.roundedRect(g, cx - 14, y + 16, 28, 28, 9, 0xFF242934, accent);
		UiRender.roundedRect(g, cx - 21, y + h - 27, 42, 25, 7, 0xFF171B23, accent);
		g.fill(cx - 12, y + 31, cx + 12, y + 33, 0xFF0B0E13);
		g.fill(cx - 2, y + 22, cx + 2, y + 40, 0xFF0B0E13);
		g.fill(cx - 24, y + h - 17, cx + 24, y + h - 15, UiRender.withAlpha(0x00B0BAC7, 0.16f + energy * 0.18f));
	}

	@Override
	protected void onClick() {
		Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f));
		if (action != null) {
			action.run();
		}
	}
}
