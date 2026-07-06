package jujutsu.mod.debug;

import java.util.Locale;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;

public final class HairpinDebugLog {
	private static final String PREFIX = "[HAIRPIN-DIAG] ";

	private HairpinDebugLog() {}

	public static void info(String message, Object... args) {
		JujutsuMod.LOGGER.info(PREFIX + message, args);
	}

	public static String vec(Vec3 vec) {
		return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
	}
}
