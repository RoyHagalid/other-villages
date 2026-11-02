package dev.villagerework.ui;

import dev.villagerework.perk.FletchingRecipes;
import dev.villagerework.perk.Perks;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The fletcher's fletching table screen (level II+): shaft, head and fletching slots, and the result.
 * Recipes are in FletchingRecipes. Inputs go back to the player when the screen closes, like a crafting table.
 *
 * Slots: 0 shaft, 1 head, 2 fletching, 3 result, then the player inventory (27) and hotbar (9).
 */
public final class FletchingMenu extends AbstractContainerMenu {
	public static final int SHAFT = 0, HEAD = 1, FLETCHING = 2, RESULT = 3;
	public static final int INPUT_Y = 35, SHAFT_X = 30, HEAD_X = 48, FLETCHING_X = 66, ARROW_X = 89, RESULT_X = 124;

	private final ContainerLevelAccess access;
	private final DataSlot perk = DataSlot.standalone();
	private final Container inputs = new SimpleContainer(3) {
		@Override
		public void setChanged() {
			super.setChanged();
			FletchingMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();

	/** Client side. */
	public FletchingMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	/** Server side. */
	public FletchingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(StationMenus.fletching(), id);
		this.access = access;
		perk.set(access.evaluate((level, pos) -> Perks.level(level, pos, Perks.FLETCHER)).orElse(0));
		addDataSlot(perk);

		addSlot(new Slot(inputs, SHAFT, SHAFT_X, INPUT_Y));
		addSlot(new Slot(inputs, HEAD, HEAD_X, INPUT_Y));
		addSlot(new Slot(inputs, FLETCHING, FLETCHING_X, INPUT_Y));
		addSlot(new Slot(result, 0, RESULT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack taken) {
				useInputs();
				super.onTake(player, taken);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 143));

		// The village panel, docked to the right (added last, after the player inventory).
		DockedStation.install(this, access);
	}

	private FletchingRecipes.Craft current() {
		return FletchingRecipes.craft(inputs.getItem(SHAFT), inputs.getItem(HEAD), inputs.getItem(FLETCHING), perk.get());
	}

	@Override
	public void slotsChanged(Container container) {
		FletchingRecipes.Craft craft = current();
		result.setItem(0, craft == null ? ItemStack.EMPTY : craft.result().copy());
		broadcastChanges();
	}

	/** Takes one craft's worth from the inputs (the result was just taken). */
	private void useInputs() {
		FletchingRecipes.Craft craft = current();
		if (craft == null) return;
		if (craft.shaft() > 0) inputs.removeItem(SHAFT, craft.shaft());
		if (craft.head() > 0) inputs.removeItem(HEAD, craft.head());
		if (craft.fletching() > 0) inputs.removeItem(FLETCHING, craft.fletching());
		access.execute((level, pos) -> level.levelEvent(1044, pos, 0)); // smithing-style "craft" sound
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		int playerStart = 4, end = 40;
		if (index == RESULT) {
			// Shift-click the result: craft as many times as the inputs and the inventory allow.
			ItemStack first = ItemStack.EMPTY;
			for (int i = 0; i < 64 && slot.hasItem(); i++) {
				ItemStack stack = slot.getItem();
				ItemStack copy = stack.copy();
				if (!fits(copy, playerStart, end)) break; // whole crafts only: stop when the next one won't fit
				if (!moveItemStackTo(stack, playerStart, end, true)) break;
				slot.onTake(player, copy);
				if (first.isEmpty()) first = copy;
			}
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < RESULT) {
			if (!moveItemStackTo(stack, playerStart, end, true)) return ItemStack.EMPTY;
		} else {
			int target = FletchingRecipes.slotFor(stack);
			if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
				boolean hotbar = index >= 31;
				if (!moveItemStackTo(stack, hotbar ? playerStart : 31, hotbar ? 31 : end, false)) return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
	}

	/** True if all of {@code stack} fits into slots [from, to). */
	private boolean fits(ItemStack stack, int from, int to) {
		int room = 0;
		for (int i = from; i < to; i++) {
			ItemStack in = slots.get(i).getItem();
			if (in.isEmpty()) room += stack.getMaxStackSize();
			else if (ItemStack.isSameItemSameComponents(in, stack)) room += Math.max(0, in.getMaxStackSize() - in.getCount());
			if (room >= stack.getCount()) return true;
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, inputs));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.FLETCHING_TABLE);
	}
}
