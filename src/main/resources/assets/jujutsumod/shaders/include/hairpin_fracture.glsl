#ifndef JUJUTSUMOD_HAIRPIN_FRACTURE
#define JUJUTSUMOD_HAIRPIN_FRACTURE

float hairpin_hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float hairpin_fracture_noise(vec2 uv) {
    vec2 cell = floor(uv);
    vec2 local = fract(uv);
    float a = hairpin_hash(cell);
    float b = hairpin_hash(cell + vec2(1.0, 0.0));
    float c = hairpin_hash(cell + vec2(0.0, 1.0));
    float d = hairpin_hash(cell + vec2(1.0, 1.0));
    vec2 f = local * local * (3.0 - 2.0 * local);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float hairpin_broken_line(vec2 uv, float width) {
    float center = abs(uv.y - 0.5);
    float cracks = hairpin_fracture_noise(vec2(uv.x * 18.0, uv.y * 5.0));
    float broken_width = width * mix(0.45, 1.25, cracks);
    float line = 1.0 - smoothstep(broken_width, broken_width + 0.018, center);
    float gaps = step(0.18, hairpin_fracture_noise(vec2(uv.x * 24.0, 3.0)));
    return line * gaps;
}

#endif
