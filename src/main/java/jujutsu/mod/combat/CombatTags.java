package jujutsu.mod.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import jujutsu.mod.JujutsuMod;

/**
 * Shared entity-type tags for combat routing.
 */
public final class CombatTags {
	private CombatTags() {}

	/**
	 * Entity types Todo's Boogie Woogie refuses to swap. Membership is data (see
	 * {@code data/jujutsumod/tags/entity_type/boogie_woogie_immune.json}); only the greater cursed
	 * spirit is a member.
	 */
	public static final TagKey<EntityType<?>> BOOGIE_WOOGIE_IMMUNE = TagKey.create(
			Registries.ENTITY_TYPE,
			JujutsuMod.id("boogie_woogie_immune"));

	public static boolean isBoogieWoogieImmune(Entity target) {
		return target != null && target.getType().is(BOOGIE_WOOGIE_IMMUNE);
	}
}
