package jujutsu.mod.client.rich.util.render.font;

import java.util.Map;
import jujutsu.mod.client.ui.msdf.MsdfFonts;

/** Compatibility shim — fonts are loaded by MsdfFonts.warm(). */
public class FontRenderer {
	private boolean initialized;

	public void loadAllFonts(Map<String, String> registry) {
		MsdfFonts.warm();
		initialized = true;
	}

	public void loadFont(String name, String path) {
		MsdfFonts.warm();
	}

	public void initialize() {
		MsdfFonts.warm();
		initialized = true;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void drawText(String fontName, String text, float x, float y, float size, int color) {
		new Font(fontName, fontName).draw(text, x, y, size, color);
	}

	public void drawCenteredText(String fontName, String text, float x, float y, float size, int color) {
		new Font(fontName, fontName).drawCentered(text, x, y, size, color);
	}

	public float getTextWidth(String fontName, String text, float size) {
		return new Font(fontName, fontName).getWidth(text, size);
	}

	public float getLineHeight(String fontName, float size) {
		return new Font(fontName, fontName).getHeight(size);
	}

	public void close() {}
}
