package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinIgnitionTickParticle extends TextureSheetParticle {
	private static final int FULL_BRIGHT = 0x00F000F0;
	private final SpriteSet sprites;

	protected HairpinIgnitionTickParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.25, ySpeed * 0.25, zSpeed * 0.25);
		this.sprites = sprites;
		this.lifetime = 3;
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.045f + this.random.nextFloat() * 0.02f;
		this.rCol = 0.62f;
		this.gCol = 0.24f;
		this.bCol = 0.36f;
		this.alpha = 0.9f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.alpha = 0.9f * (1.0f - ((float) this.age / (float) this.lifetime));
	}

	@Override
	public int getLightColor(float partialTick) {
		return FULL_BRIGHT;
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
