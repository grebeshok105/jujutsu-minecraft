package jujutsu.mod.client.rich.screens.clickgui.impl.configs;

import net.minecraft.client.gui.GuiGraphics;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;

/** Configs UI stripped — empty stub. */
public final class ConfigsRenderer {
	public void render(GuiGraphics context, float bgX, float bgY, float mx, float my, float delta,
			int guiScale, float alpha, ModuleCategory selectedCategory) {}

	public boolean mouseClicked(double mx, double my, int button, float bgX, float bgY) {
		return false;
	}

	public boolean mouseScrolled(double vertical) {
		return false;
	}

	public boolean charTyped(char c) {
		return false;
	}

	public boolean keyPressed(int key) {
		return false;
	}
}
