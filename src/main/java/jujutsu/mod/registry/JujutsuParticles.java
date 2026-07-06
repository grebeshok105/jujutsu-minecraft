package jujutsu.mod.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import jujutsu.mod.JujutsuMod;

public final class JujutsuParticles {
	public static final SimpleParticleType HAIRPIN_SPARK = FabricParticleTypes.simple();

	private JujutsuParticles() {}

	public static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_spark"), HAIRPIN_SPARK);
	}
}
