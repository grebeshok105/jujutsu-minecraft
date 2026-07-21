package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.EmojiGlow;
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
    /** Restored pre-crush portrait size so the skin head centers cleanly. */
    private static final float PORTRAIT = 46f;
    private static final float PAD = 8f;
    private static final int HEAD = 40;

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

        NeonTheme t = ctx.theme();
        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(8)
                .border(1, borderArgb)
                .glow(glowR, glowArgb)
                .highlight(0.25f + 0.4f * selectAnim)
                .fill(t.raised(), t.raisedBottom())
                .build());

        // Portrait well — slightly darker inset of the same theme family.
        float wellX = ax + PAD, wellY = ay + PAD;
        ctx.sdf().add(SdfShape.builder()
                .rect(wellX, wellY, PORTRAIT, PORTRAIT)
                .radius(8)
                .border(1, applyAlpha(accentRgb, 0.18f + 0.35f * selectAnim))
                .glow(selected ? 10f : 4f, applyAlpha(accentRgb, 0.40f + 0.2f * selectAnim))
                .highlight(0.18f)
                .fill(t.panelInset(), t.panelInset())
                .build());

        // Emoji halo AFTER well so it is not covered (skin portraits skip this).
        if (emojiPortrait != null) {
            float cx = wellX + PORTRAIT / 2f;
            float cy = wellY + PORTRAIT / 2f;
            EmojiGlow.add(ctx, cx, cy, 30f, accentRgb | 0xFF000000, 0.85f + 0.15f * selectAnim);
        }

        if (selected) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(ax + width - 24, ay + 6, 17, 17)
                    .radius(8.5f)
                    .border(0, 0)
                    .glow(8, applyAlpha(accentRgb, 0.5f))
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
            // Integer center: (PORTRAIT - HEAD) / 2 == 3 for 46/40.
            int hx = Math.round(wellX + (PORTRAIT - HEAD) / 2f);
            int hy = Math.round(wellY + (PORTRAIT - HEAD) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 8.0f, 8.0f, HEAD, HEAD, 8, 8, 64, 64);
            g.blit(RenderPipelines.GUI_TEXTURED, skinPortrait, hx, hy, 40.0f, 8.0f, HEAD, HEAD, 8, 8, 64, 64);
        } else if (emojiPortrait != null) {
            int size = 30;
            int ex = Math.round(wellX + (PORTRAIT - size) / 2f);
            int ey = Math.round(wellY + (PORTRAIT - size) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, emojiPortrait, ex, ey, 0f, 0f, size, size, 96, 96, 96, 96);
        }

        float metaX = ax + PAD + PORTRAIT + 10;
        int nameColor = unlocked ? NeonTheme.text() : NeonTheme.textMuted();
        int line = Math.max(8, ctx.font().lineHeight);
        float textTop = ay + PAD + 2;
        NeonFonts.draw(g, ctx.font(), NeonFonts.colored(name.getString(), nameColor), metaX, textTop, nameColor);
        NeonFonts.draw(g, ctx.font(), tech, metaX, textTop + line + 1,
                unlocked ? (accentRgb | 0xFF000000) : NeonTheme.textDim());
        NeonFonts.draw(g, ctx.font(), grade, metaX, textTop + (line + 1) * 2, NeonTheme.textDim());

        if (selected) {
            // ASCII-only: Segoe specials + pixel fallback look mixed/ugly.
            NeonFonts.draw(g, ctx.font(), "OK", ax + width - 22, ay + 9, NeonTheme.textOnAccent());
        } else if (!unlocked) {
            NeonFonts.draw(g, ctx.font(), "SOON", ax + width - 32, ay + 8, NeonTheme.textDim());
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
