package jujutsu.mod.client.vfx.domain;

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
import java.util.List;
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
 * GPU owner of the world-space domain-sphere post-effect: a fullscreen raymarch pass that draws one
 * cyan shell per active sphere over the already-rendered world, using copies of the scene colour and
 * depth so the shell is hidden by terrain and can tint what is behind it.
 *
 * <p>Structurally this mirrors {@code MsdfFontPipeline} / {@code SdfRenderer}: the device objects are
 * created lazily on the first {@link #render} call, the constructor and JUnit stay GPU-free, and every
 * failure path disables the effect for the rest of the session instead of throwing into the frame.
 *
 * <p>The scene copies are recreated whenever the main target is resized (window resize, F3+T), so a
 * mid-effect resize self-heals on the next frame.
 */
public final class DomainSphereRenderer implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/domain_sphere");

	private static final ResourceLocation PIPELINE_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "pipeline/domain_sphere");
	private static final ResourceLocation SHADER_ID =
			ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/domain_sphere");

	/** Mirrors the channel's active-sphere cap: one UBO block per sphere per frame. */
	private static final int MAX_SPHERES_PER_FRAME = 4;
	/** std140 SphereData: two mat4 + two vec4 = 160 bytes. */
	private static final int SPHERE_DATA_SIZE = 160;

	/**
	 * Color-only fullscreen pass: no blend (the shader composites the scene copy itself), no depth
	 * attachment and no depth test - occlusion is done in the shader against the depth copy, because a
	 * fullscreen triangle would otherwise be rejected by the depth test wherever the world is closer.
	 */
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(PIPELINE_ID)
					.withVertexShader(SHADER_ID)
					.withFragmentShader(SHADER_ID)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.withUniform("SphereData", UniformType.UNIFORM_BUFFER)
					.withSampler("SceneSampler")
					.withSampler("SceneDepthSampler")
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.withoutBlend()
					.build());

	/** One frame of one shell. {@code centerWorld} is the fixed world anchor; the shader gets it relative to the camera. */
	public record Sphere(Vec3 centerWorld, float radius, float fade, float expansionProgress, float ageProgress) {}

	/** Force-touch for pipeline registration. */
	public static RenderPipeline pipeline() {
		return PIPELINE;
	}

	/**
	 * World anchor -> camera-relative coordinates. Pure math so the anchoring can be unit-tested; the
	 * subtraction is exactly what keeps the shell stable while the camera moves.
	 */
	public static Vec3 toCameraRelative(Vec3 centerWorld, Vec3 cameraPos) {
		return centerWorld.subtract(cameraPos);
	}

	// --- Device objects. All null until the first render(): the constructor must not touch GL. ---
	private GpuTexture sceneCopy;
	private GpuTextureView sceneCopyView;
	private GpuTexture depthCopy;
	private GpuTextureView depthCopyView;
	private int copyWidth = -1;
	private int copyHeight = -1;
	private GpuBuffer sphereData;
	private ByteBuffer sphereDataBytes;
	private int sphereDataStride = SPHERE_DATA_SIZE;
	private GpuBuffer dummyVertexBuffer;

	private final Matrix4f inverseProjection = new Matrix4f();
	private final Matrix4f inversePosition = new Matrix4f();
	private boolean disabledForSession;
	private boolean copyProbeLogged;

	/**
	 * Draws one frame of every active shell. Never throws: a broken GL path logs once and disables the
	 * effect for the session, because a half-drawn post-effect that retries every frame is worse than a
	 * missing one.
	 */
	public void render(Minecraft client, WorldRenderContext context, List<Sphere> spheres) {
		if (disabledForSession || spheres.isEmpty()) {
			return;
		}
		RenderTarget target = client.getMainRenderTarget();
		if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null
				|| target.getColorTextureView() == null) {
			return;
		}
		try {
			drawFrame(target, context, spheres);
		} catch (RuntimeException | LinkageError error) {
			disabledForSession = true;
			LOG.warn("Disabling domain sphere VFX for this client session: {}", error.toString());
		}
	}

	private void drawFrame(RenderTarget target, WorldRenderContext context, List<Sphere> spheres) {
		Matrix4fc projection = context.projectionMatrix();
		Matrix4fc position = context.positionMatrix();
		if (projection == null || position == null) {
			return;
		}

		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		if (!copyScene(target, encoder)) {
			return;
		}

		inverseProjection.set(projection).invert();
		inversePosition.set(position).invert();
		ensureDummyVertexBuffer();

		int count = Math.min(spheres.size(), MAX_SPHERES_PER_FRAME);
		ensureUniformBuffer();
		writeSphereData(encoder, context.camera().getPosition(), spheres, count);

		try (RenderPass pass = encoder.createRenderPass(
				() -> "jujutsumod:domain_sphere",
				target.getColorTextureView(),
				OptionalInt.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setPipeline(PIPELINE);
			pass.bindSampler("SceneSampler", sceneCopyView);
			pass.bindSampler("SceneDepthSampler", depthCopyView);
			pass.setVertexBuffer(0, dummyVertexBuffer);
			for (int i = 0; i < count; i++) {
				pass.setUniform("SphereData", sphereData.slice(i * sphereDataStride, SPHERE_DATA_SIZE));
				pass.draw(0, 3);
			}
		}
	}

	/**
	 * Blits the main target's colour and depth into the sampler textures. Returns false when the copy
	 * is unavailable, in which case the effect is disabled: without a depth copy there is no occlusion,
	 * and a shell drawn over terrain is worse than no shell at all.
	 *
	 * <p>The first attempt logs a dedicated marker so an in-game run can tell "depth copy rejected"
	 * apart from "sphere never spawned" - both otherwise look like a silent absence of the effect.
	 *
	 * <p>Fallback if this ever rejects in-game: bind {@code target.getDepthTextureView()} as
	 * {@code SceneDepthSampler} instead of {@code depthCopyView} and drop the depth blit. That is
	 * legal because this pass is colour-only (no depth attachment, so reading the bound depth texture
	 * creates no read/write hazard), but it changes occlusion semantics for translucent geometry, so it
	 * stays a documented fallback rather than a silent path.
	 */
	private boolean copyScene(RenderTarget target, CommandEncoder encoder) {
		try {
			ensureTargets(target.width, target.height);
			encoder.copyTextureToTexture(
					target.getColorTexture(), sceneCopy, 0, 0, 0, 0, 0, target.width, target.height);
			encoder.copyTextureToTexture(
					target.getDepthTexture(), depthCopy, 0, 0, 0, 0, 0, target.width, target.height);
			if (!copyProbeLogged) {
				copyProbeLogged = true;
				LOG.info("[DomainSphere] depth copy OK ({}x{})", target.width, target.height);
			}
			return true;
		} catch (RuntimeException | LinkageError error) {
			if (!copyProbeLogged) {
				copyProbeLogged = true;
				LOG.warn("[DomainSphere] depth copy FAILED: {}", error.toString());
			}
			disabledForSession = true;
			return false;
		}
	}

	/** Creates the copies once, and recreates them whenever the main target changes size. */
	private void ensureTargets(int width, int height) {
		if (sceneCopy != null && depthCopy != null && copyWidth == width && copyHeight == height) {
			return;
		}
		closeTargets();
		GpuDevice device = RenderSystem.getDevice();
		// RGBA8 matches the main target's colour texture; DEPTH32 matches its depth texture
		// (RenderTarget.createBuffers), which is what copyTextureToTexture requires of both ends.
		sceneCopy = device.createTexture(
				() -> "jujutsumod:domain_sphere_scene_copy",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.RGBA8,
				width, height, 1, 1);
		depthCopy = device.createTexture(
				() -> "jujutsumod:domain_sphere_depth_copy",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.DEPTH32,
				width, height, 1, 1);
		sceneCopyView = device.createTextureView(sceneCopy);
		depthCopyView = device.createTextureView(depthCopy);
		copyWidth = width;
		copyHeight = height;
	}

	/**
	 * One uniform buffer for all spheres of the frame, with the per-sphere stride rounded up to the
	 * device's uniform offset alignment: the per-sphere slices are bound as ranges, and an unaligned
	 * offset is invalid there.
	 */
	private void ensureUniformBuffer() {
		int alignment = Math.max(1, RenderSystem.getDevice().getUniformOffsetAlignment());
		int stride = (SPHERE_DATA_SIZE + alignment - 1) / alignment * alignment;
		if (sphereData != null && sphereDataStride == stride) {
			return;
		}
		if (sphereData != null) {
			sphereData.close();
			sphereData = null;
		}
		if (sphereDataBytes != null) {
			MemoryUtil.memFree(sphereDataBytes);
			sphereDataBytes = null;
		}
		sphereDataStride = stride;
		sphereData = RenderSystem.getDevice().createBuffer(
				() -> "jujutsumod:domain_sphere_uniform",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
				stride * MAX_SPHERES_PER_FRAME);
		sphereDataBytes = MemoryUtil.memAlloc(stride * MAX_SPHERES_PER_FRAME);
	}

	private void writeSphereData(CommandEncoder encoder, Vec3 cameraPos, List<Sphere> spheres, int count) {
		ByteBuffer data = sphereDataBytes;
		// The limit is shrunk to the written range below; restore it first or a frame with more
		// spheres than the previous one overflows on position(i*stride) and kills the session.
		data.clear();
		for (int i = 0; i < count; i++) {
			Sphere sphere = spheres.get(i);
			Vec3 center = toCameraRelative(sphere.centerWorld(), cameraPos);
			data.position(i * sphereDataStride);
			putMatrix(data, inverseProjection);
			putMatrix(data, inversePosition);
			data.putFloat((float) center.x)
					.putFloat((float) center.y)
					.putFloat((float) center.z)
					.putFloat(sphere.radius());
			data.putFloat(sphere.fade())
					.putFloat(sphere.expansionProgress())
					.putFloat(sphere.ageProgress())
					.putFloat(0f);
		}
		data.position(0);
		data.limit(count * sphereDataStride);
		encoder.writeToBuffer(sphereData.slice(0, count * sphereDataStride), data);
	}

	/** std140 mat4: four consecutive columns, matching JOML's column-major m<col><row> accessors. */
	private static void putMatrix(ByteBuffer buffer, Matrix4fc matrix) {
		buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
		buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
		buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
		buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
	}

	/** Attribute-less draw still needs a bound vertex buffer: one int, never read. */
	private void ensureDummyVertexBuffer() {
		if (dummyVertexBuffer != null) {
			return;
		}
		ByteBuffer dummy = MemoryUtil.memAlloc(4);
		try {
			dummy.putInt(0);
			dummy.flip();
			dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
					() -> "jujutsumod:domain_sphere_dummy_vertex",
					GpuBuffer.USAGE_VERTEX,
					dummy);
		} finally {
			MemoryUtil.memFree(dummy);
		}
	}

	private void closeTargets() {
		if (sceneCopyView != null) {
			sceneCopyView.close();
			sceneCopyView = null;
		}
		if (depthCopyView != null) {
			depthCopyView.close();
			depthCopyView = null;
		}
		if (sceneCopy != null) {
			sceneCopy.close();
			sceneCopy = null;
		}
		if (depthCopy != null) {
			depthCopy.close();
			depthCopy = null;
		}
		copyWidth = -1;
		copyHeight = -1;
	}

	/**
	 * Re-enables the effect after a session-level disable (disconnect/world change resets the
	 * session). Also re-arms the first-frame depth-copy probe so the next session logs its own
	 * {@code [DomainSphere] depth copy OK/FAILED} marker.
	 */
	public void resetSession() {
		disabledForSession = false;
		copyProbeLogged = false;
	}

	/** Safe with no render() call ever made, so a headless channel can always close. */
	@Override
	public void close() {
		closeTargets();
		if (sphereData != null) {
			sphereData.close();
			sphereData = null;
		}
		if (sphereDataBytes != null) {
			MemoryUtil.memFree(sphereDataBytes);
			sphereDataBytes = null;
		}
		if (dummyVertexBuffer != null) {
			dummyVertexBuffer.close();
			dummyVertexBuffer = null;
		}
	}
}
