package jujutsu.mod.client.ui.msdf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses an msdf-atlas-gen JSON atlas (glyphs + plane/atlas bounds).
 * Texture is loaded lazily via {@link net.minecraft.client.renderer.texture.TextureManager#getTexture}.
 */
public final class MsdfFontAtlas {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/msdf");

	private final ResourceLocation jsonId;
	private final ResourceLocation textureId;
	private final Map<Integer, MsdfGlyph> glyphs = new HashMap<>();
	private final AtomicBoolean loaded = new AtomicBoolean(false);

	private float atlasWidth = 512f;
	private float atlasHeight = 512f;
	private float fontSize = 32f;
	private float lineHeight = 40f;
	private float distanceRange = 4f;
	private boolean yOriginBottom;

	public MsdfFontAtlas(ResourceLocation jsonId, ResourceLocation textureId) {
		this.jsonId = jsonId;
		this.textureId = textureId;
	}

	public void ensureLoaded() {
		if (loaded.get() && !glyphs.isEmpty()) {
			return;
		}
		synchronized (this) {
			if (loaded.get() && !glyphs.isEmpty()) {
				return;
			}
			// Allow retry if earlier warm happened before resources were ready.
			loaded.set(false);
			glyphs.clear();
			doLoad();
		}
	}

	public void forceLoad() {
		synchronized (this) {
			loaded.set(false);
			glyphs.clear();
			doLoad();
		}
	}

	private void doLoad() {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.getResourceManager() == null) {
				LOG.warn("MSDF load skipped (no ResourceManager yet): {}", jsonId);
				loaded.set(false);
				return;
			}
			Optional<Resource> resourceOpt = mc.getResourceManager().getResource(jsonId);
			if (resourceOpt.isEmpty()) {
				LOG.warn("MSDF font JSON missing: {}", jsonId);
				loaded.set(false);
				return;
			}
			try (InputStream is = resourceOpt.get().open();
					InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				parseJson(root);
				loaded.set(true);
				LOG.info("Loaded MSDF font {} ({} glyphs)", jsonId, glyphs.size());
			}
		} catch (Exception e) {
			LOG.error("Failed to load MSDF font {}", jsonId, e);
			loaded.set(false);
		}
	}

	private void parseJson(JsonObject root) {
		float emSize = 1.0f;
		if (root.has("atlas")) {
			JsonObject atlas = root.getAsJsonObject("atlas");
			atlasWidth = getFloat(atlas, "width", 512f);
			atlasHeight = getFloat(atlas, "height", 512f);
			fontSize = getFloat(atlas, "size", 32f);
			distanceRange = getFloat(atlas, "distanceRange", 4f);
			if (atlas.has("yOrigin")) {
				yOriginBottom = "bottom".equalsIgnoreCase(atlas.get("yOrigin").getAsString());
			}
		}
		if (root.has("metrics")) {
			JsonObject metrics = root.getAsJsonObject("metrics");
			emSize = getFloat(metrics, "emSize", 1.0f);
			float normalizedLineHeight = getFloat(metrics, "lineHeight", 1.2f);
			lineHeight = normalizedLineHeight * fontSize;
		}
		if (root.has("glyphs")) {
			JsonArray glyphsArray = root.getAsJsonArray("glyphs");
			for (JsonElement elem : glyphsArray) {
				parseMsdfGlyph(elem.getAsJsonObject(), emSize);
			}
		}
	}

	private void parseMsdfGlyph(JsonObject g, float emSize) {
		int unicode = -1;
		if (g.has("unicode")) {
			unicode = g.get("unicode").getAsInt();
		} else if (g.has("char")) {
			String charStr = g.get("char").getAsString();
			if (!charStr.isEmpty()) {
				unicode = charStr.codePointAt(0);
			}
		} else if (g.has("id")) {
			unicode = g.get("id").getAsInt();
		}
		if (unicode < 0) {
			return;
		}

		float advance = getFloat(g, "advance", 0f) * fontSize;
		if (advance == 0f) {
			advance = getFloat(g, "xadvance", 0f);
		}

		float x = 0f;
		float y = 0f;
		float w = 0f;
		float h = 0f;
		float xOffset = 0f;
		float yOffset = 0f;

		if (g.has("atlasBounds")) {
			JsonObject bounds = g.getAsJsonObject("atlasBounds");
			float left = getFloat(bounds, "left", 0f);
			float bottom = getFloat(bounds, "bottom", 0f);
			float right = getFloat(bounds, "right", 0f);
			float top = getFloat(bounds, "top", 0f);
			x = left;
			w = right - left;
			h = top - bottom;
			y = yOriginBottom ? atlasHeight - top : bottom;
		} else if (g.has("x") && g.has("y") && g.has("width") && g.has("height")) {
			x = getFloat(g, "x", 0f);
			y = getFloat(g, "y", 0f);
			w = getFloat(g, "width", 0f);
			h = getFloat(g, "height", 0f);
		}

		if (g.has("planeBounds")) {
			JsonObject plane = g.getAsJsonObject("planeBounds");
			float pLeft = getFloat(plane, "left", 0f);
			float pTop = getFloat(plane, "top", 0f);
			xOffset = pLeft * fontSize;
			// Match Rich baseline: push glyph down from a virtual ascender.
			float ascender = 0.95f;
			yOffset = (ascender - pTop) * fontSize;
		} else if (g.has("xoffset") && g.has("yoffset")) {
			xOffset = getFloat(g, "xoffset", 0f);
			yOffset = getFloat(g, "yoffset", 0f);
		}

		glyphs.put(unicode, new MsdfGlyph(unicode, x, y, w, h, xOffset, yOffset, advance, atlasWidth, atlasHeight));
	}

	private static float getFloat(JsonObject obj, String key, float def) {
		return obj.has(key) ? obj.get(key).getAsFloat() : def;
	}

	public MsdfGlyph getGlyph(int codePoint) {
		return glyphs.get(codePoint);
	}

	public ResourceLocation getTextureId() {
		return textureId;
	}

	public float getFontSize() {
		return fontSize;
	}

	public float getLineHeight() {
		return lineHeight;
	}

	public float getAtlasWidth() {
		return atlasWidth;
	}

	public float getAtlasHeight() {
		return atlasHeight;
	}

	public float getDistanceRange() {
		return distanceRange;
	}

	public int getGlyphCount() {
		return glyphs.size();
	}

	public boolean isLoaded() {
		return loaded.get();
	}
}
