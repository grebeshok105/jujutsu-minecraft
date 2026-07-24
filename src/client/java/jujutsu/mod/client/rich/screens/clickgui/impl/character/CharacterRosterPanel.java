package jujutsu.mod.client.rich.screens.clickgui.impl.character;

import java.util.Arrays;
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

/** Character selection surface shared by every current playable vessel. */
public final class CharacterRosterPanel {
	private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
	private static final ResourceLocation EMOJI_BUST = JujutsuMod.id("textures/gui/dashboard/emoji_bust.png");
	private static final ResourceLocation EMOJI_FIST = JujutsuMod.id("textures/gui/dashboard/emoji_fist.png");
	private static final ResourceLocation EMOJI_PIN = JujutsuMod.id("textures/gui/dashboard/emoji_pin.png");
	private static final ResourceLocation EMOJI_BOOM = JujutsuMod.id("textures/gui/dashboard/emoji_boom.png");
	private static final ResourceLocation EMOJI_LINK = JujutsuMod.id("textures/gui/dashboard/emoji_link.png");
	private static final ResourceLocation EMOJI_BOLT = JujutsuMod.id("textures/gui/dashboard/emoji_bolt.png");
	private static final CharacterCard[] CARDS = {
			new CharacterCard(JujutsuCharacter.NOBARA, "Nobara Kugisaki", "Straw Doll", "Grade 3", NOBARA_SKIN, true,
					new ResourceLocation[] {EMOJI_PIN, EMOJI_BOOM, EMOJI_LINK, EMOJI_BOLT},
					new String[] {"Piercing", "Enlarge", "Resonance", "Boom"}, new String[] {"R", "B", "⇧R", "LMB"}),
			new CharacterCard(JujutsuCharacter.TODO, "Aoi Todo", "Boogie Woogie", "Heavy Melee", EMOJI_FIST, false,
					new ResourceLocation[] {EMOJI_FIST}, new String[] {"Boogie Woogie"}, new String[] {"R"}),
			new CharacterCard(JujutsuCharacter.NONE, "None", "No Technique", "Default", EMOJI_BUST, false,
					new ResourceLocation[0], new String[0], new String[0])
	};

	private JujutsuCharacter preview = JujutsuCharacter.NONE;
	private final float[] hover = new float[CARDS.length];
	private final float[] select = new float[CARDS.length];
	private float confirmHover;
	private float stripReveal;
	private float openProgress;
	private float lastPanelX;
	private float lastPanelY;
	private float lastPanelW;
	private float lastPanelH;
	private float lastCardH;
	private float lastGap;

	public CharacterRosterPanel() {
		syncFromClient();
	}

	public void syncFromClient() {
		Minecraft minecraft = Minecraft.getInstance();
		preview = minecraft.player == null ? JujutsuCharacter.NONE : ClientCharacterSelectionManager.characterOrNone(minecraft.player.getUUID());
		ClickGuiTheme.snapTo(preview);
		for (int index = 0; index < CARDS.length; index++) {
			select[index] = CARDS[index].character() == preview ? 1.0f : 0.0f;
		}
		stripReveal = abilitiesFor(preview).labels().length == 0 ? 0.0f : 1.0f;
		openProgress = 0.0f;
		if (lastPanelW <= 1.0f) {
			lastPanelW = 298.0f;
			lastPanelH = 204.0f;
			lastCardH = 110.0f;
			lastGap = 6.0f;
		}
	}

	public void tick(float deltaTicks) {
		openProgress = UiEase.approach(openProgress, 1.0f, 0.28f, deltaTicks);
		for (int index = 0; index < CARDS.length; index++) {
			select[index] = UiEase.approach(select[index], CARDS[index].character() == preview ? 1.0f : 0.0f, 0.28f, deltaTicks);
		}
		stripReveal = UiEase.approach(stripReveal, abilitiesFor(preview).labels().length == 0 ? 0.0f : 1.0f, 0.24f, deltaTicks);
	}

	public void updateHover(float mouseX, float mouseY) {
		for (int index = 0; index < CARDS.length; index++) {
			hover[index] = UiEase.approach(hover[index], hit(mouseX, mouseY, cardBounds(index)) ? 1.0f : 0.0f, 0.35f, 1.0f);
		}
		confirmHover = UiEase.approach(confirmHover, hit(mouseX, mouseY, confirmBounds()) ? 1.0f : 0.0f, 0.35f, 1.0f);
	}

	public void render(GuiGraphics graphics, float x, float y, float width, float height, float mouseX, float mouseY, float alpha) {
		lastPanelX = x;
		lastPanelY = y;
		lastPanelW = width;
		lastPanelH = height;
		lastGap = 6.0f;
		lastCardH = Math.min(118.0f, height - 78.0f);
		updateHover(mouseX, mouseY);

		float entry = UiEase.outCubic(openProgress);
		float yLift = (1.0f - entry) * 10.0f;
		float visibleAlpha = alpha * entry;
		Render2D.rect(x, y + yLift, width, height, withAlpha(ClickGuiTheme.raised(40), visibleAlpha), 8.0f);
		Render2D.outline(x, y + yLift, width, height, 0.6f, withAlpha(ClickGuiTheme.outline(200), visibleAlpha), 8.0f);

		for (int index = 0; index < CARDS.length; index++) {
			renderCard(graphics, index, CARDS[index], visibleAlpha, yLift);
		}
		renderAbilityStrip(graphics, visibleAlpha, yLift);
		renderConfirm(graphics, visibleAlpha, yLift);
	}

