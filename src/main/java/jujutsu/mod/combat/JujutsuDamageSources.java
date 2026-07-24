package jujutsu.mod.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import jujutsu.mod.JujutsuMod;

/** Character-neutral custom damage sources shared by current combat kits. */
public final class JujutsuDamageSources {
	public static final ResourceKey<DamageType> HAIRPIN = key("hairpin");
	public static final ResourceKey<DamageType> SELF_RESONANCE = key("self_resonance");
	public static final ResourceKey<DamageType> BLACK_FLASH = key("black_flash");

	private JujutsuDamageSources() {}

	public static DamageSource hairpin(Level level, Entity attacker) {
		return source(level, HAIRPIN, attacker);
	}

	public static DamageSource selfResonance(Level level, Entity attacker) {
		return source(level, SELF_RESONANCE, attacker);
	}

	public static DamageSource blackFlash(Level level, Entity attacker) {
		return source(level, BLACK_FLASH, attacker);
	}

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, JujutsuMod.id(path));
	}

	private static DamageSource source(Level level, ResourceKey<DamageType> key, Entity attacker) {
		var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
		return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker);
	}
}
