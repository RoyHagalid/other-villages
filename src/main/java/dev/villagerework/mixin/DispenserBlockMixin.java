package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.CauldronDispensing;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dispensers fill and empty cauldrons with buckets and bottles (see CauldronDispensing). */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/DispenserBlock;getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;"))
	private DispenseItemBehavior villagerework$cauldrons(DispenserBlock self, Level level, ItemStack stack,
			Operation<DispenseItemBehavior> original) {
		return CauldronDispensing.wrap(stack, original.call(self, level, stack));
	}
}