	private void renderCard(GuiGraphics graphics, int index, CharacterCard card, float alpha, float yLift) {
		float[] bounds = cardBounds(index);
		float cardX = bounds[0];
		float cardY = bounds[1] + yLift;
		float cardW = bounds[2];
		float cardH = bounds[3];
		float pop = 1.0f + 0.025f * hover[index] + 0.02f * select[index];
		float drawW = cardW * pop;
		float drawH = cardH * pop;
		float drawX = cardX - (drawW - cardW) * 0.5f;
		float drawY = cardY - (drawH - cardH) * 0.5f;
		int accent = ClickGuiTheme.accentFor(card.character());
		int fill = mix(0xFF1C1C1C, accent, 0.08f + 0.22f * select[index] + 0.10f * hover[index]);
		int border = mix(0xFF373737, accent, 0.35f + 0.55f * select[index] + 0.20f * hover[index]);
		Render2D.rect(drawX, drawY, drawW, drawH, withAlpha(fill, alpha), 9.0f);
		Render2D.outline(drawX, drawY, drawW, drawH, 1.0f + 0.5f * select[index], withAlpha(border, alpha), 9.0f);

		float well = 42.0f;
		float wellX = drawX + 8.0f;
		float wellY = drawY + 10.0f;
		Render2D.rect(wellX, wellY, well, well, withAlpha(0xFF121212, alpha), 8.0f);
		Render2D.outline(wellX, wellY, well, well, 0.8f, withAlpha(mix(0xFF2A2A2A, accent, 0.5f + 0.5f * select[index]), alpha), 8.0f);
		int head = 34;
		int headX = Math.round(wellX + (well - head) * 0.5f);
		int headY = Math.round(wellY + (well - head) * 0.5f);
		if (card.skin()) {
			drawSkinHead(graphics, card.portrait(), headX, headY, head, alpha);
		} else {
			drawEmoji(graphics, card.portrait(), headX, headY, head, alpha);
		}

		float textX = wellX + well + 7.0f;
		Fonts.BOLD.draw(card.name(), textX, wellY + 5.0f, 6.0f, withAlpha(0xFFFFFFFF, alpha));
		Fonts.BOLD.draw(card.technique(), textX, wellY + 16.0f, 4.7f, withAlpha(mix(0xFFAAAAAA, accent, 0.4f + 0.5f * select[index]), alpha));
		Fonts.BOLD.draw(card.grade(), textX, wellY + 27.0f, 4.2f, withAlpha(0xFF888888, alpha));

		String pill = select[index] > 0.55f ? "SELECTED" : (hover[index] > 0.55f ? "HOVER" : "READY");
		float pillW = Fonts.BOLD.getWidth(pill, 4.0f) + 7.0f;
		float pillX = drawX + drawW - pillW - 6.0f;
		float pillY = drawY + drawH - 15.0f;
		Render2D.rect(pillX, pillY, pillW, 9.0f, withAlpha(mix(0xFF202020, accent, 0.35f + 0.5f * select[index]), alpha), 4.0f);
		Fonts.BOLD.draw(pill, pillX + 3.5f, pillY + 2.2f, 4.0f, withAlpha(mix(0xFFCCCCCC, accent, select[index]), alpha));
	}

	private void renderAbilityStrip(GuiGraphics graphics, float alpha, float yLift) {
		CharacterCard card = abilitiesFor(preview);
		if (stripReveal < 0.02f || card.labels().length == 0) {
			return;
		}
		float visibleAlpha = alpha * stripReveal;
		float[] confirm = confirmBounds();
		float stripH = 34.0f;
		float stripY = confirm[1] - stripH - 8.0f + yLift;
		float stripX = lastPanelX + 10.0f;
		float stripW = lastPanelW - 20.0f;
		Render2D.rect(stripX, stripY, stripW, stripH, withAlpha(ClickGuiTheme.raised(50), visibleAlpha), 7.0f);
		Render2D.outline(stripX, stripY, stripW, stripH, 0.6f, withAlpha(ClickGuiTheme.accent(140), visibleAlpha), 7.0f);

		float gap = 6.0f;
		int count = card.labels().length;
		float cellW = (stripW - gap * (count - 1)) / count;
		for (int index = 0; index < count; index++) {
			float cellX = stripX + index * (cellW + gap);
			Render2D.rect(cellX + 1.0f, stripY + 3.0f, cellW - 2.0f, stripH - 6.0f, withAlpha(0xFF161616, visibleAlpha), 5.0f);
			int icon = 14;
			drawEmoji(graphics, card.abilityIcons()[index], Math.round(cellX + 5.0f), Math.round(stripY + 9.0f), icon, visibleAlpha);
			Fonts.BOLD.draw(card.labels()[index], cellX + 22.0f, stripY + 8.0f, 4.5f, withAlpha(0xFFE0E0E0, visibleAlpha));
			Fonts.BOLD.draw(card.abilityKeys()[index], cellX + 22.0f, stripY + 18.0f, 4.0f, withAlpha(ClickGuiTheme.accent(200), visibleAlpha));
		}
	}

