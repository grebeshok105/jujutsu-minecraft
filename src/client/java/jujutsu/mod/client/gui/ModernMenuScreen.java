package jujutsu.mod.client.gui;

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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

/**
 * Vessel menu (N) — structure and numbers ported from Rich-Modern clickgui:
 * BackgroundComponent/BackgroundRenderer/CategoryRenderer/HeaderRenderer/
 * ModuleListRenderer/SettingsPanelRenderer (codegraph on full rar extract).
 *
 * Gameplay content = jujutsu character select. Neon dashboard (V) unchanged.
 */
public final class ModernMenuScreen extends Screen {
	private static final long OPEN_MS = 250;
	private static final long CLOSE_MS = 200;

	// CategoryRenderer MAIN_CATEGORY_NAMES / ICONS
	private static final String[] CAT_NAMES = {"Character", "Combat", "Visuals", "Misc"};
	private static final String[] CAT_ICONS = {"a", "b", "c", "d"}; // categoryicons font

	private static final String[] VESSEL_NAMES = {"Nobara", "None"};
	private static final String[] VESSEL_BINDS = {"N", "—"};
	private static final JujutsuCharacter[] VESSELS = {JujutsuCharacter.NOBARA, JujutsuCharacter.NONE};

	private static final String[] ABILITY_NAMES = {
			"Piercing Nail", "Hairpin Enlarge", "Hairpin Boom", "Resonance"
	};
	private static final String[] ABILITY_DESC = {
			"Directed nail shot", "Enlarge marked hairpin", "Mass hairpin detonation", "Hammer / ritual"
	};
	private static final String[] ABILITY_KEYS = {"R", "B", "Shift+R", "LMB"};

	private final SdfRenderer sdf = new SdfRenderer();

	private int category; // 0..3
	private int selectedVessel;
	private JujutsuCharacter selection;

	private float openAnim;
	private boolean closing;
	private boolean disposed;
	private boolean forceClose;
	private long openStartMs;
	private long closeStartMs;
	private long lastNanos;
	private long lastAnimMs;

	// CategoryRenderer.categoryAnimations
	private final float[] catAnim = new float[CAT_NAMES.length];
	// Module hover / state-style
	private final float[] modHover = new float[8];
	private float confirmHover;
	private float cancelHover;
	private float selectPulse;

	public ModernMenuScreen() {
		super(Component.translatable("screen.jujutsumod.modern_menu"));
		openStartMs = System.currentTimeMillis();
		lastNanos = System.nanoTime();
		lastAnimMs = System.currentTimeMillis();
		selection = initialSelection();
		selectedVessel = indexOf(selection);
		catAnim[0] = 1f;
	}

