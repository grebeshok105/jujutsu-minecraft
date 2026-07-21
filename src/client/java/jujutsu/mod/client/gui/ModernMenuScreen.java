package jujutsu.mod.client.gui;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
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
 * Separate modern character menu (key N). Does not replace the neon dashboard (key V).
 * Surfaces use the neon SDF pipeline; all labels use MSDF for sharp UI type.
 */
public final class ModernMenuScreen extends Screen {
	private static final long OPEN_MS = 280;
	private static final long CLOSE_MS = 200;

	private static final float PANEL_W = 520f;
	private static final float PANEL_H = 320f;
	private static final float CARD_W = 200f;
	private static final float CARD_H = 168f;

	private static final ResourceLocation NOBARA_SKIN =
			JujutsuMod.id("textures/entity/character/nobara.png");
	private static final ResourceLocation EMOJI_BUST =
			JujutsuMod.id("textures/gui/dashboard/emoji_bust.png");

	// Palette — ink + cursed-energy amber, editorial contrast
	private static final int INK = 0xFF0A0B10;
	private static final int INK_SOFT = 0xFF12141C;
	private static final int PANEL_TOP = 0xF0141620;
	private static final int PANEL_BOT = 0xF00C0E14;
	private static final int LINE = 0x33FFFFFF;
	private static final int TEXT = 0xFFF4F1EA;
	private static final int TEXT_DIM = 0xFF9A958C;
	private static final int TEXT_MUTED = 0xFF6B6660;
	private static final int AMBER = 0xFFE8A04A;
	private static final int AMBER_SOFT = 0xFFC47A2E;
	private static final int SLATE = 0xFF7A8494;
	private static final int GOOD = 0xFF5ECF8A;

	private final SdfRenderer sdf = new SdfRenderer();

	private JujutsuCharacter selection;
	private float openAnim;
	private boolean closing;
	private boolean disposed;
	private long openStartMillis;
	private long closeStartMillis;

	private float panelX;
	private float panelY;
	private float hoverNobara;
	private float hoverNone;
	private float hoverConfirm;
	private float hoverCancel;

