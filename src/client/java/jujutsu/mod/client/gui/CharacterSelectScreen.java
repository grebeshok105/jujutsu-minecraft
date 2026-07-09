package jujutsu.mod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.ui.CharacterCard;
import jujutsu.mod.client.ui.UiButton;
import jujutsu.mod.client.ui.UiRender;
import jujutsu.mod.client.ui.UiScreen;
import jujutsu.mod.client.ui.UiTheme;
import jujutsu.mod.network.SelectCharacterPayload;

public final class CharacterSelectScreen extends UiScreen {
	private static final int PANEL_W = 420;
	private static final int PANEL_H = 286;
	private static final ResourceLocation PIERCING_NAIL_ICON = JujutsuMod.id("textures/gui/abilities/piercing_nail.png");
	private static final ResourceLocation HAIRPIN_ENLARGE_ICON = JujutsuMod.id("textures/gui/abilities/hairpin_enlargement.png");
	private static final ResourceLocation HAIRPIN_EXPLOSION_ICON = JujutsuMod.id("textures/gui/abilities/hairpin_explosion.png");
	private static final ResourceLocation RESONANCE_ICON = JujutsuMod.id("textures/gui/abilities/resonance.png");

	private JujutsuCharacter selection = JujutsuCharacter.NOBARA;
	private CharacterCard nobaraCard;
	private CharacterCard defaultCard;
	private UiButton confirmButton;
	private UiButton cancelButton;

	public CharacterSelectScreen() {
		super(Component.translatable("screen.jujutsumod.character_select"));
	}

	@Override
	protected void layout() {
		int panelX = (width - PANEL_W) / 2;
		int panelY = (height - PANEL_H) / 2;

		int cardW = 150;
		int cardH = 150;
		int gap = 24;
		int cardsY = panelY + 48;
		int cardsX = panelX + (PANEL_W - (cardW * 2 + gap)) / 2;

		nobaraCard = new CharacterCard(cardsX, cardsY, cardW, cardH,
				Component.translatable("screen.jujutsumod.character_select.nobara"),
				Component.translatable("screen.jujutsumod.character_select.nobara.role"),
				UiTheme.NOBARA_ACCENT_RGB, CharacterCard.Portrait.NOBARA,
				() -> selection = JujutsuCharacter.NOBARA);
		defaultCard = new CharacterCard(cardsX + cardW + gap, cardsY, cardW, cardH,
				Component.translatable("screen.jujutsumod.character_select.default"),
				Component.translatable("screen.jujutsumod.character_select.default.role"),
				UiTheme.NONE_ACCENT_RGB, CharacterCard.Portrait.NONE,
				() -> selection = JujutsuCharacter.NONE);
		add(nobaraCard);
		add(defaultCard);

		int footerY = panelY + PANEL_H - 30;
		int btnW = 116;
		int btnH = 20;
		confirmButton = new UiButton(panelX + PANEL_W - btnW - 16, footerY, btnW, btnH,
				Component.translatable("screen.jujutsumod.character_select.confirm"), this::confirm).primary();
		cancelButton = new UiButton(panelX + PANEL_W - btnW * 2 - 26, footerY, btnW, btnH,
				Component.translatable("gui.cancel"), this::onClose);
		add(confirmButton);
		add(cancelButton);
	}

	@Override
	protected void paint(GuiGraphics g, int mouseX, int mouseY, float deltaTicks, float anim) {
		int panelX = (width - PANEL_W) / 2;
		int panelY = (height - PANEL_H) / 2;

		nobaraCard.setSelected(selection == JujutsuCharacter.NOBARA);
		defaultCard.setSelected(selection == JujutsuCharacter.NONE);
		int accent = selectedAccentRgb();
		confirmButton.setAccentRgb(accent);
		cancelButton.setAccentRgb(accent);

		// Main panel.
		UiRender.fastShadow(g, panelX, panelY + 2, PANEL_W, PANEL_H, 0xAA000000);
		UiRender.fastGlow(g, panelX, panelY, PANEL_W, PANEL_H, accent, 0.35f * anim);
		UiRender.roundedRect(g, panelX, panelY, PANEL_W, PANEL_H, 10, UiTheme.PANEL, UiRender.withAlpha(accent, 0.28f + 0.18f * anim));
		UiRender.horizontalLine(g, panelX + 18, panelX + PANEL_W - 18, panelY + 3, UiRender.withAlpha(accent, 0.68f * anim));

		Font font = Minecraft.getInstance().font;
		Component title = Component.translatable("screen.jujutsumod.character_select.title");
		g.drawString(font, title, panelX + (PANEL_W - font.width(title)) / 2, panelY + 16, UiTheme.TEXT, false);
		Component subtitle = Component.translatable("screen.jujutsumod.character_select.subtitle");
		g.drawString(font, subtitle, panelX + (PANEL_W - font.width(subtitle)) / 2, panelY + 28, UiTheme.TEXT_DIM, false);
		drawAbilityStrip(g, font, panelX + 18, panelY + PANEL_H - 78, PANEL_W - 36, 36, accent);
	}

