package jujutsu.mod.cursedspirit;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Server-side hook registration for the cursed-spirit tiers. Attributes only — the natural-spawn
 * call is Block 4's serialized one-line edit, not this block's.
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
	}
}
