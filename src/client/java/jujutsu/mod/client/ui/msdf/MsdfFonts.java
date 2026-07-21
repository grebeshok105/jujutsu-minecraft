package jujutsu.mod.client.ui.msdf;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-atlas MSDF entry (ported from rich.util.render.font.Fonts usage).
 * Atlases live under assets/jujutsumod/fonts/ (msdf-atlas-gen JSON+PNG).
 */
public final class MsdfFonts {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/msdf");

	public enum Face {
		UI("ui"),
		BOLD("bold"),
		ICONS("guiicons"),
		CATEGORY("categoryicons");

		final String path;

		Face(String path) {
			this.path = path;
		}
	}

	private static final MsdfFontAtlas[] ATLASES = new MsdfFontAtlas[Face.values().length];
	private static final MsdfFontPipeline PIPELINE = new MsdfFontPipeline();
	private static boolean warmed;

	static {
		for (Face face : Face.values()) {
			ResourceLocation json = ResourceLocation.fromNamespaceAndPath("jujutsumod", "fonts/" + face.path + ".json");
			ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("jujutsumod", "fonts/" + face.path + ".png");
			ATLASES[face.ordinal()] = new MsdfFontAtlas(json, tex);
		}
	}

	private MsdfFonts() {}

	public static void bootstrap() {
		if (MsdfFontPipeline.pipeline() == null) {
			throw new IllegalStateException("MSDF pipeline failed to register");
		}
	}

	public static void warm() {
		if (warmed) {
			return;
		}
		for (MsdfFontAtlas atlas : ATLASES) {
			try {
				atlas.forceLoad();
			} catch (Exception e) {
				LOG.error("MSDF warm failed for {}", atlas.getTextureId(), e);
			}
		}
		warmed = true;
		LOG.info("MSDF faces ready: ui={} bold={} icons={} cat={}",
				ATLASES[0].getGlyphCount(), ATLASES[1].getGlyphCount(),
				ATLASES[2].getGlyphCount(), ATLASES[3].getGlyphCount());
	}

	public static void draw(Face face, String text, float x, float y, float size, int argb) {
		warm();
		PIPELINE.drawText(ATLASES[face.ordinal()], text, x, y, size, argb);
	}

	public static void draw(String text, float x, float y, float size, int argb) {
		draw(Face.UI, text, x, y, size, argb);
	}

	public static void drawBold(String text, float x, float y, float size, int argb) {
		draw(Face.BOLD, text, x, y, size, argb);
	}

	public static void drawIcon(String glyph, float x, float y, float size, int argb) {
		draw(Face.ICONS, glyph, x, y, size, argb);
	}

	public static void drawCategoryIcon(String glyph, float x, float y, float size, int argb) {
		draw(Face.CATEGORY, glyph, x, y, size, argb);
	}

	public static void drawCentered(Face face, String text, float x, float y, float size, int argb) {
		warm();
		float w = PIPELINE.getTextWidth(ATLASES[face.ordinal()], text, size);
		PIPELINE.drawText(ATLASES[face.ordinal()], text, x - w * 0.5f, y, size, argb);
	}

	public static void drawCentered(String text, float x, float y, float size, int argb) {
		drawCentered(Face.UI, text, x, y, size, argb);
	}

	public static void endFrame() {
		PIPELINE.flush();
	}

	public static float width(Face face, String text, float size) {
		warm();
		return PIPELINE.getTextWidth(ATLASES[face.ordinal()], text, size);
	}

	public static float width(String text, float size) {
		return width(Face.UI, text, size);
	}

	public static float lineHeight(Face face, float size) {
		warm();
		MsdfFontAtlas a = ATLASES[face.ordinal()];
		return (a.getLineHeight() / a.getFontSize()) * size;
	}

	public static void close() {
		PIPELINE.close();
		warmed = false;
	}
}
