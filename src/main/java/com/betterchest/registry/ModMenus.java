package com.betterchest.registry;

import com.betterchest.BetterChestStorage;
import com.betterchest.menu.ChestGridMenu;
import com.betterchest.storage.ChestArea;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registration of the mod's menu types. */
public final class ModMenus {

	/**
	 * The aggregated storage screen. The area travels with the open packet so the client can label
	 * the screen and draw the right highlight without a second round trip.
	 */
	public static final ExtendedMenuType<ChestGridMenu, ChestArea> CHEST_GRID = Registry.register(
			BuiltInRegistries.MENU,
			BetterChestStorage.id("chest_grid"),
			new ExtendedMenuType<>(ChestGridMenu::new, ChestArea.STREAM_CODEC));

	private ModMenus() {
	}

	/** Forces class loading, which performs the registrations above. */
	public static void register() {
	}
}
