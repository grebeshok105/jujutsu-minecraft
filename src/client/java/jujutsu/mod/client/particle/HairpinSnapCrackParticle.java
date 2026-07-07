package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinSnapCrackParticle extends TextureSheetParticle {
	private static final int DIM_BLOOD_LIGHT = 0x00B000B0;
	private final SpriteSet sprites;

	protected HairpinSnapCrackParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.35, ySpeed * 0.35, zSpeed * 0.35);
		this.sprites = sprites;
		this.lifetime = 11 + this.random.nextInt(4);
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.38f + this.random.nextFloat() * 0.12f;
		this.rCol = 0.26f;
		this.gCol = 0.018f;
		this.bCol = 0.055f;
		this.alpha = 0.86f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.alpha = 0.86f * (1.0f - ((float) this.age / (float) this.lifetime));
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
			return new HairpinSnapCrackParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
