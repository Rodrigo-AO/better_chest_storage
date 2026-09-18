package com.betterchest.client.input;

import com.betterchest.client.state.ClientAreaStore;
import com.betterchest.net.payload.ConnectAreaPayload;
import com.betterchest.net.payload.ReleaseAreaPayload;
import com.betterchest.net.payload.SelectAreaPayload;
import com.betterchest.registry.ModItems;
import com.betterchest.storage.ChestArea;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the Chest Connector's mouse and modifier gestures into packets.
 *
 * <p>All of this lives on the client because modifier keys do: the server never learns whether
 * control was held, only which of the three intentions the player expressed. The gestures are:
 *
 * <ul>
 *   <li>sneak and hold right click to drag out an area, released to select it;
 *   <li>control and right click inside a selection to connect it;
 *   <li>control and left click on any highlight to take it back.
 * </ul>
 */
public final class ConnectorInput {

	/** Where the current drag started, or null when no drag is in progress. */
	private static @Nullable BlockPos anchor;

	private ConnectorInput() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(ConnectorInput::onUseBlock);
		ClientPreAttackCallback.EVENT.register(ConnectorInput::onPreAttack);
		ClientTickEvents.END_CLIENT_TICK.register(ConnectorInput::onClientTick);
	}

	// ---------------------------------------------------------------- right click

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide() || !isHoldingConnector(player, hand)) {
			return InteractionResult.PASS;
		}

		if (anchor != null) {
			// Minecraft keeps re-firing the interaction while right click is held. Swallowing the
			// repeats keeps the drag anchored where it started, whatever the player is looking at
			// and whether or not they are still holding sneak.
			return InteractionResult.FAIL;
		}

		if (Minecraft.getInstance().hasControlDown()) {
			ClientPlayNetworking.send(new ConnectAreaPayload(hit.getBlockPos()));
			return InteractionResult.FAIL;
		}

		if (player.isShiftKeyDown()) {
			anchor = hit.getBlockPos();
			ClientAreaStore.beginDrag(anchor);
			return InteractionResult.FAIL;
		}

		// No modifier: let the interaction through so the chest, or its network, opens normally.
		return InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- left click

	private static boolean onPreAttack(Minecraft client, LocalPlayer player, int clickCount) {
		if (!isHoldingConnector(player, InteractionHand.MAIN_HAND)) {
			return false;
		}

		// clickCount is zero while the button is merely held down, so this fires once per press.
		if (clickCount != 0 && client.hasControlDown()) {
			targetedBlock(client).ifPresent(pos -> ClientPlayNetworking.send(new ReleaseAreaPayload(pos)));
		}

		// The connector is a wrench, never a pickaxe: it must not break or hit anything.
		return true;
	}

	// ---------------------------------------------------------------- drag tracking

	private static void onClientTick(Minecraft client) {
		if (anchor == null) {
			return;
		}

		LocalPlayer player = client.player;

		if (player == null || !isHoldingConnector(player, InteractionHand.MAIN_HAND)) {
			cancelDrag();
			return;
		}

		if (!client.options.keyUse.isDown()) {
			finishDrag();
			return;
		}

		targetedBlock(client).ifPresent(corner -> ClientAreaStore.updateDrag(anchor, corner));
	}

	private static void finishDrag() {
		ChestArea area = ClientAreaStore.drag().orElseGet(() -> ChestArea.single(anchor));
		ClientPlayNetworking.send(new SelectAreaPayload(area.min(), area.max()));
		cancelDrag();
	}

	private static void cancelDrag() {
		anchor = null;
		ClientAreaStore.endDrag();
	}

	// ---------------------------------------------------------------- helpers

	private static boolean isHoldingConnector(Player player, InteractionHand hand) {
		return player.getItemInHand(hand).is(ModItems.CHEST_CONNECTOR);
	}

	private static Optional<BlockPos> targetedBlock(Minecraft client) {
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(hit.getBlockPos());
		}

		return Optional.empty();
	}
}
