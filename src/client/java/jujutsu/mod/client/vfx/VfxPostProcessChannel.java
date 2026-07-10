package jujutsu.mod.client.vfx;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;

public final class VfxPostProcessChannel {
	private long activeUntilMillis;
	private boolean disabledForSession;

	public void triggerBlur(int durationMillis, float initialAgeTicks) {
		if (disabledForSession || durationMillis <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		long startedAt = VfxTimeline.startedAtMillis(now, initialAgeTicks);
		long expiresAt = startedAt + durationMillis;
		if (expiresAt > now) {
			activeUntilMillis = Math.max(activeUntilMillis, expiresAt);
		}
	}

	void render(Minecraft client) {
		if (disabledForSession || System.currentTimeMillis() >= activeUntilMillis) {
			return;
		}
		if (client.level == null || client.player == null) {
			return;
		}
		try {
			client.gameRenderer.processBlurEffect();
		} catch (RuntimeException | LinkageError error) {
			disabledForSession = true;
			activeUntilMillis = 0L;
			JujutsuMod.LOGGER.warn("Disabling VFX blur for this client session: {}", error.toString());
		}
	}

	void clear() {
		activeUntilMillis = 0L;
	}

	void resetSession() {
		clear();
		disabledForSession = false;
	}
}
