#version 150

// Black hole post-effect: bent-ray accretion disk + event horizon + screen-space gravitational
// warp + monochrome grade, composited over a copy of the rendered world (hand included).
//
// Two cooperating mechanisms, one pass:
//   1. Screen-space warp displaces the sampled scene UV toward/around the projected centre —
//      this is what bends the world, the sky and the hand around the hole.
//   2. A short bent-ray march from the eye accumulates accretion-disk luminance and decides
//      capture (event horizon). The march runs on the UNWARPED direction so the disk stays a
//      coherent 3D object while the background warps around it.
//
// std140, 320 bytes.
layout(std140) uniform BlackHoleData {
    mat4 InvProjMat;
    mat4 InvViewMat;
    mat4 ProjMat;
    mat4 ViewMat;
    vec4 CenterRadius;   // xyz: centre, camera-relative world; w: horizon radius
    vec4 DiskNormal;     // xyz: disk plane normal (camera-relative world); w: disk phase
    vec4 Params0;        // x: intensity, y: lensStrength, z: diskIntensity, w: desaturation
    vec4 Params1;        // x: jolt, y: aspect ratio (w/h), z: time seconds, w: camera-inside flag
    vec4 DiskParams;     // x: disk inner radius, y: disk outer radius, z: disk half-thickness, w: unused
};

uniform sampler2D SceneSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

#define STEPS 64
#define MAX_MARCH 512.0
#define PI 3.14159265

// ---------------------------------------------------------------------------
// helpers

vec3 relWorldPos(vec3 uvz) {
    vec3 ndc = uvz * 2.0 - 1.0;
    vec4 viewHom = InvProjMat * vec4(ndc, 1.0);
    vec3 viewPos = viewHom.xyz / viewHom.w;
    return (InvViewMat * vec4(viewPos, 1.0)).xyz;
}

