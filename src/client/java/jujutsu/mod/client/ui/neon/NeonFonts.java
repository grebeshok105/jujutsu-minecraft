package jujutsu.mod.client.ui.neon;

import jujutsu.mod.JujutsuMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Dashboard typography: Inter-based TTF registered as {@code jujutsumod:neon}. */
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
        return component.copy().withStyle(style());
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key).withStyle(style());
    }
}
