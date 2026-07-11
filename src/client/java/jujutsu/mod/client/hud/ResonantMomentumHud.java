package jujutsu.mod.client.hud;

import java.util.Locale;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.character.ClientResonantMomentum;
import jujutsu.mod.registry.JujutsuItems;

public final class ResonantMomentumHud {
	private ResonantMomentumHud() {}

	public static void initialize() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				JujutsuMod.id("resonant_momentum"), ResonantMomentumHud::render);
	}

	private static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		int remaining = ClientResonantMomentum.remainingTicks();
		Minecraft client = Minecraft.getInstance();
		if (remaining <= 0 || client.player == null || client.options.hideGui) return;
		int x = 8;
		int y = graphics.guiHeight() - 48;
		graphics.fill(x - 3, y - 3, x + 124, y + 21, 0xA814090C);
		graphics.fill(x - 2, y - 2, x + 123, y + 20, 0x803BCED8);
		graphics.renderItem(new ItemStack(JujutsuItems.RESONANCE_REMNANT), x, y);
		graphics.drawString(client.font, Component.translatable("hud.jujutsumod.resonant_momentum"), x + 20, y, 0xFFE8D3, true);
		String seconds = String.format(Locale.ROOT, "%.1fs", remaining / 20.0f);
		graphics.drawString(client.font, seconds, x + 20, y + 10, 0x55E8F2, true);
	}
}
