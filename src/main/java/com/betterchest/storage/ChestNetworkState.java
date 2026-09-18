package com.betterchest.storage;

import com.betterchest.BetterChestStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per dimension persistent store of every connected network and every pending selection.
 *
 * <p>Selections are kept here rather than on the player so that a half finished selection survives
 * a relog or a server restart, which is what a player expects from a highlight that stays visible
 * in the world.
 */
public final class ChestNetworkState extends SavedData {

	private static final Codec<ChestNetworkState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ChestNetwork.CODEC.listOf().optionalFieldOf("networks", List.of()).forGetter(state -> state.networks),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, ChestArea.CODEC)
					.optionalFieldOf("selections", Map.of()).forGetter(state -> state.selections)
	).apply(instance, ChestNetworkState::new));

	private static final SavedDataType<ChestNetworkState> TYPE = new SavedDataType<>(
			BetterChestStorage.id("chest_networks"),
			ChestNetworkState::new,
			CODEC,
			DataFixTypes.LEVEL);

	private final List<ChestNetwork> networks;
	private final Map<UUID, ChestArea> selections;

	private ChestNetworkState() {
		this(List.of(), Map.of());
	}

	private ChestNetworkState(List<ChestNetwork> networks, Map<UUID, ChestArea> selections) {
		this.networks = new ArrayList<>(networks);
		this.selections = new HashMap<>(selections);
	}

	public static ChestNetworkState of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public List<ChestNetwork> networks() {
		return Collections.unmodifiableList(networks);
	}

	public Optional<ChestNetwork> networkAt(BlockPos pos) {
		return networks.stream().filter(network -> network.covers(pos)).findFirst();
	}

	public void addNetwork(ChestNetwork network) {
		networks.add(network);
		setDirty();
	}

	public void removeNetwork(ChestNetwork network) {
		if (networks.removeIf(candidate -> candidate.id().equals(network.id()))) {
			setDirty();
		}
	}

	public Map<UUID, ChestArea> selections() {
		return Collections.unmodifiableMap(selections);
	}

	public Optional<ChestArea> selectionOf(UUID playerId) {
		return Optional.ofNullable(selections.get(playerId));
	}

	public void setSelection(UUID playerId, ChestArea area) {
		selections.put(playerId, area);
		setDirty();
	}

	public void clearSelection(UUID playerId) {
		if (selections.remove(playerId) != null) {
			setDirty();
		}
	}

	/**
	 * Drops every selection that overlaps the given area. Used when an area is connected or when a
	 * chest is destroyed, so no player is left holding a highlight over blocks that moved on.
	 */
	public void clearSelectionsOverlapping(ChestArea area) {
		if (selections.values().removeIf(selection -> selection.intersects(area))) {
			setDirty();
		}
	}
}
