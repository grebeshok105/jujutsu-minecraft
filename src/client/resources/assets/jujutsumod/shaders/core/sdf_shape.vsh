#version 150

// Uniform blocks copied verbatim from vanilla core/gui.vsh (inlined, no moj_import, so the
// shader is self-contained and safe at startup).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 ShapeRect;
in vec4 ShapeParams;
in vec4 FillTop;
in vec4 FillBottom;
in vec4 BorderColor;
in vec4 GlowColor;

out vec2 fragPos;
out vec4 shapeRect;
out vec4 shapeParams;
out vec4 fillTop;
out vec4 fillBottom;
out vec4 borderColor;
out vec4 glowColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // ModelViewMat only translates z (-11000), so Position.xy is GUI screen space. The quad
    // spans the shape's (glow-expanded) bounds, so interpolating Position.xy gives each
    // fragment its exact screen coordinate for SDF evaluation.
    fragPos = Position.xy;
    shapeRect = ShapeRect;
    shapeParams = ShapeParams;
    fillTop = FillTop;
    fillBottom = FillBottom;
    borderColor = BorderColor;
    glowColor = GlowColor;
}
