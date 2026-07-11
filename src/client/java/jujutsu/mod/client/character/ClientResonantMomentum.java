package jujutsu.mod.client.character;

import net.minecraft.client.Minecraft;

public final class ClientResonantMomentum {
	private static long expiresAtGameTime;
	private ClientResonantMomentum() {}

	public static void apply(int remainingTicks) {
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		expiresAtGameTime = now + Math.max(0, remainingTicks);
	}

	public static int remainingTicks() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return 0;
		return (int)Math.max(0L, expiresAtGameTime - client.level.getGameTime());
	}

	public static void clear() { expiresAtGameTime = 0L; }
}
