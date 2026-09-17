package jujutsu.mod.client.character.megumi.selector;

/**
 * The quick-selector's palette and durations, in one place so the strip, the slot chrome and the
 * audio layer cannot drift apart.
 *
 * <p>The accent direction is the design spec's: deep dark red with a glossy caramel warmth — dark
 * rather than neon, rich rather than flat burgundy. Every accent stays in the red band (R &gt; G &gt; B)
 * and below full brightness, which is what keeps the strip from reading as a warning light.
 *
 * <p>The four durations are the spec's motion budget: the entrance/exit presentation must stay around
 * 100–150 ms so animation never delays a click, and hover/select feedback must be short enough to be
 * interruptible mid-selection.
 */
public final class SelectorTheme {
	/** Deep dark red — the strip's structural accent: frames, rules, the active slot's outline. */
	public static final int ACCENT_DEEP = 0xFF6B1414;
	/** Caramel-warm highlight — gloss bands and the active slot's inner glow. */
	public static final int ACCENT_GLOW = 0xFF9E2B1E;
	/** Brighter warm response — the hovered slot's frame. */
	public static final int ACCENT_HOVER = 0xFFC25A2E;
	/** Near-black warm panel behind the strip. */
	public static final int PANEL_FILL = 0xC0140B09;
	/** Slot plate fill, a shade lighter than the panel. */
	public static final int SLOT_FILL = 0x991C1009;
	/** Names and the strip's title at full strength. */
	public static final int TEXT_PRIMARY = 0xFFF3E4D8;
	/** Unavailable names, secondary labels, the strip's title. */
	public static final int TEXT_MUTED = 0xFF9A8578;
	/** Already-summoned marker hue — deliberately outside the red band so it never reads as an accent. */
	public static final int STATE_SUMMONED = 0xFF7FB069;
	/** Cooldown marker and wipe — desaturated slate, the one cold value in the palette. */
	public static final int STATE_COOLDOWN = 0xFF8A8F98;
	/** Not-yet-unlocked marker: dimmer slate, reads as "there is nothing here yet". */
	public static final int STATE_LOCKED = 0xFF5A5F6A;
	/** Permanently destroyed marker: near-black, present but spent. */
	public static final int STATE_DESTROYED = 0xFF3A3A3A;
	/** Entrance presentation, milliseconds. Kept at the low end of the spec's 100–150 ms budget. */
	public static final float OPEN_MS = 120f;
	/** Exit presentation, milliseconds. Shorter than the entrance — leaving should never lag. */
	public static final float CLOSE_MS = 100f;
	/** Hover approach, milliseconds. */
	public static final float HOVER_MS = 80f;
	/** Select pulse, milliseconds. The reject shake's window is {@code SelectorMotion.SHAKE_MS}. */
	public static final float SELECT_MS = 140f;

	private SelectorTheme() {}

	/** The same colour with its alpha scaled by {@code factor}, clamped to 0..1. */
	public static int withAlpha(int argb, float factor) {
		float clamped = Math.max(0.0f, Math.min(1.0f, factor));
		int alpha = Math.round(((argb >>> 24) & 0xFF) * clamped);
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** Channel-wise blend of two colours, alpha included. */
	public static int lerp(int from, int to, float t) {
		float clamped = Math.max(0.0f, Math.min(1.0f, t));
		int a = lerpChannel(from >>> 24, to >>> 24, clamped);
		int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, clamped);
		int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, clamped);
		int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerpChannel(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}
}
