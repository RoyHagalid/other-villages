package dev.villagerework.mixin;

import dev.villagerework.perk.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The weaponsmith's grindstone mills from level II, so its screen says so ("Mill, Repair & Disenchant"). */
@Mixin(GrindstoneBlock.class)
public abstract class GrindstoneBlockMixin {
	@Inject(method = "getMenuProvider", at = @At("RETURN"), cancellable = true)
	private void villagerework$title(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
		MenuProvider vanilla = cir.getReturnValue();
		if (vanilla == null || !Perks.has(level, pos, Perks.WEAPONSMITH, 2)) return;
		cir.setReturnValue(new SimpleMenuProvider(vanilla, Component.literal("Mill, Repair & Disenchant")));
	}
}
