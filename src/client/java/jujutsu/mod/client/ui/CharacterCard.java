package jujutsu.mod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Selectable character card for the roster screen. Fully hand-painted: glass body, animated accent
 * glow on hover/selection, a procedural portrait emblem, name, role tagline and a selected checkmark.
 */
public final class CharacterCard extends UiElement {
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
		UiRender.shadow(g, bx, by, bw, bh, 6, 0x77000000);
		if (energy > 0.01f) {
			UiRender.glow(g, bx, by, bw, bh, 6, accentRgb, energy);
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
		UiRender.roundedRect(g, px, py, pw, portraitH, 6, 0xFF0B0610, UiTheme.BORDER);
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

	/** Procedural portrait: a stylized bust reading as the character, tinted by accent + energy. */
	private void drawPortrait(GuiGraphics g, int x, int y, int w, int h, float energy) {
		int cx = x + w / 2;
		if (portrait == Portrait.NOBARA) {
			// Backlight halo.
			UiRender.glow(g, cx - 14, y + h / 2 - 16, 28, 34, 5, accentRgb, 0.35f + energy * 0.5f);
			int skin = 0xFFF0C9A8;
			int hair = 0xFF6E4A2E;
			int hairShade = 0xFF553517;
			int coat = 0xFF2C2436;
			// Hair back.
			UiRender.roundedRect(g, cx - 15, y + 8, 30, 26, 8, hairShade);
			// Face.
			UiRender.roundedRect(g, cx - 10, y + 12, 20, 22, 7, skin);
			// Hair front fringe.
			UiRender.roundedRect(g, cx - 15, y + 8, 30, 10, 5, hair);
			g.fill(cx - 15, y + 12, cx - 9, y + 26, hair);
			g.fill(cx + 9, y + 12, cx + 15, y + 26, hair);
			// Eyes with accent glint.
			int eyeY = y + 22;
			g.fill(cx - 6, eyeY, cx - 3, eyeY + 2, 0xFF3A2A22);
			g.fill(cx + 3, eyeY, cx + 6, eyeY + 2, 0xFF3A2A22);
			int glint = UiRender.withAlpha(accentRgb, 0.7f + energy * 0.3f);
			g.fill(cx - 5, eyeY, cx - 4, eyeY + 1, glint);
			g.fill(cx + 4, eyeY, cx + 5, eyeY + 1, glint);
			// Shoulders / coat.
			int coatY = y + h - 16;
			UiRender.roundedRect(g, cx - 18, coatY, 36, 18, 6, coat);
			UiRender.roundedRect(g, cx - 4, coatY, 8, 18, 3, UiRender.withAlpha(accentRgb, 0.6f));
			// Floating nail motif.
			int nailX = x + w - 9;
			int nailY = y + 8 + (int) (Math.sin(System.currentTimeMillis() / 320.0) * 2);
			g.fill(nailX, nailY, nailX + 2, nailY + 10, 0xFFD8D2C0);
		} else {
			// Neutral silhouette.
			UiRender.roundedRect(g, cx - 12, y + 10, 24, 24, 8, 0xFF2A2233);
			UiRender.roundedRect(g, cx - 16, y + h - 16, 32, 18, 6, 0xFF201A29);
			Font font = Minecraft.getInstance().font;
			String q = "?";
			g.drawString(font, q, cx - font.width(q) / 2, y + h / 2 - 8, UiTheme.TEXT_DIM, false);
		}
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
