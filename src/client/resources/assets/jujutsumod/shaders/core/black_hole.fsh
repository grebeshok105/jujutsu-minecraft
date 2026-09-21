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
// std140, 352 bytes.
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
    vec4 CosmosParams;   // x..w: per-spawn seed offsets (nebula, hue, stars, planets)
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
// procedural cosmos: per-spawn seeded nebula + stars + planets. Evaluated on the
// (possibly warped) view direction, so the sky bends with the lensing.

float hash31(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.zxy + 31.31);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.yxz + 19.19);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i);
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise3(p);
        p = p * 2.07 + vec3(13.3, 7.1, 5.7);
        a *= 0.5;
    }
    return v;
}

// Octahedral map of the direction sphere — seamless 2D domain for cell stars.
vec2 octa(vec3 d) {
    d /= (abs(d.x) + abs(d.y) + abs(d.z));
    if (d.z < 0.0) {
        vec2 s = vec2(d.x >= 0.0 ? 1.0 : -1.0, d.y >= 0.0 ? 1.0 : -1.0);
        d.xy = (1.0 - abs(d.yx)) * s;
    }
    return d.xy;
}

float stars(vec3 dir, float scale, float thresh, float seed) {
    vec2 sp = octa(dir) * scale + seed;
    vec2 cell = floor(sp);
    vec2 f = fract(sp);
    float h = hash21(cell + seed);
    if (h < thresh) {
        return 0.0;
    }
    vec2 pos = vec2(hash21(cell + 7.7 + seed), hash21(cell + 3.1 + seed));
    float d = length(f - pos);
    float bright = (h - thresh) / (1.0 - thresh);
    return smoothstep(0.09, 0.0, d) * (0.35 + 0.65 * bright * bright);
}

vec3 planetColor(float h, vec3 pdir, vec3 dir, float prad, float ang) {
    float band = vnoise3(pdir * 4.0 + vec3(0.0, dir.y * 9.0, h * 40.0));
    vec3 base = mix(vec3(0.45, 0.30, 0.55), vec3(0.25, 0.45, 0.60), fract(h * 7.3));
    base = mix(base, vec3(0.65, 0.45, 0.25), step(0.75, fract(h * 3.7)) * 0.6);
    vec3 surf = base * (0.55 + 0.45 * band);
    float limb = smoothstep(1.0, 0.35, ang / prad);
    return surf * (0.35 + 0.75 * limb);
}

vec3 planets(vec3 dir, float seed) {
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec3 hd = hash33(vec3(seed * 0.31, fi * 17.0, seed * 0.17));
        if (hd.x < 0.35) {
            continue; // this slot is empty this spawn
        }
        vec3 pdir = hd - 0.5 + vec3(0.001);
        // Bias into the upper hemisphere so planets hang in the sky, not under the dirt.
        pdir.y = abs(pdir.y) * 0.8 + 0.12;
        pdir = normalize(pdir);
        float ang = acos(clamp(dot(dir, pdir), -1.0, 1.0));
        float prad = 0.025 + 0.055 * fract(hd.y * 13.7);
        float disc = smoothstep(prad, prad * 0.82, ang);
        if (disc > 0.0) {
            acc += planetColor(hd.z, pdir, dir, prad, ang) * disc;
        }
        // Halo + occasional ring.
        acc += vec3(0.35, 0.40, 0.60) * exp(-ang * ang / (prad * prad * 6.0)) * 0.10;
        if (hd.z > 0.62) {
            vec3 rn = normalize(cross(pdir, vec3(0.31, 0.9, 0.2)));
            float plane = abs(dot(dir - pdir, rn)) / prad;
            float rr = ang / prad;
            float ring = smoothstep(0.30, 0.12, plane) * smoothstep(2.6, 1.9, rr) * smoothstep(1.15, 1.45, rr);
            acc += vec3(0.55, 0.50, 0.62) * ring * 0.5 * (1.0 - disc);
        }
    }
    return acc;
}

