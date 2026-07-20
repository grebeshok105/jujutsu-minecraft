package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Horizontal character card: portrait well on the left, name/tech/grade meta on the right. */
public final class NeonCard extends UiComponent {
    private static final float PORTRAIT = 46f;
    private static final float PAD = 8f;

    private final Component name;
    private final Component tech;
    private final Component grade;
    private final int accentRgb;
    private final ResourceLocation skinPortrait;
    private final String emojiGlyph;
    private final boolean unlocked;
    private final Runnable onSelect;
    private boolean selected;
    private float selectAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public NeonCard(Component name, Component tech, Component grade, int accentRgb,
                    ResourceLocation skinPortrait, String emojiGlyph, boolean unlocked, Runnable onSelect) {
        this.name = name;
        this.tech = tech;
        this.grade = grade;
        this.accentRgb = accentRgb;
        this.skinPortrait = skinPortrait;
        this.emojiGlyph = emojiGlyph;
        this.unlocked = unlocked;
        this.onSelect = onSelect;
        this.height = PORTRAIT + PAD * 2;
    }

    public void setSelected(boolean s) { this.selected = s; }
    public boolean isSelected() { return selected; }
    public boolean isUnlocked() { return unlocked; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = unlocked && contains(lastMouseX, lastMouseY);
        float target = selected ? 1f : (hoveredThisFrame ? 0.45f : 0f);
        selectAnim = UiEase.approach(selectAnim, target, 0.3f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        float ax = absX(), ay = absY();

        int borderArgb = selected
                ? applyAlpha(accentRgb, 0.9f)
                : applyAlpha(accentRgb, 0.14f + 0.4f * selectAnim);
        float glowR = selected ? 16f : 8f * selectAnim;
        int glowArgb = applyAlpha(accentRgb, (selected ? 0.5f : 0.3f) * Math.max(selectAnim, 0.001f) * (selected ? 1f : selectAnim));

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(8)
                .border(1, borderArgb)
                .glow(glowR, glowArgb)
                .highlight(0.2f + 0.4f * selectAnim)
                .fill(0xD9211914, 0xD9181210)
                .build());

        // Portrait well.
        ctx.sdf().add(SdfShape.builder()
                .rect(ax + PAD, ay + PAD, PORTRAIT, PORTRAIT)
                .radius(8)
                .border(1, applyAlpha(accentRgb, 0.12f + 0.3f * selectAnim))
                .glow(selected ? 8f : 0f, applyAlpha(accentRgb, 0.35f))
                .highlight(0.15f)
                .fill(0xE61E1611, 0xE6100B09)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();

        // Portrait: skin head (base + hat layers) or emoji glyph placeholder.
        float wellX = ax + PAD, wellY = ay + PAD;
        if (skinPortrait != null) {
            int head = 40;
            int hx = (int) (wellX + (PORTRAIT - head) / 2f);
            int hy = (int) (wellY + (PORTRAIT - head) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 8.0f, 8.0f, head, head, 8, 8, 64, 64);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 40.0f, 8.0f, head, head, 8, 8, 64, 64);
        } else if (emojiGlyph != null) {
            int gw = ctx.font().width(emojiGlyph);
            int glyphColor = unlocked ? (accentRgb | 0xFF000000) : NeonTheme.textDim();
            g.drawString(ctx.font(), emojiGlyph, (int) (wellX + (PORTRAIT - gw) / 2f), (int) (wellY + PORTRAIT / 2f - 4),
                    glyphColor, false);
        }

        // Meta column.
        float metaX = ax + PAD + PORTRAIT + 10;
        int nameColor = unlocked ? NeonTheme.text() : NeonTheme.textMuted();
        g.drawString(ctx.font(), name.copy().withStyle(s -> s.withColor(nameColor)), (int) metaX, (int) (ay + 12), nameColor, false);
        g.drawString(ctx.font(), tech, (int) metaX, (int) (ay + 25), unlocked ? accentRgb | 0xFF000000 : NeonTheme.textDim(), false);
        g.drawString(ctx.font(), grade, (int) metaX, (int) (ay + 38), NeonTheme.textDim(), false);

        // Selected badge (✓) or locked SOON tag, top-right.
        if (selected) {
            g.drawString(ctx.font(), "\u2713", (int) (ax + width - 16), (int) (ay + 6), 0xFF4ADE80, false);
        } else if (!unlocked) {
            g.drawString(ctx.font(), "SOON", (int) (ax + width - 30), (int) (ay + 7), NeonTheme.textDim(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && unlocked && contains(mouseX, mouseY)) {
            onSelect.run();
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
