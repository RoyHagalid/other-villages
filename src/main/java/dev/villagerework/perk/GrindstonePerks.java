package dev.villagerework.perk;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Weaponsmith's grindstone, level II: milling. Put a stack in one of the grindstone's input slots (the
 * other one empty) and the result shows the whole stack ground down; taking it uses up what it took.
 *  - top slot:    cobblestone or cobbled deepslate → gravel (1:1), gravel → sand (1:1), bone → 4 bone meal
 *  - bottom slot: gravel → flint (2 gravel per flint)
 * No XP, unlike grinding enchantments off.
 */
public final class GrindstonePerks {
	private GrindstonePerks() {
	}

	/** {@code in} items give {@code out} of {@code result}. */
	public record Milling(Item result, int in, int out) {
	}

	private static final Map<Item, Milling> TOP = Map.of(
			Items.COBBLESTONE, new Milling(Items.GRAVEL, 1, 1),
			Items.COBBLED_DEEPSLATE, new Milling(Items.GRAVEL, 1, 1),
			Items.GRAVEL, new Milling(Items.SAND, 1, 1),
			Items.BONE, new Milling(Items.BONE_MEAL, 1, 4));
	private static final Map<Item, Milling> BOTTOM = Map.of(
			Items.GRAVEL, new Milling(Items.FLINT, 2, 1));

	public static Milling milling(ItemStack stack, boolean bottomSlot) {
		return (bottomSlot ? BOTTOM : TOP).get(stack.getItem());
	}

	public static boolean millable(ItemStack stack) {
		return TOP.containsKey(stack.getItem()) || BOTTOM.containsKey(stack.getItem());
	}

	/** The milled result for a whole input stack (at most one stack of output), or EMPTY. */
	public static ItemStack result(ItemStack input, boolean bottomSlot) {
		Milling m = milling(input, bottomSlot);
		if (m == null) return ItemStack.EMPTY;
		int max = new ItemStack(m.result()).getMaxStackSize();
		int count = Math.min(max - max % m.out(), (input.getCount() / m.in()) * m.out());
		return count > 0 ? new ItemStack(m.result(), count) : ItemStack.EMPTY;
	}

	/** Input items used to make {@code taken} output items (rounded up to whole batches). */
	public static int inputUsed(Milling m, int taken) {
		return ((taken + m.out() - 1) / m.out()) * m.in();
	}
}
