package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class BfSparkParticle extends TextureSheetParticle {
	private final SpriteSet sprites;

	protected BfSparkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.sprites = sprites;
		this.lifetime = 12 + this.random.nextInt(7);
		this.gravity = 0.04f;
		this.hasPhysics = false;
		this.quadSize = 0.05f + this.random.nextFloat() * 0.04f;
		this.rCol = 1.0f;
		this.gCol = 0.31f;
		this.bCol = 0.31f;
		this.alpha = 0.9f;
		this.xd = xSpeed * (0.8 + this.random.nextFloat() * 0.6);
		this.yd = ySpeed * (0.8 + this.random.nextFloat() * 0.6);
		this.zd = zSpeed * (0.8 + this.random.nextFloat() * 0.6);
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		float progress = (float) this.age / (float) this.lifetime;
		this.alpha = 0.9f * (1.0f - progress);
		this.quadSize *= 0.97f;
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
			return new BfSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
