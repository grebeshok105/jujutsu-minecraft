package jujutsu.mod.client.vfx.blackhole;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU owner of the black hole post-effect.
 *
 * <p>Two capture points feed one fullscreen pass:
 * <ul>
 *   <li>{@link #captureWorldDepth} runs at {@code WorldRenderEvents.LAST} — before the hand clears
 *       the main depth buffer — and snapshots world depth for occlusion.</li>
 *   <li>{@link #render} runs from the tail of {@code GameRenderer.renderLevel} — after the hand and
 *       screen effects are in the framebuffer — snapshots colour + the post-clear depth, uploads
 *       the uniform block and draws the warped/disk/horizon composite.</li>
 * </ul>
 *
 * <p>It also owns the HUD pre/post copies: while a hole is active the framebuffer is copied before
 * and after {@code GuiRenderer.render}, and {@link #compositeHud} pulls the HUD pixels through the
 * warp shader over the restored world.
 *
 * <p>Same discipline as {@code DomainSphereRenderer}: lazy GL objects, GPU-free constructor, every
 * failure path disables the effect for the session instead of throwing into the frame.
 */
public final class BlackHoleRenderer implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/black_hole");

	private static final ResourceLocation PIPELINE_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "pipeline/black_hole");
	private static final ResourceLocation SHADER_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/black_hole");
	private static final ResourceLocation HUD_PIPELINE_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "pipeline/black_hole_hud");
	private static final ResourceLocation HUD_SHADER_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/black_hole_hud");

	/** std140 BlackHoleData: 4 mat4 + 6 vec4 = 352 bytes. */
	private static final int BLACK_HOLE_DATA_SIZE = 352;
	/** std140 BlackHoleHud: 2 vec4 = 32 bytes. */
	private static final int HUD_DATA_SIZE = 32;

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(PIPELINE_ID)
					.withVertexShader(SHADER_ID)
					.withFragmentShader(SHADER_ID)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.withUniform("BlackHoleData", UniformType.UNIFORM_BUFFER)
					.withSampler("SceneSampler")
					.withSampler("SceneDepthSampler")
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.withoutBlend()
					.build());

	private static final RenderPipeline HUD_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(HUD_PIPELINE_ID)
					.withVertexShader(HUD_SHADER_ID)
					.withFragmentShader(HUD_SHADER_ID)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.withUniform("BlackHoleHud", UniformType.UNIFORM_BUFFER)
					.withSampler("HudPre")
					.withSampler("HudPost")
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.withoutBlend()
					.build());

	/** Force-touch for pipeline registration. */
	public static RenderPipeline pipeline() {
		HUD_PIPELINE.getLocation();
		return PIPELINE;
	}

	// --- Device objects. All null until first use: the constructor must not touch GL. ---
	private GpuTexture sceneCopy;
	private GpuTextureView sceneCopyView;
	private GpuTexture worldDepthCopy;
	private GpuTextureView worldDepthCopyView;
	private int copyWidth = -1;
	private int copyHeight = -1;
	private GpuTexture hudPre;
	private GpuTextureView hudPreView;
	private GpuTexture hudPost;
	private GpuTextureView hudPostView;
	private int hudCopyWidth = -1;
	private int hudCopyHeight = -1;
	private GpuBuffer blackHoleData;
	private ByteBuffer blackHoleDataBytes;
	private GpuBuffer hudData;
	private ByteBuffer hudDataBytes;
	private GpuBuffer dummyVertexBuffer;

	private final Matrix4f inverseProjection = new Matrix4f();
	private final Matrix4f inversePosition = new Matrix4f();
	private boolean disabledForSession;
	private long fpsWindowStart;
	private int fpsFrames;
	private boolean copyProbeLogged;

	/**
	 * Snapshots the world depth buffer before the hand render clears it. Called from
	 * {@code WorldRenderEvents.LAST}; safe to call every frame while a hole is active.
	 */
	public void captureWorldDepth(Minecraft client) {
		if (disabledForSession) {
			return;
		}
		RenderTarget target = client.getMainRenderTarget();
		if (target == null || target.getDepthTexture() == null) {
			return;
		}
		try {
			ensureTargets(target.width, target.height);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(
					target.getDepthTexture(), worldDepthCopy, 0, 0, 0, 0, 0, target.width, target.height);
		} catch (RuntimeException | LinkageError error) {
			disable("world depth capture", error);
		}
	}

	/**
	 * Post-hand frame composite: copies colour + post-clear depth, uploads the uniforms and draws.
	 * Called from the tail of {@code GameRenderer.renderLevel}.
	 */
	public void render(Minecraft client, WorldRenderContext context, BlackHoleState hole, float ageTicks) {
		if (disabledForSession || hole == null) {
			return;
		}
		RenderTarget target = client.getMainRenderTarget();
		if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null
				|| target.getColorTextureView() == null) {
			return;
		}
		Matrix4fc projection = context.projectionMatrix();
		Matrix4fc position = context.positionMatrix();
		if (projection == null || position == null) {
			return;
		}
		try {
			drawFrame(client, target, context, hole, ageTicks, projection, position);
			logFrameRate();
		} catch (RuntimeException | LinkageError error) {
			disable("render", error);
		}
	}

	/** Debug probe: logs the average frame interval every 240 frames while the effect draws. */
	private void logFrameRate() {
		long now = System.nanoTime();
		if (fpsWindowStart == 0L) {
			fpsWindowStart = now;
			fpsFrames = 0;
			return;
		}
		fpsFrames++;
		if (fpsFrames >= 240) {
			double ms = (now - fpsWindowStart) / 1_000_000.0 / fpsFrames;
			LOG.info("[BlackHole] avg frame {} ms (~{} fps) over {} frames",
					String.format("%.2f", ms), String.format("%.0f", 1000.0 / ms), fpsFrames);
			fpsWindowStart = now;
			fpsFrames = 0;
		}
	}

	private void drawFrame(Minecraft client, RenderTarget target, WorldRenderContext context,
			BlackHoleState hole, float ageTicks, Matrix4fc projection, Matrix4fc position) {
		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		if (!copyScene(target, encoder)) {
			return;
		}

		inverseProjection.set(projection).invert();
		inversePosition.set(position).invert();
		ensureDummyVertexBuffer();
		ensureUniformBuffers();
		writeBlackHoleData(encoder, client, context, hole, ageTicks, projection, position);

		try (RenderPass pass = encoder.createRenderPass(
				() -> "jujutsumod:black_hole",
				target.getColorTextureView(),
				OptionalInt.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setPipeline(PIPELINE);
			pass.bindSampler("SceneSampler", sceneCopyView);
			pass.bindSampler("SceneDepthSampler", worldDepthCopyView);
			pass.setUniform("BlackHoleData", blackHoleData);
			pass.setVertexBuffer(0, dummyVertexBuffer);
			pass.draw(0, 3);
		}
	}

	/** Copies colour (world + hand) and refreshes the world-depth copy if LAST did not run. */
	private boolean copyScene(RenderTarget target, CommandEncoder encoder) {
		try {
			ensureTargets(target.width, target.height);
			encoder.copyTextureToTexture(
					target.getColorTexture(), sceneCopy, 0, 0, 0, 0, 0, target.width, target.height);
			if (!copyProbeLogged) {
				copyProbeLogged = true;
				LOG.info("[BlackHole] scene copy OK ({}x{})", target.width, target.height);
			}
			return true;
		} catch (RuntimeException | LinkageError error) {
			disable("scene copy", error);
			return false;
		}
	}

	private void writeBlackHoleData(CommandEncoder encoder, Minecraft client, WorldRenderContext context,
			BlackHoleState hole, float ageTicks, Matrix4fc projection, Matrix4fc position) {
		BlackHoleTiming timing = hole.timing();
		Vec3 cameraPos = context.camera().getPosition();
		Vec3 center = hole.centerWorld().subtract(cameraPos);
		Vec3 normal = hole.diskNormal();

		ByteBuffer data = blackHoleDataBytes;
		data.clear();
		putMatrix(data, inverseProjection);
		putMatrix(data, inversePosition);
		putMatrix(data, projection);
		putMatrix(data, position);
		float horizonRadius = (float) (BlackHoleProfile.HORIZON_RADIUS);
		data.putFloat((float) center.x).putFloat((float) center.y).putFloat((float) center.z)
				.putFloat(horizonRadius);
		data.putFloat((float) normal.x).putFloat((float) normal.y).putFloat((float) normal.z)
				.putFloat(hole.diskPhase());
		data.putFloat(timing.intensity(ageTicks))
				.putFloat(timing.lensStrength(ageTicks))
				.putFloat(timing.diskIntensity(ageTicks))
				.putFloat(timing.desaturation(ageTicks));
		float aspect = target0(client) != null ? (float) client.getMainRenderTarget().width / client.getMainRenderTarget().height : 1.0f;
		data.putFloat(timing.jolt(ageTicks))
				.putFloat(aspect)
				.putFloat(ageTicks * 0.05f)
				.putFloat(length(center) < horizonRadius ? 1.0f : 0.0f);
		data.putFloat((float) BlackHoleProfile.DISK_INNER_RADIUS)
				.putFloat((float) BlackHoleProfile.DISK_OUTER_RADIUS)
				.putFloat((float) BlackHoleProfile.DISK_HALF_THICKNESS)
				.putFloat(timing.collapse(ageTicks));
		long seed = timing.seed();
		data.putFloat(((seed >>> 32) & 0xFFFF) / 65536.0f)
				.putFloat((seed & 0xFFFF) / 65536.0f)
				.putFloat(((seed >>> 16) & 0xFFFF) / 65536.0f)
				.putFloat(((seed >>> 48) & 0xFFFF) / 65536.0f);
		data.flip();
		encoder.writeToBuffer(blackHoleData.slice(0, BLACK_HOLE_DATA_SIZE), data);
	}

	private static RenderTarget target0(Minecraft client) {
		return client.getMainRenderTarget();
	}

	private static float length(Vec3 v) {
		return (float) v.length();
	}

	// ------------------------------------------------------------------
	// HUD isolation + warp composite
	// ------------------------------------------------------------------

	/**
	 * Snapshots the framebuffer before the GUI draws. Called from the GuiRenderer HEAD mixin while
	 * a hole is active; {@link #compositeHud} restores this image and pulls the post-GUI frame
	 * through the warp on top of it.
	 */
	public void captureHudPre(Minecraft client) {
		if (disabledForSession) {
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTexture() == null) {
			return;
		}
		try {
			ensureHudCopies(main.width, main.height);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(
					main.getColorTexture(), hudPre, 0, 0, 0, 0, 0, main.width, main.height);
		} catch (RuntimeException | LinkageError error) {
			disable("hud pre copy", error);
		}
	}

	/**
	 * Restores the pre-HUD frame, then draws the post-HUD frame back through the warp shader with
	 * a mask derived from the pre/post difference. Called after {@code GuiRenderer.render}.
	 */
	public void compositeHud(Minecraft client, BlackHoleState hole, float ageTicks,
			Matrix4fc projection, Matrix4fc position, Vec3 cameraPos) {
		if (disabledForSession || hole == null || hudPre == null || hudPost == null) {
			return;
		}
		RenderTarget main = client.getMainRenderTarget();
		if (main == null || main.getColorTexture() == null || main.getColorTextureView() == null) {
			return;
		}
		try {
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(
					main.getColorTexture(), hudPost, 0, 0, 0, 0, 0, main.width, main.height);
			// Restore the clean world: the composite re-adds only the pixels the HUD drew.
			encoder.copyTextureToTexture(
					hudPre, main.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);

			ensureDummyVertexBuffer();
			ensureUniformBuffers();
			writeHudData(client, hole, ageTicks, projection, position, cameraPos);
			try (RenderPass pass = encoder.createRenderPass(
					() -> "jujutsumod:black_hole_hud",
					main.getColorTextureView(),
					OptionalInt.empty())) {
				RenderSystem.bindDefaultUniforms(pass);
				pass.setPipeline(HUD_PIPELINE);
				pass.bindSampler("HudPre", hudPreView);
				pass.bindSampler("HudPost", hudPostView);
				pass.setUniform("BlackHoleHud", hudData);
				pass.setVertexBuffer(0, dummyVertexBuffer);
				pass.draw(0, 3);
			}
		} catch (RuntimeException | LinkageError error) {
			disable("hud composite", error);
		}
	}

	private void writeHudData(Minecraft client, BlackHoleState hole, float ageTicks,
			Matrix4fc projection, Matrix4fc position, Vec3 cameraPos) {
		BlackHoleTiming timing = hole.timing();
		// Project the centre to UV space (same math as the fragment shader).
		Vec3 rel = hole.centerWorld().subtract(cameraPos);
		org.joml.Vector4f clip = new org.joml.Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
		Matrix4f view = new Matrix4f(position);
		clip.mul(view);
		clip.mul(new Matrix4f(projection));
		float u = 0.5f, v = 0.5f, ring = 0.15f;
		if (clip.w > 0.001f) {
			u = clip.x / clip.w * 0.5f + 0.5f;
			v = clip.y / clip.w * 0.5f + 0.5f;
			org.joml.Vector4f clipR = new org.joml.Vector4f(
					(float) rel.x, (float) rel.y + (float) (BlackHoleProfile.HORIZON_RADIUS * 2.6), (float) rel.z, 1.0f);
			clipR.mul(view);
			clipR.mul(new Matrix4f(projection));
			if (clipR.w > 0.001f) {
				ring = Math.max(0.02f,
						(float) Math.hypot(clipR.x / clipR.w - clip.x / clip.w, clipR.y / clipR.w - clip.y / clip.w) * 0.5f);
			}
		}
		RenderTarget main = client.getMainRenderTarget();
		float aspect = main != null && main.height > 0 ? (float) main.width / main.height : 1.0f;

		ByteBuffer data = hudDataBytes;
		data.clear();
		data.putFloat(u).putFloat(v).putFloat(ring).putFloat(aspect);
		float inside = rel.length() < BlackHoleProfile.HORIZON_RADIUS ? 1.0f : 0.0f;
		data.putFloat(timing.lensStrength(ageTicks)).putFloat(timing.jolt(ageTicks))
				.putFloat(inside).putFloat(0.0f);
		data.flip();
		RenderSystem.getDevice().createCommandEncoder()
				.writeToBuffer(hudData.slice(0, HUD_DATA_SIZE), data);
	}

	// ------------------------------------------------------------------
	// device object management (mirrors DomainSphereRenderer)
	// ------------------------------------------------------------------

	private void ensureTargets(int width, int height) {
		if (sceneCopy != null && worldDepthCopy != null && copyWidth == width && copyHeight == height) {
			return;
		}
		closeTargets();
		GpuDevice device = RenderSystem.getDevice();
		sceneCopy = device.createTexture(
				() -> "jujutsumod:black_hole_scene_copy",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.RGBA8,
				width, height, 1, 1);
		worldDepthCopy = device.createTexture(
				() -> "jujutsumod:black_hole_depth_copy",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.DEPTH32,
				width, height, 1, 1);
		sceneCopyView = device.createTextureView(sceneCopy);
		worldDepthCopyView = device.createTextureView(worldDepthCopy);
		copyWidth = width;
		copyHeight = height;
	}

	private void ensureHudCopies(int width, int height) {
		if (hudPre != null && hudPost != null && hudCopyWidth == width && hudCopyHeight == height) {
			return;
		}
		closeHudCopies();
		GpuDevice device = RenderSystem.getDevice();
		hudPre = device.createTexture(
				() -> "jujutsumod:black_hole_hud_pre",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.RGBA8,
				width, height, 1, 1);
		hudPost = device.createTexture(
				() -> "jujutsumod:black_hole_hud_post",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.RGBA8,
				width, height, 1, 1);
		hudPreView = device.createTextureView(hudPre);
		hudPostView = device.createTextureView(hudPost);
		hudCopyWidth = width;
		hudCopyHeight = height;
	}

	private void closeHudCopies() {
		if (hudPreView != null) {
			hudPreView.close();
			hudPreView = null;
		}
		if (hudPostView != null) {
			hudPostView.close();
			hudPostView = null;
		}
		if (hudPre != null) {
			hudPre.close();
			hudPre = null;
		}
		if (hudPost != null) {
			hudPost.close();
			hudPost = null;
		}
		hudCopyWidth = -1;
		hudCopyHeight = -1;
	}

	private void ensureUniformBuffers() {
		if (blackHoleData == null) {
			blackHoleData = RenderSystem.getDevice().createBuffer(
					() -> "jujutsumod:black_hole_uniform",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					BLACK_HOLE_DATA_SIZE);
			blackHoleDataBytes = MemoryUtil.memAlloc(BLACK_HOLE_DATA_SIZE);
		}
		if (hudData == null) {
			hudData = RenderSystem.getDevice().createBuffer(
					() -> "jujutsumod:black_hole_hud_uniform",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					HUD_DATA_SIZE);
			hudDataBytes = MemoryUtil.memAlloc(HUD_DATA_SIZE);
		}
	}

	private static void putMatrix(ByteBuffer buffer, Matrix4fc matrix) {
		buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
		buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
		buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
		buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
	}

	private void ensureDummyVertexBuffer() {
		if (dummyVertexBuffer != null) {
			return;
		}
		ByteBuffer dummy = MemoryUtil.memAlloc(4);
		try {
			dummy.putInt(0);
			dummy.flip();
			dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
					() -> "jujutsumod:black_hole_dummy_vertex",
					GpuBuffer.USAGE_VERTEX,
					dummy);
		} finally {
			MemoryUtil.memFree(dummy);
		}
	}

	private void disable(String where, Throwable error) {
		disabledForSession = true;
		LOG.warn("Disabling black hole VFX for this client session ({}): {}", where, error.toString());
	}

	private void closeTargets() {
		if (sceneCopyView != null) {
			sceneCopyView.close();
			sceneCopyView = null;
		}
		if (worldDepthCopyView != null) {
			worldDepthCopyView.close();
			worldDepthCopyView = null;
		}
		if (sceneCopy != null) {
			sceneCopy.close();
			sceneCopy = null;
		}
		if (worldDepthCopy != null) {
			worldDepthCopy.close();
			worldDepthCopy = null;
		}
		copyWidth = -1;
		copyHeight = -1;
	}

	public void resetSession() {
		disabledForSession = false;
		copyProbeLogged = false;
	}

	@Override
	public void close() {
		closeTargets();
		closeHudCopies();
		if (blackHoleData != null) {
			blackHoleData.close();
			blackHoleData = null;
		}
		if (blackHoleDataBytes != null) {
			MemoryUtil.memFree(blackHoleDataBytes);
			blackHoleDataBytes = null;
		}
		if (hudData != null) {
			hudData.close();
			hudData = null;
		}
		if (hudDataBytes != null) {
			MemoryUtil.memFree(hudDataBytes);
			hudDataBytes = null;
		}
		if (dummyVertexBuffer != null) {
			dummyVertexBuffer.close();
			dummyVertexBuffer = null;
		}
	}
}
