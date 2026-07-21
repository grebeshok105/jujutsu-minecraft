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
 * Modern vessel menu (key N) — Rich-Modern clickgui language:
 * charcoal shell, left categories, middle list, right detail.
 * Neon dashboard (key V) remains separate.
 */
public final class ModernMenuScreen extends Screen {
	private static final long OPEN_MS = 300;
	private static final long CLOSE_MS = 220;
	private static final float RISE = 14f;

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
		CHARACTER("Character", "Vessel"),
		COMBAT("Combat", "Kit"),
		VISUALS("Visuals", "Look"),
		MISC("Misc", "Keys");

		final String title;
		final String subtitle;

		Tab(String title, String subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	private record RosterEntry(
			String name, String tech, String grade, String pill,
			JujutsuCharacter character, boolean portraitSkin, ResourceLocation emoji) {}

	private static final RosterEntry[] ROSTER = {
			new RosterEntry("Nobara Kugisaki", "Straw Doll Technique", "Grade 3", "R",
					JujutsuCharacter.NOBARA, true, null),
			new RosterEntry("None", "No Technique", "Default", "—",
					JujutsuCharacter.NONE, false, EMOJI_BUST),
	};

	/** Matches CharacterPage / live keybinds. */
	private static final String[] NOBARA_ABILITIES = {
			"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance"
	};
	private static final String[] NOBARA_KEYS = {"R", "B", "Shift+R", "LMB"};
	private static final ResourceLocation[] NOBARA_ICONS = {
			EMOJI_PIN, EMOJI_BOLT, EMOJI_BOOM, EMOJI_LINK
	};
	private static final String[] VISUAL_ROWS = {"MSDF Type", "SDF Panels", "Rich Shell"};
	private static final String[] MISC_ROWS = {"Menu  N", "Neon  V", "Nail  R", "Boom  B"};
	private static final String[] VISUAL_DETAIL_T = {
			"MSDF Typography", "SDF Surfaces", "Rich Layout"
	};
	private static final String[] VISUAL_DETAIL_B = {
			"Sharp multi-channel distance field text at any size.",
			"Soft rounded panels with border + glow channels.",
			"Sidebar · list · detail shell matching the reference."
	};
	private static final String[] MISC_DETAIL_T = {
			"Modern Menu", "Neon Dashboard", "Combat Keys"
	};
	private static final String[] MISC_DETAIL_B = {
			"N — open / close this Rich-style vessel menu.",
			"V — original neon dashboard (unchanged).",
			"R / B / LMB — Nobara kit when selected."
	};

	private final SdfRenderer sdf = new SdfRenderer();

	private Tab tab = Tab.CHARACTER;
	private JujutsuCharacter selection;
	private int listIndex;

	private float openAnim;
	private boolean closing;
	private boolean disposed;
	private boolean forceClose;
	private long openStartMillis;
	private long closeStartMillis;
	private long lastFrameNanos;

	// Fitted panel size (responsive)
	private float panelW = ModernTheme.BG_W;
	private float panelH = ModernTheme.BG_H;
	private float listW = ModernTheme.LIST_W;
	private float sidebarW = ModernTheme.SIDEBAR_W;

	private final float[] sidebarHover = new float[Tab.values().length];
	private final float[] listHover = new float[ROSTER.length];
	private float selectFlash;
	private float confirmHover;
	private float cancelHover;
	private float contentAlpha = 1f;

	public ModernMenuScreen() {
		super(Component.translatable("screen.jujutsumod.modern_menu"));
		this.openStartMillis = System.currentTimeMillis();
		this.lastFrameNanos = System.nanoTime();
		this.selection = initialSelection();
		this.listIndex = indexOf(selection);
	}

	private static JujutsuCharacter initialSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			return ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
		}
		return JujutsuCharacter.NONE;
	}

	private static int indexOf(JujutsuCharacter c) {
		for (int i = 0; i < ROSTER.length; i++) {
			if (ROSTER[i].character() == c) {
				return i;
			}
		}
		return ROSTER.length > 1 ? 1 : 0; // prefer None if unknown
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		MsdfFonts.warm();
		layout();
	}

	@Override
	public void resize(Minecraft mc, int w, int h) {
		super.resize(mc, w, h);
		layout();
	}

	private void layout() {
		panelW = Math.min(ModernTheme.BG_W, Math.max(360f, width - 24f));
		panelH = Math.min(ModernTheme.BG_H, Math.max(240f, height - 32f));
		float scale = panelW / ModernTheme.BG_W;
		sidebarW = ModernTheme.SIDEBAR_W * scale;
		listW = ModernTheme.LIST_W * scale;
	}

	/** Same geometry for draw, hover, and clicks. */
	private float panelX() {
		return (width - panelW) * 0.5f;
	}

	private float panelY() {
		float base = (height - panelH) * 0.5f;
		float anim = UiEase.outCubic(openAnim);
		return base + (1f - anim) * RISE;
	}

	private float contentW() {
		return panelW - sidebarW - 1f;
	}

	private float contentH() {
		return panelH - ModernTheme.HEADER_H - 1f;
	}

	private float detailW() {
		return contentW() - ModernTheme.PAD * 2f - listW - ModernTheme.GAP;
	}

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
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = tickDelta();
		updateAnimation();
		float anim = UiEase.outCubic(openAnim);
		float px = panelX();
		float py = panelY();

		contentAlpha = UiEase.approach(contentAlpha, 1f, 0.28f, dt * 20f);
		selectFlash = UiEase.approach(selectFlash, 0f, 0.12f, dt * 20f);
		updateHovers(mouseX, mouseY, px, py, dt);

		float ca = anim * contentAlpha;

		// SDF first (immediate). Scrim is an SDF quad so it cannot cover the shell.
		sdf.setGlobalAlpha(anim);
		sdf.begin();

		// World dim (under UI because same batch order: first shape, then shell on top)
		sdf.add(SdfShape.builder()
				.rect(0, 0, width, height)
				.radius(0).border(0, 0).glow(0, 0)
				.fill(0xA0000000, 0xA0000000)
				.build());

		// Outer shell
		sdf.add(SdfShape.builder()
				.rect(px, py, panelW, panelH)
				.radius(ModernTheme.RADIUS)
				.border(1.1f, ModernTheme.PANEL_EDGE)
				.glow(10f, 0x40000000)
				.highlight(0.10f)
				.fill(ModernTheme.PANEL, 0xF0121214)
				.build());

		// Sidebar with matching outer corners
		sdf.add(SdfShape.builder()
				.rect(px, py, sidebarW, panelH)
				.radius(ModernTheme.RADIUS)
				.border(0, 0)
				.fill(ModernTheme.SIDEBAR, ModernTheme.SIDEBAR)
				.build());
		// Cover right side of sidebar so only left corners stay round
		sdf.add(SdfShape.builder()
				.rect(px + sidebarW - 12, py, 12, panelH)
				.radius(0).border(0, 0)
				.fill(ModernTheme.SIDEBAR, ModernTheme.SIDEBAR)
				.build());
		sdf.add(SdfShape.builder()
				.rect(px + sidebarW, py + 10, 1f, panelH - 20)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());

		// Header strip
		sdf.add(SdfShape.builder()
				.rect(px + sidebarW, py, contentW(), ModernTheme.HEADER_H)
				.radius(0).border(0, 0)
				.fill(0xF0151518, 0xF0151518)
				.build());
		sdf.add(SdfShape.builder()
				.rect(px + sidebarW + 10, py + ModernTheme.HEADER_H - 1, contentW() - 20, 1f)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());

		drawAvatarSurface(px, py);
		drawSidebarSurfaces(px, py);
		drawListSurfaces(px, py);
		drawDetailSurfaces(px, py);
		sdf.flush();

		// Text (batched MSDF)
		drawAvatarText(px, py, ca);
		drawSidebarText(px, py, ca);
		drawHeaderText(px, py, ca);
		drawListText(px, py, ca);
		drawDetailText(px, py, ca);
		MsdfFonts.endFrame();

		// Icons / portraits last via GuiGraphics (above panels)
		drawPortraits(g, px, py, ca);
		drawIcons(g, px, py, ca);

		MsdfFonts.drawCentered(
				"N close  ·  V neon dashboard",
				px + panelW * 0.5f,
				py + panelH + 8,
				7f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, anim * 0.85f));
		MsdfFonts.endFrame();
	}

	private void drawAvatarSurface(float px, float py) {
		float ax = px + 10;
		float ay = py + 10;
		float aw = sidebarW - 20;
		sdf.add(SdfShape.builder()
				.rect(ax, ay, aw, 34)
				.radius(10f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.06f))
				.highlight(0.12f)
				.fill(ModernTheme.CARD, ModernTheme.CARD)
				.build());
		sdf.add(SdfShape.builder()
				.rect(ax + 8, ay + 13, 8, 8)
				.radius(4f)
				.border(0, 0)
				.glow(4f, ModernTheme.withAlpha(ModernTheme.ONLINE, 0.5f))
				.fill(ModernTheme.ONLINE, ModernTheme.ONLINE)
				.build());
	}

	private void drawSidebarSurfaces(float px, float py) {
		float y = py + 70;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			float h = 26f;
			float hover = sidebarHover[i];
			boolean sel = tabs[i] == tab;
			float x = px + 8;
			float w = sidebarW - 16;
			if (sel || hover > 0.02f) {
				int fill = sel ? ModernTheme.CARD_SELECTED
						: ModernTheme.lerpColor(ModernTheme.CARD, ModernTheme.CARD_HOVER, hover);
				sdf.add(SdfShape.builder()
						.rect(x, y, w, h)
						.radius(8f)
						.border(sel ? 1f : 0f, ModernTheme.withAlpha(0xFFFFFFFF, sel ? 0.08f : 0f))
						.highlight(sel ? 0.14f : 0.08f * hover)
						.fill(fill, ModernTheme.CARD)
						.build());
			}
			if (sel) {
				sdf.add(SdfShape.builder()
						.rect(x + 2, y + 6, 2.5f, h - 12)
						.radius(1.2f)
						.border(0, 0)
						.glow(4f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.45f))
						.fill(ModernTheme.ACCENT, ModernTheme.ACCENT)
						.build());
			}
			y += h + 4;
		}
	}

	private void drawListSurfaces(float px, float py) {
		float lx = px + sidebarW + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float lh = contentH() - ModernTheme.PAD * 2f;

		sdf.add(SdfShape.builder()
				.rect(lx, ly, listW, lh)
				.radius(12f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f))
				.fill(ModernTheme.COLUMN, ModernTheme.COLUMN)
				.build());

		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				boolean sel = i == listIndex;
				float hover = listHover[i];
				float rowH = 34f;
				int fill = sel ? ModernTheme.CARD_SELECTED
						: ModernTheme.lerpColor(ModernTheme.CARD, ModernTheme.CARD_HOVER, hover);
				sdf.add(SdfShape.builder()
						.rect(lx + 6, rowY, listW - 12, rowH)
						.radius(9f)
						.border(sel ? 1.2f : 1f, ModernTheme.withAlpha(
								sel ? ModernTheme.ACCENT : 0xFFFFFFFF,
								sel ? 0.40f + 0.25f * selectFlash : 0.05f + 0.08f * hover))
						.glow(sel ? 7f : 0f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.20f))
						.highlight(0.10f + (sel ? 0.08f : 0.06f * hover))
						.fill(fill, fill)
						.build());
				// bind pill
				sdf.add(SdfShape.builder()
						.rect(lx + listW - 34, rowY + 9, 18, 16)
						.radius(5f)
						.border(0, 0)
						.fill(0xFF2A2A30, 0xFF2A2A30)
						.build());
				rowY += rowH + 6;
			}
		} else if (tab == Tab.COMBAT) {
			float rowY = ly + 8;
			int n = selection == JujutsuCharacter.NOBARA ? NOBARA_ABILITIES.length : 1;
			for (int i = 0; i < n; i++) {
				float rowH = 32f;
				sdf.add(SdfShape.builder()
						.rect(lx + 6, rowY, listW - 12, rowH)
						.radius(9f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.05f))
						.highlight(0.08f)
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += rowH + 5;
			}
		} else {
			String[] rows = tab == Tab.VISUALS ? VISUAL_ROWS : MISC_ROWS;
			float rowY = ly + 8;
			for (int i = 0; i < rows.length; i++) {
				sdf.add(SdfShape.builder()
						.rect(lx + 6, rowY, listW - 12, 30f)
						.radius(8f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f))
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += 35;
			}
		}
	}

	private void drawDetailSurfaces(float px, float py) {
		float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float dw = detailW();
		float dh = contentH() - ModernTheme.PAD * 2f;

		sdf.add(SdfShape.builder()
				.rect(dx, dy, dw, dh)
				.radius(12f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.05f))
				.highlight(0.08f)
				.fill(ModernTheme.COLUMN, 0xF0121214)
				.build());

		sdf.add(SdfShape.builder()
				.rect(dx + 12, dy + 28, Math.min(48, dw - 24), 2f)
				.radius(1f)
				.border(0, 0)
				.glow(4f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.35f))
				.fill(ModernTheme.ACCENT, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.3f))
				.build());

		if (tab == Tab.CHARACTER) {
			float head = 52f;
			sdf.add(SdfShape.builder()
					.rect(dx + 14, dy + 42, head, head)
					.radius(12f)
					.border(1.2f, ModernTheme.withAlpha(ModernTheme.ACCENT,
							ROSTER[listIndex].character() == JujutsuCharacter.NOBARA ? 0.4f : 0.15f))
					.glow(ROSTER[listIndex].character() == JujutsuCharacter.NOBARA ? 8f : 0f,
							ModernTheme.withAlpha(ModernTheme.ACCENT, 0.25f))
					.fill(0xFF0E0E10, 0xFF0E0E10)
					.build());

			if (ROSTER[listIndex].character() == JujutsuCharacter.NOBARA) {
				float chipY = dy + 108;
				float chipX = dx + 12;
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					float cw = (dw - 24 - 6) * 0.5f;
					float ch = 28f;
					float cx = chipX + (i % 2) * (cw + 6);
					float cy = chipY + (i / 2) * (ch + 6);
					// Info chips only (not clickable) — quieter border
					sdf.add(SdfShape.builder()
							.rect(cx, cy, cw, ch)
							.radius(8f)
							.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.06f))
							.fill(0xF01C1C20, 0xF0161619)
							.build());
				}
			}

			float btnH = 28f;
			float btnY = dy + dh - btnH - 12;
			float cancelW = (dw - 12 - 10) * 0.38f;
			float confW = (dw - 12 - 10) * 0.62f;
			float bx = dx + 12;

			sdf.add(SdfShape.builder()
					.rect(bx, btnY, cancelW, btnH)
					.radius(8f)
					.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.08f + 0.1f * cancelHover))
					.highlight(0.08f + 0.08f * cancelHover)
					.fill(ModernTheme.lerpColor(ModernTheme.CARD, ModernTheme.CARD_HOVER, cancelHover),
							ModernTheme.CARD)
					.build());

			int confFill = ModernTheme.lerpColor(0xFF2A2118, 0xFF3A2A16, confirmHover);
			sdf.add(SdfShape.builder()
					.rect(bx + cancelW + 10, btnY, confW, btnH)
					.radius(8f)
					.border(1.2f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.45f + 0.35f * confirmHover))
					.glow(8f + 4f * confirmHover, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.25f + 0.2f * confirmHover))
					.highlight(0.14f + 0.1f * confirmHover)
					.fill(confFill, 0xFF1A140E)
					.build());
		} else {
			float rowY = dy + 42;
			int rows = tab == Tab.COMBAT
					? (selection == JujutsuCharacter.NOBARA ? 4 : 1)
					: 3;
			for (int i = 0; i < rows; i++) {
				sdf.add(SdfShape.builder()
						.rect(dx + 10, rowY, dw - 20, tab == Tab.COMBAT ? 36f : 40f)
						.radius(9f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f))
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += tab == Tab.COMBAT ? 42 : 48;
			}
		}
	}

	private void drawAvatarText(float px, float py, float a) {
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player != null ? mc.player.getName().getString() : "Player";
		if (name.length() > 10) {
			name = name.substring(0, 9) + "…";
		}
		MsdfFonts.draw(name, px + 28, py + 16, 8.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
		String sub = selection == JujutsuCharacter.NOBARA ? "Nobara" : "None";
		MsdfFonts.draw(sub, px + 28, py + 28, 7f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
	}

	private void drawSidebarText(float px, float py, float a) {
		MsdfFonts.draw("MAIN", px + 12, py + 54, 7.5f, ModernTheme.withAlpha(0xFF8A8A94, a));
		float y = py + 70;
		for (Tab t : Tab.values()) {
			boolean sel = t == tab;
			int col = sel ? ModernTheme.TEXT : ModernTheme.TEXT_DIM;
			MsdfFonts.draw(t.title, px + 28, y + 8, 9f, ModernTheme.withAlpha(col, a));
			y += 30;
		}
	}

	private void drawHeaderText(float px, float py, float a) {
		float hx = px + sidebarW + 14;
		MsdfFonts.draw(tab.title, hx, py + 8, 12f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
		MsdfFonts.draw(tab.subtitle, hx + MsdfFonts.width(tab.title, 12f) + 8, py + 12, 8f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
	}

	private void drawListText(float px, float py, float a) {
		float lx = px + sidebarW + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;

		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				RosterEntry e = ROSTER[i];
				boolean sel = i == listIndex;
				MsdfFonts.draw(shortName(e.name()), lx + 14, rowY + 8, 9f,
						ModernTheme.withAlpha(sel ? ModernTheme.TEXT : ModernTheme.TEXT_DIM, a));
				MsdfFonts.draw(e.grade(), lx + 14, rowY + 19, 7f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				MsdfFonts.drawCentered(e.pill(), lx + listW - 25, rowY + 12, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
				rowY += 40;
			}
		} else if (tab == Tab.COMBAT) {
			float rowY = ly + 8;
			if (selection == JujutsuCharacter.NOBARA) {
				for (String ability : NOBARA_ABILITIES) {
					MsdfFonts.draw(ability, lx + 12, rowY + 11, 8f,
							ModernTheme.withAlpha(ModernTheme.TEXT, a));
					rowY += 37;
				}
			} else {
				MsdfFonts.draw("No active kit", lx + 12, rowY + 11, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}
		} else {
			String[] rows = tab == Tab.VISUALS ? VISUAL_ROWS : MISC_ROWS;
			float rowY = ly + 8;
			for (String row : rows) {
				MsdfFonts.draw(row, lx + 12, rowY + 10, 8.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				rowY += 35;
			}
		}
	}

	private void drawDetailText(float px, float py, float a) {
		float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float dw = detailW();
		float dh = contentH() - ModernTheme.PAD * 2f;

		if (tab == Tab.CHARACTER) {
			RosterEntry e = ROSTER[listIndex];
			MsdfFonts.draw(e.name(), dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			MsdfFonts.draw(e.tech(), dx + 74, dy + 48, 9f, ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
			MsdfFonts.draw(e.grade(), dx + 74, dy + 62, 8f, ModernTheme.withAlpha(ModernTheme.ACCENT, a));
			MsdfFonts.draw(e.character() == JujutsuCharacter.NOBARA ? "R · B · Shift+R · LMB" : "No technique binds",
					dx + 74, dy + 76, 7.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));

			if (e.character() == JujutsuCharacter.NOBARA) {
				float chipY = dy + 108;
				float chipX = dx + 12;
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					float cw = (dw - 24 - 6) * 0.5f;
					float ch = 28f;
					float cx = chipX + (i % 2) * (cw + 6);
					float cy = chipY + (i / 2) * (ch + 6);
					MsdfFonts.draw(shortAbility(NOBARA_ABILITIES[i]), cx + 22, cy + 9, 7.5f,
							ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
					MsdfFonts.draw(NOBARA_KEYS[i],
							cx + cw - 8 - MsdfFonts.width(NOBARA_KEYS[i], 7f), cy + 9, 7f,
							ModernTheme.withAlpha(ModernTheme.ACCENT, a));
				}
			} else {
				MsdfFonts.draw("Clear vessel — vanilla play", dx + 14, dy + 120, 8.5f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}

			float btnH = 28f;
			float btnY = dy + dh - btnH - 12;
			float cancelW = (dw - 12 - 10) * 0.38f;
			float confW = (dw - 12 - 10) * 0.62f;
			float bx = dx + 12;
			MsdfFonts.drawCentered("Cancel", bx + cancelW * 0.5f, btnY + 9, 9f,
					ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
			MsdfFonts.drawCentered("Confirm", bx + cancelW + 10 + confW * 0.5f, btnY + 9, 9.5f,
					ModernTheme.withAlpha(ModernTheme.TEXT, a));
		} else if (tab == Tab.COMBAT) {
			MsdfFonts.draw("Loadout", dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			float rowY = dy + 42;
			if (selection == JujutsuCharacter.NOBARA) {
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					MsdfFonts.draw(NOBARA_ABILITIES[i], dx + 20, rowY + 12, 9f,
							ModernTheme.withAlpha(ModernTheme.TEXT, a));
					MsdfFonts.draw(NOBARA_KEYS[i],
							dx + dw - 20 - MsdfFonts.width(NOBARA_KEYS[i], 8f), rowY + 13, 8f,
							ModernTheme.withAlpha(ModernTheme.ACCENT, a));
					rowY += 42;
				}
			} else {
				MsdfFonts.draw("Select a vessel first", dx + 20, rowY + 12, 9f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}
		} else if (tab == Tab.VISUALS) {
			MsdfFonts.draw("Presentation", dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			float rowY = dy + 48;
			for (int i = 0; i < VISUAL_DETAIL_T.length; i++) {
				MsdfFonts.draw(VISUAL_DETAIL_T[i], dx + 20, rowY + 6, 9f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(VISUAL_DETAIL_B[i], dx + 20, rowY + 20, 7.2f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 48;
			}
		} else {
			MsdfFonts.draw("Controls", dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			float rowY = dy + 48;
			for (int i = 0; i < MISC_DETAIL_T.length; i++) {
				MsdfFonts.draw(MISC_DETAIL_T[i], dx + 20, rowY + 6, 9f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(MISC_DETAIL_B[i], dx + 20, rowY + 20, 7.2f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 48;
			}
		}
	}

	private void drawPortraits(GuiGraphics g, float px, float py, float a) {
		if (a < 0.05f || tab != Tab.CHARACTER) {
			return;
		}
		float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		int head = 52;
		int hx = Math.round(dx + 14);
		int hy = Math.round(dy + 42);
		// Clip square skin blit into rounded frame
		g.enableScissor(hx + 1, hy + 1, hx + head - 1, hy + head - 1);
		RosterEntry e = ROSTER[listIndex];
		if (e.portraitSkin()) {
			g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 8f, 8f, head, head, 8, 8, 64, 64);
			g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 40f, 8f, head, head, 8, 8, 64, 64);
		} else if (e.emoji() != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, e.emoji(), hx + 4, hy + 4, 0f, 0f, head - 8, head - 8, 96, 96, 96, 96);
		}
		g.disableScissor();
	}

	private void drawIcons(GuiGraphics g, float px, float py, float a) {
		if (a < 0.05f) {
			return;
		}
		ResourceLocation[] icons = {EMOJI_BUST, EMOJI_SWORDS, EMOJI_SPARKLES, EMOJI_GEAR};
		float y = py + 74;
		for (ResourceLocation icon : icons) {
			int s = 12;
			g.blit(RenderPipelines.GUI_TEXTURED, icon, Math.round(px + 12), Math.round(y), 0f, 0f, s, s, 96, 96, 96, 96);
			y += 30;
		}
		if (tab == Tab.CHARACTER && ROSTER[listIndex].character() == JujutsuCharacter.NOBARA) {
			float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float chipY = dy + 108;
			float chipX = dx + 12;
			for (int i = 0; i < NOBARA_ICONS.length; i++) {
				float cw = (dw - 24 - 6) * 0.5f;
				float ch = 28f;
				float cx = chipX + (i % 2) * (cw + 6);
				float cy = chipY + (i / 2) * (ch + 6);
				g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_ICONS[i],
						Math.round(cx + 6), Math.round(cy + 8), 0f, 0f, 12, 12, 96, 96, 96, 96);
			}
		}
	}

	private static String shortName(String name) {
		int sp = name.indexOf(' ');
		return sp > 0 ? name.substring(0, sp) : name;
	}

	private static String shortAbility(String name) {
		return name.length() > 12 ? name.substring(0, 11) + "…" : name;
	}

	private float tickDelta() {
		long now = System.nanoTime();
		float dt = (now - lastFrameNanos) / 1_000_000_000f;
		lastFrameNanos = now;
		return Math.min(dt, 0.05f);
	}

	private void updateHovers(int mx, int my, float px, float py, float dt) {
		float speed = 0.35f;
		float fdt = dt * 60f;

		float y = py + 70;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			float h = 26f;
			boolean hit = hit(mx, my, px + 8, y, sidebarW - 16, h);
			sidebarHover[i] = UiEase.approach(sidebarHover[i], hit ? 1f : 0f, speed, fdt);
			y += h + 4;
		}

		float lx = px + sidebarW + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				boolean hit = hit(mx, my, lx + 6, rowY, listW - 12, 34f);
				listHover[i] = UiEase.approach(listHover[i], hit ? 1f : 0f, speed, fdt);
				rowY += 40;
			}
		}

		if (tab == Tab.CHARACTER) {
			float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float dh = contentH() - ModernTheme.PAD * 2f;
			float btnH = 28f;
			float btnY = dy + dh - btnH - 12;
			float cancelW = (dw - 12 - 10) * 0.38f;
			float confW = (dw - 12 - 10) * 0.62f;
			float bx = dx + 12;
			cancelHover = UiEase.approach(cancelHover, hit(mx, my, bx, btnY, cancelW, btnH) ? 1f : 0f, speed, fdt);
			confirmHover = UiEase.approach(confirmHover,
					hit(mx, my, bx + cancelW + 10, btnY, confW, btnH) ? 1f : 0f, speed, fdt);
		} else {
			cancelHover = UiEase.approach(cancelHover, 0f, speed, fdt);
			confirmHover = UiEase.approach(confirmHover, 0f, speed, fdt);
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// scrim drawn in SDF batch
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || openAnim < 0.85f || closing) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		float px = panelX();
		float py = panelY();

		float y = py + 70;
		for (Tab t : Tab.values()) {
			if (hit(mouseX, mouseY, px + 8, y, sidebarW - 16, 26f)) {
				if (tab != t) {
					tab = t;
					contentAlpha = 0.4f;
				}
				return true;
			}
			y += 30;
		}

		float lx = px + sidebarW + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;

		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				if (hit(mouseX, mouseY, lx + 6, rowY, listW - 12, 34f)) {
					listIndex = i;
					selection = ROSTER[i].character();
					selectFlash = 1f;
					return true;
				}
				rowY += 40;
			}

			float dx = px + sidebarW + ModernTheme.PAD + listW + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float dh = contentH() - ModernTheme.PAD * 2f;
			float btnH = 28f;
			float btnY = dy + dh - btnH - 12;
			float cancelW = (dw - 12 - 10) * 0.38f;
			float confW = (dw - 12 - 10) * 0.62f;
			float bx = dx + 12;
			if (hit(mouseX, mouseY, bx, btnY, cancelW, btnH)) {
				animateClose();
				return true;
			}
			if (hit(mouseX, mouseY, bx + cancelW + 10, btnY, confW, btnH)) {
				confirm();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void confirm() {
		selection = ROSTER[listIndex].character();
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
		if (keyCode >= com.mojang.blaze3d.platform.InputConstants.KEY_1
				&& keyCode <= com.mojang.blaze3d.platform.InputConstants.KEY_4) {
			int idx = keyCode - com.mojang.blaze3d.platform.InputConstants.KEY_1;
			Tab[] tabs = Tab.values();
			if (idx < tabs.length && tab != tabs[idx]) {
				tab = tabs[idx];
				contentAlpha = 0.4f;
			}
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
			float t = (now - openStartMillis) / (float) OPEN_MS;
			openAnim = UiEase.clamp01(t);
		}
	}

	private static boolean hit(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
