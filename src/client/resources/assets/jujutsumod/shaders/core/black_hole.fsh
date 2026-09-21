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
    vec4 DiskParams;     // x: disk inner radius, y: disk outer radius, z: disk half-thickness, w: collapse (1=alive, 0=gone)
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
    float rIn = DiskParams.x * DiskParams.w;
    float rOut = DiskParams.y * DiskParams.w;
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

    // Turbulence: two counter-drifting shear layers so the surface never reads as one texture.
    float streaks = fbm(vec2(rad * 0.9, ang * 3.4 + rot * 3.0));
    streaks = streaks * 0.75 + 0.45 * fbm(vec2(rad * 2.3, ang * 7.0 - rot * 5.0));
    // Bright knots riding the flow: sparse clumps plus rarer local flashes.
    float clump = pow(fbm(vec2(rad * 0.5, ang * 1.6 + rot * 1.2)), 3.0) * 2.4;
    float flash = pow(vnoise(vec2(ang * 4.0 + rot * 2.0, rad * 0.7 - Params1.z * 0.35)), 6.0) * 3.0;
    float tex = (0.40 + 0.95 * streaks) * (1.0 + clump + flash);

    // Vertical falloff inside the slab.
    float vert = 1.0 - (s * s) / (half_ * half_);

    // Doppler-ish beaming: the side moving toward the camera burns brighter.
    vec3 velDir = normalize(cross(n, planar));
    float beam = 1.0 + 1.9 * max(0.0, dot(velDir, -viewDir)) - 0.55 * max(0.0, dot(velDir, viewDir));

    // Depth asymmetry: the far side of the disk sits behind the hole — dim it so the ring reads
    // as a 3D object with a near and a far half, not a flat Ø sign.
    float farSide = 1.0 - 0.62 * max(0.0, dot(normalize(planar), viewDir));

    return profile * tex * vert * beam * farSide * Params0.z;
}

// ---------------------------------------------------------------------------
// bent-ray march: disk luminance + the ray's deepest dip below the disk plane (for the
// under-image attenuation — light that swings under the hole is the far side's ghost image).

vec2 march(vec3 dir, float maxDist) {
    vec3 p = vec3(0.0);
    vec3 d = dir;
    float R = CenterRadius.w * DiskParams.w;
    float bendK = R * 1.35;
    float traveled = 0.0;
    float accum = 0.0;
    float minS = 0.0;

    for (int i = 0; i < STEPS; i++) {
        float dist = length(p - CenterRadius.xyz);
        if (dist < R * 0.96) {
            break;
        }
        float stepLen = clamp(dist * 0.16, 0.22, 4.0);
        // Gravitational bend toward the centre, scaled by the step.
        vec3 toC = (CenterRadius.xyz - p) / dist;
        d = normalize(d + toC * (bendK / (dist * dist)) * stepLen);
        p += d * stepLen;
        traveled += stepLen;
        minS = min(minS, dot(p - CenterRadius.xyz, DiskNormal.xyz));

        if (traveled < maxDist) {
            accum += diskEmission(p, d) * stepLen;
        }
        if (dist > 420.0 || traveled > MAX_MARCH) {
            break;
        }
    }
    return vec2(accum, minS);
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
    float collapse = DiskParams.w;
    // The object shrinks to nothing during the disappearance: every radius scales by collapse.
    float R = CenterRadius.w * collapse;
    float Rs = R * 2.6; // capture shadow radius, analytic
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

    float marchReach = DiskParams.y * collapse * 2.5 + R * 4.0;
    vec2 mr = b < marchReach ? march(dir, maxDist) : vec2(0.0);
    // Light that dipped far below the disk plane is the far side's ghost image under the hole —
    // keep it faint so the shadow stays a void, not a grey ball.
    float diskLum = mr.x * (1.0 - smoothstep(0.5, 4.0, -mr.y));


    // Analytic capture: the shadow is a perfect ray-sphere silhouette — no march aliasing, no
    // jagged under-cuts. Only counts when the sphere is closer than the scene surface.
    float tHit = dot(dir, CenterRadius.xyz) - sqrt(max(Rs * Rs - b * b, 0.0));
    float captured = (b < Rs && tHit > 0.0 && tHit < maxDist) ? 1.0 : 0.0;

    // --- photon ring / edge glow on the unbent impact parameter ---
    float bCrit = Rs;
    float ring = exp(-pow((b - bCrit) / (R * 0.13 + 0.001), 2.0)) * intensity * 1.6;
    float edgeGlow = exp(-max(b - R, 0.0) / (R * 0.30 + 0.001)) * 0.16 * intensity;
    // Ring only where the hole is in front of the scene surface.
    float ringVis = step(length(CenterRadius.xyz), maxDist);
    ring *= ringVis;
    edgeGlow *= ringVis;


    // --- grade ---
    float lum = dot(scene, vec3(0.299, 0.587, 0.114));
    scene = mix(scene, vec3(lum), Params0.w);
    scene *= 1.0 - 0.22 * intensity;
    // The warp drags bright sky pixels down onto the ground under the hole — a grey dome that
    // reads as a rendering artifact. Pixels below the projected centre near the shadow fall
    // into shade instead.
    float under = (1.0 - smoothstep(ringScreen * 0.5, ringScreen * 2.6, r))
                * smoothstep(-0.02, 0.10, screenC.y - uv.y);
    scene *= 1.0 - 0.95 * under;
    // Slight corner falloff while the effect holds: the world submits at the edges first.
    float vig = 1.0 - 0.30 * intensity * smoothstep(0.35, 1.15, length(dvecA));
    scene *= vig;

    vec3 holeLight = vec3(diskLum * 2.2 + ring * 1.2 + edgeGlow);
    vec3 col = scene + holeLight;
    // Soft HDR rolloff so the disk blows out to white instead of clipping ugly.
    col = col / (1.0 + col * 0.16);
    // Captured rays keep ONLY the bright equatorial band gathered before the horizon — the
    // diffuse lensed under-image dies, so the shadow interior is true black, not a grey ball.
    vec3 band = vec3(diskLum * 2.2) * smoothstep(0.55, 1.4, diskLum);

    vec3 capturedCol = band / (1.0 + band * 0.16);
    fragColor = vec4(mix(col, capturedCol, captured), 1.0);

}
