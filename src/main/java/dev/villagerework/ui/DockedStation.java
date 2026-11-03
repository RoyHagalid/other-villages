package dev.villagerework.ui;

import dev.villagerework.mixin.AbstractContainerMenuInvoker;
import dev.villagerework.village.StationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.function.Supplier;

/**
 * The village station panel docked to the right of a vanilla screen (smoker, blast furnace, brewing stand,
 * cartography table, ...). Every such menu gets these slots and values on both sides, so the slot lists match;
 * the panel only shows (and its slots only work) when the server links the block to an active station.
 *
 * Slots, added after the vanilla ones (and after the player inventory):
 *   +0 donate, +1 reward, +2 milestone, +3 reward 2, +4 reward 3   (the village's station slots)
 *
 * Perk actions (Bottle XP, Cave Map) are not part of the panel: they belong to the vanilla screen (ActionRow),
 * so they also work where there is no village panel.
 */
public final class DockedStation {
	/** The panel's left edge, relative to the vanilla screen (all docked screens are 176 wide). */
	public static final int X = 176 + 2;
	public static final int Y = 0;
	/** Extra synced values after the station's own. */
	public static final int D_ATTACHED = StationMenu.DATA_COUNT, D_PROFESSION = StationMenu.DATA_COUNT + 1;
	public static final int DATA_COUNT = StationMenu.DATA_COUNT + 2;
	/** Button ids are offset so they never clash with a vanilla menu's own buttons (stonecutter, loom). */
	public static final int BUTTON_BASE = 1000;

	public static final int PANEL_SLOTS = StationMenu.PANEL_SLOTS;
	public static final int SLOTS = PANEL_SLOTS;

	private final AbstractContainerMenu menu;
	private final StationContainer station = new StationContainer();
	private final Data data = new Data();
	private final StationRules rules = new StationRules(station, data);
	private final int firstSlot;
	private StationPanel.Link link;

	private DockedStation(AbstractContainerMenu menu) {
		this.menu = menu;
		this.firstSlot = menu.slots.size();
	}

	/** Adds the docked panel's slots and values to a menu (from the menu's constructor, on both sides). */
	public static DockedStation install(AbstractContainerMenu menu) {
		DockedStation dock = new DockedStation(menu);
		AbstractContainerMenuInvoker invoker = (AbstractContainerMenuInvoker) menu;
		for (Slot slot : dock.rules.createSlots(X, Y, dock::visible)) invoker.villagerework$addSlot(slot);
		invoker.villagerework$addDataSlots(dock.data);
		((DockHost) menu).villagerework$setDock(dock);
		return dock;
	}

	/** Installs the panel and, on the server, links it through the menu's level access (blocks without a block entity). */
	public static DockedStation install(AbstractContainerMenu menu, net.minecraft.world.inventory.ContainerLevelAccess access) {
		DockedStation dock = install(menu);
		access.execute((level, pos) -> {
			if (level instanceof ServerLevel serverLevel) dock.attach(serverLevel, pos);
		});
		return dock;
	}

	/** Server: links the panel to the station at this position, if the block is one. */
	public void attach(ServerLevel level, BlockPos pos) {
		StationPanel.link(level, pos).ifPresent(l -> this.link = l);
	}

	// ---------------------------------------------------------------- state (both sides)

	public StationRules rules() {
		return rules;
	}

	public boolean shown() {
		return data.get(D_ATTACHED) == 1;
	}

	/** Client: the panel is folded into its tab (it doesn't fit next to the recipe book). */
	private boolean collapsed;

	public void setCollapsed(boolean collapsed) {
		this.collapsed = collapsed;
	}

	/** Shown and not folded away: the panel's slots are drawn and clickable. */
	public boolean visible() {
		return shown() && !collapsed;
	}

	public Slot slot(int index) {
		return menu.slots.get(firstSlot + index);
	}

	public int firstSlot() {
		return firstSlot;
	}

