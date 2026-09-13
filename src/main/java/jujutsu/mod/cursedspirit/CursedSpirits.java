package jujutsu.mod.cursedspirit;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import jujutsu.mod.cursedspirit.perception.CursedSpiritInteractionGates;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Server-side hook registration for the cursed-spirit tiers: attributes plus the natural-spawn
 * rows (Block 4's serialized one-line edit below).
 */
public final class CursedSpirits {
	private CursedSpirits() {}

	public static void registerServerHooks() {
		FabricDefaultAttributeRegistry.register(JujutsuEntities.LESSER_CURSED_SPIRIT,
				CursedSpiritEntity.createAttributes(CursedSpiritTier.LESSER));
		FabricDefaultAttributeRegistry.register(JujutsuEntities.CURSED_SPIRIT,
				CursedSpiritEntity.createAttributes(CursedSpiritTier.COMMON));
		FabricDefaultAttributeRegistry.register(JujutsuEntities.GREATER_CURSED_SPIRIT,
				CursedSpiritEntity.createAttributes(CursedSpiritTier.GREATER));
		CursedSpiritSpawnIntegration.register();
		CursedSpiritInteractionGates.register();
		// Block 3 (#86): acid zone pulse and the runner's exactly-three action denies.
		jujutsu.mod.cursedspirit.ability.effects.AcidZoneRuntime.register();
		jujutsu.mod.cursedspirit.ability.effects.RunnerEffect.register();
	}
}
