package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.KeybindField;
import jujutsu.mod.client.ui.neon.widget.NeonLabel;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class MiscPage extends NeonPage {
    public MiscPage() {
        super(NeonFonts.literal("Misc"), NeonFonts.literal("Bindings shell \u2014 local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel bindings = new SectionLabel(NeonFonts.literal("Bindings"));
        bindings.setBounds(0, y, pageW, 12); add(bindings); y += 16;

        y = addRow(y, pageW, NeonFonts.literal("Toggle this menu"),
                new KeybindField(NeonFonts.literal("Open dashboard"), "V"));
        y = addRow(y, pageW, NeonFonts.literal("Fire prepared nails"),
                new KeybindField(NeonFonts.literal("Piercing Nail"), "R"));
        y = addRow(y, pageW, NeonFonts.literal("Detonate marks"),
                new KeybindField(NeonFonts.literal("Hairpin / Boom"), "B"), 8);

        SectionLabel note = new SectionLabel(NeonFonts.literal("Note"));
        note.setBounds(0, y, pageW, 12); add(note); y += 16;

        addRow(y, pageW, NeonFonts.literal("Driven by the selected character \u2014 not user-picked"),
                new NeonLabel(NeonFonts.literal("Accent theme"), NeonTheme.text(), false));
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
