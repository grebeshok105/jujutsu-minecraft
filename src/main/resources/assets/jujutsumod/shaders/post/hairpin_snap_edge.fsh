#version 150

#moj_import <jujutsumod:hairpin_fracture.glsl>
#moj_import <jujutsumod:hairpin_timeline.glsl>

uniform sampler2D DiffuseSampler;
uniform float HairpinTime;

in vec2 texCoord;
out vec4 fragColor;

const vec3 DARK_CARMINE = vec3(0.3569, 0.0627, 0.1059);
const vec3 DIRTY_FUCHSIA = vec3(0.5412, 0.1843, 0.3451);

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float snap = hairpin_snap_gate(HairpinTime);
    float crack = hairpin_broken_line(texCoord, 0.045);
    vec3 accent = mix(DARK_CARMINE, DIRTY_FUCHSIA, crack * 0.35);
    float influence = snap * crack * 0.32;
    fragColor = vec4(mix(src.rgb, accent, influence), src.a);
}
