package jujutsu.mod.client.rich.screens.clickgui.impl.character;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.rich.util.render.Render2D;
import jujutsu.mod.client.rich.util.render.font.Fonts;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.network.SelectCharacterPayload;

/**
 * Characters tab body: Nobara + None cards with head/icons, smooth selection, themed accents.
 */
public final class CharacterRosterPanel {
	private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
	private static final ResourceLocation EMOJI_BUST = JujutsuMod.id("textures/gui/dashboard/emoji_bust.png");
	private static final ResourceLocation[] ABILITY_ICONS = {
			JujutsuMod.id("textures/gui/dashboard/emoji_pin.png"),
			JujutsuMod.id("textures/gui/dashboard/emoji_boom.png"),
			JujutsuMod.id("textures/gui/dashboard/emoji_link.png"),
			JujutsuMod.id("textures/gui/dashboard/emoji_bolt.png"),
	};
	private static final String[] ABILITY_KEYS = {"R", "B", "⇧R", "LMB"};
	private static final String[] ABILITY_LABELS = {"Piercing", "Enlarge", "Resonance", "Boom"};

	private JujutsuCharacter preview = JujutsuCharacter.NONE;
	private float nobaraHover;
	private float noneHover;
	private float nobaraSelect;
	private float noneSelect;
	private float confirmHover;
	private float stripReveal;
	private float openProgress;
	private float lastPanelX, lastPanelY, lastPanelW, lastPanelH;
	private float lastCardH;
	private float lastGap;

	public CharacterRosterPanel() {
		syncFromClient();
	}

