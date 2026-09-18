package com.betterchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

/**
 * A group of chests whose contents are presented and mutated as a single inventory.
 *
 * <p>A network owns no items of its own: it is purely the identity of an {@link ChestArea}. The
 * items always live in the vanilla chest block entities inside that area, which is what keeps the
 * shared storage impossible to duplicate from.
 */
public record ChestNetwork(UUID id, ChestArea area) {

	public static final Codec<ChestNetwork> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(ChestNetwork::id),
			ChestArea.CODEC.fieldOf("area").forGetter(ChestNetwork::area)
	).apply(instance, ChestNetwork::new));

	public static final StreamCodec<ByteBuf, ChestNetwork> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ChestNetwork::id,
			ChestArea.STREAM_CODEC, ChestNetwork::area,
			ChestNetwork::new);

	public static ChestNetwork of(ChestArea area) {
		return new ChestNetwork(UUID.randomUUID(), area);
	}

	public boolean covers(BlockPos pos) {
		return area.contains(pos);
	}
}
