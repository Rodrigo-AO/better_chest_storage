package com.betterchest.storage;

import com.betterchest.BetterChestStorage;
import net.minecraft.network.chat.Component;

/** The reasons an area cannot become a chest network, each with a player facing message. */
public enum AreaRejection {

	/** The area contains something other than a chest, including air. */
	NOT_ONLY_CHESTS("not_only_chests"),

	/** The area has more chests than {@link ChestAreaValidator#MAX_CHESTS}. */
	TOO_LARGE("too_large"),

	/** The area overlaps an already connected network. */
	OVERLAPS_NETWORK("overlaps_network"),

	/** A double chest would be cut in half by the area border. */
	SPLITS_DOUBLE_CHEST("splits_double_chest");

	private final String translationKey;

	AreaRejection(String suffix) {
		this.translationKey = "message." + BetterChestStorage.MOD_ID + ".rejected." + suffix;
	}

	public Component message(Object... args) {
		return Component.translatable(translationKey, args);
	}
}
