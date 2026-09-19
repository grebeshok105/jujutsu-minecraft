package jujutsu.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Cursed-energy mote for incident zones: a slow-rising violet ember with an occasional
 * brighter magenta accent. Longer-lived than the shadow mote so a zone reads as a
 * persistent field, not a burst.
 */
public final class CursedMoteParticle extends TextureSheetParticle {
	private final SpriteSet sprites;
	private final boolean accent;

	private CursedMoteParticle(
			ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.sprites = sprites;
		accent = random.nextInt(8) == 0;
		lifetime = 30 + random.nextInt(25);
		gravity = -0.012f; // negative gravity: motes drift upward like cursed embers
		hasPhysics = false;
		quadSize = 0.04f + random.nextFloat() * 0.05f;
		rCol = accent ? 0.62f : 0.24f;
		gCol = accent ? 0.18f : 0.05f;
		bCol = accent ? 0.85f : 0.38f;
		alpha = accent ? 0.85f : 0.6f;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		setSpriteFromAge(this.sprites);
		float progress = (float) this.age / (float) this.lifetime;
		this.alpha = (accent ? 0.85f : 0.6f) * (1.0f - progress * progress);
		// Gentle lateral wander so the field feels alive rather than a static column.
		this.xd += (this.random.nextFloat() - 0.5f) * 0.002f;
		this.zd += (this.random.nextFloat() - 0.5f) * 0.002f;
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
			return new CursedMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
		}
	}
}
