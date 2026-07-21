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
 * Modern vessel menu (key N) — Rich-Modern clickgui visual language:
 * dark charcoal shell, left categories, middle list, right detail panel.
 * Does not replace the neon dashboard (key V).
 */
public final class ModernMenuScreen extends Screen {
	private static final long OPEN_MS = 300;
	private static final long CLOSE_MS = 220;

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
			String name, String tech, String grade, String bindHint,
			JujutsuCharacter character, boolean portraitSkin, ResourceLocation emoji) {}

	private static final RosterEntry[] ROSTER = {
			new RosterEntry("Nobara Kugisaki", "Straw Doll Technique", "Grade 3", "R / B / Shift",
					JujutsuCharacter.NOBARA, true, null),
			new RosterEntry("None", "No Technique", "Default", "—",
					JujutsuCharacter.NONE, false, EMOJI_BUST),
	};

	private static final String[] NOBARA_ABILITIES = {
			"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance"
	};
	private static final String[] NOBARA_KEYS = {"R", "B", "Shift+B", "LMB"};
	private static final ResourceLocation[] NOBARA_ICONS = {
			EMOJI_PIN, EMOJI_BOLT, EMOJI_BOOM, EMOJI_LINK
	};

	private final SdfRenderer sdf = new SdfRenderer();

	private Tab tab = Tab.CHARACTER;
	private JujutsuCharacter selection;
	private int listIndex;

	private float openAnim;
	private boolean closing;
	private boolean disposed;
	private long openStartMillis;
	private long closeStartMillis;
	private long lastFrameNanos;

	private float panelX;
	private float panelY;

	// Smooth hover / selection
	private final float[] sidebarHover = new float[Tab.values().length];
	private final float[] listHover = new float[ROSTER.length];
	private float selectFlash;
	private float confirmHover;
	private float cancelHover;
	private float tabFade = 1f;
	private Tab fadeFrom = Tab.CHARACTER;
	private final float[] abilityHover = new float[NOBARA_ABILITIES.length];
	private final boolean[] abilityOn = {true, true, true, true}; // cosmetic kit toggles in combat tab

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
		return 0;
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
		panelX = (width - ModernTheme.BG_W) * 0.5f;
		panelY = (height - ModernTheme.BG_H) * 0.5f;
	}

	// ─── geometry helpers ─────────────────────────────────────────────

	private float contentX() {
		return panelX + ModernTheme.SIDEBAR_W + 1f;
	}

	private float contentY() {
		return panelY + ModernTheme.HEADER_H;
	}

	private float contentW() {
		return ModernTheme.BG_W - ModernTheme.SIDEBAR_W - 1f;
	}

	private float contentH() {
		return ModernTheme.BG_H - ModernTheme.HEADER_H - 1f;
	}

	private float listX() {
		return contentX() + ModernTheme.PAD;
	}

	private float listY() {
		return contentY() + ModernTheme.PAD;
	}

	private float listH() {
		return contentH() - ModernTheme.PAD * 2f;
	}

	private float detailX() {
		return listX() + ModernTheme.LIST_W + ModernTheme.GAP;
	}

	private float detailW() {
		return contentW() - ModernTheme.PAD * 2f - ModernTheme.LIST_W - ModernTheme.GAP;
	}

	// ─── render ───────────────────────────────────────────────────────

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = tickDelta();
		updateAnimation();
		float anim = UiEase.outCubic(openAnim);
		float rise = (1f - anim) * 14f;
		float px = panelX;
		float py = panelY + rise;

		// store for hit tests this frame
		this.panelX = px;
		// keep base layout x; only y animates
		float baseX = (width - ModernTheme.BG_W) * 0.5f;
		float baseY = (height - ModernTheme.BG_H) * 0.5f;
		this.panelX = baseX;
		this.panelY = baseY;
		px = baseX;
		py = baseY + rise;

		updateHovers(mouseX, mouseY, px, py, dt);
		tabFade = UiEase.approach(tabFade, 1f, 0.28f, dt * 20f);
		selectFlash = UiEase.approach(selectFlash, 0f, 0.12f, dt * 20f);

		// Scrim
		int scrimA = Math.round(0xA0 * anim);
		g.fill(0, 0, width, height, (scrimA << 24));

		sdf.setGlobalAlpha(anim);
		sdf.begin();

		// Outer shell
		sdf.add(SdfShape.builder()
				.rect(px, py, ModernTheme.BG_W, ModernTheme.BG_H)
				.radius(ModernTheme.RADIUS)
				.border(1.1f, ModernTheme.withAlpha(ModernTheme.PANEL_EDGE, anim))
				.glow(10f, ModernTheme.withAlpha(0xFF000000, 0.35f * anim))
				.highlight(0.10f)
				.fill(ModernTheme.PANEL, ModernTheme.withAlpha(0xFF121214, 0.98f))
				.build());

		// Sidebar plate
		sdf.add(SdfShape.builder()
				.rect(px, py, ModernTheme.SIDEBAR_W, ModernTheme.BG_H)
				.radius(0f)
				.border(0, 0)
				.fill(ModernTheme.SIDEBAR, ModernTheme.SIDEBAR)
				.build());
		// soft right divider
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.SIDEBAR_W, py + 10, 1f, ModernTheme.BG_H - 20)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());

		// Header bar over content
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.SIDEBAR_W, py, contentW(), ModernTheme.HEADER_H)
				.radius(0).border(0, 0)
				.fill(0xF0151518, 0xF0151518)
				.build());
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.SIDEBAR_W + 10, py + ModernTheme.HEADER_H - 1, contentW() - 20, 1f)
				.radius(0).border(0, 0)
				.fill(ModernTheme.DIVIDER, ModernTheme.DIVIDER)
				.build());
		// Search pill (visual, matches Rich header)
		float searchW = 88f;
		sdf.add(SdfShape.builder()
				.rect(px + ModernTheme.BG_W - 14 - searchW, py + 8, searchW, 20f)
				.radius(8f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.06f * anim))
				.fill(ModernTheme.CARD, ModernTheme.CARD)
				.build());

		// Avatar chip
		drawAvatarSurface(px, py, anim);

		// Sidebar items
		drawSidebarSurfaces(px, py, anim);

		// Middle list + detail for current tab
		drawListSurfaces(px, py, anim);
		drawDetailSurfaces(px, py, anim);

		sdf.flush();

		// ── text / icons ──
		float textAlpha = anim * tabFade;
		drawAvatarText(px, py, textAlpha);
		drawSidebarText(px, py, textAlpha);
		drawHeaderText(px, py, textAlpha);
		drawListText(px, py, textAlpha);
		drawDetailText(px, py, textAlpha);
		drawPortraits(g, px, py, anim);
		drawIcons(g, px, py, anim);

		// Footer hint
		MsdfFonts.drawCentered(
				"N close  ·  V neon dashboard",
				px + ModernTheme.BG_W * 0.5f,
				py + ModernTheme.BG_H + 8,
				7f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, anim * 0.85f));
	}

	private void drawAvatarSurface(float px, float py, float anim) {
		float ax = px + 10;
		float ay = py + 10;
		float aw = ModernTheme.SIDEBAR_W - 20;
		float ah = 34;
		sdf.add(SdfShape.builder()
				.rect(ax, ay, aw, ah)
				.radius(10f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.06f * anim))
				.highlight(0.12f)
				.fill(ModernTheme.CARD, ModernTheme.CARD)
				.build());
		// online dot
		sdf.add(SdfShape.builder()
				.rect(ax + 8, ay + 13, 8, 8)
				.radius(4f)
				.border(0, 0)
				.glow(4f, ModernTheme.withAlpha(ModernTheme.ONLINE, 0.5f * anim))
				.fill(ModernTheme.ONLINE, ModernTheme.ONLINE)
				.build());
	}

	private void drawSidebarSurfaces(float px, float py, float anim) {
		float y = py + 56;
		// section label space
		y += 14;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			float h = 26f;
			float hover = sidebarHover[i];
			boolean sel = tabs[i] == tab;
			float x = px + 8;
			float w = ModernTheme.SIDEBAR_W - 16;

			int fill = sel
					? ModernTheme.lerpColor(ModernTheme.CARD, ModernTheme.CARD_SELECTED, 1f)
					: ModernTheme.lerpColor(0x00000000, ModernTheme.CARD_HOVER, hover);
			if (sel || hover > 0.02f) {
				sdf.add(SdfShape.builder()
						.rect(x, y, w, h)
						.radius(8f)
						.border(sel ? 1f : 0f, ModernTheme.withAlpha(0xFFFFFFFF, sel ? 0.08f : 0f))
						.highlight(sel ? 0.14f : 0.08f * hover)
						.fill(ModernTheme.withAlpha(fill == 0 ? ModernTheme.CARD : fill, sel ? 1f : 0.55f + 0.45f * hover),
								ModernTheme.withAlpha(ModernTheme.CARD, sel ? 1f : 0.4f * hover))
						.build());
			}
			if (sel) {
				sdf.add(SdfShape.builder()
						.rect(x + 2, y + 6, 2.5f, h - 12)
						.radius(1.2f)
						.border(0, 0)
						.glow(4f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.45f * anim))
						.fill(ModernTheme.ACCENT, ModernTheme.ACCENT)
						.build());
			}
			y += h + 4;
			if (i == 0) {
				// after Character, tiny section gap for "Другие" feel
			}
		}
	}

	private void drawListSurfaces(float px, float py, float anim) {
		float lx = listX() - panelX + px;
		float ly = listY() - panelY + py;
		// recompute against animated py
		lx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD;
		ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float lw = ModernTheme.LIST_W;
		float lh = listH();

		// list column bg
		sdf.add(SdfShape.builder()
				.rect(lx, ly, lw, lh)
				.radius(12f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f * anim))
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
						.rect(lx + 6, rowY, lw - 12, rowH)
						.radius(9f)
						.border(sel ? 1.1f : 1f, ModernTheme.withAlpha(
								sel ? ModernTheme.ACCENT : 0xFFFFFFFF,
								sel ? 0.35f + 0.25f * selectFlash : 0.05f + 0.08f * hover))
						.glow(sel ? 6f : 0f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.18f * anim))
						.highlight(0.10f + (sel ? 0.08f : 0.06f * hover))
						.fill(fill, ModernTheme.withAlpha(fill, 0.95f))
						.build());
				// bind pill
				sdf.add(SdfShape.builder()
						.rect(lx + lw - 34, rowY + 9, 18, 16)
						.radius(5f)
						.border(0, 0)
						.fill(0xFF2A2A30, 0xFF2A2A30)
						.build());
				rowY += rowH + 6;
			}
		} else if (tab == Tab.COMBAT) {
			float rowY = ly + 8;
			String[] rows = selection == JujutsuCharacter.NOBARA
					? NOBARA_ABILITIES
					: new String[] {"No active kit"};
			for (int i = 0; i < rows.length; i++) {
				float rowH = 32f;
				float hover = i < abilityHover.length ? abilityHover[i] : 0f;
				boolean on = selection == JujutsuCharacter.NOBARA && abilityOn[i];
				sdf.add(SdfShape.builder()
						.rect(lx + 6, rowY, lw - 12, rowH)
						.radius(9f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.05f + 0.08f * hover))
						.highlight(0.08f + 0.06f * hover)
						.fill(ModernTheme.lerpColor(ModernTheme.CARD, ModernTheme.CARD_HOVER, hover),
								ModernTheme.CARD)
						.build());
				// mini toggle track
				if (selection == JujutsuCharacter.NOBARA) {
					float tw = 22f, th = 12f;
					float tx = lx + lw - 12 - tw - 6;
					float ty = rowY + (rowH - th) * 0.5f;
					sdf.add(SdfShape.builder()
							.rect(tx, ty, tw, th)
							.radius(th * 0.5f)
							.border(0, 0)
							.fill(on ? ModernTheme.TOGGLE_ON : ModernTheme.TOGGLE_OFF,
									on ? ModernTheme.TOGGLE_ON : ModernTheme.TOGGLE_OFF)
							.build());
					float knob = 10f;
					float kx = on ? tx + tw - knob - 1 : tx + 1;
					sdf.add(SdfShape.builder()
							.rect(kx, ty + 1, knob, knob)
							.radius(knob * 0.5f)
							.border(0, 0)
							.fill(ModernTheme.TOGGLE_KNOB, ModernTheme.TOGGLE_KNOB)
							.build());
				}
				rowY += rowH + 5;
			}
		} else {
			// single info cards for visuals/misc list
			float rowY = ly + 8;
			String[] rows = tab == Tab.VISUALS
					? new String[] {"MSDF Type", "SDF Panels", "Rich Shell"}
					: new String[] {"Open Menu N", "Neon Dash V", "Piercing R", "Hairpin B"};
			for (int i = 0; i < rows.length; i++) {
				float rowH = 30f;
				sdf.add(SdfShape.builder()
						.rect(lx + 6, rowY, lw - 12, rowH)
						.radius(8f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.05f))
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += rowH + 5;
			}
		}
	}

	private void drawDetailSurfaces(float px, float py, float anim) {
		float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float dw = detailW();
		float dh = listH();

		sdf.add(SdfShape.builder()
				.rect(dx, dy, dw, dh)
				.radius(12f)
				.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.05f * anim))
				.highlight(0.08f)
				.fill(ModernTheme.COLUMN, ModernTheme.withAlpha(0xFF121214, 0.98f))
				.build());

		// title underline accent
		sdf.add(SdfShape.builder()
				.rect(dx + 12, dy + 28, Math.min(48, dw - 24), 2f)
				.radius(1f)
				.border(0, 0)
				.glow(4f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.35f * anim))
				.fill(ModernTheme.ACCENT, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.3f))
				.build());

		if (tab == Tab.CHARACTER) {
			// portrait frame
			float head = 52f;
			float hx = dx + 14;
			float hy = dy + 42;
			sdf.add(SdfShape.builder()
					.rect(hx, hy, head, head)
					.radius(12f)
					.border(1.2f, ModernTheme.withAlpha(ModernTheme.ACCENT, selection == JujutsuCharacter.NOBARA ? 0.4f : 0.15f))
					.glow(selection == JujutsuCharacter.NOBARA ? 8f : 0f,
							ModernTheme.withAlpha(ModernTheme.ACCENT, 0.25f * anim))
					.fill(0xFF0E0E10, 0xFF0E0E10)
					.build());

			// ability chips (follow list preview, not only confirmed selection)
			if (ROSTER[listIndex].character() == JujutsuCharacter.NOBARA) {
				float chipY = dy + 108;
				float chipX = dx + 12;
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					float cw = (dw - 24 - 6) * 0.5f;
					float ch = 28f;
					float cx = chipX + (i % 2) * (cw + 6);
					float cy = chipY + (i / 2) * (ch + 6);
					sdf.add(SdfShape.builder()
							.rect(cx, cy, cw, ch)
							.radius(8f)
							.border(1f, ModernTheme.withAlpha(ModernTheme.ACCENT, 0.18f))
							.fill(0xF01C1C20, 0xF0161619)
							.build());
				}
			}

			// action buttons
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
		} else if (tab == Tab.COMBAT) {
			// settings-style rows in detail
			float rowY = dy + 42;
			String[] labels = selection == JujutsuCharacter.NOBARA
					? new String[] {"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance / Hammer"}
					: new String[] {"Select a vessel first"};
			String[] values = selection == JujutsuCharacter.NOBARA
					? new String[] {"Key R", "Key B", "Shift + B", "LMB / Shift+R"}
					: new String[] {"—"};
			for (int i = 0; i < labels.length; i++) {
				float rowH = 36f;
				sdf.add(SdfShape.builder()
						.rect(dx + 10, rowY, dw - 20, rowH)
						.radius(9f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f))
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += rowH + 6;
			}
		} else {
			float rowY = dy + 42;
			for (int i = 0; i < 3; i++) {
				sdf.add(SdfShape.builder()
						.rect(dx + 10, rowY, dw - 20, 40f)
						.radius(9f)
						.border(1f, ModernTheme.withAlpha(0xFFFFFFFF, 0.04f))
						.fill(ModernTheme.CARD, ModernTheme.CARD)
						.build());
				rowY += 48;
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
		MsdfFonts.draw("MAIN", px + 12, py + 54, 7f, ModernTheme.withAlpha(ModernTheme.TEXT_SECTION, a));
		float y = py + 70;
		ResourceLocation[] icons = {EMOJI_BUST, EMOJI_SWORDS, EMOJI_SPARKLES, EMOJI_GEAR};
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			boolean sel = tabs[i] == tab;
			int col = sel ? ModernTheme.TEXT : ModernTheme.withAlpha(ModernTheme.TEXT_DIM, 0.9f);
			MsdfFonts.draw(tabs[i].title, px + 28, y + 8, 9f, ModernTheme.withAlpha(col, a));
			y += 30;
			if (i == 0) {
				// no extra
			}
		}
		MsdfFonts.draw("Soon…", px + 14, py + ModernTheme.BG_H - 22, 7.5f,
				ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a * 0.8f));
	}

	private void drawHeaderText(float px, float py, float a) {
		float hx = px + ModernTheme.SIDEBAR_W + 14;
		MsdfFonts.draw(tab.title, hx, py + 12, 12f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
		// fake search pill label
		String search = "Search…";
		float sw = MsdfFonts.width(search, 8f) + 20;
		float sx = px + ModernTheme.BG_W - 14 - sw;
		// search drawn as text only; surface is optional
		MsdfFonts.draw(search, sx + 8, py + 13, 8f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a * 0.7f));
	}

	private void drawListText(float px, float py, float a) {
		float lx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD;
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
				String bind = i == 0 ? "N" : "–";
				MsdfFonts.drawCentered(bind, lx + ModernTheme.LIST_W - 25, rowY + 12, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
				rowY += 40;
			}
		} else if (tab == Tab.COMBAT) {
			float rowY = ly + 8;
			if (selection == JujutsuCharacter.NOBARA) {
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					MsdfFonts.draw(NOBARA_ABILITIES[i], lx + 12, rowY + 11, 8f,
							ModernTheme.withAlpha(ModernTheme.TEXT, a));
					rowY += 37;
				}
			} else {
				MsdfFonts.draw("No active kit", lx + 12, rowY + 11, 8f,
						ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
			}
		} else if (tab == Tab.VISUALS) {
			float rowY = ly + 8;
			String[] rows = {"MSDF Type", "SDF Panels", "Rich Shell"};
			for (String row : rows) {
				MsdfFonts.draw(row, lx + 12, rowY + 10, 8.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				rowY += 35;
			}
		} else {
			float rowY = ly + 8;
			String[] rows = {"Menu  N", "Neon  V", "Nail  R", "Boom  B"};
			for (String row : rows) {
				MsdfFonts.draw(row, lx + 12, rowY + 10, 8.5f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				rowY += 35;
			}
		}
	}

	private void drawDetailText(float px, float py, float a) {
		float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		float dw = detailW();
		float dh = listH();

		if (tab == Tab.CHARACTER) {
			RosterEntry e = ROSTER[listIndex];
			MsdfFonts.draw(e.name(), dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			MsdfFonts.draw(e.tech(), dx + 74, dy + 48, 9f, ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
			MsdfFonts.draw(e.grade(), dx + 74, dy + 62, 8f, ModernTheme.withAlpha(ModernTheme.ACCENT, a));
			MsdfFonts.draw(e.bindHint(), dx + 74, dy + 76, 7.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));

			if (e.character() == JujutsuCharacter.NOBARA) {
				float chipY = dy + 108;
				float chipX = dx + 12;
				for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
					float cw = (dw - 24 - 6) * 0.5f;
					float ch = 28f;
					float cx = chipX + (i % 2) * (cw + 6);
					float cy = chipY + (i / 2) * (ch + 6);
					MsdfFonts.draw(NOBARA_ABILITIES[i], cx + 22, cy + 9, 7.5f,
							ModernTheme.withAlpha(ModernTheme.TEXT_DIM, a));
					MsdfFonts.draw(NOBARA_KEYS[i], cx + cw - 8 - MsdfFonts.width(NOBARA_KEYS[i], 7f), cy + 9, 7f,
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
			String[] labels = selection == JujutsuCharacter.NOBARA
					? new String[] {"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance / Hammer"}
					: new String[] {"Select a vessel first"};
			String[] values = selection == JujutsuCharacter.NOBARA
					? new String[] {"Key R", "Key B", "Shift + B", "LMB / Shift+R"}
					: new String[] {"—"};
			for (int i = 0; i < labels.length; i++) {
				MsdfFonts.draw(labels[i], dx + 20, rowY + 8, 9f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(values[i], dx + 20, rowY + 20, 7.5f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				if (selection == JujutsuCharacter.NOBARA) {
					String val = values[i];
					MsdfFonts.draw(val, dx + dw - 20 - MsdfFonts.width(val, 8f), rowY + 13, 8f,
							ModernTheme.withAlpha(ModernTheme.ACCENT, a));
				}
				rowY += 42;
			}
		} else if (tab == Tab.VISUALS) {
			MsdfFonts.draw("Presentation", dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			String[] titles = {"MSDF Typography", "SDF Surfaces", "Rich Layout"};
			String[] bodies = {
					"Sharp multi-channel distance field text at any size.",
					"Soft rounded panels with border + glow channels.",
					"Sidebar · list · detail shell matching the reference."
			};
			float rowY = dy + 48;
			for (int i = 0; i < titles.length; i++) {
				MsdfFonts.draw(titles[i], dx + 20, rowY + 6, 9f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(bodies[i], dx + 20, rowY + 20, 7.2f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 48;
			}
		} else {
			MsdfFonts.draw("Controls", dx + 12, dy + 12, 11f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
			String[] titles = {"Modern Menu", "Neon Dashboard", "Combat Keys"};
			String[] bodies = {
					"N — open / close this Rich-style vessel menu.",
					"V — original neon dashboard (unchanged).",
					"R / B / LMB — Nobara kit when selected."
			};
			float rowY = dy + 48;
			for (int i = 0; i < titles.length; i++) {
				MsdfFonts.draw(titles[i], dx + 20, rowY + 6, 9f, ModernTheme.withAlpha(ModernTheme.TEXT, a));
				MsdfFonts.draw(bodies[i], dx + 20, rowY + 20, 7.2f, ModernTheme.withAlpha(ModernTheme.TEXT_MUTED, a));
				rowY += 48;
			}
		}
	}

	private void drawPortraits(GuiGraphics g, float px, float py, float anim) {
		if (anim < 0.05f || tab != Tab.CHARACTER) {
			return;
		}
		float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
		float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		int head = 52;
		int hx = Math.round(dx + 14);
		int hy = Math.round(dy + 42);
		RosterEntry e = ROSTER[listIndex];
		if (e.portraitSkin()) {
			g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 8f, 8f, head, head, 8, 8, 64, 64);
			g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 40f, 8f, head, head, 8, 8, 64, 64);
		} else if (e.emoji() != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, e.emoji(), hx + 4, hy + 4, 0f, 0f, head - 8, head - 8, 96, 96, 96, 96);
		}
	}

	private void drawIcons(GuiGraphics g, float px, float py, float anim) {
		if (anim < 0.05f) {
			return;
		}
		// sidebar icons
		ResourceLocation[] icons = {EMOJI_BUST, EMOJI_SWORDS, EMOJI_SPARKLES, EMOJI_GEAR};
		float y = py + 74;
		for (ResourceLocation icon : icons) {
			int s = 12;
			g.blit(RenderPipelines.GUI_TEXTURED, icon, Math.round(px + 12), Math.round(y), 0f, 0f, s, s, 96, 96, 96, 96);
			y += 30;
		}
		if (tab == Tab.CHARACTER && listIndex >= 0 && listIndex < ROSTER.length
				&& ROSTER[listIndex].character() == JujutsuCharacter.NOBARA) {
			float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float chipY = dy + 108;
			float chipX = dx + 12;
			for (int i = 0; i < NOBARA_ICONS.length; i++) {
				float cw = (dw - 24 - 6) * 0.5f;
				float ch = 28f;
				float cx = chipX + (i % 2) * (cw + 6);
				float cy = chipY + (i / 2) * (ch + 6);
				int s = 12;
				g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_ICONS[i],
						Math.round(cx + 6), Math.round(cy + 8), 0f, 0f, s, s, 96, 96, 96, 96);
			}
		}
	}

	private static String shortName(String name) {
		int sp = name.indexOf(' ');
		return sp > 0 ? name.substring(0, sp) : name;
	}

	// ─── input / animation ────────────────────────────────────────────

	private float tickDelta() {
		long now = System.nanoTime();
		float dt = (now - lastFrameNanos) / 1_000_000_000f;
		lastFrameNanos = now;
		return Math.min(dt, 0.05f);
	}

	private void updateHovers(int mx, int my, float px, float py, float dt) {
		float speed = 0.35f;
		float fdt = dt * 60f;

		// sidebar
		float y = py + 70;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			float h = 26f;
			float x = px + 8;
			float w = ModernTheme.SIDEBAR_W - 16;
			boolean hit = hit(mx, my, x, y, w, h);
			sidebarHover[i] = UiEase.approach(sidebarHover[i], hit ? 1f : 0f, speed, fdt);
			y += h + 4;
		}

		// list rows
		float lx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;
		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				boolean hit = hit(mx, my, lx + 6, rowY, ModernTheme.LIST_W - 12, 34f);
				listHover[i] = UiEase.approach(listHover[i], hit ? 1f : 0f, speed, fdt);
				rowY += 40;
			}
		} else if (tab == Tab.COMBAT && selection == JujutsuCharacter.NOBARA) {
			float rowY = ly + 8;
			for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
				boolean hit = hit(mx, my, lx + 6, rowY, ModernTheme.LIST_W - 12, 32f);
				abilityHover[i] = UiEase.approach(abilityHover[i], hit ? 1f : 0f, speed, fdt);
				rowY += 37;
			}
		}

		// buttons
		if (tab == Tab.CHARACTER) {
			float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float dh = listH();
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
		// custom scrim in render()
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || openAnim < 0.7f || closing) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		float px = (width - ModernTheme.BG_W) * 0.5f;
		float py = (height - ModernTheme.BG_H) * 0.5f;

		// sidebar tabs
		float y = py + 70;
		Tab[] tabs = Tab.values();
		for (Tab t : tabs) {
			float h = 26f;
			float x = px + 8;
			float w = ModernTheme.SIDEBAR_W - 16;
			if (hit(mouseX, mouseY, x, y, w, h)) {
				if (tab != t) {
					fadeFrom = tab;
					tab = t;
					tabFade = 0.35f;
				}
				return true;
			}
			y += h + 4;
		}

		float lx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD;
		float ly = py + ModernTheme.HEADER_H + ModernTheme.PAD;

		if (tab == Tab.CHARACTER) {
			float rowY = ly + 8;
			for (int i = 0; i < ROSTER.length; i++) {
				if (hit(mouseX, mouseY, lx + 6, rowY, ModernTheme.LIST_W - 12, 34f)) {
					listIndex = i;
					selection = ROSTER[i].character();
					selectFlash = 1f;
					return true;
				}
				rowY += 40;
			}

			float dx = px + ModernTheme.SIDEBAR_W + ModernTheme.PAD + ModernTheme.LIST_W + ModernTheme.GAP;
			float dy = py + ModernTheme.HEADER_H + ModernTheme.PAD;
			float dw = detailW();
			float dh = listH();
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
		} else if (tab == Tab.COMBAT && selection == JujutsuCharacter.NOBARA) {
			float rowY = ly + 8;
			for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
				if (hit(mouseX, mouseY, lx + 6, rowY, ModernTheme.LIST_W - 12, 32f)) {
					abilityOn[i] = !abilityOn[i];
					return true;
				}
				rowY += 37;
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
		if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_N
				|| keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			animateClose();
			return true;
		}
		// quick tab numbers
		if (keyCode >= com.mojang.blaze3d.platform.InputConstants.KEY_1
				&& keyCode <= com.mojang.blaze3d.platform.InputConstants.KEY_4) {
			int idx = keyCode - com.mojang.blaze3d.platform.InputConstants.KEY_1;
			Tab[] tabs = Tab.values();
			if (idx < tabs.length && tab != tabs[idx]) {
				tab = tabs[idx];
				tabFade = 0.35f;
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

	private static boolean hit(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
