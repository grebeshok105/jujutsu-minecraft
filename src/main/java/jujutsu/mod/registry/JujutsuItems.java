package jujutsu.mod.registry;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkHammerItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRemnantItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import jujutsu.mod.JujutsuMod;

public final class JujutsuItems {
	public static final Item HAIRPIN_NAIL = createProjectJjkNail("hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item STRAW_DOLL_HAMMER = createProjectJjkHammer("straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));
	public static final Item PROJECTJJK_HAIRPIN_NAIL = createProjectJjkNail("projectjjk_hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item PROJECTJJK_STRAW_DOLL_HAMMER = createProjectJjkHammer("projectjjk_straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));
	public static final Item RESONANCE_REMNANT = createProjectJjkRemnant("resonance_remnant", new Item.Properties().stacksTo(1));
	public static final Item STRAW_DOLL = createProjectJjkStrawDoll("straw_doll", strawDollProperties());

	private JujutsuItems() {}

	public static void register() {
		register("hairpin_nail", HAIRPIN_NAIL);
		register("straw_doll_hammer", STRAW_DOLL_HAMMER);
		register("projectjjk_hairpin_nail", PROJECTJJK_HAIRPIN_NAIL);
		register("projectjjk_straw_doll_hammer", PROJECTJJK_STRAW_DOLL_HAMMER);
		register("resonance_remnant", RESONANCE_REMNANT);
		register("straw_doll", STRAW_DOLL);
	}

	private static Item createProjectJjkNail(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkNailItem(properties.setId(key));
	}

	private static Item createProjectJjkHammer(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkHammerItem(properties.setId(key));
	}

	private static Item createProjectJjkRemnant(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkRemnantItem(properties.setId(key));
	}

	private static Item createProjectJjkStrawDoll(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new ProjectJjkStrawDollItem(properties.setId(key));
	}

	private static Item.Properties strawDollProperties() {
		return new Item.Properties()
				.stacksTo(1)
				.component(DataComponents.LORE, new ItemLore(List.of(
						Component.translatable("tooltip.jujutsumod.straw_doll.ritual"),
						Component.translatable("tooltip.jujutsumod.straw_doll.requires")
				)));
	}

	private static void register(String path, Item item) {
		Registry.register(BuiltInRegistries.ITEM, JujutsuMod.id(path), item);
	}
}
