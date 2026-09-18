package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import com.betterchest.storage.GridEntry;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The aggregated contents of an open network, pushed whenever they change. */
public record GridContentsPayload(int containerId, List<GridEntry> entries) implements CustomPacketPayload {

	public static final Type<GridContentsPayload> TYPE =
			new Type<>(BetterChestStorage.id("grid_contents"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GridContentsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GridContentsPayload::containerId,
			GridEntry.LIST_STREAM_CODEC, GridContentsPayload::entries,
			GridContentsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
