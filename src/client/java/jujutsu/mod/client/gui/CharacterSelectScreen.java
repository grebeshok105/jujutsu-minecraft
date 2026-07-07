package jujutsu.mod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.ui.CharacterCard;
import jujutsu.mod.client.ui.UiButton;
import jujutsu.mod.client.ui.UiEase;
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
				UiTheme.ACCENT_RGB, CharacterCard.Portrait.NOBARA,
				() -> selection = JujutsuCharacter.NOBARA);
		defaultCard = new CharacterCard(cardsX + cardW + gap, cardsY, cardW, cardH,
				Component.translatable("screen.jujutsumod.character_select.default"),
				Component.translatable("screen.jujutsumod.character_select.default.role"),
				0x008FB8FF, CharacterCard.Portrait.NONE,
				() -> selection = JujutsuCharacter.NONE);
		add(nobaraCard);
		add(defaultCard);

		int footerY = panelY + PANEL_H - 30;
		int btnW = 116;
		int btnH = 20;
		add(new UiButton(panelX + PANEL_W - btnW - 16, footerY, btnW, btnH,
				Component.translatable("screen.jujutsumod.character_select.confirm"), this::confirm).primary());
		add(new UiButton(panelX + PANEL_W - btnW * 2 - 26, footerY, btnW, btnH,
				Component.translatable("gui.cancel"), this::onClose));
	}

	@Override
	protected void paint(GuiGraphics g, int mouseX, int mouseY, float deltaTicks, float anim) {
		int panelX = (width - PANEL_W) / 2;
		int panelY = (height - PANEL_H) / 2;

		nobaraCard.setSelected(selection == JujutsuCharacter.NOBARA);
		defaultCard.setSelected(selection == JujutsuCharacter.NONE);

		// Backdrop glow + animated cursed motes behind the panel.
		UiRender.glow(g, panelX, panelY, PANEL_W, PANEL_H, 10, UiTheme.ACCENT_RGB, 0.35f * anim);
		drawBackdropMotes(g, panelX, panelY, anim);

		// Main panel.
		UiRender.shadow(g, panelX, panelY, PANEL_W, PANEL_H, 8, 0x99000000);
		UiRender.roundedRect(g, panelX, panelY, PANEL_W, PANEL_H, 12, UiTheme.PANEL, UiTheme.BORDER);
		UiRender.horizontalLine(g, panelX + 16, panelX + PANEL_W - 16, panelY + 3, UiRender.withAlpha(UiTheme.ACCENT_RGB, 0.8f * anim));

		Font font = Minecraft.getInstance().font;
		Component title = Component.translatable("screen.jujutsumod.character_select.title");
		g.drawString(font, title, panelX + (PANEL_W - font.width(title)) / 2, panelY + 16, UiTheme.TEXT, false);
		Component subtitle = Component.translatable("screen.jujutsumod.character_select.subtitle");
		g.drawString(font, subtitle, panelX + (PANEL_W - font.width(subtitle)) / 2, panelY + 28, UiTheme.TEXT_DIM, false);
		// Elements (cards + buttons) are drawn by the base UiScreen after paint().
	}

	private void drawBackdropMotes(GuiGraphics g, int panelX, int panelY, float anim) {
		long t = System.currentTimeMillis();
		for (int i = 0; i < 18; i++) {
			double phase = i * 1.7 + t / 900.0;
			int mx = panelX + (int) ((Math.sin(phase) * 0.5 + 0.5) * PANEL_W);
			int my = panelY + (int) (((Math.cos(phase * 0.7) * 0.5 + 0.5)) * PANEL_H);
			float tw = (float) (0.4 + 0.6 * (0.5 + 0.5 * Math.sin(t / 300.0 + i)));
			int size = 1 + (i % 3);
			g.fill(mx, my, mx + size, my + size, UiRender.withAlpha(UiTheme.ACCENT_RGB, 0.25f * tw * anim));
		}
	}

	private void confirm() {
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(selection.id()));
		}
		onClose();
	}
}
