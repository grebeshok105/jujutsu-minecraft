package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiRabbitEntity;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.character.todo.TodoStoneEntity;

public final class JujutsuEntities {
	public static final EntityType<ProjectJjkNailEntity> PROJECTJJK_NAIL = createProjectJjkNail("projectjjk_nail");
	public static final EntityType<MegumiDivineDogEntity> MEGUMI_DIVINE_DOG = createMegumiDivineDog("megumi_divine_dog");
	public static final EntityType<MegumiNueEntity> MEGUMI_NUE = createMegumiNue("megumi_nue");
	public static final EntityType<MegumiToadEntity> MEGUMI_TOAD = createMegumiToad("megumi_toad");
	public static final EntityType<MegumiElephantEntity> MEGUMI_MAX_ELEPHANT = createMegumiMaxElephant("megumi_max_elephant");
	public static final EntityType<MegumiRabbitEntity> MEGUMI_RABBIT = createMegumiRabbit("megumi_rabbit");
	public static final EntityType<TodoStoneEntity> TODO_STONE = createTodoStone("todo_stone");

	private JujutsuEntities() {}

	public static void register() {
		register("projectjjk_nail", PROJECTJJK_NAIL);
		register("megumi_divine_dog", MEGUMI_DIVINE_DOG);
		register("megumi_nue", MEGUMI_NUE);
		register("megumi_toad", MEGUMI_TOAD);
		register("megumi_max_elephant", MEGUMI_MAX_ELEPHANT);
		register("megumi_rabbit", MEGUMI_RABBIT);
		register("todo_stone", TODO_STONE);
	}

	private static EntityType<TodoStoneEntity> createTodoStone(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<TodoStoneEntity>of(TodoStoneEntity::new, MobCategory.MISC)
				.sized(TodoProfile.STONE_HITBOX_SIZE, TodoProfile.STONE_HITBOX_SIZE)
				.clientTrackingRange(96)
				.updateInterval(2)
				// A stone exists only in flight: it must never outlive the session that threw it, and an
				// unloaded chunk discarding it is what makes the expiry sweep's "entity gone" branch sound.
				.noSave()
				.build(key);
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

	private static EntityType<MegumiNueEntity> createMegumiNue(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiNueEntity>of(MegumiNueEntity::new, MobCategory.MISC)
				.sized(0.9f, 0.9f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiToadEntity> createMegumiToad(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiToadEntity>of(MegumiToadEntity::new, MobCategory.MISC)
				.sized(1.3f, 1.0f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiElephantEntity> createMegumiMaxElephant(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiElephantEntity>of(MegumiElephantEntity::new, MobCategory.MISC)
				.sized(2.0f, 2.2f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiRabbitEntity> createMegumiRabbit(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiRabbitEntity>of(MegumiRabbitEntity::new, MobCategory.MISC)
				.sized(0.4f, 0.4f)
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
