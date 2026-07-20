package jujutsu.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.gui.neon.PageContainer;
import jujutsu.mod.client.gui.neon.pages.CharacterPage;
import jujutsu.mod.client.gui.neon.pages.CombatPage;
import jujutsu.mod.client.gui.neon.pages.MiscPage;
import jujutsu.mod.client.gui.neon.pages.VisualsPage;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiRoot;
import jujutsu.mod.client.ui.neon.render.NeonBlur;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import jujutsu.mod.client.ui.neon.widget.NeonButton;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;
import jujutsu.mod.client.ui.neon.widget.NeonLabel;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import jujutsu.mod.client.ui.neon.widget.SidebarItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NeonDashboardScreen extends Screen {
    private static final long OPEN_MS = 260;
    private static final long CLOSE_MS = 200;
    private static final float SIDEBAR_W = 132;
    private static final float HEADER_H = 40;

    private final SdfRenderer sdf = new SdfRenderer();
    private UiRoot root;
    private NeonTheme theme = NeonTheme.NOBARA;

    private final List<SidebarItem> sidebarItems = new ArrayList<>();
    private PageContainer pageContainer;
    private NeonButton closeBtn;
    private CharacterPage charPage;
    private CombatPage combatPage;
    private VisualsPage visualsPage;
    private MiscPage miscPage;
    private boolean contentBuilt;
    private NeonTheme targetTheme = NeonTheme.NOBARA;

    private float openAnim;
    private boolean closing;
    private boolean disposed;
    private long openStartMillis;
    private long closeStartMillis;
    private long lastFrameNanos;

    public NeonDashboardScreen() {
        super(Component.empty());
        openStartMillis = System.currentTimeMillis();
        lastFrameNanos = System.nanoTime();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        if (root == null) {
            root = buildRoot();
        }
        layoutWindow();
        root.layout();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        layoutWindow();
        root.layout();
    }

    private void layoutWindow() {
        float ww = Math.min(660, width - 40);
        float wh = Math.min(440, height - 40);
        float wx = (width - ww) / 2f;
        float wy = (height - wh) / 2f;
        root.setWindow(wx, wy, ww, wh);
        layoutInternal();
    }

    private void layoutInternal() {
        float ww = root.windowW();
        float wh = root.windowH();

        closeBtn.setBounds(ww - 34, 8, 24, 24);

        float sbY = HEADER_H;
        for (int i = 0; i < sidebarItems.size(); i++) {
            sidebarItems.get(i).setBounds(10, sbY + 26 + i * 38, SIDEBAR_W - 20, 34);
        }

        float pageX = SIDEBAR_W + 12;
        float pageY = HEADER_H + 10;
        float pageW = ww - SIDEBAR_W - 24;
        float pageH = wh - HEADER_H - 20;
        pageContainer.setBounds(pageX, pageY, pageW, pageH);
        pageContainer.layout();

        if (!contentBuilt && pageW > 0 && pageH > 0) {
            contentBuilt = true;
            charPage.buildContent(pageW, pageH);
            combatPage.buildContent(pageW, pageH);
            visualsPage.buildContent(pageW, pageH);
            miscPage.buildContent(pageW, pageH);
        }
    }

    private UiRoot buildRoot() {
        UiRoot r = new UiRoot(theme, this::animateClose);
        r.setChromeRenderer(c -> {
            renderSidebarBackground(c);
            renderHeaderChrome(c);
        });

        closeBtn = new NeonButton(Component.literal("\u2715"), 24, 24, false, this::animateClose);
        r.add(closeBtn);

        CharacterPage charPage = new CharacterPage(this::animateClose);
        this.charPage = charPage;
        this.combatPage = new CombatPage();
        this.visualsPage = new VisualsPage();
        this.miscPage = new MiscPage();

        pageContainer = new PageContainer();
        r.add(pageContainer);

        SidebarItem charItem = new SidebarItem(dashIcon("bust"), Component.literal("Character"), () -> selectPage(charPage, 0));
        SidebarItem combatItem = new SidebarItem(dashIcon("swords"), Component.literal("Combat"), () -> selectPage(combatPage, 1));
        SidebarItem visualsItem = new SidebarItem(dashIcon("sparkles"), Component.literal("Visuals"), () -> selectPage(visualsPage, 2));
        SidebarItem miscItem = new SidebarItem(dashIcon("gear"), Component.literal("Misc"), () -> selectPage(miscPage, 3));

        sidebarItems.add(charItem);
        sidebarItems.add(combatItem);
        sidebarItems.add(visualsItem);
        sidebarItems.add(miscItem);
        for (SidebarItem item : sidebarItems) r.add(item);

        charItem.setSelected(true);
        pageContainer.setPage(charPage);

        return r;
    }

    private static net.minecraft.resources.ResourceLocation dashIcon(String name) {
        return jujutsu.mod.JujutsuMod.id("textures/gui/dashboard/emoji_" + name + ".png");
    }

    private void selectPage(NeonPage page, int index) {
        for (int i = 0; i < sidebarItems.size(); i++) {
            sidebarItems.get(i).setSelected(i == index);
        }
        pageContainer.setPage(page);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float deltaTicks = (now - lastFrameNanos) / 50_000_000f;
        deltaTicks = Math.max(0.05f, Math.min(deltaTicks, 3f));
        lastFrameNanos = now;

        updateAnimation();
        if (disposed) return;
        root.drag(mouseX, mouseY, width, height);
        layoutInternal();

        updateMouseTrackers(mouseX, mouseY);
        root.tick(deltaTicks);

        if (charPage != null) {
            targetTheme = charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                    ? NeonTheme.NOBARA : NeonTheme.NONE;
        }
        theme = theme.lerp(targetTheme, 0.12f * deltaTicks);
        root.setTheme(theme);

        float anim = UiEase.outCubic(openAnim);
        NeonContext ctx = new NeonContext(sdf, g, font, theme, mouseX, mouseY, deltaTicks, anim);

        sdf.setGlobalAlpha(anim);
        sdf.begin();
        root.renderSurface(ctx);
        sdf.flush();

        root.renderText(ctx);
        renderShellText(ctx);
    }

    private void renderHeaderChrome(NeonContext ctx) {
        NeonTheme t = theme;
        float wx = root.windowX(), wy = root.windowY();
        float ww = root.windowW();

        // Header bottom border line.
        ctx.sdf().add(SdfShape.builder()
                .rect(wx, wy + HEADER_H - 1, ww, 1)
                .radius(0).border(0, 0).glow(0, 0)
                .fill(applyAlpha(t.accentArgb(), 0.12f), applyAlpha(t.accentArgb(), 0.12f))
                .build());

        // Sigil: outer accent ring + inner ring.
        float sigX = wx + 14, sigY = wy + 10;
        ctx.sdf().add(SdfShape.builder()
                .rect(sigX, sigY, 20, 20)
                .radius(10).border(1.5f, t.accentArgb()).glow(4, applyAlpha(t.accentArgb(), 0.35f))
                .fill(0x00000000, 0x00000000).highlight(0f)
                .build());
        ctx.sdf().add(SdfShape.builder()
                .rect(sigX + 5, sigY + 5, 10, 10)
                .radius(5).border(1f, 0xFFFFD9A8).glow(0, 0)
                .fill(0x00000000, 0x00000000).highlight(0f)
                .build());

        // Version badge background.
        float badgeW = 44;
        ctx.sdf().add(SdfShape.builder()
                .rect(wx + 178, wy + 12, badgeW, 16)
                .radius(4).border(1, t.border()).glow(0, 0)
                .fill(t.fillAccentTop(), t.fillAccentTop()).highlight(0f)
                .build());
    }

    private void renderShellText(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        NeonTheme t = theme;
        float wx = root.windowX(), wy = root.windowY();
        float wh = root.windowH();

        // Title with accent-colored "//".
        Component title = Component.literal("JUJUTSU ")
                .append(Component.literal("//").withStyle(s -> s.withColor(t.accentArgb())))
                .append(Component.literal(" DASHBOARD"));
        g.drawString(font, title, (int) (wx + 42), (int) (wy + 16), NeonTheme.text(), false);

        // Version badge text.
        g.drawString(font, "v1.0.0", (int) (wx + 184), (int) (wy + 16), NeonTheme.textDim(), false);

        // MODULES category label.
        g.drawString(font, "MODULES", (int) (wx + 18), (int) (wy + HEADER_H + 12), NeonTheme.textDim(), false);

        // Sidebar footer status.
        String firstName = charPage != null && charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                ? "Nobara" : "None";
        String tech = charPage != null && charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                ? "Straw Doll" : "No";
        float footY = wy + wh - 34;
        g.drawString(font, firstName + " kit active", (int) (wx + 18), (int) footY, NeonTheme.textDim(), false);
        Component techLine = Component.literal(tech + " ").withStyle(s -> s.withColor(t.accentArgb()))
                .append(Component.literal("technique"));
        g.drawString(font, techLine, (int) (wx + 18), (int) (footY + 12), NeonTheme.textDim(), false);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private void renderSidebarBackground(NeonContext ctx) {
        NeonTheme t = theme;
        float wx = root.windowX(), wy = root.windowY();
        float wh = root.windowH();
        ctx.sdf().add(SdfShape.builder()
                .rect(wx, wy + HEADER_H, SIDEBAR_W, wh - HEADER_H)
                .radius(0).border(0, 0).glow(0, 0)
                .fill(t.sidebarTop(), t.sidebarBottom())
                .build());
    }

    private void updateMouseTrackers(double mx, double my) {
        closeBtn.updateMouse(mx, my);
        for (SidebarItem item : sidebarItems) item.updateMouse(mx, my);
        updatePageMouseTrackers(pageContainer, mx, my);
    }

    private void updatePageMouseTrackers(jujutsu.mod.client.ui.neon.UiContainer container, double mx, double my) {
        for (UiComponent child : container.children()) {
            if (child instanceof NeonToggle t) t.updateMouse(mx, my);
            else if (child instanceof NeonSlider s) s.updateMouse(mx, my);
            else if (child instanceof NeonDropdown d) d.updateMouse(mx, my);
            else if (child instanceof jujutsu.mod.client.ui.neon.widget.KeybindField k) k.updateMouse(mx, my);
            else if (child instanceof jujutsu.mod.client.ui.neon.widget.NeonColorPicker c) c.updateMouse(mx, my);
            else if (child instanceof jujutsu.mod.client.ui.neon.widget.NeonCard card) card.updateMouse(mx, my);
            else if (child instanceof NeonButton b) b.updateMouse(mx, my);
            else if (child instanceof jujutsu.mod.client.ui.neon.UiContainer c) updatePageMouseTrackers(c, mx, my);
        }
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        if (closing) {
            float t = (now - closeStartMillis) / (float) CLOSE_MS;
            openAnim = 1f - UiEase.clamp01(t);
            if (t >= 1f && !disposed) {
                disposed = true;
                sdf.close();
                super.onClose();
            }
        } else {
            float t = (now - openStartMillis) / (float) OPEN_MS;
            openAnim = UiEase.clamp01(t);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        NeonBlur.apply();
        // GuiGraphics fill batches after the HUD flush, so this covers the crosshair.
        // The SDF gradient scrim still renders on top for the neon look.
        g.fill(0, 0, this.width, this.height, 0x8A060403);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && root.isInHeader(mouseX, mouseY)) {
            root.startDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        root.endDrag();
        return root.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_V) {
            animateClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        animateClose();
    }

    private void animateClose() {
        if (closing) return;
        closing = true;
        closeStartMillis = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        if (!disposed) {
            disposed = true;
            sdf.close();
        }
        super.removed();
    }
}
