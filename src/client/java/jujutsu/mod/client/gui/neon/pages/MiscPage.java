package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.KeybindField;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class MiscPage extends NeonPage {
    public MiscPage() {
        super(Component.literal("Misc"), Component.literal("Bindings shell \u2014 local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel bindings = new SectionLabel(Component.literal("Bindings"));
        bindings.setBounds(0, y, pageW, 12); add(bindings); y += 18;

        CtrlRow openKey = new CtrlRow(Component.literal("Toggle this menu"),
                new KeybindField(Component.literal("Open dashboard"), "V"));
        openKey.setBounds(0, y, pageW, 46); add(openKey); y += 52;

        CtrlRow piercing = new CtrlRow(Component.literal("Fire prepared nails"),
                new KeybindField(Component.literal("Piercing Nail"), "R"));
        piercing.setBounds(0, y, pageW, 46); add(piercing); y += 52;

        CtrlRow hairpin = new CtrlRow(Component.literal("Detonate marks"),
                new KeybindField(Component.literal("Hairpin / Boom"), "B"));
        hairpin.setBounds(0, y, pageW, 46); add(hairpin); y += 58;

        SectionLabel note = new SectionLabel(Component.literal("Note"));
        note.setBounds(0, y, pageW, 12); add(note); y += 18;

        CtrlRow accent = new CtrlRow(Component.literal("Driven by the selected character \u2014 not user-picked"),
                new jujutsu.mod.client.ui.neon.widget.NeonLabel(
                        Component.literal("Accent theme"), NeonTheme.text(), false));
        accent.setBounds(0, y, pageW, 46); add(accent);
    }
}
