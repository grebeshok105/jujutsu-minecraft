package jujutsu.mod.client.rich.screens.clickgui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

/**
 * Declines the vanilla crosshair while the ClickGui owns the screen.
 *
 * <p>Vanilla draws the crosshair from {@code Gui.render}, which has no open-screen gate, and the
 * blit is only recorded into the GUI render state — it is rasterized after the screen has already
 * been drawn. The ClickGui, by contrast, rasterizes immediately through {@code SdfRenderer.flush()},
 * so the crosshair composites on top of the finished menu and an opaque scrim cannot hide it.
 * Skipping the draw is the only mechanism that works.
 *
 * <p>Replacing the element rather than removing it keeps the vanilla crosshair one condition away,
 * so nothing has to be restored when the menu closes.
 */
public final class ClickGuiHud {
	private ClickGuiHud() {}

	/** Call once from client init. */
	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, tickCounter) -> {
			if (isClickGuiOpen()) {
				return;
			}
			vanilla.render(graphics, tickCounter);
		});
	}

	/**
	 * True for the whole close animation as well: the screen stays set until the panel has faded out,
	 * and popping the crosshair back mid-fade would read as a flicker.
	 */
	private static boolean isClickGuiOpen() {
		return Minecraft.getInstance().screen instanceof ClickGui;
	}
}
