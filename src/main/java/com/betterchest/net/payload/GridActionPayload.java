package com.betterchest.net.payload;

import com.betterchest.BetterChestStorage;
import com.betterchest.menu.GridAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * A click on one of the aggregated grid entries.
 *
 * <p>The sample identifies what the player clicked rather than where, so a grid that scrolled or
 * reordered between the click and its arrival can never make the server act on the wrong item.
 *
 * @param containerId the menu the click belongs to, checked so a stale screen cannot reach a new one
 * @param action      what the click should do
 * @param sample      the item kind that was clicked
 */
public record GridActionPayload(int containerId, GridAction action, ItemStack sample) implements CustomPacketPayload {

	public static final Type<GridActionPayload> TYPE =
			new Type<>(BetterChestStorage.id("grid_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GridActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GridActionPayload::containerId,
			GridAction.STREAM_CODEC, GridActionPayload::action,
			ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, GridActionPayload::sample,
			GridActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
