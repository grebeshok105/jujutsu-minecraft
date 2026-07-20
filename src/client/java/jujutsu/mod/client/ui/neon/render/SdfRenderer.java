package jujutsu.mod.client.ui.neon.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Batches {@link SdfShape}s and draws them in a single immediate render pass using the custom
 * SDF pipeline. Call {@link #begin()}, add shapes, then {@link #flush()} once per frame from
 * Screen.render. Draws land UNDER the vanilla GuiGraphics batch (which flushes last), so these
 * shapes act as background surfaces - exactly what the dashboard panels need.
 */
public final class SdfRenderer implements AutoCloseable {
    // Extra quad padding beyond the shape bounds so the glow + shadow + AA have room.
    private static final float PAD = 6f;

    private final List<SdfShape> shapes = new ArrayList<>();
    private final CachedOrthoProjectionMatrixBuffer projection =
            new CachedOrthoProjectionMatrixBuffer("jujutsumod_sdf", 1000.0f, 11000.0f, true);
    private float globalAlpha = 1f;

    public void begin() {
        shapes.clear();
    }

    public void setGlobalAlpha(float alpha) {
        this.globalAlpha = alpha;
    }

    public void add(SdfShape shape) {
        shapes.add(shape);
    }

    public void flush() {
        if (shapes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        float guiW = window.getGuiScaledWidth();
        float guiH = window.getGuiScaledHeight();

        GpuBufferSlice projSlice = projection.getBuffer(guiW, guiH);
        RenderSystem.setProjectionMatrix(projSlice, ProjectionType.ORTHOGRAPHIC);

        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
                new Matrix4f().setTranslation(0.0f, 0.0f, -11000.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f(),
                new Matrix4f(),
                0.0f);

        int stride = SdfPipelines.VERTEX_STRIDE;
        ByteBuffer bytes = ByteBuffer.allocateDirect(shapes.size() * 4 * stride);
        for (SdfShape s : shapes) {
            float margin = s.glowRadius() + PAD;
            float x0 = s.x() - margin, x1 = s.x() + s.w() + margin;
            float y0 = s.y() - margin, y1 = s.y() + s.h() + margin;
            putVertex(bytes, x0, y0, s);
            putVertex(bytes, x0, y1, s);
            putVertex(bytes, x1, y1, s);
            putVertex(bytes, x1, y0, s);
        }
        bytes.flip();

        // Cached inside the VertexFormat - do NOT close the returned buffer.
        GpuBuffer vertexBuffer = SdfPipelines.SDF_SHAPE_FORMAT.uploadImmediateVertexBuffer(bytes);

        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        int indexCount = shapes.size() * 6;
        GpuBuffer indexBuffer = sequential.getBuffer(indexCount);

        RenderTarget target = mc.getMainRenderTarget();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "jujutsumod:sdf_shapes",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.setPipeline(SdfPipelines.SDF_SHAPE);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, sequential.type());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    private void putVertex(ByteBuffer b, float px, float py, SdfShape s) {
        b.putFloat(px).putFloat(py).putFloat(0.0f);                                  // Position
        b.putFloat(s.x()).putFloat(s.y()).putFloat(s.w()).putFloat(s.h());           // ShapeRect
        b.putFloat(s.radius()).putFloat(s.borderWidth()).putFloat(s.glowRadius()).putFloat(s.highlight()); // ShapeParams
        putColor(b, s.fillTopArgb());
        putColor(b, s.fillBottomArgb());
        putColor(b, s.borderArgb());
        putColor(b, s.glowArgb());
    }

    private void putColor(ByteBuffer b, int argb) {
        b.putFloat(((argb >> 16) & 0xFF) / 255.0f);
        b.putFloat(((argb >> 8) & 0xFF) / 255.0f);
        b.putFloat((argb & 0xFF) / 255.0f);
        b.putFloat(((argb >>> 24) & 0xFF) / 255.0f * globalAlpha);
    }

    @Override
    public void close() {
        projection.close();
    }
}
