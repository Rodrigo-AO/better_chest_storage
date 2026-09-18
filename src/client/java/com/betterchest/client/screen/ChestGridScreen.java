package com.betterchest.client.screen;

import com.betterchest.BetterChestStorage;
import com.betterchest.menu.ChestGridMenu;
import com.betterchest.menu.GridAction;
import com.betterchest.net.payload.GridActionPayload;
import com.betterchest.storage.GridEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The aggregated storage screen.
 *
 * <p>The grid is not made of slots. Each cell shows one item kind and the total the whole network
 * holds, which is routinely far more than a stack, so the cells are drawn by hand and clicks are
 * sent as intents naming the item rather than a slot index. Nothing here decides how many items the
 * player actually gets; that is settled on the server.
 */
public class ChestGridScreen extends AbstractContainerScreen<ChestGridMenu> {

	private static final Identifier BACKGROUND = BetterChestStorage.id("textures/gui/chest_grid.png");
	private static final int TEXTURE_SIZE = 256;

	private static final int IMAGE_WIDTH = 194;
	private static final int IMAGE_HEIGHT = 190;
	private static final int SLOT_SIZE = 18;

	private static final int GRID_X = 8;
	private static final int GRID_Y = 20;
	private static final int COLUMNS = ChestGridMenu.GRID_COLUMNS;
	private static final int ROWS = ChestGridMenu.GRID_ROWS;

	private static final int SCROLLBAR_X = 172;
	private static final int SCROLLBAR_Y = 20;
	private static final int SCROLLBAR_WIDTH = 14;
	private static final int SCROLLBAR_HEIGHT = ROWS * SLOT_SIZE;

	private static final int KNOB_U = 0;
	private static final int KNOB_V = IMAGE_HEIGHT + 2;
	private static final int KNOB_WIDTH = 12;
	private static final int KNOB_HEIGHT = 15;

	private static final int SEARCH_X = 96;
	private static final int SEARCH_Y = 4;
	private static final int SEARCH_WIDTH = 90;
	private static final int SEARCH_HEIGHT = 12;

	private static final int LABEL_COLOR = 0xFF404040;
	private static final int HOVER_OVERLAY = 0x80FFFFFF;

	private EditBox searchBox;

	/** The entries currently passing the search filter, in display order. */
	private List<GridEntry> visible = List.of();

	/** The entry list the filter was last built from, used to notice server pushes. */
	private List<GridEntry> filteredFrom = List.of();

	private int scrollRow;
	private boolean draggingKnob;

	public ChestGridScreen(ChestGridMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelY = IMAGE_HEIGHT - 96;
	}

	@Override
	protected void init() {
		super.init();

		searchBox = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT,
				Component.translatable("gui." + BetterChestStorage.MOD_ID + ".search"));
		searchBox.setMaxLength(64);
		searchBox.setHint(Component.translatable("gui." + BetterChestStorage.MOD_ID + ".search.hint"));
		searchBox.setResponder(query -> {
			scrollRow = 0;
			applyFilter();
		});
		addRenderableWidget(searchBox);

