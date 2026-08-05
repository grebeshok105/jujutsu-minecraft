package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;

public final class JujutsuEntities {
	public static final EntityType<ProjectJjkNailEntity> PROJECTJJK_NAIL = createProjectJjkNail("projectjjk_nail");
	public static final EntityType<MegumiDivineDogEntity> MEGUMI_DIVINE_DOG = createMegumiDivineDog("megumi_divine_dog");

	private JujutsuEntities() {}

	public static void register() {
		register("projectjjk_nail", PROJECTJJK_NAIL);
		register("megumi_divine_dog", MEGUMI_DIVINE_DOG);
	}

	private static EntityType<MegumiDivineDogEntity> createMegumiDivineDog(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiDivineDogEntity>of(MegumiDivineDogEntity::new, MobCategory.MISC)
				.sized(0.6f, 0.85f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<ProjectJjkNailEntity> createProjectJjkNail(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.of(ProjectJjkNailEntity::new, MobCategory.MISC)
				.sized(0.28f, 0.28f)
				.clientTrackingRange(96)
				.updateInterval(1)
				.noSave()
				.build(key);
	}

	private static void register(String path, EntityType<?> entityType) {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, JujutsuMod.id(path), entityType);
	}
}
