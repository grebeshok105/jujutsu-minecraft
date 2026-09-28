package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.megumi.MegumiDeerEntity;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiRabbitEntity;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.character.megumi.MegumiTigerEntity;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.character.todo.TodoStoneEntity;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritTier;
import jujutsu.mod.cursedspirit.ability.effects.CursedSpiritAcidSpitEntity;

public final class JujutsuEntities {
	public static final EntityType<ProjectJjkNailEntity> PROJECTJJK_NAIL = createProjectJjkNail("projectjjk_nail");
	public static final EntityType<MegumiDivineDogEntity> MEGUMI_DIVINE_DOG = createMegumiDivineDog("megumi_divine_dog");
	public static final EntityType<MegumiNueEntity> MEGUMI_NUE = createMegumiNue("megumi_nue");
	public static final EntityType<MegumiToadEntity> MEGUMI_TOAD = createMegumiToad("megumi_toad");
	public static final EntityType<MegumiElephantEntity> MEGUMI_MAX_ELEPHANT = createMegumiMaxElephant("megumi_max_elephant");
	public static final EntityType<MegumiRabbitEntity> MEGUMI_RABBIT = createMegumiRabbit("megumi_rabbit");
	public static final EntityType<MegumiSerpentEntity> MEGUMI_SERPENT = createMegumiSerpent("megumi_serpent");
	public static final EntityType<MegumiDeerEntity> MEGUMI_DEER = createMegumiDeer("megumi_deer");
	public static final EntityType<MegumiOxEntity> MEGUMI_OX = createMegumiOx("megumi_ox");
	public static final EntityType<MegumiTigerEntity> MEGUMI_TIGER = createMegumiTiger("megumi_tiger");
	public static final EntityType<TodoStoneEntity> TODO_STONE = createTodoStone("todo_stone");
	public static final EntityType<CursedSpiritAcidSpitEntity> CURSED_ACID_SPIT =
			createCursedAcidSpit("cursed_acid_spit");
	public static final EntityType<CursedSpiritEntity> LESSER_CURSED_SPIRIT =
			createCursedSpirit("lesser_cursed_spirit", CursedSpiritTier.LESSER, 0.85f, 1.0f);
	public static final EntityType<CursedSpiritEntity> CURSED_SPIRIT =
			createCursedSpirit("cursed_spirit", CursedSpiritTier.COMMON, 0.75f, 1.9f);
	public static final EntityType<CursedSpiritEntity> GREATER_CURSED_SPIRIT =
			createCursedSpirit("greater_cursed_spirit", CursedSpiritTier.GREATER, 1.35f, 2.4f);
	private JujutsuEntities() {}

	public static void register() {
		register("projectjjk_nail", PROJECTJJK_NAIL);
		register("megumi_divine_dog", MEGUMI_DIVINE_DOG);
		register("cursed_acid_spit", CURSED_ACID_SPIT);
		register("megumi_nue", MEGUMI_NUE);
		register("megumi_toad", MEGUMI_TOAD);
		register("megumi_max_elephant", MEGUMI_MAX_ELEPHANT);
		register("megumi_rabbit", MEGUMI_RABBIT);
		register("megumi_serpent", MEGUMI_SERPENT);
		register("megumi_deer", MEGUMI_DEER);
		register("megumi_ox", MEGUMI_OX);
		register("megumi_tiger", MEGUMI_TIGER);
		register("todo_stone", TODO_STONE);
		register("lesser_cursed_spirit", LESSER_CURSED_SPIRIT);
		register("cursed_spirit", CURSED_SPIRIT);
		register("greater_cursed_spirit", GREATER_CURSED_SPIRIT);
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
	private static EntityType<CursedSpiritAcidSpitEntity> createCursedAcidSpit(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<CursedSpiritAcidSpitEntity>of(CursedSpiritAcidSpitEntity::new, MobCategory.MISC)
				.sized(0.35f, 0.35f)
				.clientTrackingRange(96)
				.updateInterval(1)
				// A glob exists only in flight: it must never outlive the cast, and an unloaded
				// chunk discarding it is an expiry like any other.
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

	private static EntityType<MegumiSerpentEntity> createMegumiSerpent(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiSerpentEntity>of(MegumiSerpentEntity::new, MobCategory.MISC)
				.sized(1.9f, 1.0f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiDeerEntity> createMegumiDeer(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiDeerEntity>of(MegumiDeerEntity::new, MobCategory.MISC)
				.sized(0.9f, 1.7f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiOxEntity> createMegumiOx(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiOxEntity>of(MegumiOxEntity::new, MobCategory.MISC)
				.sized(1.5f, 1.6f)
				.clientTrackingRange(96)
				.updateInterval(2)
				.noSave()
				.build(key);
	}

	private static EntityType<MegumiTigerEntity> createMegumiTiger(String path) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		return EntityType.Builder
				.<MegumiTigerEntity>of(MegumiTigerEntity::new, MobCategory.MISC)
				.sized(1.2f, 1.1f)
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

	private static EntityType<CursedSpiritEntity> createCursedSpirit(String path, CursedSpiritTier tier,
			float width, float height) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JujutsuMod.id(path));
		// Hostile world mobs: MONSTER (not the MISC the summons use), frozen hitboxes, and — unlike
		// every summon type — no .noSave(), so they persist through chunk unload like vanilla mobs.
		return EntityType.Builder
				.<CursedSpiritEntity>of((type, level) -> new CursedSpiritEntity(type, level, tier), MobCategory.MONSTER)
				.sized(width, height)
				.clientTrackingRange(96)
				.updateInterval(3)
				.build(key);
	}

	private static void register(String path, EntityType<?> entityType) {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, JujutsuMod.id(path), entityType);
	}
}