	public void syncFromClient() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			preview = ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
		} else {
			preview = JujutsuCharacter.NONE;
		}
		ClickGuiTheme.snapTo(preview);
		nobaraSelect = preview == JujutsuCharacter.NOBARA ? 1f : 0f;
		noneSelect = preview == JujutsuCharacter.NONE ? 1f : 0f;
		stripReveal = preview == JujutsuCharacter.NOBARA ? 1f : 0f;
		openProgress = 0f;
		// Defaults so clicks work before the first render pass.
		if (lastPanelW <= 1f) {
			lastPanelW = 298f;
			lastPanelH = 204f;
			lastCardH = 110f;
			lastGap = 8f;
		}
	}

	public void tick(float deltaTicks) {
		openProgress = UiEase.approach(openProgress, 1f, 0.28f, deltaTicks);
		nobaraSelect = UiEase.approach(nobaraSelect, preview == JujutsuCharacter.NOBARA ? 1f : 0f, 0.28f, deltaTicks);
		noneSelect = UiEase.approach(noneSelect, preview == JujutsuCharacter.NONE ? 1f : 0f, 0.28f, deltaTicks);
		stripReveal = UiEase.approach(stripReveal, preview == JujutsuCharacter.NOBARA ? 1f : 0f, 0.24f, deltaTicks);
	}

	public void updateHover(float mx, float my) {
		float[] nb = cardBounds(0);
		float[] nn = cardBounds(1);
		float[] conf = confirmBounds();
		nobaraHover = UiEase.approach(nobaraHover, hit(mx, my, nb) ? 1f : 0f, 0.35f, 1f);
		noneHover = UiEase.approach(noneHover, hit(mx, my, nn) ? 1f : 0f, 0.35f, 1f);
		confirmHover = UiEase.approach(confirmHover, hit(mx, my, conf) ? 1f : 0f, 0.35f, 1f);
	}

	public void render(GuiGraphics g, float x, float y, float w, float h, float mx, float my, float alpha) {
		lastPanelX = x;
		lastPanelY = y;
		lastPanelW = w;
		lastPanelH = h;
		lastGap = 8f;
		lastCardH = Math.min(118f, h - 78f);

		updateHover(mx, my);

		float entry = UiEase.outCubic(openProgress);
		float yLift = (1f - entry) * 10f;
		float a = alpha * entry;

		// Outer content plate
		int plateA = (int) (18 * a * 255 / 255f);
		Render2D.rect(x, y + yLift, w, h, withAlpha(ClickGuiTheme.raised(40), a), 8f);
		Render2D.outline(x, y + yLift, w, h, 0.6f, withAlpha(ClickGuiTheme.outline(200), a), 8f);

		renderCard(g, 0, JujutsuCharacter.NOBARA, "Nobara Kugisaki", "Straw Doll", "Grade 3",
				nobaraHover, nobaraSelect, a, yLift, true);
		renderCard(g, 1, JujutsuCharacter.NONE, "None", "No Technique", "Default",
				noneHover, noneSelect, a, yLift, false);

		renderAbilityStrip(g, a, yLift);
		renderConfirm(g, a, yLift);
	}

	private void renderCard(GuiGraphics g, int index, JujutsuCharacter character,
			String name, String tech, String grade,
			float hover, float select, float alpha, float yLift, boolean skin) {
		float[] b = cardBounds(index);
		float cx = b[0], cy = b[1] + yLift, cw = b[2], ch = b[3];

		float pop = 1f + 0.025f * hover + 0.02f * select;
		float dw = cw * pop;
		float dh = ch * pop;
		float dx = cx - (dw - cw) * 0.5f;
		float dy = cy - (dh - ch) * 0.5f;

		int accent = character == JujutsuCharacter.NOBARA ? ClickGuiTheme.NOBARA_ACCENT : ClickGuiTheme.NONE_ACCENT;
		int fill = mix(0xFF1C1C1C, accent, 0.08f + 0.22f * select + 0.10f * hover);
		int border = mix(0xFF373737, accent, 0.35f + 0.55f * select + 0.20f * hover);

		Render2D.rect(dx, dy, dw, dh, withAlpha(fill, alpha), 9f);
		Render2D.outline(dx, dy, dw, dh, 1f + 0.5f * select, withAlpha(border, alpha), 9f);

		// Portrait well
		float well = 46f;
		float wx = dx + 10f;
		float wy = dy + 12f;
		Render2D.rect(wx, wy, well, well, withAlpha(0xFF121212, alpha), 8f);
		Render2D.outline(wx, wy, well, well, 0.8f, withAlpha(mix(0xFF2A2A2A, accent, 0.5f + 0.5f * select), alpha), 8f);

		// Soft accent ring when selected
		if (select > 0.02f) {
			Render2D.outline(wx - 1.5f, wy - 1.5f, well + 3f, well + 3f, 1.2f,
					withAlpha(mix(0x00000000, accent, select), alpha * 0.85f), 10f);
		}

		int head = 38;
		int hx = Math.round(wx + (well - head) * 0.5f);
		int hy = Math.round(wy + (well - head) * 0.5f);
		if (skin) {
			drawSkinHead(g, NOBARA_SKIN, hx, hy, head, alpha);
		} else {
			drawEmoji(g, EMOJI_BUST, hx, hy, head, alpha);
		}

		float textX = wx + well + 10f;
		float textW = dx + dw - textX - 8f;
		int nameA = (int) (235 * alpha);
		int dimA = (int) (160 * alpha);
		Fonts.BOLD.draw(name, textX, wy + 6f, 7f, withAlpha(0xFFFFFFFF, alpha));
		Fonts.BOLD.draw(tech, textX, wy + 18f, 5.5f, withAlpha(mix(0xFFAAAAAA, accent, 0.4f + 0.5f * select), alpha));
		Fonts.BOLD.draw(grade, textX, wy + 30f, 5f, withAlpha(0xFF888888, alpha));

		// Status pill
		String pill = select > 0.55f ? "SELECTED" : (hover > 0.55f ? "HOVER" : "READY");
		float pillW = Fonts.BOLD.getWidth(pill, 4.5f) + 8f;
		float pillX = dx + dw - pillW - 8f;
		float pillY = dy + dh - 16f;
		Render2D.rect(pillX, pillY, pillW, 10f, withAlpha(mix(0xFF202020, accent, 0.35f + 0.5f * select), alpha), 4f);
		Fonts.BOLD.draw(pill, pillX + 4f, pillY + 2.5f, 4.5f, withAlpha(mix(0xFFCCCCCC, accent, select), alpha));

		// Keep text width used for potential future clipping
		if (textW < 0) {
			// no-op
		}
		if (nameA + dimA == 0) {
			// no-op
		}
	}

	private void renderAbilityStrip(GuiGraphics g, float alpha, float yLift) {
		if (stripReveal < 0.02f) return;
		float a = alpha * stripReveal;
		float[] conf = confirmBounds();
		float stripH = 34f;
		float stripY = conf[1] - stripH - 8f + yLift;
		float stripX = lastPanelX + 10f;
		float stripW = lastPanelW - 20f;

		Render2D.rect(stripX, stripY, stripW, stripH, withAlpha(ClickGuiTheme.raised(50), a), 7f);
		Render2D.outline(stripX, stripY, stripW, stripH, 0.6f, withAlpha(ClickGuiTheme.accent(140), a), 7f);

		float gap = 6f;
		float cellW = (stripW - gap * 3) / 4f;
		for (int i = 0; i < 4; i++) {
			float cx = stripX + i * (cellW + gap);
			Render2D.rect(cx + 1, stripY + 3, cellW - 2, stripH - 6, withAlpha(0xFF161616, a), 5f);
			int icon = 14;
			int ix = Math.round(cx + 5);
			int iy = Math.round(stripY + (stripH - icon) * 0.5f - 1);
			drawEmoji(g, ABILITY_ICONS[i], ix, iy, icon, a);
			Fonts.BOLD.draw(ABILITY_LABELS[i], cx + 22f, stripY + 8f, 4.5f, withAlpha(0xFFE0E0E0, a));
			Fonts.BOLD.draw(ABILITY_KEYS[i], cx + 22f, stripY + 18f, 4f, withAlpha(ClickGuiTheme.accent(200), a));
		}
	}

	private void renderConfirm(GuiGraphics g, float alpha, float yLift) {
		float[] b = confirmBounds();
		float x = b[0], y = b[1] + yLift, w = b[2], h = b[3];
		float pop = 1f + 0.03f * confirmHover;
		float dw = w * pop;
		float dh = h * pop;
		float dx = x - (dw - w) * 0.5f;
		float dy = y - (dh - h) * 0.5f;

		int fill = mix(0xFF2A2118, ClickGuiTheme.accentFull(), 0.55f + 0.35f * confirmHover);
		int border = ClickGuiTheme.accent(220);
		Render2D.rect(dx, dy, dw, dh, withAlpha(fill, alpha), 7f);
		Render2D.outline(dx, dy, dw, dh, 1f, withAlpha(border, alpha), 7f);

		String label = "Confirm vessel";
		float tw = Fonts.BOLD.getWidth(label, 6.5f);
		Fonts.BOLD.draw(label, dx + (dw - tw) * 0.5f, dy + 8f, 6.5f, withAlpha(0xFFFFFFFF, alpha));
	}

	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		if (hit(mx, my, cardBounds(0))) {
			selectPreview(JujutsuCharacter.NOBARA);
			return true;
		}
		if (hit(mx, my, cardBounds(1))) {
			selectPreview(JujutsuCharacter.NONE);
			return true;
		}
		if (hit(mx, my, confirmBounds())) {
			applySelection();
			return true;
		}
		return false;
	}

	private void selectPreview(JujutsuCharacter character) {
		preview = character;
		ClickGuiTheme.setCharacter(character);
	}

	private void applySelection() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			PlayerSkin.Model model = preview.modelId().equals("slim") ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
			ClientCharacterSelectionManager.applyLocal(mc.player.getUUID(), preview, model);
		}
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(preview.id()));
		}
		ClickGuiTheme.setCharacter(preview);
	}

	private float[] cardBounds(int index) {
		float pad = 10f;
		float gap = lastGap > 0 ? lastGap : 8f;
		float cardW = (lastPanelW - pad * 2 - gap) * 0.5f;
		float cardH = lastCardH > 0 ? lastCardH : 110f;
		float x = lastPanelX + pad + index * (cardW + gap);
		float y = lastPanelY + pad;
		return new float[] {x, y, cardW, cardH};
	}

	private float[] confirmBounds() {
		float pad = 10f;
		float h = 24f;
		float w = lastPanelW - pad * 2;
		float x = lastPanelX + pad;
		float y = lastPanelY + lastPanelH - pad - h;
		return new float[] {x, y, w, h};
	}

	private static boolean hit(float mx, float my, float[] b) {
		return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
	}

	private static void drawSkinHead(GuiGraphics g, ResourceLocation skin, int x, int y, int size, float alpha) {
		if (alpha < 0.05f) return;
		// Base head + hat layer (standard 64x64 skin UVs).
		g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
	}

	private static void drawEmoji(GuiGraphics g, ResourceLocation icon, int x, int y, int size, float alpha) {
		if (alpha < 0.05f) return;
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, size, size, 96, 96, 96, 96);
	}

	private static int withAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * UiEase.clamp01(alpha));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private static int mix(int base, int accent, float t) {
		t = UiEase.clamp01(t);
		int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
		int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
		int r = Math.round(br + (ar - br) * t);
		int g = Math.round(bg + (ag - bg) * t);
		int b = Math.round(bb + (ab - bb) * t);
		int aa = (base >>> 24) & 0xFF;
		return (aa << 24) | (r << 16) | (g << 8) | b;
	}
}
