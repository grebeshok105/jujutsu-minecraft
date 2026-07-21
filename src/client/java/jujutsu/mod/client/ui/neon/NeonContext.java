package jujutsu.mod.client.ui.neon;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public record NeonContext(
        SdfRenderer sdf,
        GuiGraphics graphics,
        Font font,
        NeonTheme theme,
        double mouseX,
        double mouseY,
        float deltaTicks,
        float openAnim,
        List<Runnable> overlays) {

    public NeonContext(
            SdfRenderer sdf,
            GuiGraphics graphics,
            Font font,
            NeonTheme theme,
            double mouseX,
            double mouseY,
            float deltaTicks,
            float openAnim) {
        this(sdf, graphics, font, theme, mouseX, mouseY, deltaTicks, openAnim, new ArrayList<>());
    }

    public void deferOverlay(Runnable r) {
        overlays.add(r);
    }

    public void flushOverlays() {
        for (Runnable r : overlays) {
            r.run();
        }
        overlays.clear();
    }
}
