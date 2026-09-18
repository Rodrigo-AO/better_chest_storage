package com.betterchest.net;

import com.betterchest.menu.ChestGridMenu;
import com.betterchest.net.payload.ConnectAreaPayload;
import com.betterchest.net.payload.GridActionPayload;
import com.betterchest.net.payload.ReleaseAreaPayload;
import com.betterchest.net.payload.SelectAreaPayload;
import com.betterchest.storage.ChestNetworkService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Wires the server side of the mod: the packets it accepts and the world events it reacts to.
 *
 * <p>Handlers here stay thin on purpose. They validate that a packet could plausibly have come from
 * the player who sent it and then hand over to {@link ChestNetworkService}, which owns the rules.
 */
public final class ServerNetworkHandlers {

	private ServerNetworkHandlers() {
	}

	public static void register() {
		registerPacketHandlers();
		registerWorldHandlers();
		registerSyncTriggers();
	}

	private static void registerPacketHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectAreaPayload.TYPE, (payload, context) ->
				ChestNetworkService.select(context.player(), payload.first(), payload.second()));

		ServerPlayNetworking.registerGlobalReceiver(ConnectAreaPayload.TYPE, (payload, context) ->
				ChestNetworkService.connect(context.player(), payload.target()));

		ServerPlayNetworking.registerGlobalReceiver(ReleaseAreaPayload.TYPE, (payload, context) ->
				ChestNetworkService.release(context.player(), payload.target()));

		ServerPlayNetworking.registerGlobalReceiver(GridActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// Only ever act on the menu the player actually has open, so a screen that was closed
			// or replaced cannot reach into a network the player is no longer standing at.
			if (player.containerMenu instanceof ChestGridMenu menu
					&& menu.containerId == payload.containerId()) {
				menu.applyGridAction(payload.action(), payload.sample());
			}
		});
	}

	private static void registerWorldHandlers() {
		// Opening any chest of a network shows the shared grid instead of that single chest.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || player.isSecondaryUseActive()
					|| !(player instanceof ServerPlayer serverPlayer)
					|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ChestBlock)) {
				return InteractionResult.PASS;
			}

			return ChestNetworkService.openNetwork(serverPlayer, hit.getBlockPos())
					? InteractionResult.SUCCESS_SERVER
					: InteractionResult.PASS;
		});

		// A network is only ever as real as its chests: lose one and the whole thing dissolves.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && state.getBlock() instanceof ChestBlock) {
				ChestNetworkService.dissolveAt(serverLevel, pos);
			}
		});
	}

	private static void registerSyncTriggers() {
		ServerPlayerEvents.JOIN.register(ChestNetworkService::syncTo);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				ChestNetworkService.syncTo(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
				ChestNetworkService.syncTo(player));
	}
}
