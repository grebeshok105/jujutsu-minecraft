package jujutsu.mod.client.ui.neon.render;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeonBlur {
    private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/neon-blur");
    private static boolean disabledForSession;

    private NeonBlur() {}

    public static void apply() {
        if (disabledForSession) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            mc.gameRenderer.processBlurEffect();
        } catch (RuntimeException | LinkageError error) {
            disabledForSession = true;
            LOG.warn("Neon blur disabled for this session (shader unavailable): {}", error.toString());
        }
    }

    public static boolean isDisabled() { return disabledForSession; }
}
