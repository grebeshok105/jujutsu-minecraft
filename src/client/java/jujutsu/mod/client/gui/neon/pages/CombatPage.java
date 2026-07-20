package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.CtrlRow;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import jujutsu.mod.client.ui.neon.widget.SectionLabel;
import net.minecraft.network.chat.Component;

public final class CombatPage extends NeonPage {
    public CombatPage() {
        super(Component.literal("Combat"), Component.literal("Tuning shell \u2014 values are local preview only."));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        float y = contentTop();

        SectionLabel straw = new SectionLabel(Component.literal("Straw Doll"));
        straw.setBounds(0, y, pageW, 12); add(straw); y += 18;

        CtrlRow autoPrep = new CtrlRow(Component.literal("Begin charging nails while holding the item"),
                new NeonToggle(Component.literal("Auto nail prepare"), true));
        autoPrep.setBounds(0, y, pageW, 46); add(autoPrep); y += 52;

        CtrlRow bfAssist = new CtrlRow(Component.literal("Widen the input window slightly"),
                new NeonToggle(Component.literal("Black Flash assist"), false));
        bfAssist.setBounds(0, y, pageW, 46); add(bfAssist); y += 52;

        CtrlRow slowMo = new CtrlRow(Component.literal("Client time-dilation on confirmed hit"),
                new NeonToggle(Component.literal("Resonance slow-motion"), true));
        slowMo.setBounds(0, y, pageW, 46); add(slowMo); y += 58;

        SectionLabel balance = new SectionLabel(Component.literal("Balance preview"));
        balance.setBounds(0, y, pageW, 12); add(balance); y += 18;

        CtrlRow chargeSpeed = new CtrlRow(Component.literal("Ticks per prepared nail"),
                new NeonSlider(Component.literal("Nail charge speed"), 0.5f));
        chargeSpeed.setBounds(0, y, pageW, 46); add(chargeSpeed); y += 52;

        CtrlRow chainRadius = new CtrlRow(Component.literal("Blocks between detonation links"),
                new NeonSlider(Component.literal("Hairpin chain radius"), 0.5f));
        chainRadius.setBounds(0, y, pageW, 46); add(chainRadius);
    }
}
