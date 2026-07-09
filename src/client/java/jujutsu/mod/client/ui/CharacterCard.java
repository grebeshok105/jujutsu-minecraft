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
		selectAnim = UiEase.approach(selectAnim, selected ? 1.0f : 0.0f, 0.22f, deltaTicks);
		int bx = Math.round(x);
		int by = Math.round(y);
		int bw = Math.round(width);
		int bh = Math.round(height);

		float energy = UiEase.inOutCubic(Math.max(hover, selectAnim));
		UiRender.fastShadow(g, bx, by + 2, bw, bh, 0x88000000);
		if (energy > 0.01f) {
			UiRender.fastGlow(g, bx, by, bw, bh, accentRgb, energy * 0.82f);
		}

		// Body with a subtle vertical gradient.
		UiRender.roundedRect(g, bx, by, bw, bh, 8, 0xFF111116);
		paintBody(g, bx, by, bw, bh, energy);
		int border = UiRender.lerpColor(UiTheme.BORDER, UiRender.withAlpha(accentRgb, 1.0f), energy);
		UiRender.roundedRect(g, bx, by, bw, bh, 8, 0x00000000, border);

		// Portrait panel.
		int pad = 10;
		int portraitH = bh - 44;
		int px = bx + pad;
		int py = by + pad;
		int pw = bw - pad * 2;
		if (portrait != Portrait.NOBARA) {
			UiRender.roundedRect(g, px, py, pw, portraitH, 6, 0xFF15171D, UiRender.withAlpha(accentRgb, 0.14f + energy * 0.2f));
		}
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
		UiRender.roundedRect(g, x + 1, y + 1, w - 2, h - 2, 7, top);
		UiRender.fill(g, x + 7, y + h - 12, w - 14, 5, UiTheme.PANEL_INSET);
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
		int head = Math.min(60, Math.max(52, Math.min(w - 34, h - 32)));
		int headX = cx - head / 2;
		int headY = y + 16;
		if (energy > 0.02f) {
			UiRender.fastGlow(g, headX + 3, headY + 3, head - 6, head - 6, accentRgb, energy * 0.16f);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, headX, headY, 8.0f, 8.0f, head, head, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, headX, headY, 40.0f, 8.0f, head, head, 8, 8, 64, 64);
	}

	private void drawNonePortrait(GuiGraphics g, int cx, int y, int h, float energy) {
		int accent = UiRender.withAlpha(accentRgb, 0.26f + energy * 0.25f);
		UiRender.fastGlow(g, cx - 18, y + 16, 36, 42, accentRgb, 0.12f + energy * 0.22f);
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
