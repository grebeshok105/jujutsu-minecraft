package jujutsu.mod.client.rich.util.render.shader;

import jujutsu.mod.client.rich.util.render.font.FontRenderer;
import jujutsu.mod.client.rich.util.render.font.Fonts;
import jujutsu.mod.client.ui.msdf.MsdfFonts;

public class RenderCore {
	private final FontRenderer fontRenderer = new FontRenderer();

	public FontRenderer getFontRenderer() {
		return fontRenderer;
	}

	// pipelines no longer used — Render2D routes to SDF
	public Object getRectPipeline() {
		return null;
	}

	public Object getOutlinePipeline() {
		return null;
	}

	public Object getTexturePipeline() {
		return null;
	}

	public Object getBlurPipeline() {
		return null;
	}

	public void setupOverlayState() {}

	public void restoreState() {}

	public void clearDepthBuffer() {}

	public void initArc() {}

	public void initArcOutline() {}
}
