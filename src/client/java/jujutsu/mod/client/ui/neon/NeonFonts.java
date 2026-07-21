package jujutsu.mod.client.ui.neon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Dashboard text helpers. Uses the default Minecraft font (custom TTF was tofu / unreadable).
 */
public final class NeonFonts {
    private NeonFonts() {}

    public static Style style() {
        return Style.EMPTY;
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent wrap(Component component) {
        return component.copy();
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }
}
