package jujutsu.mod.client.ui.neon;

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
        float openAnim) {
}
