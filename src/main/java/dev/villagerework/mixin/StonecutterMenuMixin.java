package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.Perks;
import dev.villagerework.perk.StonecutterPerks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mason's stonecutter perks. The woodcutting and reverse-cut recipes are ordinary stonecutting recipes
 * (data/villagerework/recipe/), so every stonecutter knows them; this hides them unless the stonecutter
 * is the mason's station at level II (woodcutting) or III (reverse cuts). The station level is sent to the
 * client as an extra synced value, so both sides show the same list.
 */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Unique
	private final DataSlot villagerework$perk = DataSlot.standalone();

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void villagerework$init(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		villagerework$perk.set(access.evaluate((level, pos) -> Perks.level(level, pos, Perks.MASON)).orElse(0));
		((AbstractContainerMenuInvoker) this).villagerework$addDataSlot(villagerework$perk);
		// The village panel, docked to the right of the screen (shown only at a station).
		dev.villagerework.ui.DockedStation.install((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this, access);
	}

	@WrapOperation(method = "setupRecipeList", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;selectByInput(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;"))
	private SelectableRecipe.SingleInputSet<StonecutterRecipe> villagerework$gate(
			SelectableRecipe.SingleInputSet<StonecutterRecipe> all, ItemStack input,
			Operation<SelectableRecipe.SingleInputSet<StonecutterRecipe>> original) {
		SelectableRecipe.SingleInputSet<StonecutterRecipe> result = original.call(all, input);
		return StonecutterPerks.allowed(input, villagerework$perk.get()) ? result : SelectableRecipe.SingleInputSet.empty();
	}
}
