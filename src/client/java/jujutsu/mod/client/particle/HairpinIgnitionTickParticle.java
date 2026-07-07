package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinIgnitionTickParticle extends TextureSheetParticle {
	private static final int DIM_BLUE_LIGHT = 0x00B000D0;
	private final SpriteSet sprites;

	protected HairpinIgnitionTickParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.25, ySpeed * 0.25, zSpeed * 0.25);
		this.sprites = sprites;
		this.lifetime = 8 + this.random.nextInt(3);
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.13f + this.random.nextFloat() * 0.05f;
		this.rCol = 0.12f;
		this.gCol = 0.32f;
		this.bCol = 0.78f;
		this.alpha = 0.74f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.alpha = 0.74f * (1.0f - ((float) this.age / (float) this.lifetime));
	}

	@Override
	public int getLightColor(float partialTick) {
		return DIM_BLUE_LIGHT;
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
