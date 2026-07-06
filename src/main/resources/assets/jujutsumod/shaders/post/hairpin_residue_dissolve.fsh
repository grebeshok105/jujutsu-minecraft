#version 150

#moj_import <jujutsumod:hairpin_fracture.glsl>
#moj_import <jujutsumod:hairpin_timeline.glsl>

uniform sampler2D DiffuseSampler;
uniform float HairpinTime;

in vec2 texCoord;
out vec4 fragColor;

const vec3 BLOOD_BLACK = vec3(0.0706, 0.0353, 0.0471);
const vec3 BLACK_CHERRY = vec3(0.1451, 0.0353, 0.0745);
const vec3 DIRTY_FUCHSIA = vec3(0.5412, 0.1843, 0.3451);

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float residue = hairpin_residue_gate(HairpinTime);
    float broken = hairpin_broken_line(texCoord, 0.075);
    float noise = hairpin_fracture_noise(texCoord * 28.0 + HairpinTime * 2.0);
    vec3 darkBody = mix(BLOOD_BLACK, BLACK_CHERRY, noise);
    vec3 edge = mix(darkBody, DIRTY_FUCHSIA, broken * 0.22);
    float alpha = residue * broken * (1.0 - smoothstep(0.74, 1.0, HairpinTime));
    fragColor = vec4(mix(src.rgb, edge, alpha * 0.45), src.a);
}
