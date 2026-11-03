package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;

/**
 * Fisherman's barrel: extra rows of storage below the barrel's own three ({@code perks.barrelExtraRowsByLevel},
 * 0 / 1 / 2 → 27, 36 or 45 slots). The extra rows are kept by the village, like the station slots, and spill
 * out with them when the station is broken or the village disbanded. If the station's level goes down, the
 * hidden rows keep their items until it's back up. Only the station screen shows them; hoppers on a station
 * reach the village slots as before.
 */
public final class BarrelPerks {
	private BarrelPerks() {
	}

	public static int extraRows(int perkLevel) {
		if (perkLevel <= 0) return 0;
		int[] table = VRConfig.get().perks.barrelExtraRowsByLevel;
		return Math.max(0, Math.min(2, table[Math.min(perkLevel, table.length) - 1]));
	}
}
