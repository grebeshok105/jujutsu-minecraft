package jujutsu.mod.client.gui;

import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * TEMPORARY Stage-2 probe screen. Draws a representative dashboard layout entirely through the
 * custom SDF pipeline to prove the shader works before the real UI kit is built. Deleted once
 * the dashboard lands. Opened by the V keybind during the probe stage only.
 */
public final class SdfProbeScreen extends Screen {
    private final SdfRenderer sdf = new SdfRenderer();

    public SdfProbeScreen() {
        super(Component.literal("SDF Probe"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int sw = this.width;
        int sh = this.height;

        // Nobara theme (matches the approved HTML mockup).
        int accent = 0xFFE48A36;
        int accentDeep = 0xFF8B3F1C;
        int glow = 0x8CE48A36;          // ~0.55 alpha
        int panelTop = 0xEB17110F;
        int panelBottom = 0xF0110C0A;
        int border = 0x38E48A36;        // ~0.22 alpha
        int raised = 0xD9211914;
        int text = 0xFFF4EFE8;
        int textDim = 0xFF635850;

        sdf.begin();

        // Scrim (full-screen dark quad, no border/glow).
        sdf.add(SdfShape.builder().rect(0, 0, sw, sh).radius(0).border(0, 0).glow(0, 0)
                .fill(0xB8090605, 0xC7090605).build());

        // Main window.
        float ww = Math.min(660, sw - 40);
        float wh = Math.min(440, sh - 40);
        float wx = (sw - ww) / 2f;
        float wy = (sh - wh) / 2f;
        sdf.add(SdfShape.builder().rect(wx, wy, ww, wh).radius(10)
                .border(1, border).glow(12, glow).highlight(1f)
                .fill(panelTop, panelBottom).build());

        // Sidebar.
        float sbw = 132;
        sdf.add(SdfShape.builder().rect(wx, wy + 40, sbw, wh - 40).radius(0).border(0, 0).glow(0, 0)
                .fill(0x80131009, 0x4D0F0A08).build());

        // Active sidebar item (glowing).
        sdf.add(SdfShape.builder().rect(wx + 10, wy + 66, sbw - 20, 34).radius(6)
                .border(1, 0x48E48A36).glow(10, glow).highlight(0.5f)
                .fill(0x24E48A36, 0x0AE48A36).build());

        // Page title underline + two cards.
        float pageX = wx + sbw + 18;
        float pageW = ww - sbw - 36;
        float cardW = (pageW - 12) / 2f;
        for (int i = 0; i < 2; i++) {
            float cx = pageX + i * (cardW + 12);
            boolean selected = i == 0;
            sdf.add(SdfShape.builder().rect(cx, wy + 92, cardW, 96).radius(8)
                    .border(1, selected ? 0x8CE48A36 : 0x24E48A36)
                    .glow(selected ? 14 : 0, glow).highlight(selected ? 0.8f : 0.2f)
                    .fill(raised, 0xD9181210).build());
        }

        // Confirm button (primary, glowing).
        float btnW = 180, btnH = 36;
        float btnX = pageX + pageW - btnW;
        float btnY = wy + wh - 56;
        sdf.add(SdfShape.builder().rect(btnX, btnY, btnW, btnH).radius(18)
                .border(0, 0).glow(12, glow).highlight(0.6f)
                .fill(accent, accentDeep).build());

        sdf.flush();

        // Text via vanilla GuiGraphics - proves text renders ON TOP of the SDF surfaces.
        g.drawString(this.font, "JUJUTSU // DASHBOARD", (int) wx + 14, (int) wy + 15, text, false);
        g.drawString(this.font, "Character", (int) pageX, (int) wy + 62, text, false);
        g.drawString(this.font, "SDF pipeline probe - rounded rects, borders, glow, gradients", (int) pageX, (int) wy + 210, textDim, false);
        g.drawString(this.font, "CONFIRM", (int) btnX + 62, (int) btnY + 14, 0xFF1A0D02, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        sdf.close();
        super.onClose();
    }
}
