package jujutsu.mod.client.rich.util.render.shader;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/** GL scissor helper (Rich Scissor). */
public final class Scissor {
	public static void enable(float x, float y, float w, float h, int guiScale) {
		var window = Minecraft.getInstance().getWindow();
		double scale = window.getGuiScale();
		int fbH = window.getHeight();
		int sx = (int) Math.round(x * scale);
		int sy = (int) Math.round(fbH - (y + h) * scale);
		int sw = Math.max(0, (int) Math.round(w * scale));
		int sh = Math.max(0, (int) Math.round(h * scale));
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(sx, sy, sw, sh);
	}

	public static void disable() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	public static void reset() {
		disable();
	}
}
