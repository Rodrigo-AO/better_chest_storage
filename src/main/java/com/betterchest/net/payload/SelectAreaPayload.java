package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent when the player finishes dragging with the Chest Connector.
 *
 * <p>Only the two dragged corners travel; the server decides on its own whether they describe a
 * legal block of chests.
 */
public record SelectAreaPayload(BlockPos first, BlockPos second) implements CustomPacketPayload {

	public static final Type<SelectAreaPayload> TYPE =
			new Type<>(BetterChestStorage.id("select_area"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectAreaPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SelectAreaPayload::first,
			BlockPos.STREAM_CODEC, SelectAreaPayload::second,
			SelectAreaPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
