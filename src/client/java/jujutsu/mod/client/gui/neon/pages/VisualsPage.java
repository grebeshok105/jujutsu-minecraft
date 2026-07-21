package jujutsu.mod.client.gui.neon.pages;

import java.util.List;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class VisualsPage extends NeonPage {
    public VisualsPage() {
        super(NeonFonts.literal("Visuals"), NeonFonts.literal("Render pipeline shell \u2014 local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel pipeline = new SectionLabel(NeonFonts.literal("Pipeline"));
        pipeline.setBounds(0, y, pageW, 12); add(pipeline); y += 14;

        y = addRow(y, pageW, NeonFonts.literal("Blur the world behind the dashboard"),
                new NeonToggle(NeonFonts.literal("Background blur"), true));
        y = addRow(y, pageW, NeonFonts.literal("Shader-based borders and halos"),
                new NeonToggle(NeonFonts.literal("SDF neon glow"), true));
        y = addRow(y, pageW, NeonFonts.literal("Soft shadow under panels and cards"),
                new NeonToggle(NeonFonts.literal("Drop shadows"), true), 6);

        SectionLabel density = new SectionLabel(NeonFonts.literal("Density"));
        density.setBounds(0, y, pageW, 12); add(density); y += 14;

        y = addRow(y, pageW, NeonFonts.literal("VFX particle density multiplier"),
                new NeonDropdown(NeonFonts.literal("Particle quality"),
                        List.of(NeonFonts.literal("Full"), NeonFonts.literal("Reduced"), NeonFonts.literal("Minimal")), 0));
        addRow(y, pageW, NeonFonts.literal("Neon halo strength"),
                new NeonSlider(NeonFonts.literal("Glow intensity"), 0, 100, 70));
    }

    private float addRow(float y, float pageW, Component desc, jujutsu.mod.client.ui.neon.UiComponent control) {
        return addRow(y, pageW, desc, control, 4);
    }

    private float addRow(float y, float pageW, Component desc, jujutsu.mod.client.ui.neon.UiComponent control, float gap) {
        CtrlRow row = new CtrlRow(desc, control);
        float h = row.preferredHeight();
        row.setBounds(0, y, pageW, h);
        add(row);
        return y + h + gap;
    }
}
