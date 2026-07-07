#version 150

uniform sampler2D InSampler;
uniform vec2 ScreenSize;
uniform vec2 ImpactCenter;
uniform vec2 BlurVector;
uniform float Intensity;
uniform float HairpinTime;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 center = ImpactCenter;
    vec2 toCenter = texCoord - center;
    float distanceFromImpact = length(toCenter);
    float shock = smoothstep(0.33, 0.0, abs(distanceFromImpact - HairpinTime));
    float noise = hash(floor((texCoord + HairpinTime) * ScreenSize.xy * 0.035));
    vec2 radial = normalize(toCenter + vec2(0.0001));
    vec2 blur = BlurVector * Intensity * 0.0025;
    vec2 warp = radial * shock * Intensity * 0.012 + blur * (0.5 + noise);

    vec4 color = texture(InSampler, texCoord + warp);
    color.r += shock * Intensity * 0.08;
    color.g *= 1.0 - shock * Intensity * 0.08;
    color.b *= 1.0 - shock * Intensity * 0.12;
    fragColor = color;
}
