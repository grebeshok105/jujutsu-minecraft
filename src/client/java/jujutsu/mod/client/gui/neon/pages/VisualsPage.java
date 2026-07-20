package jujutsu.mod.client.gui.neon.pages;

import java.util.List;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class VisualsPage extends NeonPage {
    public VisualsPage() {
        super(Component.literal("Visuals"), Component.literal("Render pipeline shell \u2014 local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel pipeline = new SectionLabel(Component.literal("Pipeline"));
        pipeline.setBounds(0, y, pageW, 12); add(pipeline); y += 18;

        CtrlRow blur = new CtrlRow(Component.literal("Blur the world behind the dashboard"),
                new NeonToggle(Component.literal("Background blur"), true));
        blur.setBounds(0, y, pageW, 46); add(blur); y += 52;

        CtrlRow glow = new CtrlRow(Component.literal("Shader-based borders and halos"),
                new NeonToggle(Component.literal("SDF neon glow"), true));
        glow.setBounds(0, y, pageW, 46); add(glow); y += 52;

        CtrlRow shadows = new CtrlRow(Component.literal("Soft shadow under panels and cards"),
                new NeonToggle(Component.literal("Drop shadows"), true));
        shadows.setBounds(0, y, pageW, 46); add(shadows); y += 58;

        SectionLabel density = new SectionLabel(Component.literal("Density"));
        density.setBounds(0, y, pageW, 12); add(density); y += 18;

        CtrlRow particle = new CtrlRow(Component.literal("VFX particle density multiplier"),
                new NeonDropdown(Component.literal("Particle quality"),
                        List.of(Component.literal("Full"), Component.literal("Reduced"), Component.literal("Minimal")), 0));
        particle.setBounds(0, y, pageW, 46); add(particle); y += 52;

        CtrlRow glowIntensity = new CtrlRow(Component.literal("Neon halo strength"),
                new NeonSlider(Component.literal("Glow intensity"), 0.7f));
        glowIntensity.setBounds(0, y, pageW, 46); add(glowIntensity);
    }
}
