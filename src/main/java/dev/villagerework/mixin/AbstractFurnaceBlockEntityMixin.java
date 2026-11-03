package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.config.VRConfig;
import dev.villagerework.perk.FurnacePerks;
import dev.villagerework.perk.PerkContext;
import dev.villagerework.perk.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Butcher's smoker and armorer's blast furnace perks.
 *  - Level II (both): fuel burns {@code perks.fuelMultiplier} longer (1.5).
 *  - Level III smoker: cooks {@code perks.smokerSpeedMultiplier} faster (1.5), on top of the smoker's own speed.
 *  - Level III blast furnace: iron and gold armor and tools give back ingots (see FurnacePerks).
 * Plain furnaces are never affected.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
	@Unique
	private static String villagerework$profession(Object blockEntity) {
		if (blockEntity instanceof SmokerBlockEntity) return Perks.BUTCHER;
		if (blockEntity instanceof BlastFurnaceBlockEntity) return Perks.ARMORER;
		return null;
	}

	@Inject(method = "getBurnDuration", at = @At("RETURN"), cancellable = true)
	private void villagerework$fuel(ServerLevel level, ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
		String profession = villagerework$profession(this);
		if (profession == null || cir.getReturnValue() <= 0) return;
		BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
		if (Perks.has(level, pos, profession, 2)) {
			cir.setReturnValue((int) Math.round(cir.getReturnValue() * VRConfig.get().perks.fuelMultiplier));
		}
	}

	@Inject(method = "getSpeedMultiplier", at = @At("RETURN"), cancellable = true)
	private void villagerework$speed(ServerLevel level, ItemStack fuel, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof SmokerBlockEntity) || cir.getReturnValue() <= 0) return;
		BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
		if (Perks.has(level, pos, Perks.BUTCHER, 3)) {
			cir.setReturnValue((float) (cir.getReturnValue() * VRConfig.get().perks.smokerSpeedMultiplier));
		}
	}

	// ---------------------------------------------------------------- blast furnace recycling

	@Inject(method = "serverTick", at = @At("HEAD"))
	private static void villagerework$tickStart(ServerLevel level, BlockPos pos, BlockState state,
			AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
		PerkContext.set(furnace instanceof BlastFurnaceBlockEntity ? Perks.level(level, pos, Perks.ARMORER) : 0);
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private static void villagerework$tickEnd(ServerLevel level, BlockPos pos, BlockState state,
			AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
		PerkContext.clear();
	}

	@WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack villagerework$recycle(AbstractCookingRecipe recipe, SingleRecipeInput input,
			Operation<ItemStack> original) {
		ItemStack result = original.call(recipe, input);
		if (PerkContext.get() < 3) return result;
		ItemStack recycled = FurnacePerks.recycle(input.item());
		return recycled != null ? recycled : result;
	}
}
