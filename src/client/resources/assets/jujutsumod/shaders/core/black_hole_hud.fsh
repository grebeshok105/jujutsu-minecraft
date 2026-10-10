#version 150

// HUD warp composite for the black hole (copy-based variant):
//   HudPre  — framebuffer before the GUI drew (the clean world)
//   HudPost — framebuffer after the GUI drew (world + HUD)
// The pass restores HudPre everywhere, then pulls HudPost toward the hole centre through the same
// field as the world warp and blends it in only where the HUD actually changed the pixel —
// a geometric pull, not a fade.

layout(std140) uniform BlackHoleHud {
    vec4 Screen;   // xy: hole centre in UV space, z: ring radius in UV space, w: aspect (w/h)
    vec4 Params;   // x: hud warp strength, y: jolt, z: camera-inside flag, w: unused
};

uniform sampler2D HudPre;
uniform sampler2D HudPost;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Inside the horizon the HUD dies with the world: absolute black.
    if (Params.z > 0.5) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 dvec = texCoord - Screen.xy;
    float r = length(dvec * vec2(Screen.w, 1.0));
    vec2 dirS = r > 1e-5 ? dvec / r : vec2(0.0);
    float ring = max(Screen.z, 0.02);
    float bump = (ring * ring) / (r * r + ring * ring * 0.55);
    float pull = Params.x * (0.038 * bump + 0.006 * exp(-r * 1.4)) + Params.y * 0.10;
    vec2 uvW = clamp(texCoord + dirS * pull, vec2(0.0), vec2(1.0));

    vec3 pre = texture(HudPre, texCoord).rgb;
    vec3 post = texture(HudPost, uvW).rgb;

    // HUD presence at the warped source pixel: how much the GUI changed what we sampled.
    vec3 preW = texture(HudPre, uvW).rgb;
    float mask = smoothstep(0.02, 0.12, length(post - preW) * 2.0);

    fragColor = vec4(mix(pre, post, mask), 1.0);
}
