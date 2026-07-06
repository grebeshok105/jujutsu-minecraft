package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class HairpinWarnEdgeParticle extends TextureSheetParticle {
	private static final int FULL_BRIGHT = 0x00F000F0;
	private final SpriteSet sprites;

	protected HairpinWarnEdgeParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed * 0.18, ySpeed * 0.18, zSpeed * 0.18);
		this.sprites = sprites;
		this.lifetime = 5 + this.random.nextInt(2);
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.quadSize = 0.09f + this.random.nextFloat() * 0.035f;
		this.rCol = 0.38f;
		this.gCol = 0.105f;
		this.bCol = 0.21f;
		this.alpha = 0.62f;
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		float progress = (float) this.age / (float) this.lifetime;
		this.alpha = 0.62f * (1.0f - progress);
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
			return new HairpinWarnEdgeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
		}
	}
}
