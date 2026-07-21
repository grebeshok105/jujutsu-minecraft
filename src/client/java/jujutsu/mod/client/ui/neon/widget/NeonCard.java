package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Horizontal character card: portrait well on the left, name/tech/grade meta on the right. */
public final class NeonCard extends UiComponent {
    private static final float PORTRAIT = 36f;
    private static final float PAD = 6f;

    private final Component name;
    private final Component tech;
    private final Component grade;
    private final int accentRgb;
    private final ResourceLocation skinPortrait;
    private final ResourceLocation emojiPortrait;
    private final boolean unlocked;
    private final Runnable onSelect;
    private boolean selected;
    private float selectAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public NeonCard(Component name, Component tech, Component grade, int accentRgb,
                    ResourceLocation skinPortrait, ResourceLocation emojiPortrait, boolean unlocked, Runnable onSelect) {
        this.name = NeonFonts.wrap(name);
        this.tech = NeonFonts.wrap(tech);
        this.grade = NeonFonts.wrap(grade);
        this.accentRgb = accentRgb;
        this.skinPortrait = skinPortrait;
        this.emojiPortrait = emojiPortrait;
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
        float glowR = selected ? 14f : 7f * selectAnim;
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

        // Soft glow under emoji portraits.
        if (emojiPortrait != null) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(ax + PAD + 4, ay + PAD + 4, PORTRAIT - 8, PORTRAIT - 8)
                    .radius((PORTRAIT - 8) / 2f)
                    .border(0, 0)
                    .glow(10, applyAlpha(accentRgb, 0.28f + 0.2f * selectAnim))
                    .fill(applyAlpha(accentRgb, 0.08f), applyAlpha(accentRgb, 0.02f))
                    .highlight(0f)
                    .build());
        }

        if (selected) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(ax + width - 20, ay + 5, 14, 14)
                    .radius(7)
                    .border(0, 0)
                    .glow(7, applyAlpha(accentRgb, 0.5f))
                    .highlight(0.4f)
                    .fill(accentRgb | 0xFF000000, accentRgb | 0xFF000000)
                    .build());
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();

        float wellX = ax + PAD, wellY = ay + PAD;
        if (skinPortrait != null) {
            int head = 32;
            int hx = (int) (wellX + (PORTRAIT - head) / 2f);
            int hy = (int) (wellY + (PORTRAIT - head) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 8.0f, 8.0f, head, head, 8, 8, 64, 64);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 40.0f, 8.0f, head, head, 8, 8, 64, 64);
        } else if (emojiPortrait != null) {
            int size = 28;
            int ex = (int) (wellX + (PORTRAIT - size) / 2f);
            int ey = (int) (wellY + (PORTRAIT - size) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, emojiPortrait, ex, ey, 0f, 0f, size, size, 96, 96, 96, 96);
        }

        float metaX = ax + PAD + PORTRAIT + 8;
        int nameColor = unlocked ? NeonTheme.text() : NeonTheme.textMuted();
        g.drawString(ctx.font(), name.copy().withStyle(s -> s.withColor(nameColor).withFont(NeonFonts.ID)), (int) metaX, (int) (ay + 9), nameColor, false);
        g.drawString(ctx.font(), tech, (int) metaX, (int) (ay + 20), unlocked ? accentRgb | 0xFF000000 : NeonTheme.textDim(), false);
        g.drawString(ctx.font(), grade, (int) metaX, (int) (ay + 31), NeonTheme.textDim(), false);

        if (selected) {
            g.drawString(ctx.font(), NeonFonts.literal("\u2713"), (int) (ax + width - 16), (int) (ay + 7), NeonTheme.textOnAccent(), false);
        } else if (!unlocked) {
            g.drawString(ctx.font(), NeonFonts.literal("SOON"), (int) (ax + width - 28), (int) (ay + 6), NeonTheme.textDim(), false);
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
