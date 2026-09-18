package com.betterchest.menu;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/** What a click on an aggregated grid entry should do. */
public enum GridAction {

	/** Pull a full stack onto the cursor. */
	TAKE_STACK,

	/** Pull half a stack onto the cursor. */
	TAKE_HALF,

	/** Pull a single item onto the cursor. */
	TAKE_ONE,

	/** Send a stack straight to the player inventory. */
	QUICK_MOVE,

	/** Store everything the cursor is holding. */
	DEPOSIT_ALL,

	/** Store one item from the cursor. */
	DEPOSIT_ONE;

	private static final IntFunction<GridAction> BY_ID =
			ByIdMap.continuous(GridAction::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

	public static final StreamCodec<ByteBuf, GridAction> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(BY_ID::apply, GridAction::ordinal);
}
