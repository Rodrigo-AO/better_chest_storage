package com.betterchest.registry;

import com.betterchest.BetterChestStorage;
import com.betterchest.item.ChestConnectorItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Registration of the mod's items. */
public final class ModItems {

	public static final Item CHEST_CONNECTOR = register("chest_connector", ChestConnectorItem::new);

	private ModItems() {
	}

	/** Forces class loading, which performs the registrations above. */
	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(CHEST_CONNECTOR));
	}

	private static Item register(String path, ItemFactory factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BetterChestStorage.id(path));
		// The tool is a single unique instrument, never a stack of them.
		Item.Properties properties = new Item.Properties().stacksTo(1).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.create(properties));
	}

	@FunctionalInterface
	private interface ItemFactory {
		Item create(Item.Properties properties);
	}
}
