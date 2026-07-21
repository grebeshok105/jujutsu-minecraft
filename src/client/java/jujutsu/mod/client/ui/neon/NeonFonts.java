package jujutsu.mod.client.ui.neon;

import jujutsu.mod.JujutsuMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Dashboard typography — Windows Segoe UI (system default UI face) bundled as
 * {@code jujutsumod:neon}. Apply only via Component style so multi-font Font resolves it.
 */
public final class NeonFonts {
    public static final ResourceLocation ID = JujutsuMod.id("neon");

    private NeonFonts() {}

    public static Style style() {
        return Style.EMPTY.withFont(ID);
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text).withStyle(style());
    }

    public static MutableComponent wrap(Component component) {
        return component.copy().withStyle(s -> s.withFont(ID));
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key).withStyle(style());
    }
}
