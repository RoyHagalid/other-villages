package dev.villagerework.perk;

import dev.villagerework.VillageRework;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Mason's stonecutter: which inputs need which station level. The recipes and both tags are generated
 * by tools/stonecutter_data.py from vanilla's crafting recipes.
 *  - Level II, woodcutting: logs, stems and bamboo blocks → planks; planks → stairs (1), slabs (2), fence (1)
 *  - Level III, reverse cuts: any stairs → one of the block it's made from
 */
public final class StonecutterPerks {
	private StonecutterPerks() {
	}

	public static final TagKey<Item> WOODCUTTING = TagKey.create(Registries.ITEM, VillageRework.id("woodcutting_inputs"));
	public static final TagKey<Item> REVERSE_CUTS = TagKey.create(Registries.ITEM, VillageRework.id("reverse_cut_inputs"));

	/** True if a stonecutter at {@code perkLevel} may show recipes for {@code input}. */
	public static boolean allowed(ItemStack input, int perkLevel) {
		if (input.is(WOODCUTTING)) return perkLevel >= 2;
		if (input.is(REVERSE_CUTS)) return perkLevel >= 3;
		return true;
	}
}
