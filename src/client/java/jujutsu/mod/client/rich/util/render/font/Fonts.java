package jujutsu.mod.client.rich.util.render.font;

import java.util.LinkedHashMap;
import java.util.Map;
import jujutsu.mod.client.ui.msdf.MsdfFonts;

/**
 * Rich Fonts registry — same names, backed by jujutsumod MSDF atlases.
 */
public final class Fonts {
	private static final Map<String, String> FONT_REGISTRY = new LinkedHashMap<>();

	public static final Font BOLD = register("bold", "bold");
	public static final Font ICONS = register("icons", "guiicons");
	public static final Font ICONSTYPETHO = register("iconstypetho", "guiicons");
	public static final Font GUI_ICONS = register("guiicons", "guiicons");
	public static final Font HUD_ICONS = register("hudicons", "guiicons");
	public static final Font CATEGORY_ICONS = register("categoryicons", "categoryicons");
	public static final Font DEFAULT = register("default", "ui");
	public static final Font REGULAR = register("regular", "ui");
	public static final Font TEST = register("test", "ui");
	public static final Font INTER = register("inter", "ui");
	public static final Font REGULARNEW = register("regularnew", "ui");
	public static final Font MAINMENUSCREEN = register("mainmenuicons", "guiicons");

	private static Font register(String name, String path) {
		FONT_REGISTRY.put(name, path);
		return new Font(name, path);
	}

	public static Map<String, String> getRegistry() {
		return FONT_REGISTRY;
	}

	private Fonts() {}

	static MsdfFonts.Face faceFor(String path) {
		return switch (path) {
			case "bold" -> MsdfFonts.Face.BOLD;
			case "guiicons" -> MsdfFonts.Face.ICONS;
			case "categoryicons" -> MsdfFonts.Face.CATEGORY;
			default -> MsdfFonts.Face.UI;
		};
	}
}
