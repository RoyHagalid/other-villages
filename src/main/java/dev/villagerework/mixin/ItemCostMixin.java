package dev.villagerework.mixin;

import dev.villagerework.perk.TakeHomes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A take-home workstation never counts as payment in a trade (e.g. as the plain block for the next take-home),
 * so shift-click buying can't eat the ones just bought.
 */
@Mixin(ItemCost.class)
public abstract class ItemCostMixin {
	@Inject(method = "test", at = @At("RETURN"), cancellable = true)
	private void villagerework$notTakeHomes(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && TakeHomes.fromItem(stack).isPresent()) cir.setReturnValue(false);
	}
}
