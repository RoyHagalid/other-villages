package dev.villagerework.village;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * Generates a village name from its bell position, so the same village
 * always gets the same name. Renaming comes later.
 */
public final class VillageNames {
	private static final String[] FIRST = {
			"Oak", "Ash", "Birch", "Elm", "Willow", "Thorn", "Stone", "Brook", "Mill", "Fern",
			"Heath", "Moss", "Pine", "Clay", "Amber", "Barley", "Copper", "Hollow", "Lantern", "Bram",
			"Wheat", "Maple", "Rowan", "Hazel", "Juniper", "Flint", "Marsh", "Ember", "Briar", "Linden"
	};
	private static final String[] SECOND = {
			"ford", "wick", "stead", "holm", "dale", "field", "brook", "haven", "moor", "vale",
			"croft", "bury", "ton", "well", "mere", "ridge", "fell", "by", "worth", "hurst"
	};

	private VillageNames() {
	}

	public static String generate(BlockPos bellPos) {
		RandomSource random = RandomSource.create(bellPos.asLong() ^ 0x5DEECE66DL);
		return FIRST[random.nextInt(FIRST.length)] + SECOND[random.nextInt(SECOND.length)];
	}
}
