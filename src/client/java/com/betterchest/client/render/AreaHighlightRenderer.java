package com.betterchest.client.render;

import com.betterchest.client.state.ClientAreaStore;
import com.betterchest.registry.ModItems;
import com.betterchest.storage.ChestArea;
import com.betterchest.storage.ChestNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Draws the wood coloured boxes around selections and connected networks.
 *
 * <p>Highlights are only worth showing while the player is actually holding the tool, so networks
 * stay invisible during normal play and the storage room keeps looking like a storage room. The one
 * exception is the drag in progress, which is drawn regardless since the tool is by definition in
 * hand at that moment.
 */
public final class AreaHighlightRenderer {

	/** A pending selection: warm oak, the colour of the tool itself. */
	private static final int SELECTION_OUTLINE = ARGB.color(230, 176, 137, 84);
	private static final int SELECTION_FILL = ARGB.color(60, 176, 137, 84);

	/** A live network: the darker, settled brown of a chest. */
	private static final int NETWORK_OUTLINE = ARGB.color(200, 122, 84, 38);
	private static final int NETWORK_FILL = ARGB.color(42, 122, 84, 38);

	/** The area being dragged right now, before the server has approved it. */
	private static final int DRAG_OUTLINE = ARGB.color(255, 208, 172, 117);
	private static final int DRAG_FILL = ARGB.color(48, 208, 172, 117);

	private static final float LINE_WIDTH = 3.0F;

	/**
	 * Faces are pulled very slightly out of the block so they do not fight with the chest models
	 * for the same pixels.
	 */
	private static final double FACE_INFLATION = 0.002;

	private AreaHighlightRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(AreaHighlightRenderer::collectSubmits);
	}

	private static void collectSubmits(LevelRenderContext context) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		boolean holdingConnector = player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.CHEST_CONNECTOR)
				|| player.getItemInHand(InteractionHand.OFF_HAND).is(ModItems.CHEST_CONNECTOR);

		if (!holdingConnector && ClientAreaStore.drag().isEmpty()) {
			return;
		}

		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		Vec3 camera = context.levelState().cameraRenderState.pos;

		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		if (holdingConnector) {
			for (ChestNetwork network : ClientAreaStore.networks()) {
				submitArea(poseStack, collector, network.area(), NETWORK_OUTLINE, NETWORK_FILL);
			}

			ClientAreaStore.selection().ifPresent(area ->
					submitArea(poseStack, collector, area, SELECTION_OUTLINE, SELECTION_FILL));
		}

		ClientAreaStore.drag().ifPresent(area ->
				submitArea(poseStack, collector, area, DRAG_OUTLINE, DRAG_FILL));

		poseStack.popPose();
	}

	private static void submitArea(PoseStack poseStack, SubmitNodeCollector collector, ChestArea area,
			int outlineColor, int fillColor) {
		AABB bounds = area.bounds();

		collector.submitShapeOutline(poseStack, Shapes.create(bounds), RenderTypes.linesTranslucent(),
				outlineColor, LINE_WIDTH, false);

		AABB faces = bounds.inflate(FACE_INFLATION);
		collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
				(pose, buffer) -> submitBoxFaces(pose, buffer, faces, fillColor));
	}

	private static void submitBoxFaces(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
		float x0 = (float) box.minX;
		float y0 = (float) box.minY;
		float z0 = (float) box.minZ;
		float x1 = (float) box.maxX;
		float y1 = (float) box.maxY;
		float z1 = (float) box.maxZ;

		// Down and up.
		quad(pose, buffer, color, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
		quad(pose, buffer, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);

		// North and south.
		quad(pose, buffer, color, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		quad(pose, buffer, color, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);

		// West and east.
		quad(pose, buffer, color, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
		quad(pose, buffer, color, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		buffer.addVertex(pose, ax, ay, az).setColor(color);
		buffer.addVertex(pose, bx, by, bz).setColor(color);
		buffer.addVertex(pose, cx, cy, cz).setColor(color);
		buffer.addVertex(pose, dx, dy, dz).setColor(color);
	}
}
