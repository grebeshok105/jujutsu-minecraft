package jujutsu.mod.client.rich.util.render.font;

import jujutsu.mod.client.ui.msdf.MsdfFonts;

public final class FontInitializer {
	private static boolean initialized;

	private FontInitializer() {}

	public static void register() {
		// warm happens on first draw / Manager.init
	}

	public static void warm() {
		if (initialized) return;
		MsdfFonts.bootstrap();
		MsdfFonts.warm();
		initialized = true;
	}

	public static boolean isInitialized() {
		return initialized;
	}
}
