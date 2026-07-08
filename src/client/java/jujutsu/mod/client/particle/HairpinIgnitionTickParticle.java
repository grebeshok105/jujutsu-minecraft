package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinIgnitionTickParticle extends TextureSheetParticle {
	private static final int CYAN_FIRE_LIGHT = 0x00F000F0;
	private final SpriteSet sprites;

	protected HairpinIgnitionTickParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.42, ySpeed * 0.24 + 0.018, zSpeed * 0.42);
		this.sprites = sprites;
		this.lifetime = 12 + this.random.nextInt(6);
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.19f + this.random.nextFloat() * 0.11f;
		this.rCol = 0.04f;
		this.gCol = 0.88f + this.random.nextFloat() * 0.10f;
		this.bCol = 1.0f;
		this.alpha = 0.92f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		float life = Math.min(1.0f, (float) this.age / (float) this.lifetime);
		float flicker = 0.82f + 0.18f * (float) Math.sin((this.age + this.random.nextFloat()) * 1.7f);
		this.alpha = 0.92f * (1.0f - life) * flicker;
	}

	@Override
	public int getLightColor(float partialTick) {
		return CYAN_FIRE_LIGHT;
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
			return new HairpinIgnitionTickParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
