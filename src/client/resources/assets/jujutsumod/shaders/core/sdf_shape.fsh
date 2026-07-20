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

    // --- fill: vertical gradient, antialiased interior mask ---
    float inside = 1.0 - smoothstep(-aa, aa, d);
    float gradT  = clamp((fragPos.y - shapeRect.y) / max(shapeRect.w, 0.0001), 0.0, 1.0);
    vec4  fill   = mix(fillTop, fillBottom, gradT);

    // --- border: ring just inside the edge, width = borderWidth ---
    float borderT = inside * smoothstep(-borderWidth - aa, -borderWidth + aa, d);
    borderT *= step(0.5, borderWidth);              // disabled when borderWidth < 0.5px

    // --- outer glow: emissive halo outside the shape, squared falloff ---
    float glowT = step(0.0, d) * (1.0 - smoothstep(0.0, max(glowRadius, 0.001), d));
    glowT *= glowT;

    // --- drop shadow: dark offset evaluation (shifted 3px down) ---
    float dShadow   = sdRoundedBox(fragPos - center - vec2(0.0, 3.0), halfSize, radius);
    float shadowT   = (1.0 - smoothstep(-aa, aa + 2.0, dShadow)) * 0.4;
    shadowT *= (1.0 - inside);                       // shadow only shows outside the fill

    // --- top highlight: thin bright band just inside the top edge ---
    float hlT = inside * (1.0 - smoothstep(0.5, 2.5, fragPos.y - shapeRect.y)) * highlight;

    // --- composite ---
    vec3  surface     = mix(fill.rgb, borderColor.rgb, borderT);
    surface           = mix(surface, vec3(1.0), hlT * 0.35);
    float surfaceA    = fill.a * inside;

    vec3  outsideRgb  = glowColor.rgb * (glowT * glowColor.a);
    float outsideA    = max(glowT * glowColor.a, shadowT);

    vec3  rgb = mix(outsideRgb, surface, inside);
    float a   = mix(outsideA, surfaceA, inside);

    if (a <= 0.0) { discard; }
    fragColor = vec4(rgb, a) * ColorModulator;
}
