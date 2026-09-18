package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent when the player confirms their pending selection, turning it into a network. */
public record ConnectAreaPayload(BlockPos target) implements CustomPacketPayload {

	public static final Type<ConnectAreaPayload> TYPE =
			new Type<>(BetterChestStorage.id("connect_area"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConnectAreaPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ConnectAreaPayload::target,
			ConnectAreaPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