	private int selectedAccentRgb() {
		return selection == JujutsuCharacter.NOBARA ? UiTheme.NOBARA_ACCENT_RGB : UiTheme.NONE_ACCENT_RGB;
	}

	private void confirm() {
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(selection.id()));
		}
		onClose();
	}

	private void drawAbilityStrip(GuiGraphics g, Font font, int x, int y, int w, int h, int accent) {
		int body = selection == JujutsuCharacter.NOBARA ? 0xE11E2026 : 0xE116181D;
		UiRender.roundedRect(g, x, y, w, h, 7, body, UiRender.withAlpha(accent, 0.18f));
		if (selection != JujutsuCharacter.NOBARA) {
			Component label = Component.translatable("screen.jujutsumod.character_select.ability.none");
			g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1, UiTheme.TEXT_DIM, false);
			return;
		}

		int gap = 6;
		int cellW = (w - gap * 3) / 4;
		drawAbilityCell(g, font, x, y, cellW, h, PIERCING_NAIL_ICON, Component.translatable("screen.jujutsumod.character_select.ability.piercing_nail"), "", accent);
		drawAbilityCell(g, font, x + (cellW + gap), y, cellW, h, HAIRPIN_ENLARGE_ICON, Component.translatable("screen.jujutsumod.character_select.ability.hairpin_enlarge"), "R", accent);
		drawAbilityCell(g, font, x + (cellW + gap) * 2, y, cellW, h, HAIRPIN_EXPLOSION_ICON, Component.translatable("screen.jujutsumod.character_select.ability.hairpin_explosion"), "B", accent);
		drawAbilityCell(g, font, x + (cellW + gap) * 3, y, cellW, h, RESONANCE_ICON, Component.translatable("screen.jujutsumod.character_select.ability.resonance"), "", accent);
	}

	private static void drawAbilityCell(GuiGraphics g, Font font, int x, int y, int w, int h, ResourceLocation icon, Component label, String key, int accent) {
		UiRender.roundedRect(g, x, y, w, h, 6, 0x7812151B, UiRender.withAlpha(accent, 0.16f));
		int iconSize = 18;
		int iconX = x + 6;
		int iconY = y + (h - iconSize) / 2;
		UiRender.roundedRect(g, iconX - 2, iconY - 2, iconSize + 4, iconSize + 4, 5, 0xCC090A0E, UiRender.withAlpha(accent, 0.2f));
		g.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16, 16, 16);
		int textX = iconX + iconSize + 6;
		int available = Math.max(0, x + w - textX - 4);
		Component clipped = trimToWidth(font, label, available);
		g.drawString(font, clipped, textX, y + (h - font.lineHeight) / 2 + 1, UiTheme.TEXT, false);
		if (!key.isEmpty()) {
			int badgeW = 13;
			int badgeH = 12;
			int badgeX = x + w - badgeW - 4;
			int badgeY = y + h - badgeH - 4;
			UiRender.fastGlow(g, badgeX, badgeY, badgeW, badgeH, accent, 0.12f);
			UiRender.roundedRect(g, badgeX, badgeY, badgeW, badgeH, 4, UiRender.withAlpha(accent, 0.28f), UiRender.withAlpha(accent, 0.62f));
			g.drawString(font, key, badgeX + (badgeW - font.width(key)) / 2, badgeY + 2, UiTheme.TEXT, false);
		}
	}

	private static Component trimToWidth(Font font, Component label, int width) {
		String text = label.getString();
		if (font.width(text) <= width) {
			return label;
		}
		String ellipsis = "...";
		int limit = Math.max(0, width - font.width(ellipsis));
		String trimmed = font.plainSubstrByWidth(text, limit);
		return Component.literal(trimmed + ellipsis);
	}
}
