#version 150

// Fullscreen triangle for the black hole post-effect: no vertex data
// (DefaultVertexFormat.EMPTY + a dummy vertex buffer; pos is generated from gl_VertexID).
// texCoord is NOT Y-flipped — the scene copies are framebuffer attachments with bottom-left origin.

out vec2 texCoord;

void main() {
    vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    texCoord = pos;
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
