package com.betterchest.client.state;

import com.betterchest.net.payload.AreaSyncPayload;
import com.betterchest.storage.ChestArea;
import com.betterchest.storage.ChestNetwork;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the client knows about chest areas: what the server told it, plus the drag currently
 * under the player's cursor.
 *
 * <p>This is purely a view. Nothing here is authoritative, and the server overwrites the synced
 * halves whenever anything changes, so the worst a stale entry can cause is a highlight that is one
 * packet out of date.
 */
public final class ClientAreaStore {

	private static List<ChestNetwork> networks = List.of();
	private static @Nullable ChestArea selection;
	private static @Nullable ChestArea drag;

	private ClientAreaStore() {
	}

	// ---------------------------------------------------------------- synced state

	public static List<ChestNetwork> networks() {
		return networks;
	}

	public static Optional<ChestArea> selection() {
		return Optional.ofNullable(selection);
	}

	public static void accept(AreaSyncPayload payload) {
		networks = payload.networks();
		selection = payload.selection().orElse(null);
	}

	/** Drops everything when leaving a world, so nothing leaks into the next one. */
	public static void clear() {
		networks = List.of();
		selection = null;
		drag = null;
	}

	// ---------------------------------------------------------------- drag preview

	public static Optional<ChestArea> drag() {
		return Optional.ofNullable(drag);
	}

	public static void beginDrag(BlockPos anchor) {
		drag = ChestArea.single(anchor);
	}

	public static void updateDrag(BlockPos anchor, BlockPos corner) {
		drag = ChestArea.between(anchor, corner);
	}

	public static void endDrag() {
		drag = null;
	}
}
