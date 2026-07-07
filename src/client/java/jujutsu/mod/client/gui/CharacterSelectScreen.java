package jujutsu.mod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.network.SelectCharacterPayload;

public final class CharacterSelectScreen extends Screen {
	private static final int PANEL_WIDTH = 190;
	private static final int BUTTON_WIDTH = 170;
	private static final int BUTTON_HEIGHT = 20;

	public CharacterSelectScreen() {
		super(Component.translatable("screen.jujutsumod.character_select"));
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = height / 2 - 18;
		addRenderableWidget(Button.builder(Component.translatable("screen.jujutsumod.character_select.nobara"), button -> select(JujutsuCharacter.NOBARA))
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jujutsumod.character_select.default"), button -> select(JujutsuCharacter.NONE))
				.bounds(x, y + 26, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		int panelX = width / 2 - PANEL_WIDTH / 2;
		int panelY = height / 2 - 54;
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 108, 0xCC080A12);
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFF4DB8FF);
		graphics.drawCenteredString(font, title, width / 2, panelY + 14, 0xEAF6FF);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void select(JujutsuCharacter character) {
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(character.id()));
		}
		onClose();
	}
}
