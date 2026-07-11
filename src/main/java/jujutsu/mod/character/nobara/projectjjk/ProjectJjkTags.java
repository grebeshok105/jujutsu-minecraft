package jujutsu.mod.character.nobara.projectjjk;

import jujutsu.mod.JujutsuMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

final class ProjectJjkTags {
	static final TagKey<EntityType<?>> RESONANCE_REMNANT_CURSE = TagKey.create(
			Registries.ENTITY_TYPE,
			JujutsuMod.id("resonance_remnant_curse")
	);

	private ProjectJjkTags() {}
}
