package com.betterchest.registry;

import com.betterchest.net.payload.AreaSyncPayload;
import com.betterchest.net.payload.ConnectAreaPayload;
import com.betterchest.net.payload.GridActionPayload;
import com.betterchest.net.payload.GridContentsPayload;
import com.betterchest.net.payload.ReleaseAreaPayload;
import com.betterchest.net.payload.SelectAreaPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registration of every packet the mod exchanges, on both directions of the play phase. */
public final class ModPayloads {

	private ModPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SelectAreaPayload.TYPE, SelectAreaPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConnectAreaPayload.TYPE, ConnectAreaPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ReleaseAreaPayload.TYPE, ReleaseAreaPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GridActionPayload.TYPE, GridActionPayload.STREAM_CODEC);

		PayloadTypeRegistry.clientboundPlay().register(AreaSyncPayload.TYPE, AreaSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GridContentsPayload.TYPE, GridContentsPayload.STREAM_CODEC);
	}
}
