package dev.villagerework.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.villagerework.perk.TakeHomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Placing a take-home workstation item records its tier at the new position (see TakeHomes). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
	private void villagerework$placed(Block block, Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack, Operation<Void> original) {
		original.call(block, level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) TakeHomes.placed(serverLevel, pos, stack);
		// The placing player's client shows the tier marks at once instead of waiting for the server (0.17.6).
		else if (level.isClientSide()) TakeHomes.fromItem(stack).ifPresent(e ->
				dev.villagerework.perk.StationLooks.clientPrediction.accept(pos.immutable(), e.tier()));
	}
}
