package jujutsu.mod.combat;

import java.util.UUID;

public final class BlackFlashWindow {
	private final UUID targetId;
	private final BlackFlashImpact impact;
	private final long opensAt;
	private final long closesAt;
	private final float baseDamage;
	private boolean consumed;

	public BlackFlashWindow(UUID targetId, BlackFlashImpact impact, long opensAt, long closesAt, float baseDamage) {
		if (opensAt > closesAt || baseDamage < 0.0f) {
			throw new IllegalArgumentException("Invalid Black Flash window");
		}
		this.targetId = targetId;
		this.impact = impact;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
		this.baseDamage = baseDamage;
	}

	public UUID targetId() { return targetId; }
	public BlackFlashImpact impact() { return impact; }
	public boolean accepts(long gameTime) { return !consumed && gameTime >= opensAt && gameTime <= closesAt; }
	public boolean consume(long gameTime) {
		if (!accepts(gameTime)) return false;
		consumed = true;
		return true;
	}
	public float bonusDamage(float multiplier) { return baseDamage * Math.max(0.0f, multiplier - 1.0f); }
}
