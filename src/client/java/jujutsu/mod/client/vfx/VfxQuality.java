package jujutsu.mod.client.vfx;

import net.minecraft.server.level.ParticleStatus;

public enum VfxQuality {
	FULL(1.0f),
	REDUCED(0.58f),
	MINIMAL(0.28f);

	private final float density;

	VfxQuality(float density) {
		this.density = density;
	}

	public static VfxQuality from(ParticleStatus particleStatus) {
		return switch (particleStatus) {
			case ALL -> FULL;
			case DECREASED -> REDUCED;
			case MINIMAL -> MINIMAL;
		};
	}

	public int scaledCount(int count) {
		if (count <= 0) {
			return 0;
		}
		return Math.max(1, Math.round(count * density));
	}
}
