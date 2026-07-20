package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import net.minecraft.network.chat.Component;

public final class VisualsPage extends NeonPage {
    public VisualsPage() {
        super(Component.literal("Visuals"), Component.literal("Render pipeline shell \u2014 local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        NeonToggle blur = new NeonToggle(Component.literal("Background Blur"), true);
        blur.setBounds(0, 24, pageW, 24);
        add(blur);

        NeonToggle glow = new NeonToggle(Component.literal("Neon Glow"), true);
        glow.setBounds(0, 60, pageW, 24);
        add(glow);

        NeonSlider glowIntensity = new NeonSlider(Component.literal("Glow Intensity"), 0.7f);
        glowIntensity.setBounds(0, 96, pageW, 30);
        add(glowIntensity);

        NeonToggle particles = new NeonToggle(Component.literal("VFX Particles"), true);
        particles.setBounds(0, 138, pageW, 24);
        add(particles);

        NeonSlider uiScale = new NeonSlider(Component.literal("UI Scale"), 0.5f);
        uiScale.setBounds(0, 174, pageW, 30);
        add(uiScale);
    }
}
