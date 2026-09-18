package com.betterchest.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * A single {@link Container} view over every chest of an area, in top to bottom order.
 *
 * <p>This class owns no storage: every read and write goes straight through to the vanilla chest
 * block entities, so two players browsing the same network from different chests always see, and
 * compete for, the exact same items. That is what makes duplication impossible by construction.
 *
 * <p>Writes deliberately favour the top of the area. Inserts fill the highest chests first and
 * extractions drain the lowest chests first, which keeps the contents packed towards the top so
 * that disconnecting a network leaves items where the player was told they would be.
 */
public final class NetworkInventory implements Container {

	private final List<ChestBlockEntity> chests;
	private final int size;

	private NetworkInventory(List<ChestBlockEntity> chests) {
		this.chests = chests;
		this.size = chests.stream().mapToInt(Container::getContainerSize).sum();
	}

	/**
	 * @return the inventory, or empty if any position of the area no longer holds a chest, which
	 *         means the network has been broken and should be dissolved
	 */
	public static Optional<NetworkInventory> resolve(Level level, ChestArea area) {
		List<ChestBlockEntity> chests = new ArrayList<>(area.blockCount());

		for (BlockPos pos : area.positionsTopDown()) {
			if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
				return Optional.empty();
			}

			chests.add(chest);
		}

