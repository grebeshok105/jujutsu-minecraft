package jujutsu.mod.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import jujutsu.mod.registry.JujutsuParticles;

public final class JujutsuClientParticles {
	private JujutsuClientParticles() {}

	public static void registerFactories() {
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_SPARK, HairpinSparkParticle.Provider::new);
	}
}
