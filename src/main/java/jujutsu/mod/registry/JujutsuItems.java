package jujutsu.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import jujutsu.mod.JujutsuMod;

public final class JujutsuItems {
	public static final Item HAIRPIN_NAIL = new Item(new Item.Properties());
	public static final Item STRAW_DOLL_HAMMER = new Item(new Item.Properties());

	private JujutsuItems() {}

	public static void register() {
		register("hairpin_nail", HAIRPIN_NAIL);
		register("straw_doll_hammer", STRAW_DOLL_HAMMER);
	}

	private static void register(String path, Item item) {
		Registry.register(BuiltInRegistries.ITEM, JujutsuMod.id(path), item);
	}
}
