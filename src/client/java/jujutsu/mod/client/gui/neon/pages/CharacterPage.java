package jujutsu.mod.client.gui.neon.pages;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import jujutsu.mod.client.ui.neon.widget.NeonButton;
import jujutsu.mod.client.ui.neon.widget.NeonCard;
import jujutsu.mod.network.SelectCharacterPayload;

public final class CharacterPage extends NeonPage {
    private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
    private static final ResourceLocation[] ABILITY_ICONS = {
            JujutsuMod.id("textures/gui/abilities/piercing_nail.png"),
            JujutsuMod.id("textures/gui/abilities/hairpin_enlargement.png"),
            JujutsuMod.id("textures/gui/abilities/hairpin_explosion.png"),
            JujutsuMod.id("textures/gui/abilities/resonance.png"),
    };
    private static final Component[] ABILITY_LABELS = {
            Component.translatable("screen.jujutsumod.character_select.ability.piercing_nail"),
            Component.translatable("screen.jujutsumod.character_select.ability.hairpin_enlarge"),
            Component.translatable("screen.jujutsumod.character_select.ability.hairpin_explosion"),
            Component.translatable("screen.jujutsumod.character_select.ability.resonance"),
    };
    private static final String[] ABILITY_KEYS = {"R", "B", "\u21E7R", "LMB"};

    private record Roster(String name, String tech, String grade, int accent, int deep,
                          boolean unlocked, boolean skin, String glyph, JujutsuCharacter character) {}

    private static final Roster[] ROSTER = {
            new Roster("Nobara Kugisaki", "Straw Doll Technique", "Grade 3", 0xFFE48A36, 0xFF8B3F1C, true, true, null, JujutsuCharacter.NOBARA),
            new Roster("None", "No Technique", "Default", 0xFF505760, 0xFF2E333A, true, false, "\u25CF", JujutsuCharacter.NONE),
            new Roster("Gojo Satoru", "Limitless", "Special Grade", 0xFF4CC9F0, 0xFF1B5F8C, false, false, "\u25CF", null),
            new Roster("Ryomen Sukuna", "Shrine", "Special Grade", 0xFFDC2743, 0xFF7A1030, false, false, "\u25CF", null),
            new Roster("Megumi Fushiguro", "Ten Shadows", "Grade 2", 0xFF2EC4B6, 0xFF14655D, false, false, "\u25CF", null),
            new Roster("Yuji Itadori", "Sukuna's Vessel", "Grade 1", 0xFFFF4D6D, 0xFF8C1D33, false, false, "\u25CF", null),
    };

    private JujutsuCharacter selection;
    private final List<NeonCard> cards = new ArrayList<>();
    private NeonButton confirmBtn;
    private NeonButton cancelBtn;
    private final Runnable closeAction;
    private float pageW;
    private float pageH;

    public CharacterPage(Runnable closeAction) {
        super(Component.literal("Character"),
                Component.literal("Select your vessel. The dashboard theme follows the chosen character."));
        this.closeAction = closeAction;
        this.selection = initialSelection();
    }

