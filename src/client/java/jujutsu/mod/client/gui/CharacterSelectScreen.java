package jujutsu.mod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.ui.CharacterCard;
import jujutsu.mod.client.ui.UiButton;
import jujutsu.mod.client.ui.UiRender;
import jujutsu.mod.client.ui.UiScreen;
import jujutsu.mod.client.ui.UiTheme;
import jujutsu.mod.network.SelectCharacterPayload;

/**
 * Fully custom character roster. Built entirely on the hand-drawn UI kit (UiScreen + cards + buttons)
 * — animated open, hovering cards with accent glow, procedural portraits, a live cursed-energy motif
 * backdrop, and a confirm/close footer. No vanilla widgets are used.
 */
public final class CharacterSelectScreen extends UiScreen {
	private static final int PANEL_W = 360;
	private static final int PANEL_H = 236;

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
		int cardsY = panelY + 46;
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
		// Elements (cards + buttons) are drawn by the base UiScreen after paint().
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
}