	/** "Cleric Station", from the synced profession. */
	public String title() {
		VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.byId(data.get(D_PROFESSION));
		Identifier key = profession == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
		return (key == null ? "Village" : StationManager.displayName(key.toString())) + " Station";
	}

	private boolean active() {
		return link != null && link.stillActive().getAsBoolean();
	}

	// ---------------------------------------------------------------- buttons (server)

	/** A button id from the screen, already without BUTTON_BASE. */
	public boolean click(Player player, int id) {
		if (!active()) return false;
		return switch (id) {
			case StationMenu.UPGRADE_BUTTON -> link.upgrade().test(player);
			case StationMenu.REROLL_BUTTON -> link.reroll().test(player);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- shift-click (both sides)

	/**
	 * Shift-click with the panel docked. Our slots go back to the player's inventory. From the inventory,
	 * today's item goes to the donate slot and the milestone item to its slot first; everything else (and
	 * whatever doesn't fit) is left to the vanilla menu.
	 */
	public ItemStack quickMove(Player player, int index, Supplier<ItemStack> vanilla) {
		boolean ours = index >= firstSlot && index < firstSlot + SLOTS;
		if (!shown() && !ours) return vanilla.get();
		Slot slot = menu.slots.get(index);
		if (!slot.hasItem()) return ours ? ItemStack.EMPTY : vanilla.get();
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		AbstractContainerMenuInvoker invoker = (AbstractContainerMenuInvoker) menu;

		if (ours) {
			int[] range = playerRange(menu);
			if (range == null || !invoker.villagerework$moveItemStackTo(stack, range[0], range[1], true)) return ItemStack.EMPTY;
		} else if (slot.container instanceof Inventory) {
			boolean moved = false;
			if (rules.isRequested(stack) && rules.remaining() > 0) moved = moveTo(invoker, stack, 0);
			if (!stack.isEmpty() && rules.isMilestone(stack)) moved |= moveTo(invoker, stack, 2);
			if (!moved) return vanilla.get();
		} else {
			return vanilla.get();
		}

		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, stack);
		return original;
	}

	private boolean moveTo(AbstractContainerMenuInvoker invoker, ItemStack stack, int ourSlot) {
		int index = firstSlot + ourSlot;
		return invoker.villagerework$moveItemStackTo(stack, index, index + 1, false);
	}

	/** The player inventory's slots in this menu: {first, end}. */
	public static int[] playerRange(AbstractContainerMenu menu) {
		int first = -1, end = -1;
		for (int i = 0; i < menu.slots.size(); i++) {
			if (menu.slots.get(i).container instanceof Inventory) {
				if (first < 0) first = i;
				end = i + 1;
			}
		}
		return first < 0 ? null : new int[]{first, end};
	}

	// ---------------------------------------------------------------- backing

	/** The village's station slots on the server while linked; an empty stand-in otherwise (and on the client). */
	private final class StationContainer implements Container {
		private final SimpleContainer local = new SimpleContainer(PANEL_SLOTS);

		private Container target() {
			return active() ? link.inventory() : local;
		}

		@Override
		public int getContainerSize() {
			return PANEL_SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return target().isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return target().getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return target().removeItem(slot, count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return target().removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			target().setItem(slot, stack);
		}

		@Override
		public void setChanged() {
			target().setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			target().clearContent();
		}
	}

	/** Live station values on the server while linked; the synced copy on the client. */
	private final class Data implements ContainerData {
		private final int[] values = new int[DATA_COUNT];

		@Override
		public int get(int index) {
			if (link == null) return values[index];
			boolean active = active();
			if (index == D_ATTACHED) return active ? 1 : 0;
			if (index == D_PROFESSION) {
				VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(Identifier.parse(link.profession()));
				return profession == null ? -1 : BuiltInRegistries.VILLAGER_PROFESSION.getId(profession);
			}
			return active ? link.data().get(index) : values[index];
		}

		@Override
		public void set(int index, int value) {
			values[index] = value;
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	}
}
