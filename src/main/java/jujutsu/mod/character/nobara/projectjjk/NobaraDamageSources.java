package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import jujutsu.mod.combat.JujutsuDamageSources;

/** Compatibility facade for Nobara callers; shared damage sources now live in combat. */
public final class NobaraDamageSources {
	public static final ResourceKey<DamageType> HAIRPIN = JujutsuDamageSources.HAIRPIN;
	public static final ResourceKey<DamageType> SELF_RESONANCE = JujutsuDamageSources.SELF_RESONANCE;

	private NobaraDamageSources() {}

	public static DamageSource hairpin(Level level, Entity attacker) {
		return JujutsuDamageSources.hairpin(level, attacker);
	}

	public static DamageSource selfResonance(Level level, Entity attacker) {
		return JujutsuDamageSources.selfResonance(level, attacker);
	}
}
