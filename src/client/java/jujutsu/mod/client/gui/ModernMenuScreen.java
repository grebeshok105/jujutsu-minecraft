package jujutsu.mod.client.gui;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.gui.modern.ModernTheme;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import jujutsu.mod.network.SelectCharacterPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Vessel menu (N) — visual 1:1 intent of Rich clickgui screenshot + ClickGui layout.
 * Functionality stays ours (character select, kit info). Neon dashboard (V) unchanged.
 *
 * Spec: docs/research/2026-07-21-rich-clickgui-visual-spec.md
 */
public final class ModernMenuScreen extends Screen {
	private static final long OPEN_MS = 280;
	private static final long CLOSE_MS = 200;
	private static final float RISE = 12f;

	private static final ResourceLocation NOBARA_SKIN =
			JujutsuMod.id("textures/entity/character/nobara.png");
	private static final ResourceLocation EMOJI_BUST =
			JujutsuMod.id("textures/gui/dashboard/emoji_bust.png");
	private static final ResourceLocation EMOJI_SWORDS =
			JujutsuMod.id("textures/gui/dashboard/emoji_swords.png");
	private static final ResourceLocation EMOJI_SPARKLES =
			JujutsuMod.id("textures/gui/dashboard/emoji_sparkles.png");
	private static final ResourceLocation EMOJI_GEAR =
			JujutsuMod.id("textures/gui/dashboard/emoji_gear.png");
	private static final ResourceLocation EMOJI_PIN =
			JujutsuMod.id("textures/gui/dashboard/emoji_pin.png");
	private static final ResourceLocation EMOJI_BOOM =
			JujutsuMod.id("textures/gui/dashboard/emoji_boom.png");
	private static final ResourceLocation EMOJI_LINK =
			JujutsuMod.id("textures/gui/dashboard/emoji_link.png");
	private static final ResourceLocation EMOJI_BOLT =
			JujutsuMod.id("textures/gui/dashboard/emoji_bolt.png");

	private enum Tab {
		CHARACTER("Character"),
		COMBAT("Combat"),
		VISUALS("Visuals"),
		MISC("Misc");

		final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	private record ModuleRow(String name, String bind, JujutsuCharacter character, boolean skin) {}

	private static final ModuleRow[] CHARACTER_MODULES = {
			new ModuleRow("Nobara", "N", JujutsuCharacter.NOBARA, true),
			new ModuleRow("None", "—", JujutsuCharacter.NONE, false),
	};

	private static final String[] ABILITY_NAMES = {
			"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance"
	};
	private static final String[] ABILITY_KEYS = {"R", "B", "Shift+R", "LMB"};
	private static final String[] ABILITY_SUB = {
			"Directed nail shot", "Enlarge marked hairpin", "Mass hairpin detonation", "Hammer / ritual"
	};
	private static final ResourceLocation[] ABILITY_ICONS = {
			EMOJI_PIN, EMOJI_BOLT, EMOJI_BOOM, EMOJI_LINK
	};

	private final SdfRenderer sdf = new SdfRenderer();

	private Tab tab = Tab.CHARACTER;
	private int moduleIndex;
	private JujutsuCharacter selection;

	private float openAnim;
	private boolean closing;
	private boolean disposed;
	private boolean forceClose;
	private long openStartMillis;
	private long closeStartMillis;
	private long lastFrameNanos;

	private final float[] sideHover = new float[Tab.values().length];
	private final float[] modHover = new float[8];
	private float confirmHover;
	private float cancelHover;
	private float selectFlash;
	private float contentAlpha = 1f;

	public ModernMenuScreen() {
		super(Component.translatable("screen.jujutsumod.modern_menu"));
		openStartMillis = System.currentTimeMillis();
		lastFrameNanos = System.nanoTime();
		selection = initialSelection();
		moduleIndex = indexOf(selection);
	}

