package jujutsu.mod.client.rich.theme;

import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.ui.UiEase;

/**
 * Smooth accent / surface palette for the ClickGui shell.
 *
 * <p>Which colour belongs to which vessel is the vessel's business — this file owns only the easing.
 */
public final class ClickGuiTheme {
	private static float accentR = 0.48f, accentG = 0.53f, accentB = 0.59f;
	private static float targetR = accentR, targetG = accentG, targetB = accentB;
	private static float warm = 0f;
	private static float targetWarm = 0f;

	private ClickGuiTheme() {}

	public static void setCharacter(JujutsuCharacter character) {
		CharacterClientDefinition definition = JujutsuCharacterClients.definition(character);
		int accent = definition.accent();
		targetR = ((accent >> 16) & 0xFF) / 255f;
		targetG = ((accent >> 8) & 0xFF) / 255f;
		targetB = (accent & 0xFF) / 255f;
		targetWarm = definition.warmth();
	}

	public static int accentFor(JujutsuCharacter character) {
		return JujutsuCharacterClients.definition(character).accent();
	}

	public static void snapTo(JujutsuCharacter character) {
		setCharacter(character);
		accentR = targetR;
		accentG = targetG;
		accentB = targetB;
		warm = targetWarm;
	}

	/** Call each frame from ClickGui. deltaTicks ≈ frame time in 50ms units. */
	public static void tick(float deltaTicks) {
		accentR = UiEase.approach(accentR, targetR, 0.22f, deltaTicks);
		accentG = UiEase.approach(accentG, targetG, 0.22f, deltaTicks);
		accentB = UiEase.approach(accentB, targetB, 0.22f, deltaTicks);
		warm = UiEase.approach(warm, targetWarm, 0.18f, deltaTicks);
	}

	public static int accent(int alpha) {
		int a = Math.max(0, Math.min(255, alpha));
		int r = Math.round(accentR * 255f);
		int g = Math.round(accentG * 255f);
		int b = Math.round(accentB * 255f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int accentFull() {
		return accent(255);
	}

	public static float warm() {
		return warm;
	}

	/** Dark panel top tinted by accent. */
	public static int panelTop(int alpha) {
		return mixRgb(0x1A1A1A, accentFull(), 0.08f + 0.10f * warm, alpha);
	}

	public static int panelBottom(int alpha) {
		return mixRgb(0x0A0A0A, accentFull(), 0.05f + 0.08f * warm, alpha);
	}

	public static int outline(int alpha) {
		return mixRgb(0x373737, accentFull(), 0.25f + 0.35f * warm, alpha);
	}

	public static int raised(int alpha) {
		return mixRgb(0x2C2C2C, accentFull(), 0.12f + 0.18f * warm, alpha);
	}

	private static int mixRgb(int baseRgb, int accentArgb, float t, int alpha) {
		t = UiEase.clamp01(t);
		int br = (baseRgb >> 16) & 0xFF, bg = (baseRgb >> 8) & 0xFF, bb = baseRgb & 0xFF;
		int ar = (accentArgb >> 16) & 0xFF, ag = (accentArgb >> 8) & 0xFF, ab = accentArgb & 0xFF;
		int r = Math.round(br + (ar - br) * t);
		int g = Math.round(bg + (ag - bg) * t);
		int b = Math.round(bb + (ab - bb) * t);
		return (Math.max(0, Math.min(255, alpha)) << 24) | (r << 16) | (g << 8) | b;
	}
}
