package dev.villagerework.ui;

import dev.villagerework.mixin.AbstractContainerMenuInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A perk action built into a vanilla screen, as a strip under it (brewing stand: Bottle XP, cartography table:
 * Cave Map). It belongs to the block, not to the village panel: it shows wherever the block has the perk level,
 * so it keeps working on take-home stations later.
 *
 *   [input] ([extra]) [   Button   ] [output]
 *
 * Slots (added after everything else in the menu): +0 input, +1 extra input, +2 output. They're temporary,
 * like a crafting table's: what's left goes back to the player when the screen closes.
 */
public final class ActionRow {
	/** The strip under the vanilla screen (positions relative to the screen; all these screens are 176 x 166). */
	public static final int STRIP_X = 16, STRIP_Y = 163, STRIP_WIDTH = 144, STRIP_HEIGHT = 30;
	public static final int SLOT_Y = STRIP_Y + 8;
	public static final int INPUT_X = STRIP_X + 8, EXTRA_X = INPUT_X + 18, OUTPUT_X = STRIP_X + STRIP_WIDTH - 8 - 16;
	public static final int BUTTON_Y = STRIP_Y + 6, BUTTON_HEIGHT = 20, BUTTON_RIGHT = OUTPUT_X - 4;
	public static final int BUTTON_ID = DockedStation.BUTTON_BASE + 100;
	public static final int SLOTS = 3;

	private final AbstractContainerMenu menu;
	private final String profession;
	private final Predicate<ItemStack> preferInput;
	private final SimpleContainer io = new SimpleContainer(SLOTS);
	private final Data data = new Data();
	private final int firstSlot;
	private ServerLevel level;
	private BlockPos pos;

	private ActionRow(AbstractContainerMenu menu, String profession, Predicate<ItemStack> preferInput) {
		this.menu = menu;
		this.profession = profession;
		this.preferInput = preferInput;
		this.firstSlot = menu.slots.size();
	}

	/**
	 * Adds the row to a menu (both sides). {@code preferInput} says whether shift-clicking the input item
	 * from the inventory should go to the row before the vanilla slots.
	 */
	public static ActionRow install(AbstractContainerMenu menu, String profession, Predicate<ItemStack> preferInput) {
		ActionRow row = new ActionRow(menu, profession, preferInput);
		AbstractContainerMenuInvoker invoker = (AbstractContainerMenuInvoker) menu;
		invoker.villagerework$addSlot(new Slot(row.io, 0, INPUT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return row.shown() && stack.is(StationActions.input(row.action()));
			}

			@Override
			public boolean isActive() {
				return row.shown() || hasItem();
			}
		});
		invoker.villagerework$addSlot(new Slot(row.io, 1, EXTRA_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				Item extra = StationActions.extra(row.action());
				return row.shown() && extra != null && stack.is(extra);
			}

			@Override
			public boolean isActive() {
				return (row.shown() && StationActions.extra(row.action()) != null) || hasItem();
			}
		});
		invoker.villagerework$addSlot(new Slot(row.io, 2, OUTPUT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean isActive() {
				return row.shown() || hasItem();
			}
		});
		invoker.villagerework$addDataSlots(row.data);
		((DockHost) menu).villagerework$setActionRow(row);
		return row;
	}

	public static ActionRow install(AbstractContainerMenu menu, String profession, Predicate<ItemStack> preferInput,
			ContainerLevelAccess access) {
		ActionRow row = install(menu, profession, preferInput);
		access.execute((level, pos) -> {
			if (level instanceof ServerLevel serverLevel) row.attach(serverLevel, pos);
		});
		return row;
	}

	/** Server: the block's position, for its perk level. */
	public void attach(ServerLevel level, BlockPos pos) {
		this.level = level;
		this.pos = pos;
	}

	public int action() {
		return data.get(0);
	}

	public boolean shown() {
		return action() != StationActions.NONE;
	}

	public Container io() {
		return io;
	}

	public Slot slot(int index) {
		return menu.slots.get(firstSlot + index);
	}

	/** Server: the button. */
	public boolean click(Player player) {
		return shown() && StationActions.run(action(), player, io);
	}

	/**
	 * Shift-click: the row's slots go back to the inventory; from the inventory, the extra input (shards) goes to
	 * its slot, and the input goes to the row when {@code preferInput} allows. Anything else is vanilla's.
	 */
	public ItemStack quickMove(Player player, int index, Supplier<ItemStack> vanilla) {
		boolean ours = index >= firstSlot && index < firstSlot + SLOTS;
		Slot slot = menu.slots.get(index);
		if (!ours && (!shown() || !(slot.container instanceof Inventory))) return vanilla.get();
		if (!slot.hasItem()) return ours ? ItemStack.EMPTY : vanilla.get();
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		AbstractContainerMenuInvoker invoker = (AbstractContainerMenuInvoker) menu;

		if (ours) {
			int[] range = DockedStation.playerRange(menu);
			if (range == null || !invoker.villagerework$moveItemStackTo(stack, range[0], range[1], true)) return ItemStack.EMPTY;
		} else {
			int action = action();
			Item extra = StationActions.extra(action);
			int target;
			if (extra != null && stack.is(extra)) target = 1;
			else if (stack.is(StationActions.input(action)) && preferInput.test(stack)) target = 0;
			else return vanilla.get();
			if (!invoker.villagerework$moveItemStackTo(stack, firstSlot + target, firstSlot + target + 1, false)) {
				return vanilla.get();
			}
		}

		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, stack);
		return original;
	}

	/** Menu closed: what's left goes back to the player. */
	public void removed(Player player) {
		if (!player.level().isClientSide()) {
			((AbstractContainerMenuInvoker) menu).villagerework$clearContainer(player, io);
		}
	}

	/** The action on offer: live from the block's perk level on the server, synced on the client. */
	private final class Data implements ContainerData {
		private int value;

		@Override
		public int get(int index) {
			return level == null ? value : StationActions.available(level, pos, profession);
		}

		@Override
		public void set(int index, int value) {
			this.value = value;
		}

		@Override
		public int getCount() {
			return 1;
		}
	}
}
