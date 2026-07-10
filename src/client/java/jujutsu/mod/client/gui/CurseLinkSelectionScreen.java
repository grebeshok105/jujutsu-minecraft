package jujutsu.mod.client.gui;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import jujutsu.mod.network.CurseLinkOptionsPayload;
import jujutsu.mod.network.SelectCurseLinkPayload;

public final class CurseLinkSelectionScreen extends Screen {
	private final List<CurseLinkOptionsPayload.Entry> entries;

	public CurseLinkSelectionScreen(List<CurseLinkOptionsPayload.Entry> entries) {
		super(Component.translatable("screen.jujutsumod.curse_link.title"));
		this.entries = List.copyOf(entries);
	}

	@Override protected void init() {
		int y = height / 2 - entries.size() * 12;
		for (CurseLinkOptionsPayload.Entry entry : entries) {
			Component label = Component.literal(entry.techniqueId().toString() + " · " + entry.sourceId().toString().substring(0, 8));
			addRenderableWidget(Button.builder(label, button -> {
				if (ClientPlayNetworking.canSend(SelectCurseLinkPayload.TYPE)) ClientPlayNetworking.send(new SelectCurseLinkPayload(entry.linkId()));
				onClose();
			}).bounds(width / 2 - 110, y, 220, 20).build());
			y += 24;
		}
	}

	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - entries.size() * 12 - 24, 0xFFEAFBFF);
		super.render(graphics, mouseX, mouseY, delta);
	}
}
