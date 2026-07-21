package jujutsu.mod.client.rich.util.render;

import jujutsu.mod.client.rich.util.render.font.Fonts;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Drop-in replacement for Rich Render2D.
 * Keeps the original clickgui call sites; draws via jujutsumod SDF + MSDF.
 */
public final class Render2D {
	private static final float FIXED_GUI_SCALE = 2.0f;
	private static final SdfRenderer SDF = new SdfRenderer();
	private static boolean batching;

	private Render2D() {}

	public static void beginFrame() {
		batching = true;
		SDF.setGlobalAlpha(1f);
		SDF.begin();
	}

	public static void endFrame() {
		if (batching) {
			SDF.flush();
			batching = false;
		}
		MsdfFonts.endFrame();
	}

	public static int getFixedScaledWidth() {
		var w = Minecraft.getInstance().getWindow();
		return (int) Math.ceil((double) w.getWidth() / FIXED_GUI_SCALE);
	}

	public static int getFixedScaledHeight() {
		var w = Minecraft.getInstance().getWindow();
		return (int) Math.ceil((double) w.getHeight() / FIXED_GUI_SCALE);
	}

	public static float getFixedGuiScale() {
		return FIXED_GUI_SCALE;
	}

	public static float getScaleMultiplier() {
		float current = (float) Minecraft.getInstance().getWindow().getGuiScale();
		return FIXED_GUI_SCALE / Math.max(0.5f, current);
	}

	public static void beginOverlay() {}

	public static void endOverlay() {}

	public static void clearDepth() {}

	public static void enableBlend() {}

	public static void disableBlend() {}

	public static void enableDepthTest() {}

	public static void disableDepthTest() {}

	public static void depthMask(boolean mask) {}

	public static void backgroundImage(float opacity) {}

	public static void backgroundImage(float opacity, float zoom) {}

	public static void backgroundImage(float x, float y, float width, float height, float opacity) {}

	public static void rect(float x, float y, float width, float height, int color) {
		rect(x, y, width, height, color, 0f);
	}

	public static void rect(float x, float y, float width, float height, int color, float radius) {
		shape(x, y, width, height, color, color, radius, 0f, 0);
	}

	public static void rect(float x, float y, float width, float height, int color,
			float topLeft, float topRight, float bottomRight, float bottomLeft) {
		rect(x, y, width, height, color, Math.max(Math.max(topLeft, topRight), Math.max(bottomRight, bottomLeft)));
	}

	public static void gradientRect(float x, float y, float width, float height, int[] colors, float radius) {
		int top = colors != null && colors.length > 0 ? colors[0] : 0xFF1A1A1A;
		int bot = colors != null && colors.length > 1 ? colors[colors.length - 1] : top;
		shape(x, y, width, height, top, bot, radius, 0f, 0);
	}

	public static void gradientRect(float x, float y, float width, float height, int[] colors,
			float topLeft, float topRight, float bottomRight, float bottomLeft) {
		gradientRect(x, y, width, height, colors,
				Math.max(Math.max(topLeft, topRight), Math.max(bottomRight, bottomLeft)));
	}

	public static void gradientRect(float x, float y, float width, float height, int[] colors9, float radius, float innerBlur) {
		gradientRect(x, y, width, height, colors9, radius);
	}

	public static void outline(float x, float y, float width, float height, float thickness, int color) {
		outline(x, y, width, height, thickness, color, 0f);
	}

	public static void outline(float x, float y, float width, float height, float thickness, int color, float radius) {
		// transparent fill + border ring
		shape(x, y, width, height, 0x00000000, 0x00000000, radius, thickness, color);
	}

	public static void outline(float x, float y, float width, float height, float thickness, int[] colors, float radius) {
		int c = colors != null && colors.length > 0 ? colors[0] : 0xFFFFFFFF;
		outline(x, y, width, height, thickness, c, radius);
	}

	public static void blur(float x, float y, float width, float height, float blurRadius, int tintColor) {
		// soft tinted panel stand-in for kawase blur
		rect(x, y, width, height, tintColor, 0f);
	}

	public static void blur(float x, float y, float width, float height, float blurRadius, float cornerRadius, int tintColor) {
		rect(x, y, width, height, tintColor, cornerRadius);
	}

	public static void texture(ResourceLocation id, float x, float y, float width, float height, int color) {
		// textured draw deferred — not required for core clickgui chrome
		rect(x, y, width, height, color & 0x55FFFFFF, 4f);
	}

	public static void texture(ResourceLocation id, float x, float y, float width, float height, float smoothness, float radius, int color) {
		rect(x, y, width, height, color & 0x55FFFFFF, radius);
	}

	public static void texture(ResourceLocation id, float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, int color, float radius) {
		rect(x, y, width, height, color & 0x55FFFFFF, radius);
	}

	public static void texture(ResourceLocation id, float x, float y, float width, float height,
			float u0, float v0, float u1, float v1, int color, float smoothness, float radius) {
		rect(x, y, width, height, color & 0x55FFFFFF, radius);
	}

	private static void shape(float x, float y, float w, float h, int fillTop, int fillBot, float radius, float border, int borderColor) {
		boolean owned = !batching;
		if (owned) {
			SDF.begin();
		}
		SDF.add(SdfShape.builder()
				.rect(x, y, w, h)
				.radius(radius)
				.border(border, borderColor)
				.glow(0, 0)
				.highlight(0.05f)
				.fill(fillTop, fillBot)
				.build());
		if (owned) {
			SDF.flush();
		}
	}

	// keep font draw available for code that still routes text through Render2D sometimes
	public static void drawText(String text, float x, float y, float size, int color) {
		Fonts.BOLD.draw(text, x, y, size, color);
	}
}
