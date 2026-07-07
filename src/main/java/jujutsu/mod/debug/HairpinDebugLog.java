package jujutsu.mod.debug;

import java.util.Locale;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;

public final class HairpinDebugLog {
	private static final String PREFIX = "[HAIRPIN-DIAG] ";
	private static volatile boolean enabled = Boolean.getBoolean("jujutsumod.hairpin.debug");

	private HairpinDebugLog() {}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean enabled) {
		HairpinDebugLog.enabled = enabled;
		JujutsuMod.LOGGER.info(PREFIX + "enabled={}", enabled);
	}

	public static void info(String message, Object... args) {
		if (enabled) {
			JujutsuMod.LOGGER.info(PREFIX + message, args);
		}
	}

	public static String vec(Vec3 vec) {
		return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
	}
}
