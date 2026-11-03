package dev.villagerework.mixin;

import dev.villagerework.perk.GrindstonePerks;
import dev.villagerework.perk.Honing;
import net.minecraft.world.item.Items;
import dev.villagerework.perk.MillingSlots;
import dev.villagerework.perk.Perks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Weaponsmith's grindstone, level II: milling (see GrindstonePerks). The grindstone's two input slots and
 * its result slot are wrapped: the wrappers add the milling case and hand everything else to vanilla's own
 * slots, so grinding enchantments off works exactly as before. The station level is synced to the client
 * so both sides agree on what may go in.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Shadow
	@Final
	private Container resultSlots;

	@Shadow
	@Final
	private Container repairSlots;

	@Unique
	private final DataSlot villagerework$perk = DataSlot.standalone();

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void villagerework$init(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		villagerework$perk.set(access.evaluate((level, pos) -> Perks.level(level, pos, Perks.WEAPONSMITH)).orElse(0));
		((AbstractContainerMenuInvoker) this).villagerework$addDataSlot(villagerework$perk);

		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		villagerework$replace(menu, 0, new MillingSlots.Input(menu.slots.get(0), this::villagerework$milling,
				this::villagerework$honing, false));
		villagerework$replace(menu, 1, new MillingSlots.Input(menu.slots.get(1), this::villagerework$milling,
				this::villagerework$honing, true));
		villagerework$replace(menu, 2, new MillingSlots.Result(menu.slots.get(2), this::villagerework$takeMilled,
				() -> access.execute((level, pos) -> level.levelEvent(1042, pos, 0)))); // grindstone sound
		// The village panel, docked to the right of the screen (shown only at a station).
		dev.villagerework.ui.DockedStation.install((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this, access);
	}

	@Unique
	private static void villagerework$replace(AbstractContainerMenu menu, int index, Slot slot) {
		slot.index = index;
		menu.slots.set(index, slot);
	}

	@Unique
	private boolean villagerework$milling() {
		return villagerework$perk.get() >= 2;
	}

	@Unique
	private boolean villagerework$honing() {
		return villagerework$perk.get() >= 3;
	}

	/** Honing (level III): the slot with the weapon or tool, if the other one holds flint; else -1. */
	@Unique
	private int villagerework$honeSlot() {
		if (!villagerework$honing()) return -1;
		ItemStack top = repairSlots.getItem(0);
		ItemStack bottom = repairSlots.getItem(1);
		if (Honing.honable(top) && bottom.is(Items.FLINT)) return 0;
		if (Honing.honable(bottom) && top.is(Items.FLINT)) return 1;
		return -1;
	}

	/** Which input is being milled: 0 top, 1 bottom, -1 none. */
	@Unique
	private int villagerework$millSlot() {
		if (!villagerework$milling()) return -1;
		ItemStack top = repairSlots.getItem(0);
		ItemStack bottom = repairSlots.getItem(1);
		if (!top.isEmpty() && bottom.isEmpty() && GrindstonePerks.milling(top, false) != null) return 0;
		if (top.isEmpty() && !bottom.isEmpty() && GrindstonePerks.milling(bottom, true) != null) return 1;
		return -1;
	}

	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void villagerework$millResult(ItemStack top, ItemStack bottom, CallbackInfoReturnable<ItemStack> cir) {
		int hone = villagerework$honeSlot();
		if (hone >= 0) {
			cir.setReturnValue(Honing.hone(hone == 0 ? top : bottom));
			return;
		}
		int slot = villagerework$millSlot();
		if (slot >= 0) cir.setReturnValue(GrindstonePerks.result(slot == 0 ? top : bottom, slot == 1));
	}

	/**
	 * Uses up the input for the milled items taken: the full result minus what's still in the result slot.
	 * True while milling (vanilla's onTake, which empties both inputs, must never run then).
	 */
	@Unique
	private boolean villagerework$takeMilled(int leftInResult) {
		int hone = villagerework$honeSlot();
		if (hone >= 0) {
			// Honing: the weapon or tool goes, and one flint.
			if (leftInResult <= 0) {
				repairSlots.removeItem(hone, 1);
				repairSlots.removeItem(1 - hone, 1);
			}
			return true;
		}
		int slot = villagerework$millSlot();
		if (slot < 0) return false;
		ItemStack input = repairSlots.getItem(slot);
		GrindstonePerks.Milling m = GrindstonePerks.milling(input, slot == 1);
		ItemStack full = GrindstonePerks.result(input, slot == 1);
		if (m == null || full.isEmpty()) return false;
		int taken = full.getCount() - leftInResult;
		if (taken <= 0) return true;
		int used = Math.min(input.getCount(), GrindstonePerks.inputUsed(m, taken));
		repairSlots.removeItem(slot, used);
		return true;
	}
}
