package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;

/**
 * Shepherd's loom: how many pattern layers a banner can get. Vanilla stops at 6; the shepherd's station
 * allows {@code perks.loomLayersByLevel} (6 / 8 / 12). Copying a banner in the crafting grid and the
 * banner tooltip work up to the highest of those for every banner, since bigger banners exist then.
 */
public final class LoomPerks {
	public static final int VANILLA = 6;

	private LoomPerks() {
	}

	public static int maxLayers(int perkLevel) {
		int[] table = VRConfig.get().perks.loomLayersByLevel;
		if (perkLevel <= 0) return VANILLA;
		return Math.max(VANILLA, table[Math.min(perkLevel, table.length) - 1]);
	}

	/** The most layers any loom can make (for copying and tooltips). */
	public static int highest() {
		int max = VANILLA;
		for (int v : VRConfig.get().perks.loomLayersByLevel) max = Math.max(max, v);
		return max;
	}
}
