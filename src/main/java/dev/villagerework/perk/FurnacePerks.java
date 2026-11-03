package dev.villagerework.perk;

import dev.villagerework.config.VRConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Armorer's blast furnace, level III: smelting iron or gold armor and tools gives back part of the
 * ingots they were made from (vanilla gives one nugget). {@code perks.recycleShare} of the material,
 * rounded down, at least one ingot: chestplate 4, leggings 3, helmet 2, boots 2, tools and swords 1.
 */
public final class FurnacePerks {
	private FurnacePerks() {
	}

	/** Item → ingots it took to make. */
	private static final Map<Item, Integer> IRON = Map.of(
			Items.IRON_HELMET, 5, Items.IRON_CHESTPLATE, 8, Items.IRON_LEGGINGS, 7, Items.IRON_BOOTS, 4,
			Items.IRON_SWORD, 2, Items.IRON_PICKAXE, 3, Items.IRON_AXE, 3, Items.IRON_SHOVEL, 1, Items.IRON_HOE, 2);
	private static final Map<Item, Integer> GOLD = Map.of(
			Items.GOLDEN_HELMET, 5, Items.GOLDEN_CHESTPLATE, 8, Items.GOLDEN_LEGGINGS, 7, Items.GOLDEN_BOOTS, 4,
			Items.GOLDEN_SWORD, 2, Items.GOLDEN_PICKAXE, 3, Items.GOLDEN_AXE, 3, Items.GOLDEN_SHOVEL, 1, Items.GOLDEN_HOE, 2);

	/** The recycled result for {@code input}, or null if it isn't recyclable armor or a tool. */
	public static ItemStack recycle(ItemStack input) {
		Integer units = IRON.get(input.getItem());
		Item ingot = Items.IRON_INGOT;
		if (units == null) {
			units = GOLD.get(input.getItem());
			ingot = Items.GOLD_INGOT;
		}
		if (units == null) return null;
		int count = Math.max(1, (int) Math.floor(units * VRConfig.get().perks.recycleShare));
		return new ItemStack(ingot, count);
	}
}