vec3 cosmos(vec3 dir) {
    float sNeb = CosmosParams.x;
    float sHue = CosmosParams.y;
    float sStar = CosmosParams.z;
    float sPl = CosmosParams.w;

    // Domain-warped 3D fbm — organic swirls, different every spawn.
    vec3 q = dir * 2.1 + vec3(sNeb * 37.0, sNeb * 11.0, sNeb * 23.0);
    vec3 w = vec3(fbm3(q), fbm3(q + vec3(5.2, 1.3, 2.8)), fbm3(q + vec3(9.1, 4.4, 7.7)));
    float dens = fbm3(q + (w - 0.5) * 1.9);
    float hue = fbm3(q * 0.55 + vec3(sHue * 53.0, sHue * 29.0, sHue * 71.0));
    float hot = fbm3(q * 1.4 + vec3(sHue * 97.0, sHue * 61.0, sHue * 13.0));

    // Palette: deep space → violet → blue → teal, rare amber embers. The whole ramp
    // rotates by a seed-driven shift so different spawns read as different skies.
    float shift = fract(sHue * 5.0);
    vec3 midA = mix(vec3(0.16, 0.07, 0.28), vec3(0.06, 0.20, 0.30), smoothstep(0.35, 0.75, shift));
    vec3 midB = mix(vec3(0.10, 0.22, 0.46), vec3(0.30, 0.12, 0.40), smoothstep(0.15, 0.55, fract(shift + 0.5)));
    vec3 midC = mix(vec3(0.10, 0.42, 0.42), vec3(0.50, 0.28, 0.12), smoothstep(0.6, 0.95, shift));
    vec3 col = vec3(0.010, 0.012, 0.030);
    col = mix(col, midA, smoothstep(0.38, 0.72, dens));
    col = mix(col, midB, smoothstep(0.55, 0.85, dens) * (0.35 + 0.65 * hue));
    col = mix(col, midC, smoothstep(0.62, 0.92, dens) * smoothstep(0.55, 0.8, hue) * 0.8);
    col = mix(col, vec3(0.55, 0.30, 0.10), smoothstep(0.78, 0.95, dens) * smoothstep(0.72, 0.9, hot) * 0.7);
    // Faint green wisps, sparse.
    col += vec3(0.05, 0.22, 0.12) * smoothstep(0.70, 0.95, fbm3(q * 0.8 + vec3(sHue * 41.0))) * 0.5;

    // Two star layers: dense faint dust + sparse bright sparks.
    col += vec3(0.9, 0.93, 1.0) * stars(dir, 60.0, 0.76, sStar * 91.0) * 0.6;
    col += vec3(1.0, 0.98, 0.92) * stars(dir, 26.0, 0.90, sStar * 37.0) * 1.2;

    col += planets(dir, sPl);
    return col;
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

    // Radial brightness: hot inner rim, then a broad luminous body that stays bright far
    // enough to be seen PAST the shadow's edge — a steep decay hides the disk inside the dome.
    float x = (rad - rIn) / (rOut - rIn);
    float profile = smoothstep(0.0, 0.04, x) * (pow(1.0 - x, 0.9) * 0.75 + 0.25 * (1.0 - x));
    profile += 0.9 * smoothstep(0.0, 0.03, x) * exp(-x * 16.0); // hot inner rim

    // Gentle shear streaks — slow brightness drift along the flow, not holes in the ring.
    float streaks = fbm(vec2(rad * 0.7, ang * 2.6 + rot * 2.2));
    streaks = streaks * 0.8 + 0.35 * fbm(vec2(rad * 1.9, ang * 5.0 - rot * 3.5));
    // Rare bright knots riding the flow — small boosts, never dropouts.
    float clump = pow(fbm(vec2(rad * 0.5, ang * 1.6 + rot * 1.2)), 3.0) * 1.1;
    float flash = pow(vnoise(vec2(ang * 4.0 + rot * 2.0, rad * 0.7 - Params1.z * 0.35)), 6.0) * 1.6;
    float tex = (0.72 + 0.55 * streaks) * (1.0 + clump + flash);

    // Vertical falloff inside the slab.
    float vert = 1.0 - (s * s) / (half_ * half_);

    // Doppler-ish beaming: the side moving toward the camera burns brighter.
    vec3 velDir = normalize(cross(n, planar));
    float beam = 1.0 + 1.9 * max(0.0, dot(velDir, -viewDir)) - 0.55 * max(0.0, dot(velDir, viewDir));

    // Depth asymmetry: the far side sits behind the hole — dim it just enough to read as a 3D
    // object, never enough to break the ring.
    float farSide = 1.0 - 0.30 * max(0.0, dot(normalize(planar), viewDir));

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
    // Implosion: space itself is sucked inward — the swirl spikes with the jolt, not after it.
    float swirl = lens * 0.085 * bump + jolt * 0.30 * bump;
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

    // Implosion point flash: as the shadow collapses to a point it flares once, then dies.
    float implFlash = exp(-b * b / max(R * R * 0.09, 0.04))
                    * smoothstep(0.6, 0.0, collapse) * smoothstep(0.0, 0.15, collapse) * 6.0;


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

    // --- cosmos: the sky becomes deep space, geometry dissolves into it ---
    // Sky test on the WARPED sample (the pixel actually shown); the unwarped depth still
    // drives the march/capture math above.
    float skyDepth = texture(SceneDepthSampler, uvW).r;
    float cosmosAmt = smoothstep(0.15, 0.85, intensity);
    if (cosmosAmt > 0.001) {
        vec3 dirW = normalize(relWorldPos(vec3(uvW, 0.5)));
        vec3 space = cosmos(dirW);
        float isSky = step(0.9999, skyDepth);
        // Blocks fall into space too: terrain dissolves starting ~35 blocks out, fully cosmic
        // by ~120 — the world ends, not just the sky.
        float endDistW = length(relWorldPos(vec3(uvW, skyDepth)));
        float far = smoothstep(35.0, 120.0, endDistW);
        float dissolve = max(isSky, far) * cosmosAmt;
        scene = mix(scene, space, dissolve);
        // Near geometry keeps its shape but drowns in the ambient dark + nebula tint.
        scene = mix(scene, scene * vec3(0.38, 0.34, 0.55) + space * 0.10, (1.0 - isSky) * cosmosAmt * 0.55);
    }

    vec3 holeLight = vec3(diskLum * 2.6 + ring * 1.2 + edgeGlow + implFlash);
    vec3 col = scene + holeLight;
    // Soft HDR rolloff so the disk blows out to white instead of clipping ugly.
    col = col / (1.0 + col * 0.16);
    // Captured rays keep ONLY the equatorial band gathered before the horizon — a continuous
    // bright line across the shadow, not a ragged remnant. Sub-gate regions get a 55%
    // floor instead of black so the line never breaks into segments.
    float bg = smoothstep(0.35, 1.0, diskLum);
    vec3 band = vec3(diskLum * 2.6) * max(bg, 0.55 * smoothstep(0.10, 0.35, diskLum));

    vec3 capturedCol = band / (1.0 + band * 0.16);
    fragColor = vec4(mix(col, capturedCol, captured), 1.0);

}
