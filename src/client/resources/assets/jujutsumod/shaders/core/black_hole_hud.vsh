#version 150

// Fullscreen triangle for the HUD warp composite (same convention as black_hole.vsh).

out vec2 texCoord;

void main() {
    vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    texCoord = pos;
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
