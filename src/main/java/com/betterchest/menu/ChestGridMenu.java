package com.betterchest.menu;

import com.betterchest.net.payload.GridContentsPayload;
import com.betterchest.registry.ModMenus;
import com.betterchest.storage.ChestArea;
import com.betterchest.storage.GridEntry;
import com.betterchest.storage.NetworkInventory;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The menu behind the aggregated storage screen.
 *
 * <p>Only the player inventory is exposed as real slots. The network contents are not slots at all
 * but an aggregated list that the server recomputes from the chests and pushes to the client, and
 * every interaction with that list arrives as an explicit {@link GridAction}. Modelling it this way
 * means the client never holds authority over the shared storage: it can ask for ten diamonds, but
 * only the server decides whether ten diamonds are still there.
 */
public class ChestGridMenu extends AbstractContainerMenu {

	public static final int GRID_COLUMNS = 9;
	public static final int GRID_ROWS = 4;

	/**
	 * How often the server rebuilds the aggregated view while the screen sits idle. Rebuilding
	 * walks every slot of every chest, so doing it four times a second rather than twenty keeps
	 * large networks cheap while still reacting to hoppers quickly enough to notice.
	 */
	private static final int RESYNC_INTERVAL_TICKS = 5;

	private static final int SLOT_SIZE = 18;

	/** An 18x18 slot frame has a one pixel border, so the 16x16 item sits one pixel inside it. */
	public static final int SLOT_INSET = 1;

	private static final int PLAYER_INVENTORY_X = 8;
	private static final int PLAYER_INVENTORY_Y = 106;
	private static final int HOTBAR_Y = 164;
	private static final int PLAYER_INVENTORY_ROWS = 3;

	private final ChestArea area;

	/** Server side only: the live view over the chests. Null on the client. */
	private final @Nullable NetworkInventory inventory;

	/** Server side only: who the aggregated view is pushed to. Null on the client. */
	private final @Nullable ServerPlayer viewer;

	private List<GridEntry> entries = List.of();
	private int ticksUntilResync;

	/** Client side constructor, used by the menu type when the screen opens. */
	public ChestGridMenu(int containerId, Inventory playerInventory, ChestArea area) {
		this(containerId, playerInventory, area, null, null);
	}

	/**
	 * Server side constructor. The first {@link #broadcastChanges()} tick pushes the initial
	 * contents, by which time the client has had its screen opened.
	 */
	public ChestGridMenu(int containerId, Inventory playerInventory, ChestArea area,
			@Nullable NetworkInventory inventory, @Nullable ServerPlayer viewer) {
		super(ModMenus.CHEST_GRID, containerId);
		this.area = area;
		this.inventory = inventory;
		this.viewer = viewer;

		addPlayerInventorySlots(playerInventory);
	}

	private void addPlayerInventorySlots(Inventory playerInventory) {
		for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
			for (int column = 0; column < GRID_COLUMNS; column++) {
				int index = GRID_COLUMNS + row * GRID_COLUMNS + column;
				addSlot(new Slot(playerInventory, index,
						PLAYER_INVENTORY_X + SLOT_INSET + column * SLOT_SIZE,
						PLAYER_INVENTORY_Y + SLOT_INSET + row * SLOT_SIZE));
			}
		}

		for (int column = 0; column < GRID_COLUMNS; column++) {
			addSlot(new Slot(playerInventory, column,
					PLAYER_INVENTORY_X + SLOT_INSET + column * SLOT_SIZE, HOTBAR_Y + SLOT_INSET));
		}
	}

	public ChestArea area() {
		return area;
	}

	/** The aggregated network contents as last known to this side. */
	public List<GridEntry> entries() {
		return entries;
	}

	/** Applies an update pushed by the server. */
	public void acceptEntries(List<GridEntry> entries) {
		this.entries = entries;
	}

	// ---------------------------------------------------------------- vanilla menu contract

	@Override
	public boolean stillValid(Player player) {
		return inventory == null || inventory.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (inventory == null) {
			return ItemStack.EMPTY;
		}

		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack remainder = inventory.insert(stack);

		if (remainder.getCount() == stack.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.setByPlayer(remainder);
		refreshEntries();

		// Returning empty stops the vanilla "keep shift clicking" loop, which has no meaning here:
		// a single call already moved everything that fitted.
		return ItemStack.EMPTY;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();

		if (inventory == null) {
			return;
		}

		if (--ticksUntilResync <= 0) {
			refreshEntries();
		}
	}

	// ---------------------------------------------------------------- grid interactions

	/**
	 * Applies a click on an aggregated entry. Called on the server with a sample the client asked
	 * for; everything is re-checked against the live chests, so a stale client view can only ever
	 * yield fewer items than it expected, never more.
	 */
	public void applyGridAction(GridAction action, ItemStack sample) {
		if (inventory == null || sample.isEmpty()) {
			return;
		}

		switch (action) {
			case TAKE_STACK -> takeOntoCursor(sample, sample.getMaxStackSize());
			case TAKE_HALF -> takeOntoCursor(sample, halfStackOf(sample));
			case TAKE_ONE -> takeOntoCursor(sample, 1);
			case QUICK_MOVE -> takeIntoPlayerInventory(sample);
			case DEPOSIT_ALL -> depositFromCursor(getCarried().getCount());
			case DEPOSIT_ONE -> depositFromCursor(1);
		}

		refreshEntries();
		broadcastChanges();
	}

	private int halfStackOf(ItemStack sample) {
		int available = (int) Math.min(inventory.countOf(sample), sample.getMaxStackSize());
		return Math.max(1, (available + 1) / 2);
	}

	private void takeOntoCursor(ItemStack sample, int requested) {
		ItemStack carried = getCarried();
		int amount = requested;

		if (!carried.isEmpty()) {
			if (!ItemStack.isSameItemSameComponents(carried, sample)) {
				return;
			}

			amount = Math.min(amount, carried.getMaxStackSize() - carried.getCount());
		}

		ItemStack taken = inventory.extract(sample, amount);

		if (taken.isEmpty()) {
			return;
		}

		if (carried.isEmpty()) {
			setCarried(taken);
		} else {
			carried.grow(taken.getCount());
			setCarried(carried);
		}
	}

	private void takeIntoPlayerInventory(ItemStack sample) {
		if (viewer == null) {
			return;
		}

		ItemStack taken = inventory.extract(sample, sample.getMaxStackSize());

		if (taken.isEmpty()) {
			return;
		}

		viewer.getInventory().add(taken);

		// Whatever the player had no room for goes straight back, so nothing is ever destroyed.
		if (!taken.isEmpty()) {
			inventory.insert(taken);
		}
	}

	private void depositFromCursor(int requested) {
		ItemStack carried = getCarried();

		if (carried.isEmpty() || requested <= 0) {
			return;
		}

		ItemStack offered = carried.copyWithCount(Math.min(requested, carried.getCount()));
		ItemStack remainder = inventory.insert(offered);
		int stored = offered.getCount() - remainder.getCount();

		if (stored <= 0) {
			return;
		}

		carried.shrink(stored);
		setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
	}

	// ---------------------------------------------------------------- syncing

	private void refreshEntries() {
		if (inventory == null) {
			return;
		}

		ticksUntilResync = RESYNC_INTERVAL_TICKS;
		List<GridEntry> rebuilt = inventory.aggregate();

		if (rebuilt.equals(entries)) {
			return;
		}

		entries = rebuilt;

		if (viewer != null) {
			ServerPlayNetworking.send(viewer, new GridContentsPayload(containerId, entries));
		}
	}
}
