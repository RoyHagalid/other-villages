package dev.villagerework.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * The station panel's slot rules and synced values, shared by the station screen (StationMenu) and the panel
 * docked beside vanilla screens (DockedStation). Works the same on both sides: the client applies the rules
 * from the synced data, the server checks them again.
 *
 * Station container slots: 0 donate, 1 reward, 2 milestone, 3 reward 2, 4 reward 3.
 * {@link #createSlots} returns them in that (menu) order.
 */
public final class StationRules {
	private final Container station;
	private final ContainerData data;

	public StationRules(Container station, ContainerData data) {
		this.station = station;
		this.data = data;
	}

	public int data(int index) {
		return data.get(index);
	}

	public Item requestedItem() {
		return itemById(data.get(StationMenu.D_REQUEST_ITEM));
	}

	public Item milestoneItem() {
		return itemById(data.get(StationMenu.D_MILESTONE_ITEM));
	}

	private static Item itemById(int id) {
		return id < 0 ? null : BuiltInRegistries.ITEM.byId(id);
	}

	/** Reward slots the station's level fills (1 to 3). */
	public int unlockedRewards() {
		return Math.max(1, data.get(StationMenu.D_REWARD_SLOTS));
	}

	public boolean isUnlocked(int reward) {
		return reward < unlockedRewards();
	}

	/** The perk action this station offers (StationMenu.ACTION_*). */
	public int action() {
		return data.get(StationMenu.D_ACTION);
	}

	/** Price of the next reroll, or -1 when there's nothing to reroll. */
	public int rerollCost() {
		return data.get(StationMenu.D_REROLL_COST);
	}

	public boolean isMaxLevel() {
		return data.get(StationMenu.D_LEVEL) >= data.get(StationMenu.D_MAX_LEVEL) && data.get(StationMenu.D_MAX_LEVEL) > 0;
	}

	/** True when the Upgrade button should be active (the server checks again). */
	public boolean canUpgrade() {
		Item milestone = milestoneItem();
		return milestone != null
				&& data.get(StationMenu.D_LEVEL) < data.get(StationMenu.D_MAX_LEVEL)
				&& data.get(StationMenu.D_PROGRESS) >= data.get(StationMenu.D_FILL)
				&& station.getItem(2).is(milestone);
	}

	public boolean isRequested(ItemStack stack) {
		Item item = requestedItem();
		return item != null && !stack.isEmpty() && stack.is(item);
	}

	public boolean isMilestone(ItemStack stack) {
		Item item = milestoneItem();
		return item != null && !stack.isEmpty() && stack.is(item) && station.getItem(2).isEmpty();
	}

	/** How many more of today's item the villagers want (0 when fully donated or nothing is asked). */
	public int remaining() {
		ItemStack current = station.getItem(0);
		int supplied = isRequested(current) ? current.getCount() : 0;
		return Math.max(0, data.get(StationMenu.D_REQUEST_COUNT) - supplied);
	}

	/**
	 * The five panel slots in menu order (donate, reward 1, milestone, reward 2, reward 3), at the panel's
	 * positions shifted by (dx, dy). {@code shown} hides all of them (the docked panel of a non-station block).
	 */
	public Slot[] createSlots(int dx, int dy, BooleanSupplier shown) {
		Slot donate = new Slot(station, 0, dx + StationMenu.SLOT_X, dy + StationMenu.DONATE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return isRequested(stack) && remaining() > 0;
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				if (!isRequested(stack)) return 0;
				ItemStack current = getItem();
				int inSlot = isRequested(current) ? current.getCount() : 0;
				return Math.min(stack.getMaxStackSize(), inSlot + remaining());
			}

			@Override
			public boolean isActive() {
				return shown.getAsBoolean();
			}
		};
		Slot milestone = new Slot(station, 2, dx + StationMenu.SLOT_X, dy + StationMenu.MILESTONE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return isMilestone(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}

			@Override
			public boolean isActive() {
				// Hidden (not drawn, not clickable) once the station is fully upgraded.
				return shown.getAsBoolean() && (!isMaxLevel() || hasItem());
			}
		};
		return new Slot[]{donate, reward(0, dx, dy, shown), milestone, reward(1, dx, dy, shown), reward(2, dx, dy, shown)};
	}

	/** Locked rewards are hidden (the screen draws a barrier) unless something is still in them. */
	private Slot reward(int reward, int dx, int dy, BooleanSupplier shown) {
		return new Slot(station, StationMenu.REWARD_SLOTS[reward], dx + StationMenu.REWARD_X[reward], dy + StationMenu.REWARD_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean isActive() {
				return shown.getAsBoolean() && (isUnlocked(reward) || hasItem());
			}
		};
	}
}
