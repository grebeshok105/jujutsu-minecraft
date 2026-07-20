package jujutsu.mod.client.gui.neon.pages;

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
import jujutsu.mod.client.ui.neon.widget.NeonLabel;
import jujutsu.mod.network.SelectCharacterPayload;

public final class CharacterPage extends NeonPage {
    private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
    private static final ResourceLocation PIERCING_NAIL_ICON = JujutsuMod.id("textures/gui/abilities/piercing_nail.png");
    private static final ResourceLocation HAIRPIN_ENLARGE_ICON = JujutsuMod.id("textures/gui/abilities/hairpin_enlargement.png");
    private static final ResourceLocation HAIRPIN_EXPLOSION_ICON = JujutsuMod.id("textures/gui/abilities/hairpin_explosion.png");
    private static final ResourceLocation RESONANCE_ICON = JujutsuMod.id("textures/gui/abilities/resonance.png");

    private JujutsuCharacter selection;
    private NeonCard nobaraCard;
    private NeonCard noneCard;
    private NeonButton confirmBtn;
    private final Runnable closeAction;
    private float pageW;

    public CharacterPage(Runnable closeAction) {
        super(Component.literal("Character"));
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

        nobaraCard = new NeonCard(
                Component.translatable("screen.jujutsumod.character_select.nobara"),
                Component.translatable("screen.jujutsumod.character_select.nobara.role"),
                NOBARA_SKIN, 0xFFE48A36, () -> selection = JujutsuCharacter.NOBARA);
        noneCard = new NeonCard(
                Component.translatable("screen.jujutsumod.character_select.default"),
                Component.translatable("screen.jujutsumod.character_select.default.role"),
                null, 0xFF505760, () -> selection = JujutsuCharacter.NONE);

        float cardGap = 16;
        float cardsX = (pageW - (nobaraCard.width() * 2 + cardGap)) / 2f;
        nobaraCard.setBounds(cardsX, 24, nobaraCard.width(), nobaraCard.height());
        noneCard.setBounds(cardsX + nobaraCard.width() + cardGap, 24, noneCard.width(), noneCard.height());
        add(nobaraCard);
        add(noneCard);

        confirmBtn = new NeonButton(
                Component.translatable("screen.jujutsumod.character_select.confirm"),
                140, 32, true, this::confirm);
        confirmBtn.setBounds(pageW - 150, pageH - 50, 140, 32);
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
        nobaraCard.setSelected(selection == JujutsuCharacter.NOBARA);
        noneCard.setSelected(selection == JujutsuCharacter.NONE);
        super.tick(deltaTicks);
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
        drawPortrait(ctx);
    }

    private void drawPortrait(NeonContext ctx) {
        if (selection != JujutsuCharacter.NOBARA) return;
        GuiGraphics g = ctx.graphics();
        float ax = nobaraCard.absX();
        float ay = nobaraCard.absY();
        int headSize = 48;
        int headX = (int) (ax + (nobaraCard.width() - headSize) / 2f);
        int headY = (int) (ay + 16);
        // Face region of 64x64 skin: u=8, v=8, 8x8 pixels scaled to headSize
        g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, headX, headY,
                8f / 64f, 8f / 64f, headSize, headSize, 8, 8, 64, 64);
    }

    private void drawAbilityStripSurface(NeonContext ctx) {
        NeonTheme t = ctx.theme();
        float ax = absX();
        float ay = absY();
        float stripY = ay + 190;
        float stripW = pageW;
        float stripH = 36;

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, stripY, stripW, stripH)
                .radius(7)
                .border(1, applyAlpha(t.accentArgb(), 0.18f))
                .glow(0, 0).highlight(0.1f)
                .fill(0xE11E2026, 0xE11E2026)
                .build());

        if (selection != JujutsuCharacter.NOBARA) return;

        float gap = 6;
        float cellW = (stripW - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            ctx.sdf().add(SdfShape.builder()
                    .rect(cx, stripY, cellW, stripH)
                    .radius(6)
                    .border(1, applyAlpha(t.accentArgb(), 0.16f))
                    .glow(0, 0).highlight(0.15f)
                    .fill(0x7812151B, 0x7812151B)
                    .build());
        }
    }

    private void drawAbilityStripText(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        float ax = absX();
        float ay = absY();
        float stripY = ay + 190;
        float stripW = pageW;
        float stripH = 36;

        if (selection != JujutsuCharacter.NOBARA) {
            Component label = Component.translatable("screen.jujutsumod.character_select.ability.none");
            g.drawString(ctx.font(), label, (int) (ax + (stripW - ctx.font().width(label)) / 2f),
                    (int) (stripY + (stripH - 8) / 2f), NeonTheme.textDim(), false);
            return;
        }

        float gap = 6;
        float cellW = (stripW - gap * 3) / 4f;
        ResourceLocation[] icons = {PIERCING_NAIL_ICON, HAIRPIN_ENLARGE_ICON, HAIRPIN_EXPLOSION_ICON, RESONANCE_ICON};
        String[] keys = {"", "R", "B", ""};
        Component[] labels = {
                Component.translatable("screen.jujutsumod.character_select.ability.piercing_nail"),
                Component.translatable("screen.jujutsumod.character_select.ability.hairpin_enlarge"),
                Component.translatable("screen.jujutsumod.character_select.ability.hairpin_explosion"),
                Component.translatable("screen.jujutsumod.character_select.ability.resonance")
        };

        for (int i = 0; i < 4; i++) {
            float cx = ax + i * (cellW + gap);
            int iconSize = 18;
            int iconX = (int) (cx + 6);
            int iconY = (int) (stripY + (stripH - iconSize) / 2f);
            g.blit(RenderPipelines.GUI_TEXTURED, icons[i], iconX, iconY, 0f, 0f, iconSize, iconSize, 16, 16, 16, 16);

            int textX = iconX + iconSize + 5;
            g.drawString(ctx.font(), labels[i], textX, (int) (stripY + (stripH - 8) / 2f), NeonTheme.text(), false);

            if (!keys[i].isEmpty()) {
                int badgeX = (int) (cx + cellW - 17);
                int badgeY = (int) (stripY + stripH - 16);
                g.drawString(ctx.font(), keys[i], badgeX, badgeY, NeonTheme.textMuted(), false);
            }
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
