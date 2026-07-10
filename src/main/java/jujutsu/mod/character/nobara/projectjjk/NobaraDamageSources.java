package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import jujutsu.mod.JujutsuMod;

public final class NobaraDamageSources {
	public static final ResourceKey<DamageType> HAIRPIN = ResourceKey.create(Registries.DAMAGE_TYPE, JujutsuMod.id("hairpin"));
	public static final ResourceKey<DamageType> SELF_RESONANCE = ResourceKey.create(Registries.DAMAGE_TYPE, JujutsuMod.id("self_resonance"));

	private NobaraDamageSources() {}

	public static DamageSource hairpin(Level level, Entity attacker) {
		var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HAIRPIN);
		return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker);
	}

	public static DamageSource selfResonance(Level level, Entity attacker) {
		var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SELF_RESONANCE);
		return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker);
	}
}
