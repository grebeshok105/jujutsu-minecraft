package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinWarnEdgeParticle extends TextureSheetParticle {
	private static final int DIM_BLOOD_LIGHT = 0x00A000A0;
	private static final int WARNING_DURATION_TICKS = 6;
	private final SpriteSet sprites;

	protected HairpinWarnEdgeParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.045, ySpeed * 0.045, zSpeed * 0.045);
		this.sprites = sprites;
		this.lifetime = WARNING_DURATION_TICKS;
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.18f + this.random.nextFloat() * 0.055f;
		this.rCol = 0.24f;
		this.gCol = 0.02f;
		this.bCol = 0.065f;
		this.alpha = 0.76f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		float progress = (float) this.age / (float) this.lifetime;
		this.alpha = 0.76f * (1.0f - progress);
	}

	@Override
	public int getLightColor(float partialTick) {
		return DIM_BLOOD_LIGHT;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return new HairpinWarnEdgeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
