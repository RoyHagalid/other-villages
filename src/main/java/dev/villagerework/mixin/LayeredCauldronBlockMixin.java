package dev.villagerework.mixin;

import dev.villagerework.perk.CauldronPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leatherworker's cauldron: items in its water get soaked (dirt → mud, concrete powder → concrete). */
@Mixin(LayeredCauldronBlock.class)
public abstract class LayeredCauldronBlockMixin {
	@Inject(method = "entityInside", at = @At("HEAD"))
	private void villagerework$soak(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effects, boolean intersects, CallbackInfo ci) {
		if (level instanceof ServerLevel server && entity instanceof ItemEntity item) {
			CauldronPerks.soak(server, pos, state, item);
		}
	}
}
