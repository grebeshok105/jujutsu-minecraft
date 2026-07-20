package jujutsu.mod.client.ui.neon.render;

/**
 * Immutable description of one SDF rounded-rect shape. Colors are ARGB ints (matching the
 * existing UiRender/UiTheme convention); the renderer converts them to float RGBA for the GPU.
 *
 * <p>Geometry is in GUI screen pixels. {@code glowRadius} extends the shape's draw quad outward
 * so the halo has room; the shape itself stays at (x, y, w, h).
 */
public record SdfShape(
        float x, float y, float w, float h,
        float radius, float borderWidth, float glowRadius, float highlight,
        int fillTopArgb, int fillBottomArgb, int borderArgb, int glowArgb) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float x, y, w, h;
        private float radius = 8f;
        private float borderWidth = 1f;
        private float glowRadius = 0f;
        private float highlight = 0f;
        private int fillTopArgb = 0xFF17110F;
        private int fillBottomArgb = 0xFF110C0A;
        private int borderArgb = 0x38E48A36;
        private int glowArgb = 0x00E48A36;

        public Builder rect(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder radius(float v) { this.radius = v; return this; }
        public Builder border(float width, int argb) { this.borderWidth = width; this.borderArgb = argb; return this; }
        public Builder glow(float radius, int argb) { this.glowRadius = radius; this.glowArgb = argb; return this; }
        public Builder highlight(float v) { this.highlight = v; return this; }
        public Builder fill(int topArgb, int bottomArgb) { this.fillTopArgb = topArgb; this.fillBottomArgb = bottomArgb; return this; }
        public Builder fill(int argb) { return fill(argb, argb); }

        public SdfShape build() {
            return new SdfShape(x, y, w, h, radius, borderWidth, glowRadius, highlight,
                    fillTopArgb, fillBottomArgb, borderArgb, glowArgb);
        }
    }
}