		applyFilter();
	}

	@Override
	protected void containerTick() {
		super.containerTick();

		// The server pushes a whole new list whenever the contents change.
		if (menu.entries() != filteredFrom) {
			applyFilter();
		}
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F,
				IMAGE_WIDTH, IMAGE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
				leftPos + SCROLLBAR_X + 1, topPos + knobTop(), KNOB_U, KNOB_V,
				KNOB_WIDTH, KNOB_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		extractGrid(graphics, mouseX, mouseY);
	}

	private void extractGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int first = scrollRow * COLUMNS;

		for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
			int index = first + cell;

			if (index >= visible.size()) {
				break;
			}

			GridEntry entry = visible.get(index);
			int x = cellX(cell % COLUMNS);
			int y = cellY(cell / COLUMNS);

			graphics.item(entry.sample(), x, y);
			graphics.itemDecorations(font, entry.sample(), x, y, formatCompact(entry.total()));

			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
				graphics.fill(x, y, x + 16, y + 16, HOVER_OVERLAY);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		GridEntry hovered = entryAt(mouseX, mouseY);

		if (hovered == null) {
			super.extractTooltip(graphics, mouseX, mouseY);
			return;
		}

		List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(hovered.sample()));
		lines.add(Component.translatable("gui." + BetterChestStorage.MOD_ID + ".stored",
				String.format(Locale.ROOT, "%,d", hovered.total())).withStyle(ChatFormatting.GRAY));

		graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();

		if (isOverScrollbar(mouseX, mouseY)) {
			draggingKnob = true;
			scrollToMouse(mouseY);
			return true;
		}

		if (isOverGrid(mouseX, mouseY)) {
			handleGridClick(event.button(), entryAt(mouseX, mouseY));
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (draggingKnob) {
			scrollToMouse((int) event.y());
			return true;
		}

		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingKnob = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isOverGrid((int) mouseX, (int) mouseY) || isOverScrollbar((int) mouseX, (int) mouseY)) {
			setScrollRow(scrollRow - (int) Math.signum(scrollY));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchBox.keyPressed(event)) {
			return true;
		}

		// While the player is typing, the inventory key is a letter, not a request to close.
		return searchBox.capturesInput() && !event.isEscape() || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return searchBox.charTyped(event) || super.charTyped(event);
	}

	@Override
	public boolean isInputCaptured() {
		return super.isInputCaptured() || searchBox.capturesInput();
	}

	private void handleGridClick(int button, @Nullable GridEntry hovered) {
		ItemStack carried = menu.getCarried();

		if (!carried.isEmpty()) {
			send(button == 1 ? GridAction.DEPOSIT_ONE : GridAction.DEPOSIT_ALL, carried);
			return;
		}

		if (hovered == null) {
			return;
		}

		GridAction action;

		if (minecraft != null && minecraft.hasShiftDown()) {
			action = GridAction.QUICK_MOVE;
		} else if (button == 1) {
			action = GridAction.TAKE_HALF;
		} else if (button == 2) {
			action = GridAction.TAKE_ONE;
		} else {
			action = GridAction.TAKE_STACK;
		}

		send(action, hovered.sample());
	}

	private void send(GridAction action, ItemStack sample) {
		ClientPlayNetworking.send(new GridActionPayload(menu.containerId, action, sample.copyWithCount(1)));
	}

	// ---------------------------------------------------------------- geometry

	private int cellX(int column) {
		return leftPos + GRID_X + ChestGridMenu.SLOT_INSET + column * SLOT_SIZE;
	}

	private int cellY(int row) {
		return topPos + GRID_Y + ChestGridMenu.SLOT_INSET + row * SLOT_SIZE;
	}

	private boolean isOverGrid(int mouseX, int mouseY) {
		int left = leftPos + GRID_X;
		int top = topPos + GRID_Y;
		return mouseX >= left && mouseX < left + COLUMNS * SLOT_SIZE
				&& mouseY >= top && mouseY < top + ROWS * SLOT_SIZE;
	}

	private boolean isOverScrollbar(int mouseX, int mouseY) {
		int left = leftPos + SCROLLBAR_X;
		int top = topPos + SCROLLBAR_Y;
		return mouseX >= left && mouseX < left + SCROLLBAR_WIDTH
				&& mouseY >= top && mouseY < top + SCROLLBAR_HEIGHT;
	}

	private @Nullable GridEntry entryAt(int mouseX, int mouseY) {
		if (!isOverGrid(mouseX, mouseY)) {
			return null;
		}

		int column = (mouseX - leftPos - GRID_X) / SLOT_SIZE;
		int row = (mouseY - topPos - GRID_Y) / SLOT_SIZE;
		int index = (scrollRow + row) * COLUMNS + column;

		return index >= 0 && index < visible.size() ? visible.get(index) : null;
	}

	// ---------------------------------------------------------------- scrolling and filtering

	private int maxScrollRow() {
		int rows = (visible.size() + COLUMNS - 1) / COLUMNS;
		return Math.max(0, rows - ROWS);
	}

	private void setScrollRow(int row) {
		scrollRow = Math.clamp(row, 0, maxScrollRow());
	}

	private int knobTop() {
		int travel = SCROLLBAR_HEIGHT - 2 - KNOB_HEIGHT;
		int max = maxScrollRow();
		int offset = max == 0 ? 0 : travel * scrollRow / max;
		return SCROLLBAR_Y + 1 + offset;
	}

	private void scrollToMouse(int mouseY) {
		int travel = SCROLLBAR_HEIGHT - 2 - KNOB_HEIGHT;

		if (travel <= 0) {
			return;
		}

		int offset = mouseY - (topPos + SCROLLBAR_Y + 1) - KNOB_HEIGHT / 2;
		setScrollRow(Math.round((float) offset / travel * maxScrollRow()));
	}

	private void applyFilter() {
		filteredFrom = menu.entries();
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);

		if (query.isEmpty()) {
			visible = filteredFrom;
		} else {
			visible = filteredFrom.stream()
					.filter(entry -> entry.sample().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
					.toList();
		}

		setScrollRow(scrollRow);
	}

	/**
	 * Shortens a total to something that fits under an item, in the same shape players already know
	 * from other storage mods: 964, 1.3K, 46K, 2.1M.
	 */
	static String formatCompact(long total) {
		if (total < 1_000) {
			return Long.toString(total);
		}

		if (total < 1_000_000) {
			return compact(total / 1_000.0, "K");
		}

		return compact(total / 1_000_000.0, "M");
	}

	private static String compact(double value, String suffix) {
		return value < 100
				? String.format(Locale.ROOT, "%.1f%s", value, suffix)
				: String.format(Locale.ROOT, "%.0f%s", value, suffix);
	}
}
