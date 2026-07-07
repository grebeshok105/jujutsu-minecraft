package jujutsu.mod.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import jujutsu.mod.JujutsuMod;

public final class JujutsuParticles {
	public static final SimpleParticleType HAIRPIN_SPARK = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_MARK_STAIN = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_WARN_EDGE = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_COMPRESSION_MOTE = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_SNAP_CRACK = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_BURST_RESIDUE = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_BURST_METAL_SHARD = FabricParticleTypes.simple();
	public static final SimpleParticleType HAIRPIN_IGNITION_TICK = FabricParticleTypes.simple();

	private JujutsuParticles() {}

	public static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_spark"), HAIRPIN_SPARK);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_mark_stain"), HAIRPIN_MARK_STAIN);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_warn_edge"), HAIRPIN_WARN_EDGE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_compression_mote"), HAIRPIN_COMPRESSION_MOTE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_snap_crack"), HAIRPIN_SNAP_CRACK);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_burst_residue"), HAIRPIN_BURST_RESIDUE);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_burst_metal_shard"), HAIRPIN_BURST_METAL_SHARD);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, JujutsuMod.id("hairpin_ignition_tick"), HAIRPIN_IGNITION_TICK);
	}
}
