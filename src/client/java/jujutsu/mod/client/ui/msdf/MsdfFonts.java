package jujutsu.mod.client.ui.msdf;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global MSDF font entrypoint for the modern menu (and future sharp UI).
 * Assets: {@code assets/jujutsumod/fonts/ui.{json,png}} + {@code shaders/core/msdf.*}.
 */
public final class MsdfFonts {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/msdf");

	private static final ResourceLocation JSON =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "fonts/ui.json");
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "fonts/ui.png");

	private static final MsdfFontAtlas UI = new MsdfFontAtlas(JSON, TEXTURE);
	private static final MsdfFontPipeline PIPELINE = new MsdfFontPipeline();
	private static boolean warmed;

	private MsdfFonts() {}

	/** Touch pipeline registration early (before first resource reload if possible). */
	public static void bootstrap() {
		if (MsdfFontPipeline.pipeline() == null) {
			throw new IllegalStateException("MSDF pipeline failed to register");
		}
	}

	public static void warm() {
		if (warmed) {
			return;
		}
		try {
			UI.forceLoad();
			warmed = true;
			LOG.info("MSDF UI font ready ({} glyphs)", UI.getGlyphCount());
		} catch (Exception e) {
			LOG.error("MSDF warm failed", e);
		}
	}

	public static MsdfFontAtlas ui() {
		return UI;
	}

	public static void draw(String text, float x, float y, float size, int argb) {
		warm();
		PIPELINE.drawText(UI, text, x, y, size, argb);
	}

	public static void drawCentered(String text, float x, float y, float size, int argb) {
		warm();
		float w = PIPELINE.getTextWidth(UI, text, size);
		PIPELINE.drawText(UI, text, x - w * 0.5f, y, size, argb);
	}

	public static void drawWithOutline(
			String text, float x, float y, float size, int argb, float outlineWidth, int outlineArgb) {
		warm();
		PIPELINE.drawText(UI, text, x, y, size, argb, outlineWidth, outlineArgb, 0f);
	}

	/** Flush batched MSDF glyphs once after all draw* calls for the frame. */
	public static void endFrame() {
		PIPELINE.flush();
	}

	public static float width(String text, float size) {
		warm();
		return PIPELINE.getTextWidth(UI, text, size);
	}

	public static float lineHeight(float size) {
		warm();
		return (UI.getLineHeight() / UI.getFontSize()) * size;
	}

	public static void close() {
		PIPELINE.close();
		warmed = false;
	}
}