float hash21(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.13 + vec2(17.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// ---------------------------------------------------------------------------
// accretion disk emission at a camera-relative world point

float diskEmission(vec3 p, vec3 viewDir) {
    vec3 n = DiskNormal.xyz;
    vec3 rel = p - CenterRadius.xyz;
    float s = dot(rel, n);
    float half_ = DiskParams.z;
    if (abs(s) > half_) {
        return 0.0;
    }
    vec3 planar = rel - n * s;
    float rad = length(planar);
    float rIn = DiskParams.x;
    float rOut = DiskParams.y;
    if (rad < rIn || rad > rOut) {
        return 0.0;
    }

    // Disk-local frame for angle/rotation.
    vec3 u = normalize(cross(n, vec3(0.0, 1.0, 0.001)));
    vec3 v = cross(n, u);
    float ang = atan(dot(planar, v), dot(planar, u));

    // Differential rotation: inner edge faster. Slow, stately — not a whirlpool.
    float rot = DiskNormal.w + Params1.z * 0.55 * pow(rIn / rad, 1.5);

    // Radial brightness: a hard hot rim right at the inner edge, then a decaying tail.
    float x = (rad - rIn) / (rOut - rIn);
    float profile = smoothstep(0.0, 0.06, x) * pow(1.0 - x, 1.7);
    profile += 0.55 * smoothstep(0.0, 0.03, x) * exp(-x * 14.0); // hot inner rim

    // Turbulence: stretched along the flow so it reads as shear, not noise soup.
    float tex = fbm(vec2(rad * 0.9, ang * 3.4 + rot * 3.0));
    tex = 0.45 + 1.10 * tex;

    // Vertical falloff inside the slab.
    float vert = 1.0 - (s * s) / (half_ * half_);

    // Doppler-ish beaming: the side moving toward the camera burns brighter.
    vec3 velDir = normalize(cross(n, planar));
    float beam = 1.0 + 1.9 * max(0.0, dot(velDir, -viewDir)) - 0.55 * max(0.0, dot(velDir, viewDir));

    return profile * tex * vert * beam * Params0.z;
}

// ---------------------------------------------------------------------------
// bent-ray march: disk luminance + horizon capture

vec2 march(vec3 dir, float maxDist) {
    vec3 p = vec3(0.0);
    vec3 d = dir;
    float R = CenterRadius.w;
    float bendK = R * 1.35;
    float traveled = 0.0;
    float accum = 0.0;
    float horizonT = -1.0;

    for (int i = 0; i < STEPS; i++) {
        float dist = length(p - CenterRadius.xyz);
        if (dist < R * 0.96) {
            horizonT = traveled;
            break;
        }
        float stepLen = clamp(dist * 0.16, 0.22, 4.0);
        // Gravitational bend toward the centre, scaled by the step.
        vec3 toC = (CenterRadius.xyz - p) / dist;
        d = normalize(d + toC * (bendK / (dist * dist)) * stepLen);
        p += d * stepLen;
        traveled += stepLen;

        if (traveled < maxDist) {
            accum += diskEmission(p, d) * stepLen;
        }
        if (dist > 420.0 || traveled > MAX_MARCH) {
            break;
        }
    }
    // captured only counts if the horizon is closer than the scene surface.
    float captured = (horizonT >= 0.0 && horizonT < maxDist) ? 1.0 : 0.0;
    return vec2(accum, captured);
}

// ---------------------------------------------------------------------------

void main() {
    // Inside the horizon: absolute black, no inner world.
    if (Params1.w > 0.5) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float intensity = Params0.x;
    float lens = Params0.y;
    float jolt = Params1.x;
    float aspect = Params1.y;
    // --- project the centre to screen space for the warp field ---
    vec4 clipC = ProjMat * ViewMat * vec4(CenterRadius.xyz, 1.0);
    bool behind = clipC.w <= 0.001;
    vec2 ndcC = clipC.xy / max(clipC.w, 0.001);
    vec2 screenC = ndcC * 0.5 + 0.5;

    // Einstein-ring radius on screen: project a point one ring-radius above the centre.
    float ringWorld = CenterRadius.w * 2.6;
    vec4 clipR = ProjMat * ViewMat * vec4(CenterRadius.xyz + vec3(0.0, ringWorld, 0.0), 1.0);
    vec2 ndcR = clipR.xy / max(clipR.w, 0.001);
    float ringNdc = max(length(ndcR - ndcC), 0.02);

    // --- screen-space warp ---
    vec2 uv = texCoord;
    vec2 dvec = uv - screenC;
    vec2 dvecA = dvec * vec2(aspect, 1.0);
    float r = length(dvecA);
    vec2 dirS = r > 1e-5 ? dvec / r : vec2(0.0);
    vec2 perp = vec2(-dirS.y, dirS.x);

    float ringScreen = ringNdc * 0.5;
    // Lensing bump: peaks near the ring, dies off both ways. Off-screen centre still leaves the
    // global pull so the world stays subdued when the player looks away.
    float bump = (ringScreen * ringScreen) / (r * r + ringScreen * ringScreen * 0.55);
    float pull = lens * (0.16 * bump + 0.018 * exp(-r * 1.4));
    float swirl = lens * 0.085 * bump;
    // Disappearance jolt: a single hard radial displacement with a ripple.
    float j = jolt * (0.30 + 0.10 * sin(r * 46.0 - Params1.z * 30.0));
    vec2 warp = dirS * (-pull - j) + perp * swirl;
    if (behind) {
        warp = dirS * (-lens * 0.010 - j * 0.4);
    }
    vec2 uvW = clamp(uv + warp, vec2(0.001), vec2(0.999));

    vec3 scene = texture(SceneSampler, uvW).rgb;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;

    // --- ray for the 3D march (unwarped direction) ---
    vec3 dir = normalize(relWorldPos(vec3(texCoord, 0.5)));
    vec3 scenePoint = relWorldPos(vec3(texCoord, sceneDepth));
    float endDist = length(scenePoint);
    float maxDist = (endDist >= 0.0 && endDist < MAX_MARCH) ? endDist + 1.0 : MAX_MARCH;

    // Early-out: rays whose straight-line miss distance already exceeds the reach of the bend
    // can never see disk or horizon — skip the march entirely (most of the screen).
    float b = length(cross(dir, CenterRadius.xyz));
    float marchReach = DiskParams.y * 2.5 + CenterRadius.w * 4.0;
    vec2 mr = b < marchReach ? march(dir, maxDist) : vec2(0.0);
    float diskLum = mr.x;
    float captured = mr.y;

    // --- photon ring / edge glow on the unbent impact parameter ---
    float bCrit = CenterRadius.w * 2.6;
    float ring = exp(-pow((b - bCrit) / (CenterRadius.w * 0.13), 2.0)) * intensity * 1.6;
    float edgeGlow = exp(-max(b - CenterRadius.w, 0.0) / (CenterRadius.w * 0.30)) * 0.22 * intensity;
    // Ring only where the hole is in front of the scene surface.
    float ringVis = step(length(CenterRadius.xyz), maxDist);
    ring *= ringVis;
    edgeGlow *= ringVis;


    // --- grade ---
    float lum = dot(scene, vec3(0.299, 0.587, 0.114));
    scene = mix(scene, vec3(lum), Params0.w);
    scene *= 1.0 - 0.22 * intensity;
    // Slight corner falloff while the effect holds: the world submits at the edges first.
    float vig = 1.0 - 0.30 * intensity * smoothstep(0.35, 1.15, length(dvecA));
    scene *= vig;

    vec3 holeLight = vec3(diskLum * 3.0 + ring * 1.5 + edgeGlow);
    vec3 col = scene + holeLight;
    // Soft HDR rolloff so the disk blows out to white instead of clipping ugly.
    col = col / (1.0 + col * 0.12);
    // Captured rays keep the disk light gathered before the horizon: the equatorial band and the
    // lensed arcs still reach the eye — only the world behind the shadow is gone.
    vec3 capturedCol = holeLight / (1.0 + holeLight * 0.12);
    fragColor = vec4(mix(col, capturedCol, captured), 1.0);
}
