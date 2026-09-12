package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * R1 registry contract (Step 9): the three tier ids exist in the entity registry, are MONSTER,
 * carry the frozen hitboxes, and can serialize (no {@code .noSave()}).
 */
class CursedSpiritRegistryTest {
	private static boolean registered;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		synchronized (CursedSpiritRegistryTest.class) {
			if (!registered) {
				JujutsuEntities.register();
				registered = true;
			}
		}
	}

	@Test
	void threeTiersRegisteredAsMonster() {
		assertEquals(MobCategory.MONSTER, type("lesser_cursed_spirit").getCategory());
		assertEquals(MobCategory.MONSTER, type("cursed_spirit").getCategory());
		assertEquals(MobCategory.MONSTER, type("greater_cursed_spirit").getCategory());
	}

	@Test
	void hitboxesMatchFrozenTable() {
		assertHitbox("lesser_cursed_spirit", 0.85f, 1.0f);
		assertHitbox("cursed_spirit", 0.75f, 1.9f);
		assertHitbox("greater_cursed_spirit", 1.35f, 2.4f);
	}

	@Test
	void typesPersistThroughUnload() {
		assertTrue(type("lesser_cursed_spirit").canSerialize(), "lesser serializes");
		assertTrue(type("cursed_spirit").canSerialize(), "common serializes");
		assertTrue(type("greater_cursed_spirit").canSerialize(), "greater serializes");
	}

	@Test
	void registryFieldsMatchIds() {
		assertEquals(type("lesser_cursed_spirit"), JujutsuEntities.LESSER_CURSED_SPIRIT);
		assertEquals(type("cursed_spirit"), JujutsuEntities.CURSED_SPIRIT);
		assertEquals(type("greater_cursed_spirit"), JujutsuEntities.GREATER_CURSED_SPIRIT);
	}

	private static void assertHitbox(String path, float width, float height) {
		EntityType<?> entry = type(path);
		assertEquals(width, entry.getWidth(), path + " width");
		assertEquals(height, entry.getHeight(), path + " height");
	}

	private static EntityType<?> type(String path) {
		EntityType<?> entry = BuiltInRegistries.ENTITY_TYPE.getValue(ResourceLocation.fromNamespaceAndPath(
				jujutsu.mod.JujutsuMod.MOD_ID, path));
		assertTrue(entry != null, "entity type registered: " + path);
		return entry;
	}
}
