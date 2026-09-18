package com.betterchest;

import com.betterchest.net.ServerNetworkHandlers;
import com.betterchest.registry.ModItems;
import com.betterchest.registry.ModMenus;
import com.betterchest.registry.ModPayloads;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point shared by both sides.
 *
 * <p>The mod adds a single tool, the Chest Connector, which lets a player fuse a solid block of
 * chests into one shared inventory without pipes, controllers or any other visible machinery.
 */
public final class BetterChestStorage implements ModInitializer {

	public static final String MOD_ID = "better_chest_storage";

	public static final Logger LOGGER = LoggerFactory.getLogger("Better Chest Storage");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.register();
		ModMenus.register();
		ModPayloads.register();
		ServerNetworkHandlers.register();
	}
}