		return chests.isEmpty() ? Optional.empty() : Optional.of(new NetworkInventory(chests));
	}

	// ---------------------------------------------------------------- Container

	@Override
	public int getContainerSize() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return chests.stream().allMatch(Container::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return withSlot(slot, ChestBlockEntity::getItem, ItemStack.EMPTY);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return withSlot(slot, (chest, local) -> chest.removeItem(local, amount), ItemStack.EMPTY);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return withSlot(slot, ChestBlockEntity::removeItemNoUpdate, ItemStack.EMPTY);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		withSlot(slot, (chest, local) -> {
			chest.setItem(local, stack);
			return ItemStack.EMPTY;
		}, ItemStack.EMPTY);
	}

	@Override
	public void setChanged() {
		chests.forEach(ChestBlockEntity::setChanged);
	}

	@Override
	public boolean stillValid(Player player) {
		// Losing a chest dissolves the network, so the shared view has to close with it rather than
		// keep writing into a block entity that no longer belongs to the world. Any one of the
		// remaining chests being in reach is enough to stay open.
		return chests.stream().allMatch(NetworkInventory::isStillPlaced)
				&& chests.stream().anyMatch(chest -> Container.stillValidBlockEntity(chest, player));
	}

	private static boolean isStillPlaced(ChestBlockEntity chest) {
		Level level = chest.getLevel();
		return level != null && level.getBlockEntity(chest.getBlockPos()) == chest;
	}

	@Override
	public void clearContent() {
		chests.forEach(Container::clearContent);
	}

	// ---------------------------------------------------------------- aggregated view

	/** The network contents merged per item kind, most plentiful first. */
	public List<GridEntry> aggregate() {
		Map<Item, List<MutableEntry>> byItem = new HashMap<>();
		List<MutableEntry> entries = new ArrayList<>();

		forEachStack(stack -> {
			List<MutableEntry> sameItem = byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>());
			MutableEntry existing = sameItem.stream()
					.filter(entry -> ItemStack.isSameItemSameComponents(entry.sample, stack))
					.findFirst()
					.orElse(null);

			if (existing != null) {
				existing.total += stack.getCount();
			} else {
				MutableEntry entry = new MutableEntry(stack.copyWithCount(1), stack.getCount());
				sameItem.add(entry);
				entries.add(entry);
			}
		});

		return entries.stream()
				.sorted(Comparator.comparingLong((MutableEntry entry) -> entry.total).reversed()
						.thenComparing(entry -> entry.sample.getHoverName().getString()))
				.map(entry -> new GridEntry(entry.sample, entry.total))
				.toList();
	}

	/** How many items of the given kind the network currently holds. */
	public long countOf(ItemStack sample) {
		long[] total = {0};
		forEachStack(stack -> {
			if (ItemStack.isSameItemSameComponents(sample, stack)) {
				total[0] += stack.getCount();
			}
		});
		return total[0];
	}

	// ---------------------------------------------------------------- mutations

	/**
	 * Removes up to {@code amount} items of the given kind, draining the lowest chests first.
	 *
	 * @return the items actually removed, which may be fewer than asked for, or empty
	 */
	public ItemStack extract(ItemStack sample, int amount) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}

		ItemStack extracted = ItemStack.EMPTY;
		int remaining = amount;

		for (int slot = size - 1; slot >= 0 && remaining > 0; slot--) {
			ItemStack stack = getItem(slot);

			if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(sample, stack)) {
				continue;
			}

			ItemStack taken = removeItem(slot, remaining);

			if (extracted.isEmpty()) {
				extracted = taken;
			} else {
				extracted.grow(taken.getCount());
			}

			remaining -= taken.getCount();
		}

		if (!extracted.isEmpty()) {
			setChanged();
		}

		return extracted;
	}

	/**
	 * Stores as much of the stack as fits, topping up partial stacks before using empty slots and
	 * always working from the top of the area downwards.
	 *
	 * @return what could not be stored; the passed stack is left untouched
	 */
	public ItemStack insert(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack remainder = stack.copy();
		mergeIntoExistingStacks(remainder);
		fillEmptySlots(remainder);

		if (remainder.getCount() != stack.getCount()) {
			setChanged();
		}

		return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
	}

	private void mergeIntoExistingStacks(ItemStack remainder) {
		for (int slot = 0; slot < size && !remainder.isEmpty(); slot++) {
			ItemStack target = getItem(slot);

			if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, remainder)) {
				continue;
			}

			int room = Math.min(target.getMaxStackSize(), getMaxStackSize()) - target.getCount();

			if (room > 0) {
				int moved = Math.min(room, remainder.getCount());
				target.grow(moved);
				remainder.shrink(moved);
			}
		}
	}

	private void fillEmptySlots(ItemStack remainder) {
		for (int slot = 0; slot < size && !remainder.isEmpty(); slot++) {
			if (!getItem(slot).isEmpty()) {
				continue;
			}

			int moved = Math.min(remainder.getMaxStackSize(), remainder.getCount());
			setItem(slot, remainder.split(moved));
		}
	}

	/**
	 * Repacks the whole network towards the top, leaving no gaps. Run when a network is dissolved so
	 * the player finds the contents in the chests the mod promised.
	 */
	public void compactTopDown() {
		List<ItemStack> contents = new ArrayList<>();

		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = removeItemNoUpdate(slot);

			if (!stack.isEmpty()) {
				contents.add(stack);
			}
		}

		int cursor = 0;

		for (ItemStack stack : contents) {
			setItem(cursor++, stack);
		}

		setChanged();
	}

	// ---------------------------------------------------------------- internals

	private void forEachStack(Consumer<ItemStack> action) {
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = getItem(slot);

			if (!stack.isEmpty()) {
				action.accept(stack);
			}
		}
	}

	private ItemStack withSlot(int slot, SlotAction action, ItemStack fallback) {
		int remaining = slot;

		for (ChestBlockEntity chest : chests) {
			int chestSize = chest.getContainerSize();

			if (remaining < chestSize) {
				return action.apply(chest, remaining);
			}

			remaining -= chestSize;
		}

		return fallback;
	}

	@FunctionalInterface
	private interface SlotAction {
		ItemStack apply(ChestBlockEntity chest, int localSlot);
	}

	private static final class MutableEntry {
		private final ItemStack sample;
		private long total;

		private MutableEntry(ItemStack sample, long total) {
			this.sample = sample;
			this.total = total;
		}
	}
}
