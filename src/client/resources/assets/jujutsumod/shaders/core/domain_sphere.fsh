// Ported from the Orbital Railgun "strike" fragment shader.
//
//   Orbital Railgun - https://github.com/Mishkis/orbital-railgun
//   Copyright (c) Mishkis
//   MIT License - https://opensource.org/licenses/MIT
//
// Adaptation notes:
//   * Reduced to the central spherical shell (sDist below). Deliberately NOT ported: the six outer
//     spheres, the beams/columns, the ground rings, the explosion cylinder and the chromatic
//     aberration of the reference.
//   * Camera-relative world space: the shell centre and radius arrive in SphereData already offset
//     by the camera position, so no CameraPosition uniform is needed and fp32 precision holds at
//     large coordinates.
//   * The reference's iTime/localTime clock is replaced by the per-frame radius/fade/progress
//     values in SphereData: Java owns the animation, the shader stays a pure function of them.

#version 150

#define STEPS 96
#define MIN_DIST 0.01
// Near-hit band feeding the neon rim. The reference counted near-hits at ten times its march
// epsilon; with the coarser MIN_DIST pinned for this port the counter needs its own tolerance, or
// a grazing ray would accumulate one close step at most and the rim would never saturate.
#define CLOSE_DIST 0.5
// Distance ceiling for pixels with no finite scene surface (sky / cleared depth), where the
// unprojected end point is unusable as an occlusion distance.
#define MAX_MARCH 512.0

// std140, 160 bytes: inverse matrices + centre/radius + fade/expansionProgress/ageProgress.
layout(std140) uniform SphereData {
    mat4 InvProjMat;
    mat4 InvViewMat;
    vec4 CenterRadius;
    vec4 Params;
};

uniform sampler2D SceneSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

// Reference cyan, verbatim.
const vec3 blue = vec3(0.62, 0.93, 0.93);
const float PI = 3.14159265;

// Shell only: abs(distance to centre - radius), the reference's main_sphere without the outer
// spheres, beams and explosion cylinder it was smooth-min'd with.
float sDist(vec3 p) {
    return abs(length(p - CenterRadius.xyz) - CenterRadius.w);
}

// Camera-relative world position of a (uv, depth) sample: ndc -> view -> camera-relative world.
vec3 relWorldPos(vec3 uvz) {
    vec3 ndc = uvz * 2.0 - 1.0;
    vec4 viewHom = InvProjMat * vec4(ndc, 1.0);
    vec3 viewPos = viewHom.xyz / viewHom.w;
    return (InvViewMat * vec4(viewPos, 1.0)).xyz;
}

// Sphere trace of the shell. Returns (traveled, closeSteps); closeSteps counts the steps that came
// within CLOSE_DIST of the shell and drives the rim glow.
vec2 raycast(vec3 start, vec3 dir, float maxDist) {
    float traveled = 0.0;
    int closeSteps = 0;
    for (int i = 0; i < STEPS; i++) {
        float safe = sDist(start + dir * traveled);
        if (safe <= MIN_DIST || traveled >= maxDist) {
            break;
        }
        traveled += safe;
        if (safe <= CLOSE_DIST) {
            closeSteps += 1;
        }
    }
    return vec2(traveled, float(closeSteps));
}

// Light with which the scene behind the shell is tinted: 10/dist^2 base glow (darkens with
// distance, which is what shades the shell interior) plus the reference's travelling ring.
float shockwave(vec3 p) {
    float dist = sDist(p);
    // The shell sweeps through the camera while it expands from ~0, where 10/dist^2 would be inf.
    float lightDist = max(dist, 0.25);
    float light = 10.0 / (lightDist * lightDist);

    // Ring re-based on expansionProgress instead of the reference's wall clock: the rings sweep
    // outwards with the expansion and die away once it completes, leaving the static glow and the
    // darkened interior during the hold.
    float lt = clamp(Params.y, 0.0, 1.0);
    float speed = 1.0 - lt * lt;
    float ringScale = max(CenterRadius.w, 4.0);
    float phase = abs(fract(2.0 * dist / ringScale - lt * speed) - 0.5);
    // Reference ring term, with the singular phase guarded so it saturates instead of going inf.
    float ring = 0.05 / max(phase, 1.0E-3) * 2.0;
    return light + ring * (1.0 - lt);
}

void main() {
    vec3 original = texture(SceneSampler, texCoord).rgb;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;

    // View space has the eye at the origin and InvViewMat carries it into the camera-relative frame
    // the centre arrives in. The reference started the ray on the near plane; taking the eye instead
    // keeps the ray identical while removing any dependence on which ndc z this projection maps the
    // near plane to.
    vec3 start = (InvViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    // Direction is depth-independent, so one finite sample per pixel fixes it - sky pixels carry 0
    // (reversed-Z) or 1 (classic-Z) and would unproject onto, or past, the far plane.
    vec3 dir = normalize(relWorldPos(vec3(texCoord, 0.5)) - start);

    // End point of the ray at the scene surface: bounds the march and hides the shell behind
    // geometry (a hit only counts while it is closer than this point).
    vec3 scenePoint = relWorldPos(vec3(texCoord, sceneDepth));
    float endDist = distance(start, scenePoint);
    float maxDist = (endDist >= 0.0 && endDist < MAX_MARCH) ? endDist + 1.0 : MAX_MARCH;

    vec2 hitResult = raycast(start, dir, maxDist);
    vec3 hitPoint = start + dir * hitResult.x;

    // Shell colour pulses between cyan and black with the expansion; N close steps in a row light up
    // the reference's neon rim.
    vec3 shellColor = mix(blue, vec3(0.0), abs(sin(PI * Params.y)))
            + vec3(smoothstep(5.0, 10.0, hitResult.y)) * blue;

    // Paint only where the march landed on the shell, in front of the scene surface, times the
    // per-frame fade - the same double gate the reference used to let blocks cover the sphere.
    float threshold = step(sDist(hitPoint), 0.02) * step(distance(start, hitPoint), endDist) * Params.x;
    // Tint of the light: cyan while the sphere ages, neutral once it holds.
    vec3 tint = mix(blue, vec3(1.0), clamp(Params.z, 0.0, 1.0));

    fragColor = vec4(mix(original * shockwave(scenePoint) * tint, shellColor, threshold), 1.0);
}
