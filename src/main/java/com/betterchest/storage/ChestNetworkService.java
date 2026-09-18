package com.betterchest.storage;

import com.betterchest.BetterChestStorage;
import com.betterchest.menu.ChestGridMenu;
import com.betterchest.net.payload.AreaSyncPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Every server side operation the Chest Connector can trigger.
 *
 * <p>All of them are authoritative: the client sends intent, this class decides. Each one that
 * changes what should be drawn ends by resyncing the affected players, so highlights can never
 * drift away from the real state.
 */
public final class ChestNetworkService {

	private ChestNetworkService() {
	}

	// ---------------------------------------------------------------- selecting

	/** Turns a finished drag into a pending selection, or tells the player why it cannot be one. */
	public static void select(ServerPlayer player, BlockPos first, BlockPos second) {
		ServerLevel level = player.level();
		ChestNetworkState state = ChestNetworkState.of(level);
		ChestArea area = ChestArea.between(first, second);

		Optional<AreaRejection> rejection = ChestAreaValidator.validate(level, area, state);

		if (rejection.isPresent()) {
			notify(player, rejection.get().message(ChestAreaValidator.MAX_CHESTS));
			return;
		}

		state.setSelection(player.getUUID(), area);
		notify(player, message("selected", area.blockCount()));
		playFeedback(player, true);
		syncTo(player);
	}

	// ---------------------------------------------------------------- connecting

	/** Promotes the player's pending selection into a live network. */
	public static void connect(ServerPlayer player, BlockPos target) {
		ServerLevel level = player.level();
		ChestNetworkState state = ChestNetworkState.of(level);
		Optional<ChestArea> selection = state.selectionOf(player.getUUID())
				.filter(area -> area.contains(target));

		if (selection.isEmpty()) {
			notify(player, message("no_selection"));
			return;
		}

		ChestArea area = selection.get();
		Optional<AreaRejection> rejection = ChestAreaValidator.validate(level, area, state);

		if (rejection.isPresent()) {
			// The world may have changed since the drag; drop the stale highlight.
			state.clearSelection(player.getUUID());
			notify(player, rejection.get().message(ChestAreaValidator.MAX_CHESTS));
			syncTo(player);
			return;
		}

		state.clearSelectionsOverlapping(area);
		state.addNetwork(ChestNetwork.of(area));
		notify(player, message("connected", area.blockCount()));
		playFeedback(player, true);
		syncEveryone(level);
	}

	// ---------------------------------------------------------------- releasing

	/**
	 * Undoes whatever highlight the player is pointing at: their own pending selection if the block
	 * belongs to one, otherwise the network covering it.
	 */
	public static void release(ServerPlayer player, BlockPos target) {
		ServerLevel level = player.level();
		ChestNetworkState state = ChestNetworkState.of(level);

		if (state.selectionOf(player.getUUID()).filter(area -> area.contains(target)).isPresent()) {
			state.clearSelection(player.getUUID());
			notify(player, message("deselected"));
			playFeedback(player, false);
			syncTo(player);
			return;
		}

		Optional<ChestNetwork> network = state.networkAt(target);

		if (network.isEmpty()) {
			notify(player, message("nothing_to_release"));
			return;
		}

		disconnect(level, state, network.get());
		notify(player, message("disconnected", network.get().area().blockCount()));
		playFeedback(player, false);
		syncEveryone(level);
	}

	/**
	 * Dissolves a network, first repacking its contents towards the top chests so the player finds
	 * them where the tool promised they would be.
	 */
	private static void disconnect(ServerLevel level, ChestNetworkState state, ChestNetwork network) {
		NetworkInventory.resolve(level, network.area()).ifPresent(NetworkInventory::compactTopDown);
		state.removeNetwork(network);
	}

	// ---------------------------------------------------------------- opening

	/**
	 * Opens the aggregated screen if the block belongs to a network.
	 *
	 * @return true when the interaction was handled and vanilla should not open the chest
	 */
	public static boolean openNetwork(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		ChestNetworkState state = ChestNetworkState.of(level);
		Optional<ChestNetwork> network = state.networkAt(pos);

		if (network.isEmpty()) {
			return false;
		}

		Optional<NetworkInventory> inventory = NetworkInventory.resolve(level, network.get().area());

		if (inventory.isEmpty()) {
			// A chest went missing without us noticing; fall back to plain vanilla behaviour.
			state.removeNetwork(network.get());
			notify(player, message("network_broken"));
			syncEveryone(level);
			return false;
		}

		player.openMenu(new NetworkMenuProvider(network.get(), inventory.get()));
		return true;
	}

	// ---------------------------------------------------------------- upkeep

	/** Dissolves the network covering a position, used when one of its chests is destroyed. */
	public static void dissolveAt(ServerLevel level, BlockPos pos) {
		ChestNetworkState state = ChestNetworkState.of(level);
		ChestArea broken = ChestArea.single(pos);

		boolean affected = state.networkAt(pos).isPresent()
				|| state.selections().values().stream().anyMatch(area -> area.intersects(broken));

		if (!affected) {
			// Chests are broken all the time; only say something when it mattered.
			return;
		}

		state.networkAt(pos).ifPresent(network -> {
			state.removeNetwork(network);
			BetterChestStorage.LOGGER.debug("Dissolved chest network {} after a chest was removed at {}",
					network.id(), pos);
		});

		state.clearSelectionsOverlapping(broken);
		syncEveryone(level);
	}

	// ---------------------------------------------------------------- syncing

	/** Pushes the highlights one player should be drawing. */
	public static void syncTo(ServerPlayer player) {
		ChestNetworkState state = ChestNetworkState.of(player.level());
		ServerPlayNetworking.send(player,
				new AreaSyncPayload(state.networks(), state.selectionOf(player.getUUID())));
	}

	/** Pushes the highlights to everyone currently in the level. */
	public static void syncEveryone(ServerLevel level) {
		PlayerLookup.level(level).forEach(ChestNetworkService::syncTo);
	}

	// ---------------------------------------------------------------- helpers

	private static Component message(String key, Object... args) {
		return Component.translatable("message." + BetterChestStorage.MOD_ID + "." + key, args);
	}

	private static void notify(ServerPlayer player, Component component) {
		player.sendOverlayMessage(component);
	}

	private static void playFeedback(ServerPlayer player, boolean positive) {
		player.level().playSound(null, player.blockPosition(),
				positive ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.ITEM_FRAME_REMOVE_ITEM,
				SoundSource.PLAYERS, 0.6F, positive ? 1.2F : 0.9F);
	}

	/**
	 * Hands the client the area alongside the usual open packet, so the screen can name itself
	 * after the network it belongs to without an extra round trip.
	 */
	private record NetworkMenuProvider(ChestNetwork network, NetworkInventory inventory)
			implements ExtendedMenuProvider<ChestArea> {

		@Override
		public ChestArea getScreenOpeningData(ServerPlayer player) {
			return network.area();
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("container." + BetterChestStorage.MOD_ID + ".chest_grid");
		}

		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
			return new ChestGridMenu(containerId, playerInventory, network.area(), inventory,
					(ServerPlayer) player);
		}
	}
}
