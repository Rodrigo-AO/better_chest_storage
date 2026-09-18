package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import com.betterchest.storage.ChestArea;
import com.betterchest.storage.ChestNetwork;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The highlights one player should be drawing: every network of their dimension plus their own
 * pending selection, which nobody else can see.
 */
public record AreaSyncPayload(List<ChestNetwork> networks, Optional<ChestArea> selection) implements CustomPacketPayload {

	public static final Type<AreaSyncPayload> TYPE =
			new Type<>(BetterChestStorage.id("area_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AreaSyncPayload> STREAM_CODEC = StreamCodec.composite(
			ChestNetwork.STREAM_CODEC.apply(ByteBufCodecs.list()), AreaSyncPayload::networks,
			ByteBufCodecs.optional(ChestArea.STREAM_CODEC), AreaSyncPayload::selection,
			AreaSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
