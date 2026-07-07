#version 150

#moj_import <jujutsumod:hairpin_fracture.glsl>
#moj_import <jujutsumod:hairpin_timeline.glsl>

uniform sampler2D DiffuseSampler;
uniform float HairpinTime;

in vec2 texCoord;
out vec4 fragColor;

const vec3 BLOOD_BLACK = vec3(0.1490, 0.0118, 0.0392);
const vec3 OXBLOOD_SHADOW = vec3(0.2275, 0.0196, 0.0588);

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float snap = hairpin_snap_gate(HairpinTime);
    float crack = hairpin_broken_line(texCoord, 0.045);
    vec3 accent = mix(BLOOD_BLACK, OXBLOOD_SHADOW, crack * 0.22);
    float influence = snap * crack * 0.26;
    fragColor = vec4(mix(src.rgb, accent, influence), src.a);
}
