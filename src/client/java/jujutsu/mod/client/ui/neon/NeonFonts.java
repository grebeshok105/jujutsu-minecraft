package jujutsu.mod.client.ui.neon;

import jujutsu.mod.JujutsuMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Dashboard typography — {@code jujutsumod:neon}.
 *
 * <p><b>Implementation (research-backed for MC 1.21.8):</b>
 * <ul>
 *   <li>Font atlas textures always use {@code FilterMode.NEAREST} — low-res bitmaps
 *       always look 144p. Do NOT use small bitmap atlases for smooth UI text.</li>
 *   <li>TTF provider is the correct path for smooth UI: FreeType rasterizes at
 *       {@code size * oversample} px, then draws scaled. High oversample (16) = soft AA.</li>
 *   <li>{@code file} is relative to {@code assets/<ns>/font/}; MC auto-prefixes
 *       {@code font/}. Use {@code "jujutsumod:neon.ttf"} not {@code "...:font/neon.ttf"}.</li>
 *   <li>{@code size} ≈ 9 matches vanilla glyph height; oversample 16 ≈ 144px FreeType face.</li>
 *   <li>Every string MUST go through these helpers — plain {@code String} draws use Mojangles.</li>
 * </ul>
 */
public final class NeonFonts {
    public static final ResourceLocation ID = JujutsuMod.id("neon");

    private NeonFonts() {}

    public static Style style() {
        return Style.EMPTY.withFont(ID);
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text == null ? "" : text).withStyle(style());
    }

    public static MutableComponent wrap(Component component) {
        if (component == null) {
            return literal("");
        }
        return component.copy().withStyle(s -> s.withFont(ID));
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key).withStyle(style());
    }

    public static MutableComponent colored(String text, int rgb) {
        return literal(text).withStyle(s -> s.withColor(rgb).withFont(ID));
    }

    public static int width(Font font, Component text) {
        return font.width(wrap(text));
    }

    public static int width(Font font, String text) {
        return font.width(literal(text));
    }

    public static void draw(GuiGraphics g, Font font, Component text, float x, float y, int color) {
        g.drawString(font, wrap(text), Math.round(x), Math.round(y), color, false);
    }

    public static void draw(GuiGraphics g, Font font, String text, float x, float y, int color) {
        g.drawString(font, literal(text), Math.round(x), Math.round(y), color, false);
    }

    public static void drawVCenter(GuiGraphics g, Font font, Component text, float x, float boxY, float boxH, int color) {
        int line = Math.max(8, font.lineHeight);
        int y = Math.round(boxY + (boxH - line) / 2f);
        g.drawString(font, wrap(text), Math.round(x), y, color, false);
    }

    public static void drawVCenter(GuiGraphics g, Font font, String text, float x, float boxY, float boxH, int color) {
        drawVCenter(g, font, literal(text), x, boxY, boxH, color);
    }

    public static void drawCentered(GuiGraphics g, Font font, Component text, float centerX, float y, int color) {
        Component c = wrap(text);
        int w = font.width(c);
        g.drawString(font, c, Math.round(centerX - w / 2f), Math.round(y), color, false);
    }

    public static void drawCenteredV(GuiGraphics g, Font font, Component text, float centerX, float boxY, float boxH, int color) {
        Component c = wrap(text);
        int w = font.width(c);
        int line = Math.max(8, font.lineHeight);
        int y = Math.round(boxY + (boxH - line) / 2f);
        g.drawString(font, c, Math.round(centerX - w / 2f), y, color, false);
    }
}
