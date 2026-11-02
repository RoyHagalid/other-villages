package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.PerkContext;
import dev.villagerework.perk.Perks;
import dev.villagerework.ui.ActionRow;
import dev.villagerework.ui.DockedStation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cartographer's cartography table, level II: copying a map with one empty map makes two copies
 * (the result is three maps instead of two). Vanilla's paper (zoom out) and glass pane (lock) are unchanged.
 * The result is worked out on the server, inside a lambda that knows the table's position.
 * Level III: the Cave Map row under the screen (see ActionRow).
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {
	/** Room for the docked station panel, and the cartographer's Cave Map row under the screen (level III). */
	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void villagerework$dock(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		DockedStation.install(menu, access);
		// Empty maps shift-click into the Cave Map row unless a map is waiting to be copied.
		ActionRow.install(menu, Perks.CARTOGRAPHER, stack -> menu.slots.get(0).getItem().isEmpty(), access);
	}

	@Inject(method = "lambda$setupResultSlot$0", at = @At("HEAD"))
	private void villagerework$start(ItemStack map, ItemStack additional, ItemStack result, Level level, BlockPos pos,
			CallbackInfo ci) {
		PerkContext.set(Perks.level(level, pos, Perks.CARTOGRAPHER));
	}

	@Inject(method = "lambda$setupResultSlot$0", at = @At("RETURN"))
	private void villagerework$end(ItemStack map, ItemStack additional, ItemStack result, Level level, BlockPos pos,
			CallbackInfo ci) {
		PerkContext.clear();
	}

	@WrapOperation(method = "lambda$setupResultSlot$0", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack villagerework$moreCopies(ItemStack stack, int count, Operation<ItemStack> original) {
		// The clone is vanilla's only copyWithCount(2) here; zoom and lock use 1.
		return original.call(stack, count == 2 && PerkContext.get() >= 2 ? 3 : count);
	}
}
