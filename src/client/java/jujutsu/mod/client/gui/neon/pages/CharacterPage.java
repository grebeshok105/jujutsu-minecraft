package jujutsu.mod.client.gui.neon.pages;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.EmojiGlow;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import jujutsu.mod.client.ui.neon.widget.NeonButton;
import jujutsu.mod.client.ui.neon.widget.NeonCard;
import jujutsu.mod.network.SelectCharacterPayload;

public final class CharacterPage extends NeonPage {
    private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
    private static final ResourceLocation[] ABILITY_ICONS = {
            JujutsuMod.id("textures/gui/dashboard/emoji_pin.png"),
            JujutsuMod.id("textures/gui/dashboard/emoji_boom.png"),
            JujutsuMod.id("textures/gui/dashboard/emoji_link.png"),
            JujutsuMod.id("textures/gui/dashboard/emoji_bolt.png"),
    };
    private static final Component[] ABILITY_LABELS = {
            NeonFonts.translatable("screen.jujutsumod.character_select.ability.piercing_nail"),
            NeonFonts.translatable("screen.jujutsumod.character_select.ability.hairpin_enlarge"),
            NeonFonts.translatable("screen.jujutsumod.character_select.ability.hairpin_explosion"),
            NeonFonts.translatable("screen.jujutsumod.character_select.ability.resonance"),
    };
    private static final String[] ABILITY_KEYS = {"R", "B", "\u21E7R", "LMB"};

    private record Roster(String name, String tech, String grade, int accent,
                          boolean unlocked, boolean skin, ResourceLocation emoji, JujutsuCharacter character) {}

    private static final Roster[] ROSTER = {
            new Roster("Nobara Kugisaki", "Straw Doll Technique", "Grade 3", 0xFFE48A36, true, true, null, JujutsuCharacter.NOBARA),
            new Roster("None", "No Technique", "Default", 0xFF505760, true, false, dash("bust"), JujutsuCharacter.NONE),
    };

    private static ResourceLocation dash(String name) {
        return JujutsuMod.id("textures/gui/dashboard/emoji_" + name + ".png");
    }

    private JujutsuCharacter selection;
    private final List<NeonCard> cards = new ArrayList<>();
    private NeonButton confirmBtn;
    private NeonButton cancelBtn;
    private final Runnable closeAction;
    private float pageW;
    private float pageH;

    public CharacterPage(Runnable closeAction) {
        super(NeonFonts.literal("Character"),
                NeonFonts.literal("Select your vessel. The dashboard theme follows the chosen character."));
        this.closeAction = closeAction;
        this.selection = initialSelection();
    }

