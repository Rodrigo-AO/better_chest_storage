package com.betterchest.storage;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Decides whether a dragged out area may become a selection, and later a network.
 *
 * <p>The rules are deliberately strict: an area must be filled edge to edge with chests and nothing
 * else, because a selection that silently ignored the odd stone block would make it impossible for
 * the player to tell which chests actually ended up sharing their storage.
 */
public final class ChestAreaValidator {

	/**
	 * Upper bound on the chests one network may span. Large enough for any sensible storage room,
	 * small enough that rebuilding the aggregated grid stays cheap.
	 */
	public static final int MAX_CHESTS = 256;

	private ChestAreaValidator() {
	}

	/**
	 * @return the reason the area cannot be used, or empty when it is a valid block of chests.
	 */
	public static Optional<AreaRejection> validate(Level level, ChestArea area, ChestNetworkState state) {
		if (area.blockCount() > MAX_CHESTS) {
			return Optional.of(AreaRejection.TOO_LARGE);
		}

		for (BlockPos pos : area.positionsTopDown()) {
			BlockState blockState = level.getBlockState(pos);

			if (!(blockState.getBlock() instanceof ChestBlock)) {
				return Optional.of(AreaRejection.NOT_ONLY_CHESTS);
			}

			if (splitsDoubleChest(area, pos, blockState)) {
				return Optional.of(AreaRejection.SPLITS_DOUBLE_CHEST);
			}
		}

		boolean overlaps = state.networks().stream().anyMatch(network -> network.area().intersects(area));
		return overlaps ? Optional.of(AreaRejection.OVERLAPS_NETWORK) : Optional.empty();
	}

	/**
	 * A double chest whose other half sits outside the area would still be openable as a plain
	 * vanilla double chest, showing network contents through a window the mod does not control.
	 * Rather than patch that hole we refuse the area and let the player redraw it.
	 */
	private static boolean splitsDoubleChest(ChestArea area, BlockPos pos, BlockState state) {
		if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return false;
		}

		return !area.contains(ChestBlock.getConnectedBlockPos(pos, state));
	}
}
