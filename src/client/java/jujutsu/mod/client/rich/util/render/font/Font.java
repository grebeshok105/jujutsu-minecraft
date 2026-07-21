package jujutsu.mod.client.rich.util.render.font;

import jujutsu.mod.client.ui.msdf.MsdfFonts;

/** Rich Font handle — draws via MsdfFonts. */
public class Font {
	private final String name;
	private final String path;

	public Font(String name) {
		this(name, name);
	}

	public Font(String name, String path) {
		this.name = name;
		this.path = path;
	}

	public void draw(String text, float x, float y, float size, int color) {
		MsdfFonts.draw(Fonts.faceFor(path), text, x, y, size, color);
	}

	public void drawCentered(String text, float x, float y, float size, int color) {
		MsdfFonts.drawCentered(Fonts.faceFor(path), text, x, y, size, color);
	}

	public float getWidth(String text, float size) {
		return MsdfFonts.width(Fonts.faceFor(path), text, size);
	}

	public float getHeight(float size) {
		return MsdfFonts.lineHeight(Fonts.faceFor(path), size);
	}

	public String getName() {
		return name;
	}
}
