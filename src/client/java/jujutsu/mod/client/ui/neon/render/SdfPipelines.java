package jujutsu.mod.client.ui.neon.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * Owns the custom SDF shape pipeline used by the neon dashboard.
 *
 * MC 1.21.6+ replaced the old ShaderProgram/json system with RenderPipeline defined in Java.
 * Core shaders are plain .vsh/.fsh files under assets/<ns>/shaders/core/, auto-discovered by
 * ShaderManager on resource reload. Registration happens via RenderPipelines.register (there is
 * no Fabric callback for this in 1.21.8).
 */
public final class SdfPipelines {
    // Custom per-vertex attributes. Vanilla reserves element ids 0-5; GL attribute LOCATION is
    // the order elements are added to the format (GlProgram.link binds by name), the id only
    // needs to be unique among registered elements.
    private static final VertexFormatElement SHAPE_RECT =
            VertexFormatElement.register(6, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
    private static final VertexFormatElement SHAPE_PARAMS =
            VertexFormatElement.register(7, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
    private static final VertexFormatElement FILL_TOP =
            VertexFormatElement.register(8, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
    private static final VertexFormatElement FILL_BOTTOM =
            VertexFormatElement.register(9, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
    private static final VertexFormatElement BORDER_COLOR =
            VertexFormatElement.register(10, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
    private static final VertexFormatElement GLOW_COLOR =
            VertexFormatElement.register(11, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);

    // Order defines both the interleaved byte layout and the GL attribute locations.
    // Stride = 12 (pos) + 6 * 16 (six vec4) = 108 bytes = 27 floats.
    public static final VertexFormat SDF_SHAPE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("ShapeRect", SHAPE_RECT)       // x, y (top-left), w, h  [screen px]
            .add("ShapeParams", SHAPE_PARAMS)   // cornerRadius, borderWidth, glowRadius, highlight
            .add("FillTop", FILL_TOP)           // RGBA top of vertical gradient
            .add("FillBottom", FILL_BOTTOM)     // RGBA bottom
            .add("BorderColor", BORDER_COLOR)   // RGBA border ring
            .add("GlowColor", GLOW_COLOR)       // RGB + intensity in A
            .build();

    public static final int VERTEX_STRIDE = SDF_SHAPE_FORMAT.getVertexSize();

    public static final RenderPipeline SDF_SHAPE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("jujutsumod", "pipeline/sdf_shape"))
                    // ResourceLocation overloads are mandatory: the String overloads force the
                    // "minecraft" namespace and reject mod ids.
                    .withVertexShader(ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/sdf_shape"))
                    .withFragmentShader(ResourceLocation.fromNamespaceAndPath("jujutsumod", "core/sdf_shape"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(SDF_SHAPE_FORMAT, VertexFormat.Mode.QUADS)
                    .build());

    private SdfPipelines() {}
}