    private static JujutsuCharacter initialSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var sel = ClientCharacterSelectionManager.selection(mc.player.getUUID());
            if (sel != null) return sel.character();
        }
        return JujutsuCharacter.NOBARA;
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        this.pageW = pageW;
        this.pageH = pageH;

        float top = contentTop();
        float gap = 12f;
        float cardW = (pageW - gap) / 2f;

        for (int i = 0; i < ROSTER.length; i++) {
            Roster r = ROSTER[i];
            int col = i % 2;
            int row = i / 2;
            NeonCard card = new NeonCard(
                    Component.literal(r.name()),
                    Component.literal(r.tech()),
                    Component.literal(r.grade()),
                    r.accent(),
                    r.skin() ? NOBARA_SKIN : null,
                    r.glyph(),
                    r.unlocked(),
                    () -> { if (r.character() != null) selection = r.character(); });
            card.setBounds(col * (cardW + gap), top + row * (card.height() + 10), cardW, card.height());
            cards.add(card);
            add(card);
        }

        float btnW = 120f, btnH = 30f;
        float btnY = pageH - btnH - 4;
        cancelBtn = new NeonButton(Component.literal("Cancel"), btnW, btnH, false, closeAction);
        cancelBtn.setBounds(pageW - (btnW * 2 + 10), btnY, btnW, btnH);
        add(cancelBtn);

        confirmBtn = new NeonButton(Component.translatable("screen.jujutsumod.character_select.confirm"), btnW, btnH, true, this::confirm);
        confirmBtn.setBounds(pageW - btnW, btnY, btnW, btnH);
        add(confirmBtn);
    }

    private void confirm() {
        if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
            ClientPlayNetworking.send(new SelectCharacterPayload(selection.id()));
        }
        closeAction.run();
    }

    public JujutsuCharacter selection() { return selection; }

    @Override
    public void tick(float deltaTicks) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setSelected(ROSTER[i].character() == selection);
        }
        super.tick(deltaTicks);
    }

    private float stripTop() {
        float top = contentTop();
        float cardH = cards.isEmpty() ? 62f : cards.get(0).height();
        return top + 3 * (cardH + 10) + 8;
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        super.renderSurface(ctx);
        drawAbilityStripSurface(ctx);
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        super.renderText(ctx);
        drawAbilityStripText(ctx);
    }

    private void drawAbilityStripSurface(NeonContext ctx) {
        NeonTheme t = ctx.theme();
        float ax = absX();
        float labelY = stripTop();
        float stripY = labelY + 13;
        float stripH = 40;

        // Section label rule line.
        Component label = abilityLabel();
        int labelW = ctx.font().width(label);
        ctx.sdf().add(SdfShape.builder()
                .rect(ax + labelW + 8, labelY + 3.5f, pageW - labelW - 8, 1)
                .radius(0).border(0, 0).glow(0, 0)
                .fill(applyAlpha(t.accentArgb(), 0.10f), applyAlpha(t.accentArgb(), 0.10f))
                .build());

        if (selection != JujutsuCharacter.NOBARA) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(ax, stripY, pageW, stripH)
                    .radius(6).border(1, applyAlpha(t.accentArgb(), 0.12f)).glow(0, 0).highlight(0.1f)
                    .fill(t.panelInset(), t.panelInset())
                    .build());
            return;
        }

        float gap = 8;
        float cellW = (pageW - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            ctx.sdf().add(SdfShape.builder()
                    .rect(cx, stripY, cellW, stripH)
                    .radius(6)
                    .border(1, applyAlpha(t.accentArgb(), 0.12f))
                    .glow(0, 0).highlight(0.12f)
                    .fill(t.panelInset(), t.panelInset())
                    .build());
        }
    }

    private Component abilityLabel() {
        String tech = selection == JujutsuCharacter.NOBARA ? "Straw Doll" : "None";
        return Component.literal("Innate \u00B7 " + tech);
    }

    private void drawAbilityStripText(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        float ax = absX();
        float labelY = stripTop();
        float stripY = labelY + 13;
        float stripH = 40;

        g.drawString(ctx.font(), abilityLabel(), (int) ax, (int) labelY, NeonTheme.textDim(), false);

        if (selection != JujutsuCharacter.NOBARA) {
            Component none = Component.translatable("screen.jujutsumod.character_select.ability.none");
            g.drawString(ctx.font(), none, (int) (ax + (pageW - ctx.font().width(none)) / 2f),
                    (int) (stripY + (stripH - 8) / 2f), NeonTheme.textDim(), false);
            return;
        }

        float gap = 8;
        float cellW = (pageW - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            int iconSize = 18;
            int iconX = (int) (cx + 8);
            int iconY = (int) (stripY + (stripH - iconSize) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, ABILITY_ICONS[i], iconX, iconY, 0f, 0f, iconSize, iconSize, 16, 16, 16, 16);

            int textX = iconX + iconSize + 6;
            g.drawString(ctx.font(), ABILITY_LABELS[i], textX, (int) (stripY + (stripH - 8) / 2f), NeonTheme.textMuted(), false);

            // Key tag (top-right corner badge).
            int keyW = ctx.font().width(ABILITY_KEYS[i]) + 8;
            int keyX = (int) (cx + cellW - keyW + 2);
            int keyY = (int) (stripY - 5);
            g.fill(keyX, keyY, keyX + keyW, keyY + 11, ctx.theme().accentArgb());
            g.drawString(ctx.font(), ABILITY_KEYS[i], keyX + 4, keyY + 2, NeonTheme.textOnAccent(), false);
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
