package jujutsu.mod.registry;

import java.util.List;

import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkHammerItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRemnantItem;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import jujutsu.mod.cursedincident.object.CursedObjectItem;
import jujutsu.mod.cursedincident.object.SealTier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

public final class JujutsuItems {
	public static final Item HAIRPIN_NAIL = createProjectJjkNail("hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item STRAW_DOLL_HAMMER = createProjectJjkHammer("straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));
	public static final Item PROJECTJJK_HAIRPIN_NAIL = createProjectJjkNail("projectjjk_hairpin_nail", new Item.Properties().stacksTo(64));
	public static final Item PROJECTJJK_STRAW_DOLL_HAMMER = createProjectJjkHammer("projectjjk_straw_doll_hammer", new Item.Properties().stacksTo(1).durability(256));
	public static final Item RESONANCE_REMNANT = createProjectJjkRemnant("resonance_remnant", new Item.Properties().stacksTo(1));
	public static final Item STRAW_DOLL = createProjectJjkStrawDoll("straw_doll", strawDollProperties());
	public static final Item CURSED_OBJECT = createCursedObject("cursed_object",
			new Item.Properties().stacksTo(1).fireResistant());
	public static final Item SEALING_TALISMAN = createTalisman("sealing_talisman", SealTier.TALISMAN);
	public static final Item INSCRIBED_TALISMAN = createTalisman("inscribed_talisman", SealTier.INSCRIBED);
	public static final Item PRISMATIC_TALISMAN = createTalisman("prismatic_talisman", SealTier.PRISMATIC);

	private JujutsuItems() {}

	public static void register() {
		register("hairpin_nail", HAIRPIN_NAIL);
		register("straw_doll_hammer", STRAW_DOLL_HAMMER);
		register("projectjjk_hairpin_nail", PROJECTJJK_HAIRPIN_NAIL);
		register("projectjjk_straw_doll_hammer", PROJECTJJK_STRAW_DOLL_HAMMER);
		register("resonance_remnant", RESONANCE_REMNANT);
		register("straw_doll", STRAW_DOLL);
		register("cursed_object", CURSED_OBJECT);
		register("sealing_talisman", SEALING_TALISMAN);
		register("inscribed_talisman", INSCRIBED_TALISMAN);
		register("prismatic_talisman", PRISMATIC_TALISMAN);
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

	private static Item createCursedObject(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new CursedObjectItem(properties.setId(key));
	}

	private static Item createTalisman(String path, SealTier tier) {
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), JujutsuMod.id(path));
		return new SealingTalismanItem(tier, new Item.Properties().stacksTo(16).setId(key));
	}

	private static Item.Properties strawDollProperties() {
		return new Item.Properties()
				.stacksTo(1)
				.component(net.minecraft.core.component.DataComponents.LORE, new ItemLore(List.of(
						Component.translatable("tooltip.jujutsumod.straw_doll.ritual"),
						Component.translatable("tooltip.jujutsumod.straw_doll.requires")
				)));
	}

	private static void register(String path, Item item) {
		Registry.register(BuiltInRegistries.ITEM, JujutsuMod.id(path), item);
	}

	private static final class SealingTalismanItem extends Item {
		private final SealTier tier;

		private SealingTalismanItem(SealTier tier, Properties properties) {
			super(properties);
			this.tier = tier;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!player.isCrouching()) {
				return InteractionResult.PASS;
			}
			InteractionHand objectHand = hand == InteractionHand.MAIN_HAND
					? InteractionHand.OFF_HAND
					: InteractionHand.MAIN_HAND;
			ItemStack object = player.getItemInHand(objectHand);
			if (!CursedObjectItem.isCursedObject(object)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			CursedObjectItem.SealResult result = CursedObjectItem.trySeal(object, tier.value());
			if (!result.ok()) {
				player.displayClientMessage(Component.translatable(
						"message.jujutsumod.cursed_object.seal_failed", result.requiredTier()), true);
				return InteractionResult.FAIL;
			}
			if (!player.getAbilities().instabuild) {
				player.getItemInHand(hand).shrink(1);
			}
			player.displayClientMessage(Component.translatable("message.jujutsumod.cursed_object.sealed"), true);
			return InteractionResult.SUCCESS_SERVER;
		}
	}
}
