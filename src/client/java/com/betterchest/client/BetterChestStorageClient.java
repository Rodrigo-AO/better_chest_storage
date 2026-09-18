package com.betterchest.client;

import com.betterchest.client.input.ConnectorInput;
import com.betterchest.client.render.AreaHighlightRenderer;
import com.betterchest.client.screen.ChestGridScreen;
import com.betterchest.client.state.ClientAreaStore;
import com.betterchest.net.payload.AreaSyncPayload;
import com.betterchest.net.payload.GridContentsPayload;
import com.betterchest.menu.ChestGridMenu;
import com.betterchest.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client entry point: the screen, the packet receivers, the gestures and the highlights. */
public final class BetterChestStorageClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.CHEST_GRID, ChestGridScreen::new);

		registerReceivers();
		ConnectorInput.register();
		AreaHighlightRenderer.register();
	}

	private static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(AreaSyncPayload.TYPE, (payload, context) ->
				ClientAreaStore.accept(payload));

		ClientPlayNetworking.registerGlobalReceiver(GridContentsPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof ChestGridMenu menu
					&& menu.containerId == payload.containerId()) {
				menu.acceptEntries(payload.entries());
			}
		});

		// Highlights belong to a world, so they leave with it.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientAreaStore.clear());
	}
}
