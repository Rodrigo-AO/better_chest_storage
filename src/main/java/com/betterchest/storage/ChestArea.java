package com.betterchest.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

/**
 * An axis-aligned, inclusive cuboid of block positions.
 *
 * <p>Instances are always normalised, so {@link #min()} is componentwise smaller than or equal to
 * {@link #max()} no matter which order the two corners were supplied in.
 */
public record ChestArea(BlockPos min, BlockPos max) {

	public static final Codec<ChestArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("min").forGetter(ChestArea::min),
			BlockPos.CODEC.fieldOf("max").forGetter(ChestArea::max)
	).apply(instance, ChestArea::new));

	public static final StreamCodec<ByteBuf, ChestArea> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ChestArea::min,
			BlockPos.STREAM_CODEC, ChestArea::max,
			ChestArea::new);

	/**
	 * Orders the blocks of an area the way the mod presents them: top layer first, and within a
	 * layer from lowest to highest X and then Z. Items are always inserted following this order so
	 * that a network which is later disconnected leaves its contents in the topmost chests.
	 */
	private static final Comparator<BlockPos> TOP_DOWN =
			Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
					.thenComparingInt(BlockPos::getX)
					.thenComparingInt(BlockPos::getZ);

	public ChestArea {
		BlockPos lower = new BlockPos(
				Math.min(min.getX(), max.getX()),
				Math.min(min.getY(), max.getY()),
				Math.min(min.getZ(), max.getZ()));
		BlockPos upper = new BlockPos(
				Math.max(min.getX(), max.getX()),
				Math.max(min.getY(), max.getY()),
				Math.max(min.getZ(), max.getZ()));
		min = lower;
		max = upper;
	}

	public static ChestArea between(BlockPos first, BlockPos second) {
		return new ChestArea(first, second);
	}

	public static ChestArea single(BlockPos pos) {
		return new ChestArea(pos, pos);
	}

	public int sizeX() {
		return max.getX() - min.getX() + 1;
	}

	public int sizeY() {
		return max.getY() - min.getY() + 1;
	}

	public int sizeZ() {
		return max.getZ() - min.getZ() + 1;
	}

	public int blockCount() {
		return sizeX() * sizeY() * sizeZ();
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
				&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	public boolean intersects(ChestArea other) {
		return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
				&& min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
				&& min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
	}

	/** Every position in the area, ordered top layer first. */
	public List<BlockPos> positionsTopDown() {
		List<BlockPos> positions = new ArrayList<>(blockCount());
		BlockPos.betweenClosedStream(min, max).forEach(pos -> positions.add(pos.immutable()));
		positions.sort(TOP_DOWN);
		return positions;
	}

	/** The rendered bounds of the area, covering the full volume of the outer blocks. */
	public AABB bounds() {
		return new AABB(
				min.getX(), min.getY(), min.getZ(),
				max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
	}

	public BlockPos center() {
		return new BlockPos(
				(min.getX() + max.getX()) / 2,
				(min.getY() + max.getY()) / 2,
				(min.getZ() + max.getZ()) / 2);
	}
}
