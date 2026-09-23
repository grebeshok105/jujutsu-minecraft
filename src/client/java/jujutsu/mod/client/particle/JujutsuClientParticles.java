package jujutsu.mod.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import jujutsu.mod.registry.JujutsuParticles;

public final class JujutsuClientParticles {
	private JujutsuClientParticles() {}

	public static void registerFactories() {
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_SPARK, HairpinSparkParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_MARK_STAIN, HairpinMarkStainParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_WARN_EDGE, HairpinWarnEdgeParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, HairpinCompressionMoteParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_SNAP_CRACK, HairpinSnapCrackParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_BURST_RESIDUE, HairpinBurstResidueParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, HairpinMetalShardParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.HAIRPIN_IGNITION_TICK, HairpinIgnitionTickParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.BF_LIGHTNING, BfLightningParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.BF_SPARK, BfSparkParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(JujutsuParticles.BF_IMPACT, BfImpactParticle.Provider::new);
	}
}
