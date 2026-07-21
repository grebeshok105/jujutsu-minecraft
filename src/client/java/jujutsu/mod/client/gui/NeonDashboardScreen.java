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
import jujutsu.mod.client.ui.neon.NeonFonts;
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
    /**
     * Smaller than the original 660×440, but tall enough that shell pages and
     * the character strip stay inside the window (0.72 crushed content out).
     */
    private static final float WINDOW_W = 560f;
    private static final float WINDOW_H = 400f;
    private static final float SIDEBAR_W = 118f;
    private static final float HEADER_H = 34f;

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
        float ww = Math.min(WINDOW_W, width - 24);
        float wh = Math.min(WINDOW_H, height - 24);
        float wx = (width - ww) / 2f;
        float wy = (height - wh) / 2f;
        root.setHeaderHeight(HEADER_H);
        root.setWindow(wx, wy, ww, wh);
        layoutInternal();
    }

    private void layoutInternal() {
        float ww = root.windowW();
        float wh = root.windowH();

        closeBtn.setBounds(ww - 28, 6, 20, 20);

        float sbY = HEADER_H;
        float itemH = 28f;
        float itemGap = 32f;
        for (int i = 0; i < sidebarItems.size(); i++) {
            sidebarItems.get(i).setBounds(8, sbY + 18 + i * itemGap, SIDEBAR_W - 16, itemH);
        }

        float pageX = SIDEBAR_W + 10;
        float pageY = HEADER_H + 8;
        float pageW = ww - SIDEBAR_W - 20;
        // Leave room for the sidebar footer lines at the bottom of the window.
        float pageH = wh - HEADER_H - 36;
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
        r.setHeaderHeight(HEADER_H);
        r.setChromeRenderer(c -> {
            renderSidebarBackground(c);
            renderHeaderChrome(c);
        });

        closeBtn = new NeonButton(NeonFonts.literal("\u2715"), 20, 20, false, this::animateClose);
        r.add(closeBtn);

        CharacterPage charPage = new CharacterPage(this::animateClose);
        this.charPage = charPage;
        this.combatPage = new CombatPage();
        this.visualsPage = new VisualsPage();
        this.miscPage = new MiscPage();

        pageContainer = new PageContainer();
        r.add(pageContainer);

        SidebarItem charItem = new SidebarItem(dashIcon("bust"), NeonFonts.literal("Character"), () -> selectPage(charPage, 0));
        SidebarItem combatItem = new SidebarItem(dashIcon("swords"), NeonFonts.literal("Combat"), () -> selectPage(combatPage, 1));
        SidebarItem visualsItem = new SidebarItem(dashIcon("sparkles"), NeonFonts.literal("Visuals"), () -> selectPage(visualsPage, 2));
        SidebarItem miscItem = new SidebarItem(dashIcon("gear"), NeonFonts.literal("Misc"), () -> selectPage(miscPage, 3));

        sidebarItems.add(charItem);
        sidebarItems.add(combatItem);
        sidebarItems.add(visualsItem);
        sidebarItems.add(miscItem);
        for (SidebarItem item : sidebarItems) r.add(item);

        charItem.setSelected(true);
        pageContainer.setPage(charPage);

        // Theme follows currently selected character at open.
        targetTheme = charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                ? NeonTheme.NOBARA : NeonTheme.NONE;
        theme = targetTheme;
        r.setTheme(theme);

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
        ctx.flushOverlays();
        renderShellText(ctx);
    }

    private void renderHeaderChrome(NeonContext ctx) {
        NeonTheme t = theme;
        float wx = root.windowX(), wy = root.windowY();
        float ww = root.windowW();

        ctx.sdf().add(SdfShape.builder()
                .rect(wx, wy + HEADER_H - 1, ww, 1)
                .radius(0).border(0, 0).glow(0, 0)
                .fill(applyAlpha(t.accentArgb(), 0.12f), applyAlpha(t.accentArgb(), 0.12f))
                .build());

        // Sigil rings.
        float sigX = wx + 12, sigY = wy + 6;
        float sig = 16f;
        ctx.sdf().add(SdfShape.builder()
                .rect(sigX, sigY, sig, sig)
                .radius(sig / 2f).border(1.4f, t.accentArgb()).glow(3, applyAlpha(t.accentArgb(), 0.35f))
                .fill(0x00000000, 0x00000000).highlight(0f)
                .build());
        ctx.sdf().add(SdfShape.builder()
                .rect(sigX + 4, sigY + 4, 8, 8)
                .radius(4).border(1f, 0xFFFFD9A8).glow(0, 0)
                .fill(0x00000000, 0x00000000).highlight(0f)
                .build());

        // Version badge next to sigil (title removed).
        float badgeX = wx + 36;
        float badgeW = 40;
        ctx.sdf().add(SdfShape.builder()
                .rect(badgeX, wy + 8, badgeW, 14)
                .radius(4).border(1, t.border()).glow(0, 0)
                .fill(t.fillAccentTop(), t.fillAccentTop()).highlight(0f)
                .build());
    }

    private void renderShellText(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        NeonTheme t = theme;
        float wx = root.windowX(), wy = root.windowY();
        float wh = root.windowH();

        // Version only — no "JUJUTSU // DASHBOARD" title.
        g.drawString(font, NeonFonts.literal("v1.0.0"), (int) (wx + 42), (int) (wy + 11), NeonTheme.textDim(), false);

        g.drawString(font, NeonFonts.literal("MODULES"), (int) (wx + 14), (int) (wy + HEADER_H + 8), NeonTheme.textDim(), false);

        String firstName = charPage != null && charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                ? "Nobara" : "None";
        String tech = charPage != null && charPage.selection() == jujutsu.mod.character.JujutsuCharacter.NOBARA
                ? "Straw Doll" : "No";
        float footY = wy + wh - 28;
        g.drawString(font, NeonFonts.literal(firstName + " kit active"), (int) (wx + 14), (int) footY, NeonTheme.textDim(), false);
        Component techLine = NeonFonts.literal(tech + " ").withStyle(s -> s.withColor(t.accentArgb()))
                .append(NeonFonts.literal("technique"));
        g.drawString(font, techLine, (int) (wx + 14), (int) (footY + 10), NeonTheme.textDim(), false);
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
        // Opaque-enough scrim; crosshair also cancelled via NeonDashboardCrosshairMixin.
        g.fill(0, 0, this.width, this.height, 0xCC060403);
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