	private static JujutsuCharacter initialSelection() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			return ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
		}
		return JujutsuCharacter.NONE;
	}

	private static int indexOf(JujutsuCharacter c) {
		for (int i = 0; i < VESSELS.length; i++) {
			if (VESSELS[i] == c) {
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

	private float bgX() {
		return (width - ModernTheme.BG_W) * 0.5f;
	}

	private float bgY() {
		float base = (height - ModernTheme.BG_H) * 0.5f;
		float anim = UiEase.outCubic(openAnim);
		// ClickGui: open yOffset = (1-anim)*-15, close = (1-anim)*30 — use open style
		return base + (1f - anim) * -15f;
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
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

	// ── render ────────────────────────────────────────────────────────

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		updateOpenAnim();
		float dt = animDt();
		float anim = UiEase.outCubic(openAnim);
		// ClickGui dimAlpha = 125 * animValue
		float dimA = 125f / 255f * anim;

		float bx = bgX();
		float by = bgY();

		updateCatAnims(dt);
		updateHovers(mouseX, mouseY, bx, by, dt);
		selectPulse += dt * 4f;

		sdf.setGlobalAlpha(1f);
		sdf.begin();

		// dim world — Color(0,0,0,dimAlpha)
		sdf.add(SdfShape.builder()
				.rect(0, 0, width, height)
				.radius(0).border(0, 0)
				.fill(ModernTheme.argb(0, 0, 0, Math.round(255 * dimA)),
						ModernTheme.argb(0, 0, 0, Math.round(255 * dimA)))
				.build());

		// BackgroundRenderer.render — gradient shell 400x250 r=15
		// simplified as solid-ish top/bottom gradient via SDF fillTop/fillBottom
		int shellA = Math.round(255 * anim);
		sdf.add(SdfShape.builder()
				.rect(bx, by, ModernTheme.BG_W, ModernTheme.BG_H)
				.radius(ModernTheme.SHELL_RADIUS)
				.border(0.5f, ModernTheme.argb(55, 55, 55, shellA))
				.highlight(0.04f)
				.fill(ModernTheme.argb(26, 26, 26, shellA), ModernTheme.argb(0, 0, 0, shellA))
				.build());

		// BackgroundRenderer.renderCategoryPanel
		drawCategoryPanel(bx, by, anim);
		// HeaderRenderer.renderHeaderPanel + search
		drawHeader(bx, by, anim);
		// Module list panel
		drawModuleListPanel(bx, by, anim);
		// Settings panel
		drawSettingsPanel(bx, by, anim);

		sdf.flush();

		// text / icons after surfaces
		drawCategoryNames(bx, by, anim);
		drawHeaderText(bx, by, anim);
		drawModuleItems(bx, by, anim);
		drawSettingsContent(bx, by, anim);
		MsdfFonts.endFrame();
	}

	/** BackgroundRenderer.renderCategoryPanel — exact rects. */
	private void drawCategoryPanel(float bgX, float bgY, float a) {
		int panelA = Math.round(25 * a);
		int outlineA = Math.round(255 * a);
		// rect(bgX+7.5, bgY+7.5, 80, bgHeight-15, gray 128@25, r=10)
		sdf.add(SdfShape.builder()
				.rect(bgX + 7.5f, bgY + 7.5f, 80, ModernTheme.BG_H - 15)
				.radius(10f)
				.border(0.5f, ModernTheme.argb(55, 55, 55, outlineA))
				.fill(ModernTheme.argb(128, 128, 128, panelA), ModernTheme.argb(128, 128, 128, panelA))
				.build());
		// avatar area behind (AvatarRenderer uses 70x30 @ 12.5)
		sdf.add(SdfShape.builder()
				.rect(bgX + 12.5f, bgY + 12.5f, 70, 30)
				.radius(7f)
				.border(0, 0)
				.fill(ModernTheme.argb(0, 0, 0, Math.round(105 * a)),
						ModernTheme.argb(0, 0, 0, Math.round(105 * a)))
				.build());
		// avatar circle placeholder
		sdf.add(SdfShape.builder()
				.rect(bgX + 15f, bgY + 15f, 25, 25)
				.radius(12.5f)
				.border(0, 0)
				.fill(ModernTheme.argb(42, 42, 42, Math.round(255 * a)),
						ModernTheme.argb(42, 42, 42, Math.round(255 * a)))
				.build());
		// green online
		sdf.add(SdfShape.builder()
				.rect(bgX + 33, bgY + 33, 5, 5)
				.radius(2.5f)
				.border(0, 0)
				.fill(ModernTheme.argb(0, 255, 0, Math.round(255 * a)),
						ModernTheme.argb(0, 255, 0, Math.round(255 * a)))
				.build());
		// Soon... outline box
		sdf.add(SdfShape.builder()
				.rect(bgX + 12.5f, bgY + 220.5f, 70, 17)
				.radius(5f)
				.border(0.5f, ModernTheme.argb(55, 55, 55, outlineA))
				.fill(ModernTheme.argb(25, 25, 25, Math.round(80 * a)),
						ModernTheme.argb(25, 25, 25, Math.round(80 * a)))
				.build());
	}

	private void drawHeader(float bgX, float bgY, float a) {
		int panelA = Math.round(25 * a);
		int outlineA = Math.round(255 * a);
		// HeaderRenderer.renderHeaderPanel
		sdf.add(SdfShape.builder()
				.rect(bgX + 92f, bgY + 7.5f, ModernTheme.BG_W - 100f, 25)
				.radius(8f)
				.border(0.5f, ModernTheme.argb(55, 55, 55, outlineA))
				.fill(ModernTheme.argb(128, 128, 128, panelA), ModernTheme.argb(128, 128, 128, panelA))
				.build());
		// search box
		sdf.add(SdfShape.builder()
				.rect(bgX + 315f, bgY + 12.5f, 70, 15)
				.radius(4f)
				.border(0.5f, ModernTheme.argb(55, 55, 55, outlineA))
				.fill(ModernTheme.argb(40, 40, 45, Math.round(25 * a)),
						ModernTheme.argb(40, 40, 45, Math.round(25 * a)))
				.build());
	}

	private void drawModuleListPanel(float bgX, float bgY, float a) {
		float x = bgX + ModernTheme.LIST_X;
		float y = bgY + ModernTheme.LIST_Y;
		float w = ModernTheme.LIST_W;
		float h = ModernTheme.CONTENT_H;
		// ModuleListRenderer panel: Color(64,64,64,15), outline (55,55,55,215), r=6
		sdf.add(SdfShape.builder()
				.rect(x, y, w, h)
				.radius(ModernTheme.MODULE_LIST_RADIUS)
				.border(0.5f, ModernTheme.argb(55, 55, 55, Math.round(215 * a)))
				.fill(ModernTheme.argb(64, 64, 64, Math.round(15 * a)),
						ModernTheme.argb(64, 64, 64, Math.round(15 * a)))
				.build());

		// module rows
		String[] names = modulesForCategory();
		float startY = y + 3f + 2f;
		for (int i = 0; i < names.length; i++) {
			float modY = startY + i * (ModernTheme.MODULE_ITEM_H + ModernTheme.MODULE_GAP);
			boolean selected = category == 0 && i == selectedVessel;
			float hover = i < modHover.length ? modHover[i] : 0f;

			int bgAlpha;
			int r, g, b;
			if (selected) {
				bgAlpha = Math.round((55 + hover * 10) * a);
				r = g = b = 71;
			} else {
				bgAlpha = Math.round((25 + (45 - 25) * hover) * a);
				int gray = Math.round(64 + 36 * hover);
				r = g = b = gray;
			}
			float animX = x + 3;
			sdf.add(SdfShape.builder()
					.rect(animX, modY, w - 6, ModernTheme.MODULE_ITEM_H)
					.radius(ModernTheme.MODULE_ITEM_RADIUS)
					.border(selected ? 0.5f : (hover > 0.01f ? 0.5f : 0f),
							selected
									? ModernTheme.argb(100, 100, 100, Math.round((80 + 40 * (float) (Math.sin(selectPulse) * 0.5 + 0.5)) * a))
									: ModernTheme.argb(120, 120, 120, Math.round(60 * hover * a)))
					.fill(ModernTheme.argb(r, g, b, bgAlpha), ModernTheme.argb(r, g, b, bgAlpha))
					.build());

			// state ball if "active" vessel selected
			if (selected) {
				sdf.add(SdfShape.builder()
						.rect(animX + 4, modY + (ModernTheme.MODULE_ITEM_H - 3) / 2f + 1f, 3, 3)
						.radius(1.5f)
						.border(0, 0)
						.fill(ModernTheme.argb(255, 255, 255, Math.round(200 * a)),
								ModernTheme.argb(255, 255, 255, Math.round(200 * a)))
						.build());
			}
		}
	}

	private void drawSettingsPanel(float bgX, float bgY, float a) {
		float x = bgX + ModernTheme.SETTINGS_X;
		float y = bgY + ModernTheme.SETTINGS_Y;
		float w = ModernTheme.SETTINGS_W;
		float h = ModernTheme.CONTENT_H;
		// SettingsPanelRenderer: Color(64,64,64,15), outline 55@215, r=7
		sdf.add(SdfShape.builder()
				.rect(x, y, w, h)
				.radius(ModernTheme.SETTINGS_RADIUS)
				.border(0.5f, ModernTheme.argb(55, 55, 55, Math.round(215 * a)))
				.fill(ModernTheme.argb(64, 64, 64, Math.round(15 * a)),
						ModernTheme.argb(64, 64, 64, Math.round(15 * a)))
				.build());
		// divider under title
		sdf.add(SdfShape.builder()
				.rect(x + 8, y + 30, w - 16, 1.25f)
				.radius(1f)
				.border(0, 0)
				.fill(ModernTheme.argb(64, 64, 64, Math.round(64 * a)),
						ModernTheme.argb(64, 64, 64, Math.round(64 * a)))
				.build());

		if (category == 0) {
			// setting rows as thin bars (Checkbox-style height 16)
			float posY = y + 38f;
			int rows = VESSELS[selectedVessel] == JujutsuCharacter.NOBARA ? ABILITY_NAMES.length : 1;
			for (int i = 0; i < rows; i++) {
				sdf.add(SdfShape.builder()
						.rect(x + 8, posY, w - 16, ModernTheme.SETTING_H)
						.radius(4f)
						.border(0, 0)
						.fill(ModernTheme.argb(50, 50, 52, Math.round(40 * a)),
								ModernTheme.argb(50, 50, 52, Math.round(40 * a)))
						.build());
				posY += ModernTheme.SETTING_H + ModernTheme.SETTING_GAP + 6; // room for desc
			}

			// Confirm / Cancel as bottom module-style buttons
			float btnH = 18f;
			float btnY = y + h - btnH - 8;
			float gap = 4f;
			float cancelW = (w - 16 - gap) * 0.4f;
			float confW = (w - 16 - gap) * 0.6f;
			sdf.add(SdfShape.builder()
					.rect(x + 8, btnY, cancelW, btnH)
					.radius(5f)
					.border(0.5f, ModernTheme.argb(80, 80, 85, Math.round((60 + 40 * cancelHover) * a)))
					.fill(ModernTheme.argb(50, 50, 55, Math.round((30 + 20 * cancelHover) * a)),
							ModernTheme.argb(50, 50, 55, Math.round((30 + 20 * cancelHover) * a)))
					.build());
			sdf.add(SdfShape.builder()
					.rect(x + 8 + cancelW + gap, btnY, confW, btnH)
					.radius(5f)
					.border(0.5f, ModernTheme.argb(120, 120, 125, Math.round((80 + 40 * confirmHover) * a)))
					.fill(ModernTheme.argb(71, 71, 71, Math.round((55 + 20 * confirmHover) * a)),
							ModernTheme.argb(71, 71, 71, Math.round((55 + 20 * confirmHover) * a)))
					.build());
		}
	}

	// ── text layer (Fonts.BOLD / CATEGORY_ICONS / GUI_ICONS) ───────────

	private void drawCategoryNames(float bgX, float bgY, float a) {
		// Avatar text
		Minecraft mc = Minecraft.getInstance();
		String username = mc.player != null ? mc.player.getName().getString() : "Player";
		if (username.length() > 8) {
			username = username.substring(0, 7) + "…";
		}
		int alphaText = Math.round(200 * a);
		MsdfFonts.drawBold(username, bgX + 44, bgY + 22, 6,
				ModernTheme.argb(255, 255, 255, alphaText));
		String uid = selection == JujutsuCharacter.NOBARA ? "Nobara" : "None";
		MsdfFonts.drawBold(uid, bgX + 44, bgY + 29, 5,
				ModernTheme.argb(255, 255, 255, alphaText));

		// Section "Основные"
		drawSectionHeader(bgX, bgY + 52f, "Основные", a);

		// Category items — CategoryRenderer.renderCategoryItem
		for (int i = 0; i < CAT_NAMES.length; i++) {
			float animation = catAnim[i];
			float textY = bgY + 65f + i * ModernTheme.CAT_STEP;
			float offsetX = animation * ModernTheme.CAT_MAX_OFFSET;

			int baseGray = 128;
			int targetWhite = 255;
			int colorValue = Math.round(baseGray + (targetWhite - baseGray) * animation);
			int alpha = Math.round((128 + 127 * animation) * a);
			int col = ModernTheme.argb(colorValue, colorValue, colorValue, alpha);

			float iconX = bgX + 17f + offsetX;
			MsdfFonts.drawCategoryIcon(CAT_ICONS[i], iconX, textY + 0.5f, ModernTheme.CAT_ICON, col);
			float iconW = MsdfFonts.width(MsdfFonts.Face.CATEGORY, CAT_ICONS[i], ModernTheme.CAT_ICON);
			float textX = iconX + iconW + 4f;
			MsdfFonts.drawBold(CAT_NAMES[i], textX, textY, ModernTheme.CAT_TEXT, col);

			if (animation > 0.01f) {
				float textW = MsdfFonts.width(MsdfFonts.Face.BOLD, CAT_NAMES[i], ModernTheme.CAT_TEXT);
				float lineW = (iconW + 4f + textW) * animation;
				int lineA = Math.round(animation * 60 * a);
				// underline via thin sdf already done in surfaces? use text only — add small rect via sdf was earlier
				// draw ball
				int ballA = Math.round(animation * 200 * a);
				// ball drawn in surfaces would need another pass — draw with bold "•"
				MsdfFonts.drawBold("•", bgX + 11f, textY + 0.5f, 5,
						ModernTheme.argb(255, 255, 255, ballA));
			}
		}

		// Soon...
		MsdfFonts.drawCentered(MsdfFonts.Face.BOLD, "Soon...",
				bgX + 12.5f + 35f, bgY + 220.5f + 5f, 6,
				ModernTheme.argb(150, 150, 150, Math.round(200 * a)));
	}

	private void drawSectionHeader(float bgX, float sectionY, String title, float a) {
		float lineWidth = 18f;
		float textWidth = MsdfFonts.width(MsdfFonts.Face.BOLD, title, 5f);
		float totalWidth = 65f;
		float textX = bgX + 15f + (totalWidth - textWidth) / 2f;
		int textAlpha = Math.round(100 * a);
		MsdfFonts.drawBold(title, textX, sectionY, 5f, ModernTheme.argb(150, 150, 150, textAlpha));
	}

	private void drawHeaderText(float bgX, float bgY, float a) {
		// category label
		MsdfFonts.drawBold(CAT_NAMES[category], bgX + 100f, bgY + 15f, 7,
				ModernTheme.argb(255, 255, 255, Math.round(200 * a)));
		// search placeholder
		MsdfFonts.drawBold("Search Modules...", bgX + 320f, bgY + 17.5f, 5,
				ModernTheme.argb(128, 128, 128, Math.round(255 * a)));
		// search icon U from guiicons
		MsdfFonts.drawIcon("U", bgX + 315f + 55f, bgY + 12.5f + 1.5f, 12,
				ModernTheme.argb(128, 128, 128, Math.round(255 * a)));
	}

	private void drawModuleItems(float bgX, float bgY, float a) {
		float x = bgX + ModernTheme.LIST_X;
		float y = bgY + ModernTheme.LIST_Y;
		float w = ModernTheme.LIST_W;
		String[] names = modulesForCategory();
		String[] binds = bindsForCategory();
		float startY = y + 5f;
		for (int i = 0; i < names.length; i++) {
			float modY = startY + i * (ModernTheme.MODULE_ITEM_H + ModernTheme.MODULE_GAP);
			boolean selected = category == 0 && i == selectedVessel;
			float stateAnim = selected ? 1f : 0f;
			float hover = i < modHover.length ? modHover[i] : 0f;

			int baseGray = 128;
			int targetWhite = 255;
			int textBrightness = Math.round(baseGray + (targetWhite - baseGray) * stateAnim);
			int textAlpha = Math.round((180 + 75 * stateAnim) * a);
			if (hover > 0.01f && stateAnim < 0.99f) {
				textBrightness = Math.round(textBrightness + 40 * hover * (1 - stateAnim));
				textAlpha = Math.round(textAlpha + 40 * hover * (1 - stateAnim));
			}
			float textX = x + 3 + 5 + stateAnim * 6f;
			float textY = modY + (ModernTheme.MODULE_ITEM_H - 6f) / 2f;
			MsdfFonts.drawBold(names[i], textX, textY, 6,
					ModernTheme.argb(textBrightness, textBrightness, textBrightness, Math.min(255, textAlpha)));

			// bind box text
			if (binds[i] != null && !binds[i].isEmpty() && !binds[i].equals("—")) {
				float nameW = MsdfFonts.width(MsdfFonts.Face.BOLD, names[i], 6);
				float boxX = textX + nameW + 4;
				MsdfFonts.drawBold(binds[i], boxX, textY + 0.5f, 5,
						ModernTheme.argb(140, 140, 145, Math.round(160 * a)));
			}

			// gear / dots when selected with settings (Character vessels)
			if (category == 0) {
				float iconBaseX = x + 3 + (w - 6) - 14;
				float iconY = modY + (ModernTheme.MODULE_ITEM_H - 8f) / 2f;
				if (selected) {
					MsdfFonts.drawIcon("B", iconBaseX, iconY + 1, 8,
							ModernTheme.argb(200, 200, 200, Math.round(150 * a)));
				} else {
					MsdfFonts.drawBold("...", iconBaseX + 1f, iconY - 1f, 7,
							ModernTheme.argb(150, 150, 150, Math.round(120 * a)));
				}
			}
		}
	}

	private void drawSettingsContent(float bgX, float bgY, float a) {
		float x = bgX + ModernTheme.SETTINGS_X;
		float y = bgY + ModernTheme.SETTINGS_Y;
		float w = ModernTheme.SETTINGS_W;
		float h = ModernTheme.CONTENT_H;

		if (category == 0) {
			String title = VESSEL_NAMES[selectedVessel];
			MsdfFonts.drawBold(title, x + 8, y + 8, 7,
					ModernTheme.argb(255, 255, 255, Math.round(200 * a)));
			String desc = VESSELS[selectedVessel] == JujutsuCharacter.NOBARA
					? "Straw Doll Technique — Grade 3 vessel"
					: "No cursed technique selected";
			if (desc.length() > 40) {
				desc = desc.substring(0, 40) + "...";
			}
			MsdfFonts.drawIcon("C", x + 8, y + 20, 6,
					ModernTheme.argb(128, 128, 128, Math.round(150 * a)));
			MsdfFonts.drawBold(desc, x + 15, y + 20, 5,
					ModernTheme.argb(128, 128, 128, Math.round(150 * a)));

			float posY = y + 38f;
			if (VESSELS[selectedVessel] == JujutsuCharacter.NOBARA) {
				for (int i = 0; i < ABILITY_NAMES.length; i++) {
					MsdfFonts.drawBold(ABILITY_NAMES[i], x + 12, posY + 2, 6,
							ModernTheme.argb(210, 210, 210, Math.round(200 * a)));
					MsdfFonts.drawBold(ABILITY_DESC[i], x + 12, posY + 10, 5,
							ModernTheme.argb(128, 128, 128, Math.round(150 * a)));
					float keyW = MsdfFonts.width(MsdfFonts.Face.BOLD, ABILITY_KEYS[i], 5);
					MsdfFonts.drawBold(ABILITY_KEYS[i], x + w - 12 - keyW, posY + 5, 5,
							ModernTheme.argb(140, 140, 145, Math.round(180 * a)));
					posY += ModernTheme.SETTING_H + ModernTheme.SETTING_GAP + 6;
				}
			} else {
				MsdfFonts.drawBold("This vessel doesn't have settings", x + 12, posY + 20, 6,
						ModernTheme.argb(100, 100, 100, Math.round(150 * a)));
			}

			float btnH = 18f;
			float btnY = y + h - btnH - 8;
			float gap = 4f;
			float cancelW = (w - 16 - gap) * 0.4f;
			float confW = (w - 16 - gap) * 0.6f;
			MsdfFonts.drawCentered(MsdfFonts.Face.BOLD, "Cancel",
					x + 8 + cancelW * 0.5f, btnY + 5, 6,
					ModernTheme.argb(160, 160, 160, Math.round(200 * a)));
			MsdfFonts.drawCentered(MsdfFonts.Face.BOLD, "Confirm",
					x + 8 + cancelW + gap + confW * 0.5f, btnY + 5, 6,
					ModernTheme.argb(230, 230, 230, Math.round(220 * a)));
		} else if (category == 1) {
			MsdfFonts.drawBold("Loadout", x + 8, y + 8, 7,
					ModernTheme.argb(255, 255, 255, Math.round(200 * a)));
			if (selection == JujutsuCharacter.NOBARA) {
				float posY = y + 38f;
				for (int i = 0; i < ABILITY_NAMES.length; i++) {
					MsdfFonts.drawBold(ABILITY_NAMES[i], x + 12, posY + 2, 6,
							ModernTheme.argb(210, 210, 210, Math.round(200 * a)));
					MsdfFonts.drawBold(ABILITY_KEYS[i], x + w - 12 - MsdfFonts.width(MsdfFonts.Face.BOLD, ABILITY_KEYS[i], 5),
							posY + 5, 5, ModernTheme.argb(140, 140, 145, Math.round(180 * a)));
					posY += 22;
				}
			} else {
				MsdfFonts.drawBold("Select a vessel first", x + 12, y + 50, 6,
						ModernTheme.argb(100, 100, 100, Math.round(150 * a)));
			}
		} else if (category == 2) {
			MsdfFonts.drawBold("Presentation", x + 8, y + 8, 7,
					ModernTheme.argb(255, 255, 255, Math.round(200 * a)));
			MsdfFonts.drawBold("MSDF + SDF shell ported from Rich", x + 12, y + 40, 6,
					ModernTheme.argb(150, 150, 150, Math.round(180 * a)));
		} else {
			MsdfFonts.drawBold("Controls", x + 8, y + 8, 7,
					ModernTheme.argb(255, 255, 255, Math.round(200 * a)));
			MsdfFonts.drawBold("N — this menu", x + 12, y + 40, 6,
					ModernTheme.argb(150, 150, 150, Math.round(180 * a)));
			MsdfFonts.drawBold("V — neon dashboard", x + 12, y + 52, 6,
					ModernTheme.argb(150, 150, 150, Math.round(180 * a)));
		}
	}

	// ── data ──────────────────────────────────────────────────────────

	private String[] modulesForCategory() {
		return switch (category) {
			case 0 -> VESSEL_NAMES;
			case 1 -> selection == JujutsuCharacter.NOBARA
					? new String[] {"Piercing", "Enlarge", "Boom", "Resonance"}
					: new String[] {"Empty"};
			case 2 -> new String[] {"MSDF", "SDF", "Shell"};
			default -> new String[] {"Menu", "Neon", "Kit"};
		};
	}

	private String[] bindsForCategory() {
		return switch (category) {
			case 0 -> VESSEL_BINDS;
			case 1 -> selection == JujutsuCharacter.NOBARA
					? new String[] {"R", "B", "S+R", "LMB"}
					: new String[] {"—"};
			case 2 -> new String[] {"—", "—", "—"};
			default -> new String[] {"N", "V", "R/B"};
		};
	}

	// ── animation / input ─────────────────────────────────────────────

	private float animDt() {
		long now = System.currentTimeMillis();
		float dt = Math.min((now - lastAnimMs) / 1000f, 0.1f);
		lastAnimMs = now;
		return dt;
	}

	private void updateCatAnims(float dt) {
		// CategoryRenderer ANIMATION_SPEED = 8
		for (int i = 0; i < catAnim.length; i++) {
			float target = i == category ? 1f : 0f;
			catAnim[i] = ModernTheme.approach(catAnim[i], target, 8f, dt);
		}
	}

	private void updateHovers(int mx, int my, float bx, float by, float dt) {
		// modules
		float x = bx + ModernTheme.LIST_X;
		float y = by + ModernTheme.LIST_Y;
		float w = ModernTheme.LIST_W;
		String[] names = modulesForCategory();
		float startY = y + 5f;
		for (int i = 0; i < names.length && i < modHover.length; i++) {
			float modY = startY + i * (ModernTheme.MODULE_ITEM_H + ModernTheme.MODULE_GAP);
			boolean hit = hit(mx, my, x + 3, modY, w - 6, ModernTheme.MODULE_ITEM_H);
			modHover[i] = ModernTheme.approach(modHover[i], hit ? 1f : 0f, 10f, dt);
		}
		// buttons
		if (category == 0) {
			float sx = bx + ModernTheme.SETTINGS_X;
			float sy = by + ModernTheme.SETTINGS_Y;
			float sw = ModernTheme.SETTINGS_W;
			float sh = ModernTheme.CONTENT_H;
			float btnH = 18f;
			float btnY = sy + sh - btnH - 8;
			float gap = 4f;
			float cancelW = (sw - 16 - gap) * 0.4f;
			float confW = (sw - 16 - gap) * 0.6f;
			cancelHover = ModernTheme.approach(cancelHover,
					hit(mx, my, sx + 8, btnY, cancelW, btnH) ? 1f : 0f, 10f, dt);
			confirmHover = ModernTheme.approach(confirmHover,
					hit(mx, my, sx + 8 + cancelW + gap, btnY, confW, btnH) ? 1f : 0f, 10f, dt);
		} else {
			cancelHover = ModernTheme.approach(cancelHover, 0f, 10f, dt);
			confirmHover = ModernTheme.approach(confirmHover, 0f, 10f, dt);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || openAnim < 0.85f || closing) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		float bx = bgX();
		float by = bgY();

		// categories — CategoryRenderer.getCategoryAtPosition
		if (mouseX >= bx + 10f && mouseX <= bx + 95f) {
			for (int i = 0; i < CAT_NAMES.length; i++) {
				float catY = 65f + i * 15f;
				if (mouseY >= by + catY && mouseY <= by + catY + 13f) {
					category = i;
					return true;
				}
			}
		}

		// modules
		if (category == 0) {
			float x = bx + ModernTheme.LIST_X;
			float y = by + ModernTheme.LIST_Y;
			float w = ModernTheme.LIST_W;
			float startY = y + 5f;
			for (int i = 0; i < VESSEL_NAMES.length; i++) {
				float modY = startY + i * (ModernTheme.MODULE_ITEM_H + ModernTheme.MODULE_GAP);
				if (hit(mouseX, mouseY, x + 3, modY, w - 6, ModernTheme.MODULE_ITEM_H)) {
					selectedVessel = i;
					selection = VESSELS[i];
					return true;
				}
			}
			float sx = bx + ModernTheme.SETTINGS_X;
			float sy = by + ModernTheme.SETTINGS_Y;
			float sw = ModernTheme.SETTINGS_W;
			float sh = ModernTheme.CONTENT_H;
			float btnH = 18f;
			float btnY = sy + sh - btnH - 8;
			float gap = 4f;
			float cancelW = (sw - 16 - gap) * 0.4f;
			float confW = (sw - 16 - gap) * 0.6f;
			if (hit(mouseX, mouseY, sx + 8, btnY, cancelW, btnH)) {
				animateClose();
				return true;
			}
			if (hit(mouseX, mouseY, sx + 8 + cancelW + gap, btnY, confW, btnH)) {
				confirm();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void confirm() {
		selection = VESSELS[selectedVessel];
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
		closeStartMs = System.currentTimeMillis();
	}

	private void updateOpenAnim() {
		long now = System.currentTimeMillis();
		if (closing) {
			float t = (now - closeStartMs) / (float) CLOSE_MS;
			openAnim = 1f - UiEase.clamp01(t);
			if (t >= 1f) {
				forceClose = true;
			}
		} else {
			openAnim = UiEase.clamp01((now - openStartMs) / (float) OPEN_MS);
		}
	}

	private static boolean hit(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