	private static JujutsuCharacter initialSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			return ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
		}
		return JujutsuCharacter.NONE;
	}

	private static int indexOf(JujutsuCharacter c) {
		for (int i = 0; i < CHARACTER_MODULES.length; i++) {
			if (CHARACTER_MODULES[i].character() == c) {
				return i;
			}
		}
		return 1;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		MsdfFonts.warm();
	}

	private float panelX() {
		return (width - ModernTheme.BG_W) * 0.5f;
	}

	private float panelY() {
		float base = (height - ModernTheme.BG_H) * 0.5f;
		return base + (1f - UiEase.outCubic(openAnim)) * RISE;
	}

	private float contentH() {
		return ModernTheme.BG_H - ModernTheme.CONTENT_TOP - ModernTheme.CONTENT_BOTTOM_PAD;
	}

	// ── lifecycle ─────────────────────────────────────────────────────

	@Override
	public void tick() {
		super.tick();
		if (forceClose && !disposed) {
			disposed = true;
			sdf.close();
			minecraft.setScreen(null);
		}
	}

	@Override
	public void removed() {
		if (!disposed) {
			disposed = true;
			sdf.close();
		}
		super.removed();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// scrim in SDF batch (must not cover shell)
	}

	// ── render ────────────────────────────────────────────────────────

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = tickDelta();
		updateAnimation();
		float anim = UiEase.outCubic(openAnim);
		float px = panelX();
		float py = panelY();

		contentAlpha = UiEase.approach(contentAlpha, 1f, 0.3f, dt * 20f);
		selectFlash = UiEase.approach(selectFlash, 0f, 0.14f, dt * 20f);
		updateHovers(mouseX, mouseY, px, py, dt);
		float ca = anim * contentAlpha;

		sdf.setGlobalAlpha(anim);
		sdf.begin();

		// Scrim first (painter's order → under shell)
		sdf.add(SdfShape.builder()
				.rect(-20, -20, width + 40, height + 40)
				.radius(0).border(0, 0)
				.fill(ModernTheme.SCRIM, ModernTheme.SCRIM)
				.build());

		// Outer shell — single continuous rounded rect (screenshot)
		sdf.add(SdfShape.builder()
				.rect(px, py, ModernTheme.BG_W, ModernTheme.BG_H)
				.radius(ModernTheme.RADIUS)
				.border(1f, ModernTheme.BORDER)
				.glow(8f, 0x28000000)
				.highlight(0.06f)
				.fill(ModernTheme.PANEL, ModernTheme.PANEL_DEEP)
				.build());

		// Thin vertical divider only — no sharp sidebar rect (keeps outer radius clean).
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.SIDEBAR_W, py + 12, 1f, ModernTheme.BG_H - 24)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());
		// Header hairline under title / search row
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.SIDEBAR_W + 8, py + ModernTheme.HEADER_H - 1,
						ModernTheme.BG_W - ModernTheme.SIDEBAR_W - 16, 1f)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());

		drawAvatarChip(px, py);
		drawSearchPill(px, py);
		drawSidebarItems(px, py);
		drawModuleList(px, py);
		drawSettingsPanel(px, py);

		sdf.flush();

		// Text
		drawAvatarText(px, py, ca);
		drawSidebarText(px, py, ca);
		drawHeaderText(px, py, ca);
		drawModuleText(px, py, ca);
		drawSettingsText(px, py, ca);
		MsdfFonts.endFrame();

		drawSidebarIcons(g, px, py);
		drawModuleIcons(g, px, py);
		drawSettingsIcons(g, px, py);
	}

	// ── surfaces ──────────────────────────────────────────────────────

	private void drawAvatarChip(float px, float py) {
		// top-left profile card inside sidebar (screenshot)
		float ax = px + 8;
		float ay = py + 8;
		float aw = ModernTheme.SIDEBAR_W - 16;
		float ah = 32;
		sdf.add(SdfShape.builder()
				.rect(ax, ay, aw, ah)
				.radius(9f)
				.border(1f, ModernTheme.BORDER_SOFT)
				.highlight(0.08f)
				.fill(ModernTheme.AVATAR, ModernTheme.AVATAR)
				.build());
		// online dot
		sdf.add(SdfShape.builder()
				.rect(ax + 7, ay + 12, 7, 7)
				.radius(3.5f)
				.border(0, 0)
				.glow(3f, ModernTheme.withAlpha(ModernTheme.ONLINE, 0.45f))
				.fill(ModernTheme.ONLINE, ModernTheme.ONLINE)
				.build());
	}

	private void drawSearchPill(float px, float py) {
		float sw = 92f;
		float sh = 18f;
		float sx = px + ModernTheme.BG_W - 12 - sw;
		float sy = py + 10;
		sdf.add(SdfShape.builder()
				.rect(sx, sy, sw, sh)
				.radius(8f)
				.border(1f, ModernTheme.BORDER_SOFT)
				.fill(ModernTheme.CHIP, ModernTheme.CHIP)
				.build());
	}

	private void drawSidebarItems(float px, float py) {
		// section "MAIN"
		float y = py + 52;
		// items start
		y = py + 64;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			float h = ModernTheme.SIDE_ITEM_H;
			float x = px + 7;
			float w = ModernTheme.SIDEBAR_W - 14;
			boolean sel = tabs[i] == tab;
			float hover = sideHover[i];
			if (sel || hover > 0.02f) {
				int fill = sel ? ModernTheme.ROW_SELECTED
						: ModernTheme.lerpColor(0x00111111, ModernTheme.ROW_HOVER, hover);
				sdf.add(SdfShape.builder()
						.rect(x, y, w, h)
						.radius(7f)
						.border(0, 0)
						.highlight(sel ? 0.10f : 0.05f * hover)
						.fill(fill, fill)
						.build());
			}
			if (sel) {
				// thin left indicator — soft white, not amber
				sdf.add(SdfShape.builder()
						.rect(x + 2, y + 5, 2f, h - 10)
						.radius(1f)
						.border(0, 0)
						.fill(0xFFE8E8E8, 0xFFE8E8E8)
						.build());
			}
			y += h + ModernTheme.SIDE_ITEM_GAP;
		}
	}

	private void drawModuleList(float px, float py) {
		float lx = px + ModernTheme.LIST_X;
		float ly = py + ModernTheme.CONTENT_TOP;
		float lw = ModernTheme.LIST_W;
		float lh = contentH();

		// list column plate
		sdf.add(SdfShape.builder()
				.rect(lx, ly, lw, lh)
				.radius(10f)
				.border(1f, ModernTheme.BORDER_SOFT)
				.fill(ModernTheme.COLUMN, ModernTheme.COLUMN)
				.build());

		ModuleRow[] rows = modulesForTab();
		float rowY = ly + 6;
		for (int i = 0; i < rows.length; i++) {
			boolean sel = isModuleSelected(i);
			float hover = i < modHover.length ? modHover[i] : 0f;
			float rh = ModernTheme.ROW_H;
			int fill = sel ? ModernTheme.ROW_SELECTED
					: ModernTheme.lerpColor(ModernTheme.ROW, ModernTheme.ROW_HOVER, hover);
			sdf.add(SdfShape.builder()
					.rect(lx + 5, rowY, lw - 10, rh)
					.radius(8f)
					.border(1f, ModernTheme.withAlpha(0xFFFFFFFF,
							sel ? 0.10f + 0.12f * selectFlash : 0.04f + 0.06f * hover))
					.highlight(sel ? 0.10f : 0.05f * hover)
					.fill(fill, fill)
					.build());
			// bind chip (right)
			float bw = 14f;
			sdf.add(SdfShape.builder()
					.rect(lx + lw - 10 - bw - 4, rowY + (rh - 14) * 0.5f, bw, 14)
					.radius(4f)
					.border(0, 0)
					.fill(0xFF2A2A2A, 0xFF2A2A2A)
					.build());
			rowY += rh + ModernTheme.ROW_GAP;
		}
	}

	private void drawSettingsPanel(float px, float py) {
		float sx = px + ModernTheme.SETTINGS_X;
		float sy = py + ModernTheme.CONTENT_TOP;
		float sw = ModernTheme.SETTINGS_W;
		float sh = contentH();

		sdf.add(SdfShape.builder()
				.rect(sx, sy, sw, sh)
				.radius(10f)
				.border(1f, ModernTheme.BORDER_SOFT)
				.highlight(0.05f)
				.fill(ModernTheme.COLUMN, ModernTheme.COLUMN)
				.build());

		// title underline hairline
		sdf.add(SdfShape.builder()
				.rect(sx + 10, sy + 22, 28, 1.5f)
				.radius(1f)
				.border(0, 0)
				.fill(0xFF3A3A3A, 0xFF3A3A3A)
				.build());

		if (tab == Tab.CHARACTER) {
			// settings rows (ability keys) like screenshot
			float rowY = sy + 32;
			ModuleRow mod = CHARACTER_MODULES[moduleIndex];
			int rows = mod.character() == JujutsuCharacter.NOBARA ? ABILITY_NAMES.length : 1;
			for (int i = 0; i < rows; i++) {
				float rh = 34f;
				sdf.add(SdfShape.builder()
						.rect(sx + 6, rowY, sw - 12, rh)
						.radius(8f)
						.border(1f, ModernTheme.BORDER_SOFT)
						.fill(ModernTheme.ROW, ModernTheme.ROW)
						.build());
				// value pill (right) — like dropdown value in screenshot
				if (mod.character() == JujutsuCharacter.NOBARA) {
					float vw = 36f;
					sdf.add(SdfShape.builder()
							.rect(sx + sw - 12 - vw - 4, rowY + 9, vw, 16)
							.radius(5f)
							.border(0, 0)
							.fill(0xFF2A2A2A, 0xFF2A2A2A)
							.build());
				}
				rowY += rh + 4;
			}

			// Cancel / Confirm — quiet gray, screenshot language
			float btnH = 24f;
			float btnY = sy + sh - btnH - 8;
			float gap = 6f;
			float cancelW = (sw - 12 - gap) * 0.40f;
			float confW = (sw - 12 - gap) * 0.60f;
			float bx = sx + 6;

			sdf.add(SdfShape.builder()
					.rect(bx, btnY, cancelW, btnH)
					.radius(7f)
					.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.06f + 0.08f * cancelHover))
					.fill(ModernTheme.lerpColor(ModernTheme.ROW, ModernTheme.ROW_HOVER, cancelHover),
							ModernTheme.ROW)
					.build());
			sdf.add(SdfShape.builder()
					.rect(bx + cancelW + gap, btnY, confW, btnH)
					.radius(7f)
					.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.10f + 0.12f * confirmHover))
					.highlight(0.08f + 0.08f * confirmHover)
					.fill(ModernTheme.lerpColor(0xFF2A2A28, 0xFF343432, confirmHover),
							0xFF222220)
					.build());
		} else {
			float rowY = sy + 32;
			int n = tab == Tab.COMBAT && selection == JujutsuCharacter.NOBARA ? 4 : 3;
			for (int i = 0; i < n; i++) {
				sdf.add(SdfShape.builder()
						.rect(sx + 6, rowY, sw - 12, 34f)
						.radius(8f)
						.border(1f, ModernTheme.BORDER_SOFT)
						.fill(ModernTheme.ROW, ModernTheme.ROW)
						.build());
				rowY += 38;
			}
		}
	}

	// ── text ──────────────────────────────────────────────────────────

	private void drawAvatarText(float px, float py, float a) {
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player != null ? mc.player.getName().getString() : "Player";
		if (name.length() > 9) {
			name = name.substring(0, 8) + "…";
		}
		MsdfFonts.draw(name, px + 24, py + 13, 7.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
		String sub = selection == JujutsuCharacter.NOBARA ? "Nobara" : "None";
		MsdfFonts.draw(sub, px + 24, py + 24, 6.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
	}

	private void drawSidebarText(float px, float py, float a) {
		MsdfFonts.draw("MAIN", px + 10, py + 50, 6.5f, ModernTheme.withAlpha(ModernTheme.TEXT_SECTION, a));
		float y = py + 64;
		for (Tab t : Tab.values()) {
			boolean sel = t == tab;
			MsdfFonts.draw(t.title, px + 24, y + 7, 8f,
					ModernTheme.withAlpha(sel ? ModernTheme.TEXT : ModernTheme.TEXT_DIM, a));
			y += ModernTheme.SIDE_ITEM_H + ModernTheme.SIDE_ITEM_GAP;
		}
		MsdfFonts.draw("Soon…", px + 10, py + ModernTheme.BG_H - 18, 7f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a * 0.75f));
	}

	private void drawHeaderText(float px, float py, float a) {
		// Category title next to sidebar (screenshot: "Combat")
		MsdfFonts.draw(tab.title, px + ModernTheme.SIDEBAR_W + 12, py + 12, 11f,
				ModernTheme.withAlpha(ModernTheme.TEXT, a));
		// search placeholder
		float sw = 92f;
		float sx = px + ModernTheme.BG_W - 12 - sw;
		MsdfFonts.draw("Search…", sx + 8, py + 14, 7f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a * 0.75f));
	}

	private void drawModuleText(float px, float py, float a) {
		float lx = px + ModernTheme.LIST_X;
		float ly = py + ModernTheme.CONTENT_TOP;
		ModuleRow[] rows = modulesForTab();
		float rowY = ly + 6;
		for (int i = 0; i < rows.length; i++) {
			ModuleRow r = rows[i];
			boolean sel = isModuleSelected(i);
			MsdfFonts.draw(r.name(), lx + 12, rowY + 9, 8.5f,
					ModernTheme.withAlpha(sel ? ModernTheme.TEXT : ModernTheme.TEXT_DIM, a));
			MsdfFonts.drawCentered(r.bind(), lx + ModernTheme.LIST_W - 10 - 11, rowY + 9, 7f,
					ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			rowY += ModernTheme.ROW_H + ModernTheme.ROW_GAP;
		}
	}

	private void drawSettingsText(float px, float py, float a) {
		float sx = px + ModernTheme.SETTINGS_X;
		float sy = py + ModernTheme.CONTENT_TOP;
		float sw = ModernTheme.SETTINGS_W;
		float sh = contentH();

		if (tab == Tab.CHARACTER) {
			ModuleRow mod = CHARACTER_MODULES[moduleIndex];
			MsdfFonts.draw(mod.name(), sx + 10, sy + 8, 10.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));

			float rowY = sy + 32;
			if (mod.character() == JujutsuCharacter.NOBARA) {
				for (int i = 0; i < ABILITY_NAMES.length; i++) {
					MsdfFonts.draw(ABILITY_NAMES[i], sx + 24, rowY + 7, 8f,
							ModernTheme.withAlpha(ModernTheme.TEXT, a));
					MsdfFonts.draw(ABILITY_SUB[i], sx + 24, rowY + 18, 6.5f,
							ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
					float vw = 36f;
					MsdfFonts.drawCentered(ABILITY_KEYS[i],
							sx + sw - 12 - vw * 0.5f - 4, rowY + 12, 7f,
							ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
					rowY += 38;
				}
			} else {
				MsdfFonts.draw("No technique", sx + 14, rowY + 10, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				MsdfFonts.draw("Play as a normal survivor", sx + 14, rowY + 22, 6.5f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}

			float btnH = 24f;
			float btnY = sy + sh - btnH - 8;
			float gap = 6f;
			float cancelW = (sw - 12 - gap) * 0.40f;
			float confW = (sw - 12 - gap) * 0.60f;
			float bx = sx + 6;
			MsdfFonts.drawCentered("Cancel", bx + cancelW * 0.5f, btnY + 7, 8f,
					ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
			MsdfFonts.drawCentered("Confirm", bx + cancelW + gap + confW * 0.5f, btnY + 7, 8.5f,
					ModernTheme.withAlpha(ModernTheme.TEXT, a));
		} else if (tab == Tab.COMBAT) {
			MsdfFonts.draw("Loadout", sx + 10, sy + 8, 10.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			float rowY = sy + 32;
			if (selection == JujutsuCharacter.NOBARA) {
				for (int i = 0; i < ABILITY_NAMES.length; i++) {
					MsdfFonts.draw(ABILITY_NAMES[i], sx + 14, rowY + 7, 8f,
							ModernTheme.withAlpha(ModernTheme.TEXT, a));
					MsdfFonts.draw(ABILITY_SUB[i], sx + 14, rowY + 18, 6.5f,
							ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
					MsdfFonts.draw(ABILITY_KEYS[i],
							sx + sw - 14 - MsdfFonts.width(ABILITY_KEYS[i], 7.5f), rowY + 12, 7.5f,
							ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
					rowY += 38;
				}
			} else {
				MsdfFonts.draw("Select a vessel first", sx + 14, rowY + 12, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}
		} else if (tab == Tab.VISUALS) {
			MsdfFonts.draw("Presentation", sx + 10, sy + 8, 10.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			String[] t = {"MSDF Type", "SDF Panels", "Rich Shell"};
			String[] b = {
					"Sharp distance-field labels",
					"Soft rounded charcoal cards",
					"Sidebar · list · settings"
			};
			float rowY = sy + 32;
			for (int i = 0; i < t.length; i++) {
				MsdfFonts.draw(t[i], sx + 14, rowY + 7, 8f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(b[i], sx + 14, rowY + 18, 6.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 38;
			}
		} else {
			MsdfFonts.draw("Controls", sx + 10, sy + 8, 10.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			String[] t = {"Modern Menu", "Neon Dashboard", "Nobara Kit"};
			String[] b = {"Key N open / close", "Key V original UI", "R · B · Shift+R · LMB"};
			float rowY = sy + 32;
			for (int i = 0; i < t.length; i++) {
				MsdfFonts.draw(t[i], sx + 14, rowY + 7, 8f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(b[i], sx + 14, rowY + 18, 6.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 38;
			}
		}
	}

	// ── icons ─────────────────────────────────────────────────────────

	private void drawSidebarIcons(GuiGraphics g, float px, float py) {
		ResourceLocation[] icons = {EMOJI_BUST, EMOJI_SWORDS, EMOJI_SPARKLES, EMOJI_GEAR};
		float y = py + 64 + 5;
		for (ResourceLocation icon : icons) {
			g.blit(RenderPipelines.GUI_TEXTURED, icon,
					Math.round(px + 10), Math.round(y), 0f, 0f, 11, 11, 96, 96, 96, 96);
			y += ModernTheme.SIDE_ITEM_H + ModernTheme.SIDE_ITEM_GAP;
		}
	}

	private void drawModuleIcons(GuiGraphics g, float px, float py) {
		if (tab != Tab.CHARACTER) {
			return;
		}
		float lx = px + ModernTheme.LIST_X;
		float ly = py + ModernTheme.CONTENT_TOP;
		float rowY = ly + 6;
		for (ModuleRow r : CHARACTER_MODULES) {
			int s = 12;
			int ix = Math.round(lx + 10);
			int iy = Math.round(rowY + 8);
			if (r.skin()) {
				g.enableScissor(ix, iy, ix + s, iy + s);
				g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, ix, iy, 8f, 8f, s, s, 8, 8, 64, 64);
				g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, ix, iy, 40f, 8f, s, s, 8, 8, 64, 64);
				g.disableScissor();
			} else {
				g.blit(RenderPipelines.GUI_TEXTURED, EMOJI_BUST, ix, iy, 0f, 0f, s, s, 96, 96, 96, 96);
			}
			rowY += ModernTheme.ROW_H + ModernTheme.ROW_GAP;
		}
	}

	private void drawSettingsIcons(GuiGraphics g, float px, float py) {
		if (tab != Tab.CHARACTER) {
			return;
		}
		ModuleRow mod = CHARACTER_MODULES[moduleIndex];
		if (mod.character() != JujutsuCharacter.NOBARA) {
			return;
		}
		float sx = px + ModernTheme.SETTINGS_X;
		float sy = py + ModernTheme.CONTENT_TOP;
		float rowY = sy + 32;
		for (ResourceLocation icon : ABILITY_ICONS) {
			g.blit(RenderPipelines.GUI_TEXTURED, icon,
					Math.round(sx + 10), Math.round(rowY + 10), 0f, 0f, 11, 11, 96, 96, 96, 96);
			rowY += 38;
		}
	}

	// ── data helpers ──────────────────────────────────────────────────

	private ModuleRow[] modulesForTab() {
		return switch (tab) {
			case CHARACTER -> CHARACTER_MODULES;
			case COMBAT -> selection == JujutsuCharacter.NOBARA
					? new ModuleRow[] {
							new ModuleRow("Piercing", "R", null, false),
							new ModuleRow("Enlarge", "B", null, false),
							new ModuleRow("Boom", "S+R", null, false),
							new ModuleRow("Resonance", "LMB", null, false),
					}
					: new ModuleRow[] {new ModuleRow("Empty", "—", null, false)};
			case VISUALS -> new ModuleRow[] {
					new ModuleRow("MSDF", "—", null, false),
					new ModuleRow("SDF", "—", null, false),
					new ModuleRow("Shell", "—", null, false),
			};
			case MISC -> new ModuleRow[] {
					new ModuleRow("Menu", "N", null, false),
					new ModuleRow("Neon", "V", null, false),
					new ModuleRow("Kit", "R/B", null, false),
			};
		};
	}

	private boolean isModuleSelected(int i) {
		if (tab == Tab.CHARACTER) {
			return i == moduleIndex;
		}
		return false;
	}

	// ── input ─────────────────────────────────────────────────────────

	private float tickDelta() {
		long now = System.nanoTime();
		float dt = (now - lastFrameNanos) / 1_000_000_000f;
		lastFrameNanos = now;
		return Math.min(dt, 0.05f);
	}

	private void updateHovers(int mx, int my, float px, float py, float dt) {
		float fdt = dt * 60f;
		float speed = 0.38f;

		float y = py + 64;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			boolean hit = hit(mx, my, px + 7, y, ModernTheme.SIDEBAR_W - 14, ModernTheme.SIDE_ITEM_H);
			sideHover[i] = UiEase.approach(sideHover[i], hit ? 1f : 0f, speed, fdt);
			y += ModernTheme.SIDE_ITEM_H + ModernTheme.SIDE_ITEM_GAP;
		}

		ModuleRow[] rows = modulesForTab();
		float lx = px + ModernTheme.LIST_X;
		float ly = py + ModernTheme.CONTENT_TOP;
		float rowY = ly + 6;
		for (int i = 0; i < rows.length && i < modHover.length; i++) {
			boolean hit = hit(mx, my, lx + 5, rowY, ModernTheme.LIST_W - 10, ModernTheme.ROW_H);
			modHover[i] = UiEase.approach(modHover[i], hit ? 1f : 0f, speed, fdt);
			rowY += ModernTheme.ROW_H + ModernTheme.ROW_GAP;
		}

		if (tab == Tab.CHARACTER) {
			float sx = px + ModernTheme.SETTINGS_X;
			float sy = py + ModernTheme.CONTENT_TOP;
			float sw = ModernTheme.SETTINGS_W;
			float sh = contentH();
			float btnH = 24f;
			float btnY = sy + sh - btnH - 8;
			float gap = 6f;
			float cancelW = (sw - 12 - gap) * 0.40f;
			float confW = (sw - 12 - gap) * 0.60f;
			float bx = sx + 6;
			cancelHover = UiEase.approach(cancelHover, hit(mx, my, bx, btnY, cancelW, btnH) ? 1f : 0f, speed, fdt);
			confirmHover = UiEase.approach(confirmHover,
					hit(mx, my, bx + cancelW + gap, btnY, confW, btnH) ? 1f : 0f, speed, fdt);
		} else {
			cancelHover = UiEase.approach(cancelHover, 0f, speed, fdt);
			confirmHover = UiEase.approach(confirmHover, 0f, speed, fdt);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || openAnim < 0.85f || closing) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		float px = panelX();
		float py = panelY();

		// sidebar
		float y = py + 64;
		for (Tab t : Tab.values()) {
			if (hit(mouseX, mouseY, px + 7, y, ModernTheme.SIDEBAR_W - 14, ModernTheme.SIDE_ITEM_H)) {
				if (tab != t) {
					tab = t;
					contentAlpha = 0.45f;
				}
				return true;
			}
			y += ModernTheme.SIDE_ITEM_H + ModernTheme.SIDE_ITEM_GAP;
		}

		// modules
		if (tab == Tab.CHARACTER) {
			float lx = px + ModernTheme.LIST_X;
			float ly = py + ModernTheme.CONTENT_TOP;
			float rowY = ly + 6;
			for (int i = 0; i < CHARACTER_MODULES.length; i++) {
				if (hit(mouseX, mouseY, lx + 5, rowY, ModernTheme.LIST_W - 10, ModernTheme.ROW_H)) {
					moduleIndex = i;
					selection = CHARACTER_MODULES[i].character();
					selectFlash = 1f;
					return true;
				}
				rowY += ModernTheme.ROW_H + ModernTheme.ROW_GAP;
			}

			float sx = px + ModernTheme.SETTINGS_X;
			float sy = py + ModernTheme.CONTENT_TOP;
			float sw = ModernTheme.SETTINGS_W;
			float sh = contentH();
			float btnH = 24f;
			float btnY = sy + sh - btnH - 8;
			float gap = 6f;
			float cancelW = (sw - 12 - gap) * 0.40f;
			float confW = (sw - 12 - gap) * 0.60f;
			float bx = sx + 6;
			if (hit(mouseX, mouseY, bx, btnY, cancelW, btnH)) {
				animateClose();
				return true;
			}
			if (hit(mouseX, mouseY, bx + cancelW + gap, btnY, confW, btnH)) {
				confirm();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void confirm() {
		selection = CHARACTER_MODULES[moduleIndex].character();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			PlayerSkin.Model model =
					selection.modelId().equals("slim") ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
			ClientCharacterSelectionManager.applyLocal(mc.player.getUUID(), selection, model);
		}
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(selection.id()));
		}
		animateClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE
				|| keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_N) {
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
		if (closing) {
			return;
		}
		closing = true;
		closeStartMillis = System.currentTimeMillis();
	}

	private void updateAnimation() {
		long now = System.currentTimeMillis();
		if (closing) {
			float t = (now - closeStartMillis) / (float) CLOSE_MS;
			openAnim = 1f - UiEase.clamp01(t);
			if (t >= 1f) {
				forceClose = true;
			}
		} else {
			openAnim = UiEase.clamp01((now - openStartMillis) / (float) OPEN_MS);
		}
	}

	private static boolean hit(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
