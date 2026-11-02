package dev.villagerework.village;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A station's slots, stored in the village.
 *
 * Slot 0 (donate):    today's requested item only, never more than requested.
 * Slot 1 (reward):    the villagers' goods; take only.
 * Slot 2 (milestone): the item needed for the next level; one at a time, by hand only.
 * Slot 3 (reward 2):  the second reward, filled from level II on; take only.
 * Slot 4 (reward 3):  the third reward, filled at level III; take only.
 *
 * Hoppers see it like a furnace: pushing in from above or the side fills the donate
 * slot, and a hopper underneath pulls all rewards. Hoppers never reach the milestone slot.
 */
public final class StationInventory extends SimpleContainer implements WorldlyContainer {
	public static final int DONATE_SLOT = 0;
	public static final int REWARD_SLOT = 1;
	public static final int MILESTONE_SLOT = 2;
	public static final int REWARD_2_SLOT = 3;
	public static final int REWARD_3_SLOT = 4;
	public static final int SIZE = 5;
	/** The reward slots, in unlock order (level I, II, III by default). */
	public static final int[] REWARD_SLOTS = {REWARD_SLOT, REWARD_2_SLOT, REWARD_3_SLOT};

	/** Kept for code that loops over supply slots; there is one. */
	public static final int SUPPLY_SLOTS = 1;
	public static final int OUTPUT_SLOT = REWARD_SLOT;

	private static final int[] TOP_AND_SIDES = {DONATE_SLOT};
	private static final int[] BOTTOM = REWARD_SLOTS;

	/** Today's request: only this item is accepted, and only up to this many in total. */
	private Item requestItem;
	private int requestCount;
	/** The item that unlocks the next level (null when fully upgraded). */
	private Item milestoneItem;
	/** Called on every change, so the village gets saved. */
	private Runnable onChange = () -> {
	};

	StationInventory() {
		super(SIZE);
	}

	void setRequest(Item item, int count) {
		this.requestItem = item;
		this.requestCount = Math.max(0, count);
	}

	void setMilestone(Item item) {
		this.milestoneItem = item;
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	/** How many of the requested item are in the donate slot. */
	public int supplied() {
		ItemStack s = getItem(DONATE_SLOT);
		return requestItem != null && s.is(requestItem) ? s.getCount() : 0;
	}

	/** How many more the villagers still want today. */
	public int remaining() {
		return Math.max(0, requestCount - supplied());
	}

	/** Only today's requested item, and only while more is wanted. */
	public boolean accepts(ItemStack stack) {
		return requestItem != null && stack.is(requestItem) && remaining() > 0;
	}

	public boolean acceptsMilestone(ItemStack stack) {
		return milestoneItem != null && stack.is(milestoneItem) && getItem(MILESTONE_SLOT).isEmpty();
	}

	public Item milestoneItem() {
		return milestoneItem;
	}

	/** Used while loading, without marking the village as changed. */
	void setItemSilently(int slot, ItemStack stack) {
		super.setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		onChange.run();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == DONATE_SLOT) return accepts(stack);
		if (slot == MILESTONE_SLOT) return acceptsMilestone(stack);
		return false;
	}

	// ---------------------------------------------------------------- hoppers

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == DONATE_SLOT && accepts(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return isReward(slot) && side == Direction.DOWN;
	}

	public static boolean isReward(int slot) {
		return slot == REWARD_SLOT || slot == REWARD_2_SLOT || slot == REWARD_3_SLOT;
	}
}
