package dev.villagerework.mixin;

import dev.villagerework.perk.LoomPerks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A banner's tooltip lists all its layers, not only the first 6. */
@Mixin(BannerPatternLayers.class)
public abstract class BannerPatternLayersMixin {
	@ModifyConstant(method = "addToTooltip", constant = @Constant(intValue = 6))
	private int villagerework$tooltipLimit(int vanilla) {
		return LoomPerks.highest();
	}
}
