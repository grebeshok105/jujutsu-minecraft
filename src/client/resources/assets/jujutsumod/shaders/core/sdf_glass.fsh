#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};

in vec2 fragPos;
in vec4 shapeRect;
in vec4 shapeParams;
in vec4 fillTop;
in vec4 fillBottom;
in vec4 borderColor;
in vec4 glowColor;

// Copy of the main render target sampled before this pass; bound only when the pass runs
// with glass enabled. Non-glass shapes never branch into the sampler path.
uniform sampler2D SceneSampler;

out vec4 fragColor;

// Signed distance to a rounded box centered at the origin with half-extents b and corner
// radius r. Negative inside, positive outside, 0 at the boundary.
float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    // ShapeRect = (x, y, w, h) top-left + size in screen px.
    vec2 center   = shapeRect.xy + shapeRect.zw * 0.5;
    vec2 halfSize = shapeRect.zw * 0.5;
    float radius      = shapeParams.x;
    float borderWidth = shapeParams.y;
    float glowRadius  = shapeParams.z;
    float highlight   = shapeParams.w;

    float d  = sdRoundedBox(fragPos - center, halfSize, radius);
    float aa = max(fwidth(d), 0.0001);

    float inside = 1.0 - smoothstep(-aa, aa, d);
    float gradT  = clamp((fragPos.y - shapeRect.y) / max(shapeRect.w, 0.0001), 0.0, 1.0);
    vec4  fill   = mix(fillTop, fillBottom, gradT);

    float borderT = inside * smoothstep(-borderWidth - aa, -borderWidth + aa, d);
    borderT *= step(0.5, borderWidth);              // disabled when borderWidth < 0.5px

    float glowT = step(0.0, d) * (1.0 - smoothstep(0.0, max(glowRadius, 0.001), d));
    glowT *= glowT;

    // Glass mode is signaled by a negative highlight in ShapeParams.w; |w| stays the highlight.
    float isGlass = step(shapeParams.w, -0.001);
    float highlightAbs = abs(highlight);

    float hlT = inside * (1.0 - smoothstep(0.5, 2.5, fragPos.y - shapeRect.y)) * highlightAbs;

    if (isGlass < 0.5) {
        // --- legacy opaque path (identical to sdf_shape.fsh): no scene sampling ---
        vec3 surface     = mix(fill.rgb, borderColor.rgb, borderT);
        surface          = mix(surface, vec3(1.0), hlT * 0.35);
        float surfaceA   = fill.a * inside;

        float dShadow = sdRoundedBox(fragPos - center - vec2(0.0, 3.0), halfSize, radius);
        float shadowT = (1.0 - smoothstep(-aa, aa + 2.0, dShadow)) * 0.4;
        shadowT *= (1.0 - inside);

        vec3  outsideRgb = glowColor.rgb * (glowT * glowColor.a);
        float outsideA   = max(glowT * glowColor.a, shadowT);

        vec3 rgb = mix(outsideRgb, surface, inside);
        float a  = mix(outsideA, surfaceA, inside);

        if (a <= 0.0) { discard; }
        fragColor = vec4(rgb, a) * ColorModulator;
        return;
    }

    // --- LIQUID GLASS REFRACTION (glass shapes only; SceneSampler is bound for this pass) ---
    // The background bends toward the card center, strongest at the rim (thick glass edge),
    // plus soft lens wells around interior round elements.
    vec2 sceneSize = vec2(textureSize(SceneSampler, 0));
    vec2 uv = fragPos / sceneSize;
    vec2 toCenterN = (fragPos - center) / max(halfSize, vec2(0.0001));
    float edgeFactor = pow(clamp(length(toCenterN), 0.0, 1.0), 2.0);
    vec2 refractOffset = toCenterN * edgeFactor * 6.0;
    vec2 wellA = shapeRect.xy + vec2(shapeRect.z * 0.28, shapeRect.w * 0.5);
    vec2 wellB = shapeRect.xy + vec2(shapeRect.z * 0.72, shapeRect.w * 0.5);
    float wellR = min(shapeRect.w, shapeRect.z) * 0.30;
    refractOffset += normalize(fragPos - wellA + vec2(0.0001)) * smoothstep(wellR, 0.0, length(fragPos - wellA)) * 3.0;
    refractOffset += normalize(fragPos - wellB + vec2(0.0001)) * smoothstep(wellR, 0.0, length(fragPos - wellB)) * 3.0;

    vec3 sceneRgb = texture(SceneSampler, uv + refractOffset / sceneSize).rgb;
    // Chromatic aberration on the rim sells "thick expensive glass".
    float caAmt = edgeFactor * 2.5;
    sceneRgb.r = texture(SceneSampler, uv + (refractOffset + vec2(caAmt, 0.0)) / sceneSize).r;
    sceneRgb.b = texture(SceneSampler, uv + (refractOffset - vec2(caAmt, 0.0)) / sceneSize).b;

    // --- composite: tinted refraction under the translucent fill, bright hairline on top ---
    vec3 surface = mix(sceneRgb, fill.rgb, clamp(fill.a, 0.0, 1.0));
    surface = mix(surface, borderColor.rgb, borderT);
    surface = mix(surface, vec3(1.0), hlT * 0.35);
    float surfaceA = max(fill.a, borderT * 0.9) * inside;

    vec3  outsideRgb = glowColor.rgb * (glowT * glowColor.a);
    float outsideA   = glowT * glowColor.a;

    vec3 rgb = mix(outsideRgb, surface, inside);
    float a  = mix(outsideA, surfaceA, inside);

    if (a <= 0.0) { discard; }
    fragColor = vec4(rgb, a) * ColorModulator;
}
