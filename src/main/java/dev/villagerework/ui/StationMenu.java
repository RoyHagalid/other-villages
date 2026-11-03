package dev.villagerework.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A village station's screen: the block's own storage (if it has one) and the player inventory on the left,
 * the village panel on the right (where it's docked on vanilla screens too).
 *
 * Panel rows:
 *   Title
 *   We need   (drawn icon)     today's item and count; faded when fully donated, a barrier after the evening work
 *   Donate    [donate]         (today's item only, never more than requested)
 *   Rewards   [1] [2] [3]      (take only; one slot per level, locked ones marked with a barrier)
 *             [  Reroll   ]    rerolls everything left in the reward slots (emeralds; hover shows the price)
 *   Level II
 *   [progress bar]
 *   [Upgrade] [milestone]      (the item for the next level; shown as a ghost when empty)
 *
 * Menu slot order: 0 donate, 1 reward, 2 milestone, 3 reward 2, 4 reward 3, then the block's
 * storage (27 for a barrel, none for a composter), then the player inventory and hotbar.
 *
 * Everything the client needs to apply the slot rules and draw the panel is synced as data.
 */
public final class StationMenu extends AbstractContainerMenu {
	public static final int PANEL_WIDTH = 124;
	public static final int PANEL_HEIGHT = 150;

	public static final int DONATE = 0;
	public static final int REWARD = 1;
	public static final int MILESTONE = 2;
	public static final int REWARD_2 = 3;
	public static final int REWARD_3 = 4;
	public static final int PANEL_SLOTS = 5;
	/** Menu slot of each reward, in unlock order. */
	public static final int[] REWARD_SLOTS = {REWARD, REWARD_2, REWARD_3};

	/** Panel positions (item corner, relative to the screen's top-left). */
	public static final int SLOT_X = 98;
	public static final int[] REWARD_X = {SLOT_X - 40, SLOT_X - 20, SLOT_X};
	public static final int REQUEST_Y = 22;
	public static final int DONATE_Y = 42;
	public static final int REWARD_Y = 62;
	public static final int REROLL_Y = REWARD_Y + 19;
	public static final int REROLL_HEIGHT = 16;
	public static final int LEVEL_TEXT_Y = 100;
	public static final int BAR_Y = 112;
	public static final int UPGRADE_Y = 122;
	public static final int MILESTONE_Y = 124;

	/** Synced data. */
	public static final int D_REQUEST_ITEM = 0, D_REQUEST_COUNT = 1, D_LEVEL = 2, D_MAX_LEVEL = 3, D_PROGRESS = 4,
			D_FILL = 5, D_MILESTONE_ITEM = 6, D_WORKERS = 7, D_WORKER_CAP = 8, D_LAST_PERCENT = 9, D_REWARD_SLOTS = 10;
	/** Price of the next reroll (-1 = nothing to reroll). */
	public static final int D_REROLL_COST = 11;
	/** A station action unlocked by a perk: 0 none, 1 = bottle XP (cleric III), 2 = sonar map (cartographer III). */
	public static final int D_ACTION = 12;
	public static final int ACTION_NONE = 0, ACTION_BOTTLE_XP = 1, ACTION_SONAR_MAP = 2;
	public static final int DATA_COUNT = 13;
	public static final int UPGRADE_BUTTON = 0;
	/** Rerolls everything left in the reward slots. */
	public static final int REROLL_BUTTON = 1;
	/** The perk action button (on the panel docked to the block's own screen). */
	public static final int ACTION_BUTTON = 2;

	private final Container station;    // 5 slots: donate, reward, milestone, reward 2, reward 3 (the village's StationInventory on the server)
	private final Container storage;    // the block's own storage, or empty
	private final ContainerData data;
	private final int storageRows;
	private final BlockPos pos;
	private final BooleanSupplier stillActive;
	private final Predicate<Player> upgrade;
	private final Predicate<Player> reroll;
	private final StationRules rules;

	/** Client side: everything is filled in by the server. */
	public StationMenu(MenuType<?> type, int id, Inventory playerInventory) {
		this(type, id, playerInventory, new SimpleContainer(PANEL_SLOTS),
				new SimpleContainer(StationMenus.storageSize(type)), new SimpleContainerData(DATA_COUNT),
				BlockPos.ZERO, () -> true, p -> false, p -> false);
	}

	/** Server side. */
	StationMenu(MenuType<?> type, int id, Inventory playerInventory, Container station,
			Container storage, ContainerData data, BlockPos pos, BooleanSupplier stillActive, Predicate<Player> upgrade,
			Predicate<Player> reroll) {
		super(type, id);
		this.station = station;
		this.storage = storage;
		this.data = data;
		this.storageRows = storage.getContainerSize() / 9;
		this.pos = pos;
		this.stillActive = stillActive;
		this.upgrade = upgrade;
		this.reroll = reroll;
		this.rules = new StationRules(station, data);

		for (Slot slot : rules.createSlots(DockedStation.X, 0, () -> true)) addSlot(slot);

		int vanillaTop = vanillaTop(storageRows);
		for (int row = 0; row < storageRows; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(storage, col + row * 9, 8 + col * 18, vanillaTop + 18 + row * 18));
			}
		}
		int inventoryTop = vanillaTop + storageRows * 18 + 31;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, inventoryTop + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, inventoryTop + 58));
		}

		addDataSlots(data);
	}

	// ---------------------------------------------------------------- layout (shared with the screen)

	/** Height of the vanilla part on the right: title strip, storage rows, player inventory. */
	public static int vanillaHeight(int storageRows) {
		return storageRows * 18 + 17 + 96;
	}

	public static int screenHeight(int storageRows) {
		return Math.max(vanillaHeight(storageRows), PANEL_HEIGHT);
	}

	/** The vanilla part and the panel are top-aligned, like the panel docked to vanilla screens. */
	public static int vanillaTop(int storageRows) {
		return 0;
	}

	public int storageRows() {
		return storageRows;
	}

	public StationRules rules() {
		return rules;
	}

	public int data(int index) {
		return rules.data(index);
	}

	public Item requestedItem() {
		return rules.requestedItem();
	}

	public Item milestoneItem() {
		return rules.milestoneItem();
	}

	public boolean isMaxLevel() {
		return rules.isMaxLevel();
	}

	public boolean canUpgrade() {
		return rules.canUpgrade();
	}

	public int rerollCost() {
		return rules.rerollCost();
	}

	public int remaining() {
		return rules.remaining();
	}

	// ---------------------------------------------------------------- buttons

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == UPGRADE_BUTTON) return upgrade.test(player);
		if (id == REROLL_BUTTON) return reroll.test(player);
		return false;
	}

	// ---------------------------------------------------------------- shift-click

	/** From the player inventory: donate slot first, then the milestone slot, then the block's storage. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		int storageStart = PANEL_SLOTS;
		int playerStart = storageStart + storage.getContainerSize();
		int hotbarStart = playerStart + 27;
		int end = hotbarStart + 9;

		if (index < playerStart) {
			if (!moveItemStackTo(stack, playerStart, end, true)) return ItemStack.EMPTY;
		} else {
			boolean moved = false;
			if (rules.isRequested(stack) && remaining() > 0) {
				moved = moveItemStackTo(stack, DONATE, DONATE + 1, false);
			}
			if (!stack.isEmpty() && rules.isMilestone(stack)) {
				moved |= moveItemStackTo(stack, MILESTONE, MILESTONE + 1, false);
			}
			if (!stack.isEmpty()) {
				if (storage.getContainerSize() > 0) {
					moved |= moveItemStackTo(stack, storageStart, playerStart, false);
				} else {
					// No storage: move between inventory and hotbar, like vanilla stations do.
					moved |= index < hotbarStart
							? moveItemStackTo(stack, hotbarStart, end, false)
							: moveItemStackTo(stack, playerStart, hotbarStart, false);
				}
			}
			if (!moved) return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();
		if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
		return original;
	}

	// ---------------------------------------------------------------- server upkeep

	@Override
	public boolean stillValid(Player player) {
		return stillActive.getAsBoolean()
				&& player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}
}
