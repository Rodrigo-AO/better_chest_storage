package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent when the player undoes a highlighted area.
 *
 * <p>One packet covers both halves of the gesture, because from the player's side it is the same
 * action: point at a highlight and take it back. The server decides whether the highlight under the
 * cursor is a pending selection to drop or a live network to dissolve.
 */
public record ReleaseAreaPayload(BlockPos target) implements CustomPacketPayload {

	public static final Type<ReleaseAreaPayload> TYPE =
			new Type<>(BetterChestStorage.id("release_area"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseAreaPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ReleaseAreaPayload::target,
			ReleaseAreaPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
