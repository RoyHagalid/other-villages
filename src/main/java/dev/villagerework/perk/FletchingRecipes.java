package dev.villagerework.perk;

import dev.villagerework.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Fletcher's fletching table screen (slots: shaft, head, fletching → result).
 *  - Level II:  stick + flint + feather → 6 arrows (vanilla crafting makes 4)
 *  - Level III: arrows + lingering potion → up to 16 tipped arrows from one potion (vanilla: 8)
 *               4 arrows + TNT + fire charge → 4 explosive arrows
 */
public final class FletchingRecipes {
	private FletchingRecipes() {
	}

	public static final int ARROWS_PER_CRAFT = 6;
	public static final int TIPPED_PER_POTION = 16;
	public static final int EXPLOSIVE_PER_CRAFT = 4;

	/** What one craft takes from each slot and gives. */
	public record Craft(ItemStack result, int shaft, int head, int fletching) {
	}

	/** The craft these inputs make at {@code perkLevel}, or null. */
	public static Craft craft(ItemStack shaft, ItemStack head, ItemStack fletching, int perkLevel) {
		if (perkLevel >= 2 && shaft.is(Items.STICK) && head.is(Items.FLINT) && fletching.is(Items.FEATHER)) {
			return new Craft(new ItemStack(Items.ARROW, ARROWS_PER_CRAFT), 1, 1, 1);
		}
		if (perkLevel >= 3 && shaft.is(Items.ARROW) && head.is(Items.LINGERING_POTION) && fletching.isEmpty()) {
			PotionContents potion = head.get(DataComponents.POTION_CONTENTS);
			if (potion == null) return null;
			int count = Math.min(TIPPED_PER_POTION, shaft.getCount());
			ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, count);
			tipped.set(DataComponents.POTION_CONTENTS, potion);
			return new Craft(tipped, count, 1, 0);
		}
		if (perkLevel >= 3 && shaft.is(Items.ARROW) && shaft.getCount() >= EXPLOSIVE_PER_CRAFT
				&& head.is(Items.TNT) && fletching.is(Items.FIRE_CHARGE)) {
			return new Craft(new ItemStack(ModItems.EXPLOSIVE_ARROW, EXPLOSIVE_PER_CRAFT), EXPLOSIVE_PER_CRAFT, 1, 1);
		}
		return null;
	}

	/** Which input slot an item belongs in when shift-clicked (0 shaft, 1 head, 2 fletching, -1 none). */
	public static int slotFor(ItemStack stack) {
		if (stack.is(Items.STICK) || stack.is(Items.ARROW)) return 0;
		if (stack.is(Items.FLINT) || stack.is(Items.LINGERING_POTION) || stack.is(Items.TNT)) return 1;
		if (stack.is(Items.FEATHER) || stack.is(Items.FIRE_CHARGE)) return 2;
		return -1;
	}
}
