package jujutsu.mod.client.rich.util;

/** Minimal ColorUtil API used by Render2D (ported from Rich). */
public final class ColorUtil {
	private ColorUtil() {}

	public static int[] solid(int color) {
		return new int[] {color, color, color, color};
	}

	public static int[] solid8(int color) {
		return new int[] {color, color, color, color, color, color, color, color};
	}

	public static int[] solid9(int color) {
		return new int[] {color, color, color, color, color, color, color, color, color};
	}

	public static int multAlpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * alpha);
		return (a << 24) | (color & 0x00FFFFFF);
	}
}