	public ModernMenuScreen() {
		super(Component.translatable("screen.jujutsumod.modern_menu"));
		this.openStartMillis = System.currentTimeMillis();
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
		panelX = (width - PANEL_W) * 0.5f;
		panelY = (height - PANEL_H) * 0.5f;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		updateAnimation();
		float anim = UiEase.outCubic(openAnim);
		float rise = (1f - anim) * 18f;

		// Soft scale-in via vertical offset; alpha driven through SDF.
		float px = panelX;
		float py = panelY + rise;

		updateHovers(mouseX, mouseY, px, py, partialTick);

		// Dim world
		int scrimA = Math.round(0xB0 * anim);
		g.fill(0, 0, width, height, (scrimA << 24) | 0x05060A);

		// Outer ambient glow ring (subtle)
		sdf.setGlobalAlpha(anim);
		sdf.begin();

		// Main panel
		sdf.add(SdfShape.builder()
				.rect(px, py, PANEL_W, PANEL_H)
				.radius(18f)
				.border(1.2f, withAlpha(0xFFFFFFFF, 0.10f * anim))
				.glow(14f, withAlpha(AMBER, 0.22f * anim))
				.highlight(0.22f)
				.fill(PANEL_TOP, PANEL_BOT)
				.build());

		// Top accent bar
		sdf.add(SdfShape.builder()
				.rect(px + 18, py + 42, PANEL_W - 36, 1.5f)
				.radius(1f)
				.border(0, 0)
				.glow(4f, withAlpha(AMBER, 0.35f * anim))
				.fill(withAlpha(AMBER, 0.85f), withAlpha(AMBER_SOFT, 0.25f))
				.build());

		// Left energy strip
		sdf.add(SdfShape.builder()
				.rect(px, py + 54, 3f, PANEL_H - 70)
				.radius(1.5f)
				.border(0, 0)
				.glow(8f, withAlpha(AMBER, 0.40f * anim))
				.fill(AMBER, withAlpha(AMBER, 0.15f))
				.build());

		drawCharacterCardSurface(true, px, py, anim);
		drawCharacterCardSurface(false, px, py, anim);

		// Ability chips (surface)
		drawAbilityChipSurfaces(px, py, anim);

		// Footer action buttons
		float btnY = py + PANEL_H - 46;
		float btnH = 28f;
		float cancelW = 110f;
		float confirmW = 140f;
		float btnGap = 10f;
		float totalBtn = cancelW + confirmW + btnGap;
		float btnStart = px + (PANEL_W - totalBtn) * 0.5f;

		// Cancel
		sdf.add(SdfShape.builder()
				.rect(btnStart, btnY, cancelW, btnH)
				.radius(8f)
				.border(1f, withAlpha(0xFFFFFFFF, 0.12f + 0.10f * hoverCancel))
				.glow(hoverCancel > 0.01f ? 6f : 0f, withAlpha(0xFFFFFFFF, 0.08f * hoverCancel))
				.highlight(0.12f + 0.10f * hoverCancel)
				.fill(withAlpha(INK_SOFT, 0.95f), withAlpha(INK, 0.95f))
				.build());

		// Confirm
		int confTop = lerpColor(0xFF2A2118, 0xFF3A2A18, hoverConfirm);
		int confBot = lerpColor(0xFF1A140E, 0xFF2A1C10, hoverConfirm);
		sdf.add(SdfShape.builder()
				.rect(btnStart + cancelW + btnGap, btnY, confirmW, btnH)
				.radius(8f)
				.border(1.2f, withAlpha(AMBER, 0.45f + 0.35f * hoverConfirm))
				.glow(8f + 4f * hoverConfirm, withAlpha(AMBER, 0.28f + 0.25f * hoverConfirm))
				.highlight(0.18f + 0.15f * hoverConfirm)
				.fill(confTop, confBot)
				.build());

		sdf.flush();

		// ---- MSDF text layer ----
		float titleSize = 22f;
		MsdfFonts.draw("JUJUTSU", px + 22, py + 14, titleSize, TEXT);
		float jw = MsdfFonts.width("JUJUTSU", titleSize);
		MsdfFonts.draw("VESSEL", px + 22 + jw + 10, py + 18, 12f, withAlpha(AMBER, 0.95f));

		MsdfFonts.draw("Select a technique vessel", px + 22, py + 48, 9f, TEXT_MUTED);

		// Status pill text
		String status = selection == JujutsuCharacter.NOBARA ? "NOBARA ACTIVE" : "NO VESSEL";
		int statusCol = selection == JujutsuCharacter.NOBARA ? AMBER : SLATE;
		float statusW = MsdfFonts.width(status, 8f);
		MsdfFonts.draw(status, px + PANEL_W - 22 - statusW, py + 18, 8f, statusCol);

		drawCharacterCardText(true, px, py);
		drawCharacterCardText(false, px, py);

		// Ability strip under selection
		drawAbilityStrip(px, py);

		// Buttons labels
		MsdfFonts.drawCentered("Cancel", btnStart + cancelW * 0.5f, btnY + 8, 10f, TEXT_DIM);
		MsdfFonts.drawCentered("Confirm", btnStart + cancelW + btnGap + confirmW * 0.5f, btnY + 8, 10f, TEXT);

		// Hint
		MsdfFonts.drawCentered("N to close  ·  V opens neon dashboard", px + PANEL_W * 0.5f, py + PANEL_H - 14, 7.5f, TEXT_MUTED);

		// Portraits after SDF so they sit on cards (vanilla textured blit)
		drawPortraits(g, px, py, anim);
	}

