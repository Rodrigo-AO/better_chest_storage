package com.betterchest.item;

import com.betterchest.BetterChestStorage;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Chest Connector: a wooden wrench used to draw, connect and release chest networks.
 *
 * <p>The item carries no behaviour of its own beyond the tooltip. Every gesture it takes part in
 * needs a modifier key, and modifier keys only exist on the client, so the whole interaction is
 * driven from there and reaches the server as explicit packets. Blocking the vanilla interactions
 * here keeps the tool from placing blocks or opening chests while it is being used to drag.
 */
public class ChestConnectorItem extends Item {

	private static final List<String> TOOLTIP_LINES = List.of("drag", "connect", "release", "overview");

	public ChestConnectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		// The client handles every gesture and cancels the interaction before it is sent, so
		// reaching this point means something else triggered the use. Swallow it either way.
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
			net.minecraft.core.BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
		// Left click is a mod gesture, so the tool must never start breaking anything.
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		for (String line : TOOLTIP_LINES) {
			lines.accept(Component.translatable("tooltip." + BetterChestStorage.MOD_ID + ".chest_connector." + line)
					.withStyle(ChatFormatting.GRAY));
		}
	}
}
