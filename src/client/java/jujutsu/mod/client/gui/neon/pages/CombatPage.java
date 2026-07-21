package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class CombatPage extends NeonPage {
    public CombatPage() {
        super(NeonFonts.literal("Combat"), NeonFonts.literal("Tuning shell \u2014 values are local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel straw = new SectionLabel(NeonFonts.literal("Straw Doll"));
        straw.setBounds(0, y, pageW, 12); add(straw); y += 16;

        y = addRow(y, pageW, NeonFonts.literal("Begin charging nails while holding the item"),
                new NeonToggle(NeonFonts.literal("Auto nail prepare"), true));
        y = addRow(y, pageW, NeonFonts.literal("Widen the input window slightly"),
                new NeonToggle(NeonFonts.literal("Black Flash assist"), false));
        y = addRow(y, pageW, NeonFonts.literal("Client time-dilation on confirmed hit"),
                new NeonToggle(NeonFonts.literal("Resonance slow-motion"), true), 8);

        SectionLabel balance = new SectionLabel(NeonFonts.literal("Balance preview"));
        balance.setBounds(0, y, pageW, 12); add(balance); y += 16;

        y = addRow(y, pageW, NeonFonts.literal("Ticks per prepared nail"),
                new NeonSlider(NeonFonts.literal("Nail charge speed"), 4, 20, 10));
        addRow(y, pageW, NeonFonts.literal("Blocks between detonation links"),
                new NeonSlider(NeonFonts.literal("Hairpin chain radius"), 2, 16, 10));
    }

    private float addRow(float y, float pageW, Component desc, jujutsu.mod.client.ui.neon.UiComponent control) {
        return addRow(y, pageW, desc, control, 6);
    }

    private float addRow(float y, float pageW, Component desc, jujutsu.mod.client.ui.neon.UiComponent control, float gap) {
        CtrlRow row = new CtrlRow(desc, control);
        float h = row.preferredHeight();
        row.setBounds(0, y, pageW, h);
        add(row);
        return y + h + gap;
    }
}