	private void drawCharacterCardSurface(boolean nobara, float px, float py, float anim) {
		float[] r = cardRect(nobara, px, py);
		float cx = r[0];
		float cy = r[1];
		float cw = r[2];
		float ch = r[3];
		boolean selected = nobara
				? selection == JujutsuCharacter.NOBARA
				: selection == JujutsuCharacter.NONE;
		float hover = nobara ? hoverNobara : hoverNone;
		int accent = nobara ? AMBER : SLATE;

		int fillTop = selected ? 0xF01C1820 : 0xF012141C;
		int fillBot = selected ? 0xF0121018 : 0xF00C0E14;
		if (hover > 0.01f) {
			fillTop = lerpColor(fillTop, 0xF0242018, hover * 0.5f);
		}

		sdf.add(SdfShape.builder()
				.rect(cx, cy, cw, ch)
				.radius(14f)
				.border(selected ? 1.6f : 1.0f, withAlpha(accent, selected ? 0.70f : 0.18f + 0.25f * hover))
				.glow(selected ? 12f : 4f * hover, withAlpha(accent, selected ? 0.30f : 0.12f * hover))
				.highlight(0.16f + (selected ? 0.10f : 0f) + 0.08f * hover)
				.fill(fillTop, fillBot)
				.build());

		// Top accent hairline
		sdf.add(SdfShape.builder()
				.rect(cx + 14, cy + 1, cw - 28, 2f)
				.radius(1f)
				.border(0, 0)
				.glow(selected ? 6f : 0f, withAlpha(accent, 0.35f * anim))
				.fill(withAlpha(accent, selected ? 0.90f : 0.25f + 0.35f * hover), withAlpha(accent, 0.05f))
				.build());

		// Portrait frame
		float head = 48f;
		float hx = cx + (cw - head) * 0.5f;
		float hy = cy + 22f;
		sdf.add(SdfShape.builder()
				.rect(hx - 3, hy - 3, head + 6, head + 6)
				.radius((head + 6) * 0.5f)
				.border(1.4f, withAlpha(accent, selected ? 0.75f : 0.30f))
				.glow(selected ? 8f : 0f, withAlpha(accent, 0.35f))
				.fill(0xFF0A0B10, 0xFF0A0B10)
				.build());
	}

	private void drawCharacterCardText(boolean nobara, float px, float py) {
		float[] r = cardRect(nobara, px, py);
		float cx = r[0];
		float cy = r[1];
		float cw = r[2];

		String name = nobara ? "Nobara Kugisaki" : "None";
		String tech = nobara ? "Straw Doll Technique" : "No Technique";
		String grade = nobara ? "GRADE 3" : "DEFAULT";
		int accent = nobara ? AMBER : SLATE;
		boolean selected = nobara
				? selection == JujutsuCharacter.NOBARA
				: selection == JujutsuCharacter.NONE;

		MsdfFonts.drawCentered(name, cx + cw * 0.5f, cy + 82, 12f, TEXT);
		MsdfFonts.drawCentered(tech, cx + cw * 0.5f, cy + 100, 8.5f, TEXT_DIM);
		MsdfFonts.drawCentered(grade, cx + cw * 0.5f, cy + 118, 8f, accent);

		if (selected) {
			MsdfFonts.drawCentered("SELECTED", cx + cw * 0.5f, cy + 140, 8f, GOOD);
		} else {
			MsdfFonts.drawCentered("click to select", cx + cw * 0.5f, cy + 140, 7.5f, TEXT_MUTED);
		}
	}

	private void drawPortraits(GuiGraphics g, float px, float py, float anim) {
		if (anim < 0.05f) {
			return;
		}
		// Nobara head
		float[] n = cardRect(true, px, py);
		float head = 48f;
		int hx = Math.round(n[0] + (n[2] - head) * 0.5f);
		int hy = Math.round(n[1] + 22f);
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 8.0f, 8.0f, (int) head, (int) head, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, NOBARA_SKIN, hx, hy, 40.0f, 8.0f, (int) head, (int) head, 8, 8, 64, 64);

