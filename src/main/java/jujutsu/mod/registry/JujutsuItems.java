package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import jujutsu.mod.character.nobara.HairpinNailItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkHammerItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailItem;
import jujutsu.mod.character.nobara.StrawDollHammerItem;
import jujutsu.mod.JujutsuMod;

public final class JujutsuItems {
	public static final Item HAIRPIN_NAIL = createHairpinNail("hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item STRAW_DOLL_HAMMER = createStrawDollHammer("straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));
	public static final Item PROJECTJJK_HAIRPIN_NAIL = createProjectJjkNail("projectjjk_hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item PROJECTJJK_STRAW_DOLL_HAMMER = createProjectJjkHammer("projectjjk_straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));

	private JujutsuItems() {}

	public static void register() {
		register("hairpin_nail", HAIRPIN_NAIL);
		register("straw_doll_hammer", STRAW_DOLL_HAMMER);
		register("projectjjk_hairpin_nail", PROJECTJJK_HAIRPIN_NAIL);
		register("projectjjk_straw_doll_hammer", PROJECTJJK_STRAW_DOLL_HAMMER);
	}

	private static Item createHairpinNail(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new HairpinNailItem(properties.setId(key));
	}

	private static Item createStrawDollHammer(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new StrawDollHammerItem(properties.setId(key));
	}

	private static Item createProjectJjkNail(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkNailItem(properties.setId(key));
	}

	private static Item createProjectJjkHammer(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkHammerItem(properties.setId(key));
	}

	private static void register(String path, Item item) {
		Registry.register(BuiltInRegistries.ITEM, JujutsuMod.id(path), item);
	}
}
