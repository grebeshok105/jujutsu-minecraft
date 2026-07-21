package jujutsu.mod.client.ui.msdf;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immediate-mode MSDF text batcher adapted from Rich-Modern's FontPipeline for MC 1.21.8 Mojmap.
 * Coordinates are GUI-scaled (same space as {@link net.minecraft.client.gui.screens.Screen}).
 */
public final class MsdfFontPipeline implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/msdf");

	private static final ResourceLocation PIPELINE_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "pipeline/msdf");
	private static final ResourceLocation SHADER_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/msdf");

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(PIPELINE_ID)
					.withVertexShader(SHADER_ID)
					.withFragmentShader(SHADER_ID)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.withUniform("FontData", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.build());

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static final int[] LEGACY_COLORS = new int[32];

	static {
		for (int i = 0; i < 16; ++i) {
			int j = (i >> 3 & 1) * 85;
			int r = (i >> 2 & 1) * 170 + j;
			int g = (i >> 1 & 1) * 170 + j;
			int b = (i & 1) * 170 + j;
			if (i == 6) {
				r += 85;
			}
			LEGACY_COLORS[i] = (255 << 24) | (r << 16) | (g << 8) | b;
			LEGACY_COLORS[i + 16] = ((r & 0xFCFCFC) >> 2 << 24) | (r << 16) | (g << 8) | b;
		}
	}

	private static final int MAX_CHARS = 256;
	private static final int BUFFER_SIZE = 64 + MAX_CHARS * 64;

	private GpuBuffer uniformBuffer;
	private GpuBuffer dummyVertexBuffer;
	private ByteBuffer dataBuffer;
	private boolean initialized;

	private final List<CharData> charBatch = new ArrayList<>();
	private MsdfFontAtlas currentAtlas;
	private float currentOutlineWidth;
	private int currentOutlineColor;
	private boolean diagLogged;

	public static RenderPipeline pipeline() {
		return PIPELINE;
	}

	private void ensureInitialized() {
		if (initialized) {
			return;
		}
		this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);
		ByteBuffer dummyData = MemoryUtil.memAlloc(4);
		dummyData.putInt(0);
		dummyData.flip();
		this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
				() -> "jujutsumod:msdf_dummy_vertex",
				GpuBuffer.USAGE_VERTEX,
				dummyData);
		MemoryUtil.memFree(dummyData);
		initialized = true;
	}

	public void drawText(MsdfFontAtlas atlas, String text, float x, float y, float size, int color) {
		drawText(atlas, text, x, y, size, color, 0f, 0, 0f);
	}

	public void drawText(
			MsdfFontAtlas atlas,
			String text,
			float x,
			float y,
			float size,
			int color,
			float outlineWidth,
			int outlineColor,
			float rotation) {
		Minecraft client = Minecraft.getInstance();
		if (client.getMainRenderTarget() == null || text == null || text.isEmpty()) {
			return;
		}

		atlas.ensureLoaded();
		if (atlas.getGlyphCount() == 0) {
			return;
		}

		ensureInitialized();

		if (currentAtlas != null
				&& (currentAtlas != atlas
						|| currentOutlineWidth != outlineWidth
						|| currentOutlineColor != outlineColor)) {
			flush();
		}

		currentAtlas = atlas;
		currentOutlineWidth = outlineWidth;
		currentOutlineColor = outlineColor;

		float scale = size / atlas.getFontSize();
		float cursorX = x;
		float cursorY = y;

		float textWidth = getTextWidth(atlas, text, size);
		float textHeight = getTextHeight(atlas, text, size);
		float pivotX = x + textWidth / 2f;
		float pivotY = y + textHeight / 2f;
		float rotationRad = (float) Math.toRadians(rotation);

		int currentColor = color;
		int i = 0;
		while (i < text.length()) {
			int codePoint = text.codePointAt(i);
			int charCount = Character.charCount(codePoint);

			if ((codePoint == '§' || codePoint == '&') && i + charCount < text.length()) {
				int nextCodePoint = text.codePointAt(i + charCount);
				if (nextCodePoint == '#' && i + charCount + 6 < text.length()) {
					try {
						String hex = text.substring(i + charCount + 1, i + charCount + 7);
						currentColor = (0xFF << 24) | Integer.parseInt(hex, 16);
						i += charCount + 7;
						continue;
					} catch (Exception ignored) {
						// fall through
					}
				}
				int code = "0123456789abcdefklmnor".indexOf(Character.toLowerCase((char) nextCodePoint));
				if (code >= 0) {
					if (code < 16) {
						currentColor = LEGACY_COLORS[code];
					} else if (code == 21) {
						currentColor = color;
					}
					i += charCount + Character.charCount(nextCodePoint);
					continue;
				}
			}

			if (codePoint == '\n') {
				cursorX = x;
				cursorY += atlas.getLineHeight() * scale;
				i += charCount;
				continue;
			}

			MsdfGlyph glyph = atlas.getGlyph(codePoint);
			if (glyph == null) {
				MsdfGlyph fallback = atlas.getGlyph('?');
				cursorX += fallback != null ? fallback.xAdvance * scale : size * 0.5f;
				i += charCount;
				continue;
			}

			float glyphX = cursorX + glyph.xOffset * scale;
			float glyphY = cursorY + glyph.yOffset * scale;
			float glyphW = glyph.width * scale;
			float glyphH = glyph.height * scale;

			if (glyph.width > 0 && glyph.height > 0) {
				charBatch.add(new CharData(
						glyphX, glyphY, glyphW, glyphH,
						glyph.u0, glyph.v0, glyph.u1, glyph.v1,
						currentColor, rotationRad, pivotX, pivotY, scale));
			}

			cursorX += glyph.xAdvance * scale;
			if (charBatch.size() >= MAX_CHARS) {
				flush();
			}
			i += charCount;
		}

		if (!charBatch.isEmpty() && currentAtlas != null) {
			flush();
		}
	}

	public void flush() {
		if (charBatch.isEmpty() || currentAtlas == null) {
			charBatch.clear();
			currentAtlas = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.getMainRenderTarget() == null) {
			charBatch.clear();
			currentAtlas = null;
			return;
		}

		AbstractTexture texture = client.getTextureManager().getTexture(currentAtlas.getTextureId());
		if (texture == null) {
			charBatch.clear();
			currentAtlas = null;
			return;
		}

		// LINEAR filtering is the whole point of MSDF — force bilinear every draw.
		texture.setFilter(true, false);

		GpuTexture gpuTexture;
		GpuTextureView textureView;
		try {
			gpuTexture = texture.getTexture();
			textureView = texture.getTextureView();
			if (textureView == null) {
				textureView = RenderSystem.getDevice().createTextureView(gpuTexture);
			}
		} catch (Exception e) {
			charBatch.clear();
			currentAtlas = null;
			return;
		}

		prepareUniformData(client, currentAtlas, currentOutlineWidth, currentOutlineColor);

		int size = dataBuffer.remaining();
		if (uniformBuffer == null || uniformBuffer.size() < size) {
			if (uniformBuffer != null) {
				uniformBuffer.close();
			}
			uniformBuffer = RenderSystem.getDevice().createBuffer(
					() -> "jujutsumod:msdf_uniform",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
				RenderSystem.getModelViewMatrix(),
				COLOR_MODULATOR,
				MODEL_OFFSET,
				TEXTURE_MATRIX,
				0.0f);

		boolean ownView = textureView != texture.getTextureView();
		try (RenderPass renderPass = encoder.createRenderPass(
				() -> "jujutsumod:msdf_pass",
				client.getMainRenderTarget().getColorTextureView(),
				OptionalInt.empty(),
				client.getMainRenderTarget().getDepthTextureView(),
				OptionalDouble.empty())) {
			renderPass.setPipeline(PIPELINE);
			renderPass.setVertexBuffer(0, dummyVertexBuffer);
			renderPass.bindSampler("Sampler0", textureView);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setUniform("FontData", uniformBuffer);
			renderPass.draw(0, charBatch.size() * 6);
			if (!diagLogged) {
				diagLogged = true;
				LOG.info("MSDF draw ok: chars={} atlas={}", charBatch.size(), currentAtlas.getTextureId());
			}
		} catch (RuntimeException | LinkageError error) {
			LOG.error("MSDF draw failed", error);
		} finally {
			if (ownView) {
				textureView.close();
			}
			charBatch.clear();
			currentAtlas = null;
		}
	}

	private void prepareUniformData(
			Minecraft client, MsdfFontAtlas atlas, float outlineWidth, int outlineColor) {
		dataBuffer.clear();

		float screenWidth = client.getWindow().getGuiScaledWidth();
		float screenHeight = client.getWindow().getGuiScaledHeight();
		float guiScale = (float) client.getWindow().getGuiScale();

		dataBuffer.putFloat(screenWidth);
		dataBuffer.putFloat(screenHeight);
		dataBuffer.putFloat(guiScale);
		dataBuffer.putFloat(outlineWidth);

		float oa = ((outlineColor >> 24) & 0xFF) / 255.0f;
		float or = ((outlineColor >> 16) & 0xFF) / 255.0f;
		float og = ((outlineColor >> 8) & 0xFF) / 255.0f;
		float ob = (outlineColor & 0xFF) / 255.0f;
		dataBuffer.putFloat(or);
		dataBuffer.putFloat(og);
		dataBuffer.putFloat(ob);
		dataBuffer.putFloat(oa);

		dataBuffer.putFloat(atlas.getAtlasWidth());
		dataBuffer.putFloat(atlas.getAtlasHeight());
		dataBuffer.putFloat(atlas.getDistanceRange());
		dataBuffer.putFloat(atlas.getFontSize());

		dataBuffer.putInt(charBatch.size());
		dataBuffer.putInt(0);
		dataBuffer.putInt(0);
		dataBuffer.putInt(0);

		for (CharData cd : charBatch) {
			dataBuffer.putFloat(cd.x);
			dataBuffer.putFloat(cd.y);
			dataBuffer.putFloat(cd.width);
			dataBuffer.putFloat(cd.height);

			dataBuffer.putFloat(cd.u0);
			dataBuffer.putFloat(cd.v0);
			dataBuffer.putFloat(cd.u1);
			dataBuffer.putFloat(cd.v1);

			float a = ((cd.color >> 24) & 0xFF) / 255.0f;
			float r = ((cd.color >> 16) & 0xFF) / 255.0f;
			float g = ((cd.color >> 8) & 0xFF) / 255.0f;
			float b = (cd.color & 0xFF) / 255.0f;
			dataBuffer.putFloat(r);
			dataBuffer.putFloat(g);
			dataBuffer.putFloat(b);
			dataBuffer.putFloat(a);

			dataBuffer.putFloat(cd.rotation);
			dataBuffer.putFloat(cd.pivotX);
			dataBuffer.putFloat(cd.pivotY);
			dataBuffer.putFloat(cd.glyphScale);
		}

		dataBuffer.flip();
	}

	public float getTextWidth(MsdfFontAtlas atlas, String text, float size) {
		atlas.ensureLoaded();
		float scale = size / atlas.getFontSize();
		float width = 0f;
		float maxWidth = 0f;

		int i = 0;
		while (i < text.length()) {
			int codePoint = text.codePointAt(i);
			int charCount = Character.charCount(codePoint);

			if ((codePoint == '§' || codePoint == '&') && i + charCount < text.length()) {
				int nextCodePoint = text.codePointAt(i + charCount);
				if (nextCodePoint == '#' && i + charCount + 6 < text.length()) {
					i += charCount + 7;
					continue;
				}
				int code = "0123456789abcdefklmnor".indexOf(Character.toLowerCase((char) nextCodePoint));
				if (code >= 0) {
					i += charCount + Character.charCount(nextCodePoint);
					continue;
				}
			}

			if (codePoint == '\n') {
				maxWidth = Math.max(maxWidth, width);
				width = 0f;
				i += charCount;
				continue;
			}

			MsdfGlyph glyph = atlas.getGlyph(codePoint);
			if (glyph != null) {
				width += glyph.xAdvance * scale;
			} else {
				MsdfGlyph fallback = atlas.getGlyph('?');
				width += fallback != null ? fallback.xAdvance * scale : size * 0.5f;
			}
			i += charCount;
		}
		return Math.max(maxWidth, width);
	}

	public float getTextHeight(MsdfFontAtlas atlas, String text, float size) {
		atlas.ensureLoaded();
		float scale = size / atlas.getFontSize();
		int lines = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines * atlas.getLineHeight() * scale;
	}

	@Override
	public void close() {
		if (uniformBuffer != null) {
			uniformBuffer.close();
			uniformBuffer = null;
		}
		if (dummyVertexBuffer != null) {
			dummyVertexBuffer.close();
			dummyVertexBuffer = null;
		}
		if (dataBuffer != null) {
			MemoryUtil.memFree(dataBuffer);
			dataBuffer = null;
		}
		initialized = false;
	}

	private static final class CharData {
		final float x, y, width, height;
		final float u0, v0, u1, v1;
		final int color;
		final float rotation;
		final float pivotX, pivotY;
		final float glyphScale;

		CharData(
				float x,
				float y,
				float w,
				float h,
				float u0,
				float v0,
				float u1,
				float v1,
				int color,
				float rotation,
				float pivotX,
				float pivotY,
				float glyphScale) {
			this.x = x;
			this.y = y;
			this.width = w;
			this.height = h;
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.color = color;
			this.rotation = rotation;
			this.pivotX = pivotX;
			this.pivotY = pivotY;
			this.glyphScale = glyphScale;
		}
	}
}
