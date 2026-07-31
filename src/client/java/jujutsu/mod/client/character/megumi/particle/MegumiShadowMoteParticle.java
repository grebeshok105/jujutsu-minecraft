package jujutsu.mod.client.character.megumi.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A short-lived shadow mote: mostly black, with sparse neutral dark-gray edge motes. */
public final class MegumiShadowMoteParticle extends TextureSheetParticle {
	private final SpriteSet sprites;
	private final boolean accent;

	private MegumiShadowMoteParticle(
			ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.sprites = sprites;
		accent = random.nextInt(10) == 0;
		lifetime = 8 + random.nextInt(5);
		gravity = 0.025f;
		hasPhysics = false;
		quadSize = 0.035f + random.nextFloat() * 0.035f;
		rCol = accent ? 0.045f : 0.015f;
		gCol = accent ? 0.045f : 0.015f;
		bCol = accent ? 0.045f : 0.015f;
		alpha = accent ? 0.72f : 0.66f;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		setSpriteFromAge(sprites);
		float progress = (float) age / lifetime;
		alpha = (accent ? 0.95f : 0.66f) * (1.0f - progress);
		quadSize *= 0.96f;
	}

	@Override
	public int getLightColor(float partialTick) {
		return super.getLightColor(partialTick);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
				double xSpeed, double ySpeed, double zSpeed) {
			return new MegumiShadowMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
		}
	}
}
