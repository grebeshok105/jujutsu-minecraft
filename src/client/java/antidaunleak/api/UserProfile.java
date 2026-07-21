package antidaunleak.api;

/** Stub for Rich avatar panel (username/uid). */
public final class UserProfile {
	private static final UserProfile INSTANCE = new UserProfile();

	public static UserProfile getInstance() {
		return INSTANCE;
	}

	public String profile(String key) {
		if ("username".equals(key)) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.player != null) {
				return mc.player.getName().getString();
			}
			return "Player";
		}
		if ("uid".equals(key)) {
			return "local";
		}
		return "null";
	}
}
