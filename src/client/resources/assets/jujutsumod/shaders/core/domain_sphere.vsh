#version 150

// Fullscreen triangle for the world-space domain sphere post-effect: one draw per active sphere,
// with no vertex data at all (the pipeline uses DefaultVertexFormat.EMPTY plus a dummy vertex
// buffer; pos is generated from gl_VertexID).
//
// texCoord is the clip position remapped to [0,1] and is deliberately NOT Y-flipped. blur.vsh /
// msdf.vsh do flip Y, but only because they draw in GUI space, whose origin is top-left. This pass
// samples the scene-colour and scene-depth copies, which are framebuffer attachments with the
// bottom-left origin that gl_Position already uses, so flipping here would mirror every
// reconstructed world position vertically.

out vec2 texCoord;

void main() {
    // id 0 -> (0,0), id 1 -> (2,0), id 2 -> (0,2): one oversized triangle covering the screen.
    vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    texCoord = pos;
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