		// None bust emoji
		float[] z = cardRect(false, px, py);
		int ex = Math.round(z[0] + (z[2] - head) * 0.5f);
		int ey = Math.round(z[1] + 22f);
		g.blit(RenderPipelines.GUI_TEXTURED, EMOJI_BUST, ex, ey, 0f, 0f, (int) head, (int) head, 96, 96, 96, 96);
	}

	private static final String[] NOBARA_ABILITIES = {
			"Piercing R", "Hairpin B", "Trap Shift+R", "Hammer LMB"
	};

	private void drawAbilityChipSurfaces(float px, float py, float anim) {
		if (selection != JujutsuCharacter.NOBARA) {
			return;
		}
		float size = 8f;
		float gap = 8f;
		float chipH = 18f;
		float total = 0f;
		float[] widths = new float[NOBARA_ABILITIES.length];
		for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
			widths[i] = MsdfFonts.width(NOBARA_ABILITIES[i], size) + 16f;
			total += widths[i] + (i > 0 ? gap : 0);
		}
		float x = px + (PANEL_W - total) * 0.5f;
		float y = py + 244;
		for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
			sdf.add(SdfShape.builder()
					.rect(x, y, widths[i], chipH)
					.radius(6f)
					.border(1f, withAlpha(AMBER, 0.28f * anim))
					.glow(4f, withAlpha(AMBER, 0.12f * anim))
					.highlight(0.10f)
					.fill(0xF01A1612, 0xF012100E)
					.build());
			x += widths[i] + gap;
		}
	}

	private void drawAbilityStrip(float px, float py) {
		if (selection != JujutsuCharacter.NOBARA) {
			MsdfFonts.drawCentered(
					"Clear vessel — play as a normal survivor",
					px + PANEL_W * 0.5f,
					py + 248,
					8.5f,
					TEXT_MUTED);
			return;
		}
		float size = 8f;
		float gap = 8f;
		float chipH = 18f;
		float total = 0f;
		float[] widths = new float[NOBARA_ABILITIES.length];
		for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
			widths[i] = MsdfFonts.width(NOBARA_ABILITIES[i], size) + 16f;
			total += widths[i] + (i > 0 ? gap : 0);
		}
		float x = px + (PANEL_W - total) * 0.5f;
		float y = py + 244;
		for (int i = 0; i < NOBARA_ABILITIES.length; i++) {
			MsdfFonts.drawCentered(
					NOBARA_ABILITIES[i],
					x + widths[i] * 0.5f,
					y + (chipH - size) * 0.45f,
					size,
					withAlpha(AMBER, 0.92f));
			x += widths[i] + gap;
		}
	}

	/** Returns [x, y, w, h] for a roster card. */
	private float[] cardRect(boolean nobara, float px, float py) {
		float gap = 20f;
		float rowW = CARD_W * 2 + gap;
		float startX = px + (PANEL_W - rowW) * 0.5f;
		float cy = py + 64f;
		float cx = nobara ? startX : startX + CARD_W + gap;
		return new float[] {cx, cy, CARD_W, CARD_H};
	}

	private void updateHovers(int mx, int my, float px, float py, float partialTick) {
		float speed = 0.22f;
		float[] n = cardRect(true, px, py);
		float[] z = cardRect(false, px, py);
		hoverNobara = approach(hoverNobara, hit(mx, my, n) ? 1f : 0f, speed);
		hoverNone = approach(hoverNone, hit(mx, my, z) ? 1f : 0f, speed);

		float btnY = py + PANEL_H - 46;
		float btnH = 28f;
		float cancelW = 110f;
		float confirmW = 140f;
		float btnGap = 10f;
		float totalBtn = cancelW + confirmW + btnGap;
		float btnStart = px + (PANEL_W - totalBtn) * 0.5f;
		hoverCancel = approach(hoverCancel, hit(mx, my, btnStart, btnY, cancelW, btnH) ? 1f : 0f, speed);
		hoverConfirm = approach(
				hoverConfirm,
				hit(mx, my, btnStart + cancelW + btnGap, btnY, confirmW, btnH) ? 1f : 0f,
				speed);
	}

	private static boolean hit(double mx, double my, float[] r) {
		return hit(mx, my, r[0], r[1], r[2], r[3]);
	}

	private static boolean hit(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private static float approach(float current, float target, float t) {
		return current + (target - current) * UiEase.clamp01(t);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Custom scrim in render(); skip vanilla dirt/blur layers.
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || openAnim < 0.85f) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		float px = panelX;
		float py = panelY;
		float[] n = cardRect(true, px, py);
		float[] z = cardRect(false, px, py);
		if (hit(mouseX, mouseY, n)) {
			selection = JujutsuCharacter.NOBARA;
			return true;
		}
		if (hit(mouseX, mouseY, z)) {
			selection = JujutsuCharacter.NONE;
			return true;
		}

		float btnY = py + PANEL_H - 46;
		float btnH = 28f;
		float cancelW = 110f;
		float confirmW = 140f;
		float btnGap = 10f;
		float totalBtn = cancelW + confirmW + btnGap;
		float btnStart = px + (PANEL_W - totalBtn) * 0.5f;
		if (hit(mouseX, mouseY, btnStart, btnY, cancelW, btnH)) {
			animateClose();
			return true;
		}
		if (hit(mouseX, mouseY, btnStart + cancelW + btnGap, btnY, confirmW, btnH)) {
			confirm();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void confirm() {
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

	private static int withAlpha(int argb, float alpha) {
		int a = Math.round(UiEase.clamp01(alpha) * ((argb >>> 24) & 0xFF));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private static int lerpColor(int a, int b, float t) {
		t = UiEase.clamp01(t);
		int aa = (a >>> 24) & 0xFF;
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}
}