	private void renderConfirm(GuiGraphics graphics, float alpha, float yLift) {
		float[] bounds = confirmBounds();
		float x = bounds[0];
		float y = bounds[1] + yLift;
		float width = bounds[2];
		float height = bounds[3];
		float pop = 1.0f + 0.03f * confirmHover;
		float drawW = width * pop;
		float drawH = height * pop;
		float drawX = x - (drawW - width) * 0.5f;
		float drawY = y - (drawH - height) * 0.5f;
		Render2D.rect(drawX, drawY, drawW, drawH, withAlpha(mix(0xFF2A2118, ClickGuiTheme.accentFull(), 0.55f + 0.35f * confirmHover), alpha), 7.0f);
		Render2D.outline(drawX, drawY, drawW, drawH, 1.0f, withAlpha(ClickGuiTheme.accent(220), alpha), 7.0f);
		String label = "Confirm vessel";
		float textWidth = Fonts.BOLD.getWidth(label, 6.5f);
		Fonts.BOLD.draw(label, drawX + (drawW - textWidth) * 0.5f, drawY + 8.0f, 6.5f, withAlpha(0xFFFFFFFF, alpha));
	}

	public boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (button != 0) {
			return false;
		}
		for (int index = 0; index < CARDS.length; index++) {
			if (hit(mouseX, mouseY, cardBounds(index))) {
				selectPreview(CARDS[index].character());
				return true;
			}
		}
		if (hit(mouseX, mouseY, confirmBounds())) {
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
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			PlayerSkin.Model model = preview.modelId().equals("slim") ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
			ClientCharacterSelectionManager.applyLocal(minecraft.player.getUUID(), preview, model);
		}
		if (ClientPlayNetworking.canSend(SelectCharacterPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectCharacterPayload(preview.id()));
		}
		ClickGuiTheme.setCharacter(preview);
	}

	private CharacterCard abilitiesFor(JujutsuCharacter character) {
		return Arrays.stream(CARDS).filter(card -> card.character() == character).findFirst().orElse(CARDS[CARDS.length - 1]);
	}

	private float[] cardBounds(int index) {
		float pad = 10.0f;
		float gap = lastGap > 0.0f ? lastGap : 6.0f;
		float cardW = (lastPanelW - pad * 2.0f - gap * (CARDS.length - 1)) / CARDS.length;
		float cardH = lastCardH > 0.0f ? lastCardH : 110.0f;
		return new float[] {lastPanelX + pad + index * (cardW + gap), lastPanelY + pad, cardW, cardH};
	}

	private float[] confirmBounds() {
		float pad = 10.0f;
		float height = 24.0f;
		return new float[] {lastPanelX + pad, lastPanelY + lastPanelH - pad - height, lastPanelW - pad * 2.0f, height};
	}

	private static boolean hit(float mouseX, float mouseY, float[] bounds) {
		return mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3];
	}

	private static void drawSkinHead(GuiGraphics graphics, ResourceLocation skin, int x, int y, int size, float alpha) {
		if (alpha < 0.05f) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
	}

	private static void drawEmoji(GuiGraphics graphics, ResourceLocation icon, int x, int y, int size, float alpha) {
		if (alpha >= 0.05f) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, 96, 96, 96, 96);
		}
	}

	private static int withAlpha(int argb, float alpha) {
		int channel = Math.round(((argb >>> 24) & 0xFF) * UiEase.clamp01(alpha));
		return (channel << 24) | (argb & 0x00FFFFFF);
	}

	private static int mix(int base, int accent, float t) {
		t = UiEase.clamp01(t);
		int baseR = (base >> 16) & 0xFF;
		int baseG = (base >> 8) & 0xFF;
		int baseB = base & 0xFF;
		int accentR = (accent >> 16) & 0xFF;
		int accentG = (accent >> 8) & 0xFF;
		int accentB = accent & 0xFF;
		int red = Math.round(baseR + (accentR - baseR) * t);
		int green = Math.round(baseG + (accentG - baseG) * t);
		int blue = Math.round(baseB + (accentB - baseB) * t);
		return (((base >>> 24) & 0xFF) << 24) | (red << 16) | (green << 8) | blue;
	}

	private record CharacterCard(
			JujutsuCharacter character,
			String name,
			String technique,
			String grade,
			ResourceLocation portrait,
			boolean skin,
			ResourceLocation[] abilityIcons,
			String[] labels,
			String[] abilityKeys
	) {}
}
