package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinMetalShardParticle extends TextureSheetParticle {
	private final SpriteSet sprites;

	protected HairpinMetalShardParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.sprites = sprites;
		this.lifetime = 18 + this.random.nextInt(6);
		this.gravity = 0.04f;
		this.hasPhysics = true;
		this.quadSize = 0.12f + this.random.nextFloat() * 0.13f;
		float tint = 0.82f + this.random.nextFloat() * 0.35f;
		this.rCol = 0.42f * tint;
		this.gCol = 0.45f * tint;
		this.bCol = 0.48f * tint;
		this.alpha = 0.82f + this.random.nextFloat() * 0.15f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		float progress = (float) this.age / (float) this.lifetime;
		this.alpha = 0.95f * (1.0f - progress);
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
			return new HairpinMetalShardParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
