package com.betterchest.storage;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One line of the aggregated grid: a single item kind plus how many of it the whole network holds.
 *
 * <p>The total is kept apart from the stack because a network can easily hold far more of an item
 * than a single {@link ItemStack} is meant to represent.
 *
 * @param sample a one item stack carrying the kind and components shown in the grid
 * @param total  how many of that kind the network holds in total
 */
public record GridEntry(ItemStack sample, long total) {

	public static final StreamCodec<RegistryFriendlyByteBuf, GridEntry> STREAM_CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, GridEntry::sample,
			ByteBufCodecs.VAR_LONG, GridEntry::total,
			GridEntry::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, List<GridEntry>> LIST_STREAM_CODEC =
			STREAM_CODEC.apply(ByteBufCodecs.list());

	public GridEntry {
		sample = sample.copyWithCount(1);
	}

	public boolean matches(ItemStack stack) {
		return ItemStack.isSameItemSameComponents(sample, stack);
	}

	/*
	 * ItemStack compares by identity, so the equality a record would generate is useless here: two
	 * entries describing the same items would never test equal, and the server would push a "new"
	 * grid to every open screen several times a second. Comparing by value fixes that.
	 */

	@Override
	public boolean equals(Object other) {
		return other instanceof GridEntry entry
				&& total == entry.total
				&& ItemStack.isSameItemSameComponents(sample, entry.sample);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sample.getItem(), sample.getComponents(), total);
	}
}
