package dev.villagerework.perk;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** Wrappers around the grindstone's vanilla slots that add milling (see GrindstoneMenuMixin). */
public final class MillingSlots {
	private MillingSlots() {
	}

	/** An input slot: whatever vanilla accepts, plus millable items (level II) and flint for honing (level III). */
	public static final class Input extends Slot {
		private final Slot vanilla;
		private final BooleanSupplier milling;
		private final BooleanSupplier honing;
		private final boolean bottom;

		public Input(Slot vanilla, BooleanSupplier milling, BooleanSupplier honing, boolean bottom) {
			super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
			this.vanilla = vanilla;
			this.milling = milling;
			this.honing = honing;
			this.bottom = bottom;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return vanilla.mayPlace(stack)
					|| (milling.getAsBoolean() && GrindstonePerks.milling(stack, bottom) != null)
					|| (honing.getAsBoolean() && stack.is(net.minecraft.world.item.Items.FLINT));
		}
	}

	/**
	 * The result slot: milled results use up their input; everything else is vanilla's onTake.
	 * {@code takeMilled} gets how many items are still left in the result slot. That's how much was
	 * taken is known: on a shift-click vanilla passes the leftover, not the taken stack.
	 */
	public static final class Result extends Slot {
		private final Slot vanilla;
		private final java.util.function.IntPredicate takeMilled;
		private final Runnable sound;

		public Result(Slot vanilla, java.util.function.IntPredicate takeMilled, Runnable sound) {
			super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
			this.vanilla = vanilla;
			this.takeMilled = takeMilled;
			this.sound = sound;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack taken) {
			if (takeMilled.test(getItem().getCount())) {
				sound.run();
			} else {
				vanilla.onTake(player, taken);
			}
		}
	}
}