    private static JujutsuCharacter initialSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
        }
        return JujutsuCharacter.NONE;
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        this.pageW = pageW;
        this.pageH = pageH;
        cards.clear();
        children().clear();

        float top = contentTop();
        float gap = 10f;
        float cardW = (pageW - gap) / 2f;

        for (int i = 0; i < ROSTER.length; i++) {
            Roster r = ROSTER[i];
            int col = i % 2;
            int row = i / 2;
            NeonCard card = new NeonCard(
                    NeonFonts.literal(r.name()),
                    NeonFonts.literal(r.tech()),
                    NeonFonts.literal(r.grade()),
                    r.accent(),
                    r.skin() ? NOBARA_SKIN : null,
                    r.emoji(),
                    r.unlocked(),
                    () -> { if (r.character() != null) selection = r.character(); });
            card.setBounds(col * (cardW + gap), top + row * (card.height() + 8), cardW, card.height());
            cards.add(card);
            add(card);
        }

        float btnH = 30f;
        float btnW = (pageW - 8) / 2f;
        float btnY = pageH - btnH - 2;
        cancelBtn = new NeonButton(NeonFonts.literal("Cancel"), btnW, btnH, false, closeAction);
        cancelBtn.setBounds(0, btnY, btnW, btnH);
        add(cancelBtn);

        confirmBtn = new NeonButton(NeonFonts.translatable("screen.jujutsumod.character_select.confirm"), btnW, btnH, true, this::confirm);
        confirmBtn.setBounds(btnW + 8, btnY, btnW, btnH);
        add(confirmBtn);
    }

    private void confirm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerSkin.Model model = selection.modelId().equals("slim") ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
            ClientCharacterSelectionManager.applyLocal(mc.player.getUUID(), selection, model);
        }
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
        int rows = Math.max(1, (int) Math.ceil(cards.size() / 2.0));
        return top + rows * (cardH + 8) + 10;
    }

    /** Keep strip above Cancel/Confirm. */
    private float stripHeight() {
        return 36f;
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
        float ay = absY();
        float labelY = ay + stripTop();
        float stripY = labelY + 12;
        float stripH = stripHeight();

        // Clamp strip so it never sits on the action buttons.
        float btnY = ay + pageH - 32;
        if (stripY + stripH > btnY - 6) {
            stripY = btnY - 6 - stripH;
            labelY = stripY - 12;
        }

        Component label = abilityLabel();
        int labelW = ctx.font().width(label);
        ctx.sdf().add(SdfShape.builder()
                .rect(ax + labelW + 8, labelY + 3.5f, Math.max(0, pageW - labelW - 8), 1)
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

        float gap = 6;
        float cellW = (pageW - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            // Cell first.
            ctx.sdf().add(SdfShape.builder()
                    .rect(cx, stripY, cellW, stripH)
                    .radius(6)
                    .border(1, applyAlpha(t.accentArgb(), 0.12f))
                    .glow(0, 0).highlight(0.12f)
                    .fill(t.panelInset(), t.panelInset())
                    .build());
            // Emoji halo AFTER the cell so it is not covered.
            float iconCx = cx + 6 + 9;
            float iconCy = stripY + stripH / 2f;
            EmojiGlow.add(ctx, iconCx, iconCy, 18f, t.accentArgb(), 0.9f);
        }
    }

    private Component abilityLabel() {
        String tech = selection == JujutsuCharacter.NOBARA ? "Straw Doll" : "None";
        return NeonFonts.literal("Innate \u00B7 " + tech);
    }

    private void drawAbilityStripText(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        float ax = absX();
        float ay = absY();
        float labelY = ay + stripTop();
        float stripY = labelY + 12;
        float stripH = stripHeight();

        float btnY = ay + pageH - 32;
        if (stripY + stripH > btnY - 6) {
            stripY = btnY - 6 - stripH;
            labelY = stripY - 12;
        }

        g.drawString(ctx.font(), abilityLabel(), (int) ax, (int) labelY, NeonTheme.textDim(), false);

        if (selection != JujutsuCharacter.NOBARA) {
            Component none = NeonFonts.translatable("screen.jujutsumod.character_select.ability.none");
            g.drawString(ctx.font(), none, (int) (ax + (pageW - ctx.font().width(none)) / 2f),
                    (int) (stripY + (stripH - 8) / 2f), NeonTheme.textDim(), false);
            return;
        }

        float gap = 6;
        float cellW = (pageW - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            int iconSize = 18;
            int iconX = Math.round(cx + 6);
            int iconY = Math.round(stripY + (stripH - iconSize) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, ABILITY_ICONS[i], iconX, iconY, 0f, 0f, iconSize, iconSize, 96, 96, 96, 96);

            int textX = iconX + iconSize + 4;
            g.drawString(ctx.font(), ABILITY_LABELS[i], textX, (int) (stripY + (stripH - 8) / 2f), NeonTheme.textMuted(), false);

            int keyW = ctx.font().width(ABILITY_KEYS[i]) + 6;
            int keyX = (int) (cx + cellW - keyW - 2);
            int keyY = (int) (stripY - 4);
            g.fill(keyX, keyY, keyX + keyW, keyY + 10, ctx.theme().accentArgb());
            g.drawString(ctx.font(), NeonFonts.literal(ABILITY_KEYS[i]), keyX + 3, keyY + 1, NeonTheme.textOnAccent(), false);
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
