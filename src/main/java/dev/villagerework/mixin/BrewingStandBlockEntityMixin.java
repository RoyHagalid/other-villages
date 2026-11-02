package dev.villagerework.mixin;

import dev.villagerework.config.VRConfig;
import dev.villagerework.perk.Perks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cleric's brewing stand, level II: brews {@code perks.brewingSpeedMultiplier} faster (1.5 = 20 s → about 13 s).
 * The speed is taken when blaze powder is added, like vanilla's fuel speed.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "getSpeedMultiplier", at = @At("RETURN"), cancellable = true)
	private void villagerework$speed(ServerLevel level, BrewingFuel fuel, CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValue() <= 0) return;
		if (Perks.has(level, ((BlockEntity) (Object) this).getBlockPos(), Perks.CLERIC, 2)) {
			cir.setReturnValue((float) (cir.getReturnValue() * VRConfig.get().perks.brewingSpeedMultiplier));
		}
	}
}
